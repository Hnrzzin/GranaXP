import {initializeApp} from "firebase-admin/app";
import {getAuth} from "firebase-admin/auth";
import {
  FieldValue,
  Timestamp,
  getFirestore,
  type DocumentData,
  type DocumentSnapshot,
  type Transaction,
} from "firebase-admin/firestore";
import {HttpsError, onCall, type CallableRequest} from "firebase-functions/v2/https";
import {deleteAccountForUid} from "./accountDeletion";

initializeApp();

const db = getFirestore();
const auth = getAuth();
const callableOptions = {region: "southamerica-east1"};
const GOAL_COMPLETION_XP = 200;
const MAX_MONEY = 1_000_000_000_000;

type XpResult = {xpEarned: number; leveledUp: boolean};

function requireUid(request: CallableRequest<unknown>): string {
  const uid = request.auth?.uid;
  if (!uid) throw new HttpsError("unauthenticated", "É necessário estar autenticado.");
  return uid;
}

function requireObject(value: unknown): Record<string, unknown> {
  if (value === null || typeof value !== "object" || Array.isArray(value)) {
    throw new HttpsError("invalid-argument", "Dados da chamada inválidos.");
  }
  return value as Record<string, unknown>;
}

function requireString(data: Record<string, unknown>, field: string, maxLength = 200): string {
  const value = data[field];
  if (typeof value !== "string" || value.trim().length === 0 || value.length > maxLength) {
    throw new HttpsError("invalid-argument", `Campo ${field} inválido.`);
  }
  return value;
}

function requireMoney(data: Record<string, unknown>, field: string, allowZero = true): number {
  const value = data[field];
  if (typeof value !== "number" || !Number.isFinite(value) || value < (allowZero ? 0 : Number.EPSILON) || value > MAX_MONEY) {
    throw new HttpsError("invalid-argument", `Campo ${field} inválido.`);
  }
  return value;
}

function optionalTimestamp(data: Record<string, unknown>, field: string): Timestamp | null {
  const value = data[field];
  if (value === undefined || value === null) return null;
  if (value instanceof Timestamp) return value;
  if (typeof value === "object" && !Array.isArray(value)) {
    const timestamp = value as Record<string, unknown>;
    const seconds = timestamp.seconds ?? timestamp._seconds;
    const nanoseconds = timestamp.nanoseconds ?? timestamp._nanoseconds;
    if (typeof seconds === "number" && Number.isInteger(seconds) &&
        typeof nanoseconds === "number" && Number.isInteger(nanoseconds) &&
        nanoseconds >= 0 && nanoseconds < 1_000_000_000) {
      return new Timestamp(seconds, nanoseconds);
    }
  }
  throw new HttpsError("invalid-argument", `Campo ${field} inválido.`);
}

function requireDeadline(data: Record<string, unknown>): string {
  const deadline = requireString(data, "deadline", 20);
  if (!["CURTO", "MEDIO", "LONGO"].includes(deadline)) {
    throw new HttpsError("invalid-argument", "Campo deadline inválido.");
  }
  return deadline;
}

function isCompleted(data: DocumentData): boolean {
  return data.isCompleted === true || data.completed === true;
}

function applyXp(user: DocumentData, amount: number): {updates: DocumentData; leveledUp: boolean} {
  const {level, xp, nextLevelXp} = user;
  if (!Number.isInteger(level) || level < 1 || !Number.isInteger(xp) || xp < 0 ||
      !Number.isInteger(nextLevelXp) || nextLevelXp < 1) {
    throw new HttpsError("failed-precondition", "Perfil de XP inválido.");
  }
  let newXp = xp + amount;
  let newLevel = level;
  let newNextLevelXp = nextLevelXp;
  let leveledUp = false;
  while (newXp >= newNextLevelXp) {
    newXp -= newNextLevelXp;
    newLevel += 1;
    newNextLevelXp = Math.trunc(newNextLevelXp * 1.2);
    leveledUp = true;
  }
  return {updates: {xp: newXp, level: newLevel, nextLevelXp: newNextLevelXp}, leveledUp};
}

function wrapUnexpected(error: unknown): never {
  if (error instanceof HttpsError) throw error;
  console.error(error);
  throw new HttpsError("internal", "Não foi possível concluir a operação.");
}

async function lessonProgressDocuments(transaction: Transaction, uid: string, lessonId: string): Promise<DocumentSnapshot[]> {
  const collection = db.collection(`users/${uid}/lessonProgress`);
  const [matches, canonical] = await Promise.all([
    transaction.get(collection.where("lessonId", "==", lessonId)),
    transaction.get(collection.doc(lessonId)),
  ]);
  const documents: DocumentSnapshot[] = matches.docs.slice();
  if (canonical.exists && canonical.data()?.lessonId === lessonId &&
      !documents.some((document) => document.id === lessonId)) {
    documents.push(canonical);
  }
  return documents;
}

function completedActivityIds(documents: DocumentSnapshot[], historicalCompletion: boolean): string[] {
  const ids = new Set<string>();
  for (const document of documents) {
    const data = document.data()!;
    if (historicalCompletion || data.activityValidationVersion === 1) {
      if (Array.isArray(data.completedActivityIds)) {
        data.completedActivityIds.filter((id: unknown): id is string => typeof id === "string")
          .forEach((id: string) => ids.add(id));
      }
    }
  }
  return [...ids].sort();
}

async function assertLessonUnlocked(
  transaction: Transaction, uid: string, lessonId: string, lesson: DocumentData, alreadyCompleted: boolean,
): Promise<void> {
  if (alreadyCompleted) return; // Previously completed lessons always remain available for review.
  const moduleId = typeof lesson.moduleId === "string" ? lesson.moduleId : "";
  if (moduleId && !(await transaction.get(db.doc(`modules/${moduleId}`))).exists) {
    throw new HttpsError("failed-precondition", "Módulo da lição não encontrado.");
  }
  // The legacy group also includes documents whose moduleId field is absent.
  const lessons = (await transaction.get(db.collection("lessons"))).docs
    .filter((document) => (typeof document.data().moduleId === "string" ? document.data().moduleId : "") === moduleId)
    .sort((left, right) => {
      const orderLeft = Number.isInteger(left.data().order) ? left.data().order : 0;
      const orderRight = Number.isInteger(right.data().order) ? right.data().order : 0;
      return orderLeft - orderRight || left.id.localeCompare(right.id);
    });
  const position = lessons.findIndex((document) => document.id === lessonId);
  if (position < 0) throw new HttpsError("failed-precondition", "Lição fora da trilha.");
  if (position === 0) return;
  const previous = await lessonProgressDocuments(transaction, uid, lessons[position - 1].id);
  if (!previous.some((document) => isCompleted(document.data()!))) {
    throw new HttpsError("failed-precondition", "Conclua a lição anterior antes de continuar.");
  }
}

function validatedActivity(block: DocumentData): {alternatives: Record<string, string>; answers: string[]; feedback: string} {
  const alternatives = block.alternatives;
  const answers = block.correctAnswerIds;
  const feedback = block.feedback;
  if (typeof alternatives !== "object" || alternatives === null || Array.isArray(alternatives) ||
      Object.keys(alternatives).length === 0 ||
      !Object.values(alternatives).every((value) => typeof value === "string") ||
      !Array.isArray(answers) || answers.length === 0 || answers.length > 100 ||
      !answers.every((id) => typeof id === "string" && Object.hasOwn(alternatives, id)) ||
      new Set(answers).size !== answers.length ||
      typeof block.activityType !== "string" || block.activityType.length === 0 ||
      typeof feedback !== "string" || feedback.trim().length === 0) {
    throw new HttpsError("failed-precondition", "Atividade do catálogo inválida.");
  }
  return {alternatives, answers, feedback};
}

export const openLesson = onCall(callableOptions, async (request) => {
  const uid = requireUid(request);
  const lessonId = requireString(requireObject(request.data), "lessonId");
  const lessonRef = db.doc(`lessons/${lessonId}`);
  const userRef = db.doc(`users/${uid}`);
  try {
    return await db.runTransaction(async (transaction) => {
      const [lesson, user, blocks, progress] = await Promise.all([
        transaction.get(lessonRef), transaction.get(userRef),
        transaction.get(lessonRef.collection("blocks")),
        lessonProgressDocuments(transaction, uid, lessonId),
      ]);
      if (!lesson.exists) throw new HttpsError("not-found", "Lição não encontrada.");
      if (!user.exists) throw new HttpsError("failed-precondition", "Perfil não encontrado.");
      const alreadyCompleted = progress.some((document) => isCompleted(document.data()!));
      await assertLessonUnlocked(transaction, uid, lessonId, lesson.data()!, alreadyCompleted);
      const visibleBlocks = blocks.docs
        .sort((left, right) => (left.data().order ?? 0) - (right.data().order ?? 0) || left.id.localeCompare(right.id))
        .map((document) => {
          const data = document.data();
          const activity = String(data.type).toUpperCase() === "ACTIVITY" ? validatedActivity(data) : null;
          return {
            id: document.id,
            type: data.type ?? "",
            order: data.order ?? 0,
            title: data.title ?? "",
            content: data.content ?? "",
            activityType: activity ? data.activityType : null,
            alternatives: activity?.alternatives ?? {},
            selectionMode: activity ? (activity.answers.length > 1 ? "MULTIPLE" : "SINGLE") : null,
            chartImageUrl: data.chartImageUrl ?? null,
          };
        });
      const target = progress.find((document) => document.id === lessonId) ?? progress[0];
      if (target) {
        transaction.update(target.ref, {lastAccessed: FieldValue.serverTimestamp()});
      } else {
        transaction.create(userRef.collection("lessonProgress").doc(lessonId), {
          lessonId, isCompleted: false, completedActivityIds: [],
          lastAccessed: FieldValue.serverTimestamp(), completedAt: null,
        });
      }
      return {blocks: visibleBlocks, completedActivityIds: completedActivityIds(progress, alreadyCompleted)};
    });
  } catch (error) {
    return wrapUnexpected(error);
  }
});

export const submitLessonActivity = onCall(callableOptions, async (request) => {
  const uid = requireUid(request);
  const data = requireObject(request.data);
  const lessonId = requireString(data, "lessonId");
  const blockId = requireString(data, "blockId");
  const selected = data.selectedAnswerIds;
  if (!Array.isArray(selected) || selected.length === 0 || selected.length > 100 ||
      !selected.every((id) => typeof id === "string" && id.length > 0 && id.length <= 200) ||
      new Set(selected).size !== selected.length) {
    throw new HttpsError("invalid-argument", "Alternativas selecionadas inválidas.");
  }
  const lessonRef = db.doc(`lessons/${lessonId}`);
  try {
    return await db.runTransaction(async (transaction) => {
      const [lesson, block, progress] = await Promise.all([
        transaction.get(lessonRef), transaction.get(lessonRef.collection("blocks").doc(blockId)),
        lessonProgressDocuments(transaction, uid, lessonId),
      ]);
      if (!lesson.exists || !block.exists || String(block.data()?.type).toUpperCase() !== "ACTIVITY") {
        throw new HttpsError("not-found", "Atividade não encontrada.");
      }
      if (progress.length === 0) throw new HttpsError("failed-precondition", "Abra a lição antes de responder.");
      const alreadyCompleted = progress.some((document) => isCompleted(document.data()!));
      await assertLessonUnlocked(transaction, uid, lessonId, lesson.data()!, alreadyCompleted);
      const activity = validatedActivity(block.data()!);
      if (selected.some((id) => !Object.hasOwn(activity.alternatives, id)) ||
          (activity.answers.length === 1 && selected.length !== 1)) {
        throw new HttpsError("invalid-argument", "Alternativas selecionadas inválidas.");
      }
      const correct = selected.length === activity.answers.length &&
        selected.every((id) => activity.answers.includes(id));
      if (!correct) return {correct: false, feedback: activity.feedback};
      const verifiedIds = completedActivityIds(progress, alreadyCompleted);
      if (!verifiedIds.includes(blockId)) {
        const target = progress.find((document) => document.id === lessonId) ?? progress[0];
        transaction.update(target.ref, {
          completedActivityIds: [...new Set([...verifiedIds, blockId])].sort(),
          activityValidationVersion: 1,
        });
        verifiedIds.push(blockId);
      }
      return {correct: true, completedActivityIds: [...new Set(verifiedIds)].sort()};
    });
  } catch (error) {
    return wrapUnexpected(error);
  }
});

export const completeLesson = onCall(callableOptions, async (request) => {
  const uid = requireUid(request);
  const lessonId = requireString(requireObject(request.data), "lessonId");
  const lessonRef = db.doc(`lessons/${lessonId}`);
  const userRef = db.doc(`users/${uid}`);
  try {
    return await db.runTransaction(async (transaction): Promise<XpResult & {alreadyCompleted: boolean}> => {
      const [lessonSnapshot, userSnapshot, progressDocuments, blocksSnapshot] = await Promise.all([
        transaction.get(lessonRef),
        transaction.get(userRef),
        lessonProgressDocuments(transaction, uid, lessonId),
        transaction.get(lessonRef.collection("blocks")),
      ]);
      if (!lessonSnapshot.exists) throw new HttpsError("not-found", "Lição não encontrada.");
      if (!userSnapshot.exists) throw new HttpsError("failed-precondition", "Perfil não encontrado.");
      if (progressDocuments.length === 0) {
        throw new HttpsError("failed-precondition", "Abra a lição antes de concluí-la.");
      }
      if (progressDocuments.some((document) => isCompleted(document.data()!))) {
        return {xpEarned: 0, leveledUp: false, alreadyCompleted: true};
      }
      await assertLessonUnlocked(transaction, uid, lessonId, lessonSnapshot.data()!, false);
      const verifiedIds = new Set(completedActivityIds(progressDocuments, false));
      const requiredActivityIds = blocksSnapshot.docs
        .filter((block) => String(block.data().type).toUpperCase() === "ACTIVITY")
        .map((block) => block.id);
      if (!requiredActivityIds.every((id) => verifiedIds.has(id))) {
        throw new HttpsError("failed-precondition", "Conclua todas as atividades antes de finalizar a lição.");
      }

      const target = (progressDocuments.find((document) => document.id === lessonId) ?? progressDocuments[0])!;
      const xpReward = lessonSnapshot.data()?.xpReward;
      if (!Number.isInteger(xpReward) || xpReward < 0 || xpReward > 1_000_000) {
        throw new HttpsError("failed-precondition", "Recompensa da lição inválida.");
      }
      const xp = applyXp(userSnapshot.data()!, xpReward);
      transaction.update(target.ref, {isCompleted: true, completedAt: FieldValue.serverTimestamp()});
      transaction.update(userRef, xp.updates);
      return {xpEarned: xpReward, leveledUp: xp.leveledUp, alreadyCompleted: false};
    });
  } catch (error) {
    return wrapUnexpected(error);
  }
});

export const createGoal = onCall(callableOptions, async (request) => {
  const uid = requireUid(request);
  const data = requireObject(request.data);
  const requestId = requireString(data, "requestId", 100);
  if (!/^[A-Za-z0-9_-]{8,100}$/.test(requestId)) {
    throw new HttpsError("invalid-argument", "requestId inválido.");
  }
  const title = requireString(data, "title");
  const targetAmount = requireMoney(data, "targetAmount");
  const currentAmount = requireMoney(data, "currentAmount");
  const deadline = requireDeadline(data);
  const deadlineDate = optionalTimestamp(data, "deadlineDate");
  const userRef = db.doc(`users/${uid}`);
  const requestRef = userRef.collection("goalCreationRequests").doc(requestId);
  const goalRef = userRef.collection("goals").doc();
  const fingerprint = JSON.stringify({
    title, targetAmount, currentAmount, deadline,
    deadlineDate: deadlineDate ? [deadlineDate.seconds, deadlineDate.nanoseconds] : null,
  });
  try {
    const result = await db.runTransaction(async (transaction): Promise<XpResult & {goalId: string}> => {
      const [userSnapshot, requestSnapshot] = await Promise.all([
        transaction.get(userRef), transaction.get(requestRef),
      ]);
      if (!userSnapshot.exists) throw new HttpsError("failed-precondition", "Perfil não encontrado.");
      if (requestSnapshot.exists) {
        const previous = requestSnapshot.data()!;
        if (previous.fingerprint !== fingerprint || typeof previous.goalId !== "string") {
          throw new HttpsError("already-exists", "requestId já foi usado para outra meta.");
        }
        return {goalId: previous.goalId, xpEarned: 0, leveledUp: false};
      }
      const completed = targetAmount > 0 && currentAmount >= targetAmount;
      const goal: DocumentData = {title, targetAmount, currentAmount, deadline, deadlineDate};
      if (completed) goal.completedAt = FieldValue.serverTimestamp();
      transaction.create(goalRef, goal);
      transaction.create(requestRef, {goalId: goalRef.id, fingerprint, createdAt: FieldValue.serverTimestamp()});
      if (!completed) return {goalId: goalRef.id, xpEarned: 0, leveledUp: false};
      const xp = applyXp(userSnapshot.data()!, GOAL_COMPLETION_XP);
      transaction.update(userRef, xp.updates);
      return {goalId: goalRef.id, xpEarned: GOAL_COMPLETION_XP, leveledUp: xp.leveledUp};
    });
    return result;
  } catch (error) {
    return wrapUnexpected(error);
  }
});

export const updateGoalProgress = onCall(callableOptions, async (request) => {
  const uid = requireUid(request);
  const data = requireObject(request.data);
  const goalId = requireString(data, "goalId");
  const amountToAdd = requireMoney(data, "amountToAdd", false);
  const userRef = db.doc(`users/${uid}`);
  const goalRef = userRef.collection("goals").doc(goalId);
  try {
    return await db.runTransaction(async (transaction): Promise<XpResult> => {
      const [userSnapshot, goalSnapshot] = await Promise.all([transaction.get(userRef), transaction.get(goalRef)]);
      if (!userSnapshot.exists) throw new HttpsError("failed-precondition", "Perfil não encontrado.");
      if (!goalSnapshot.exists) throw new HttpsError("not-found", "Meta não encontrada.");
      const goal = goalSnapshot.data()!;
      if (typeof goal.targetAmount !== "number" || typeof goal.currentAmount !== "number") {
        throw new HttpsError("failed-precondition", "Meta inválida.");
      }
      const wasComplete = goal.targetAmount > 0 && goal.currentAmount >= goal.targetAmount;
      const newCurrentAmount = goal.currentAmount + amountToAdd;
      if (!Number.isFinite(newCurrentAmount) || newCurrentAmount > MAX_MONEY) {
        throw new HttpsError("invalid-argument", "O valor acumulado da meta é inválido.");
      }
      const isCompleteNow = goal.targetAmount > 0 && newCurrentAmount >= goal.targetAmount;
      const updates: DocumentData = {currentAmount: newCurrentAmount};
      if (isCompleteNow && goal.completedAt == null) updates.completedAt = FieldValue.serverTimestamp();
      transaction.update(goalRef, updates);
      if (wasComplete || !isCompleteNow || goal.completedAt != null) return {xpEarned: 0, leveledUp: false};
      const xp = applyXp(userSnapshot.data()!, GOAL_COMPLETION_XP);
      transaction.update(userRef, xp.updates);
      return {xpEarned: GOAL_COMPLETION_XP, leveledUp: xp.leveledUp};
    });
  } catch (error) {
    return wrapUnexpected(error);
  }
});

export const updateGoalDetails = onCall(callableOptions, async (request) => {
  const uid = requireUid(request);
  const data = requireObject(request.data);
  const goalId = requireString(data, "goalId");
  const title = requireString(data, "title");
  const targetAmount = requireMoney(data, "targetAmount");
  const deadline = requireDeadline(data);
  const deadlineDate = optionalTimestamp(data, "deadlineDate");
  const goalRef = db.doc(`users/${uid}/goals/${goalId}`);
  try {
    await db.runTransaction(async (transaction) => {
      const goalSnapshot = await transaction.get(goalRef);
      if (!goalSnapshot.exists) throw new HttpsError("not-found", "Meta não encontrada.");
      const goal = goalSnapshot.data()!;
      if (typeof goal.currentAmount !== "number") throw new HttpsError("failed-precondition", "Meta inválida.");
      const updates: DocumentData = {title, targetAmount, deadline, deadlineDate};
      if (targetAmount > 0 && goal.currentAmount >= targetAmount && goal.completedAt == null) {
        updates.completedAt = FieldValue.serverTimestamp();
      }
      transaction.update(goalRef, updates);
    });
    return {xpEarned: 0, leveledUp: false};
  } catch (error) {
    return wrapUnexpected(error);
  }
});

export const deleteAccount = onCall(callableOptions, async (request) => {
  const uid = requireUid(request);
  try {
    await deleteAccountForUid(uid, db, auth);
    return {deleted: true};
  } catch (error) {
    return wrapUnexpected(error);
  }
});
