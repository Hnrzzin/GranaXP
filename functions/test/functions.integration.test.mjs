import assert from "node:assert/strict";
import {after, before, beforeEach, describe, test} from "node:test";
import {initializeApp as initializeAdminApp} from "firebase-admin/app";
import {getAuth as getAdminAuth} from "firebase-admin/auth";
import {getFirestore as getAdminFirestore, Timestamp} from "firebase-admin/firestore";
import {initializeApp} from "firebase/app";
import {
  connectAuthEmulator,
  createUserWithEmailAndPassword,
  getAuth,
  signOut,
} from "firebase/auth";
import {connectFunctionsEmulator, getFunctions, httpsCallable} from "firebase/functions";
import {Timestamp as ClientTimestamp} from "firebase/firestore";

const PROJECT_ID = "demo-granaxp";
const REGION = "southamerica-east1";
const FIRESTORE_HOST = process.env.FIRESTORE_EMULATOR_HOST ?? "127.0.0.1:8080";
const AUTH_HOST = process.env.FIREBASE_AUTH_EMULATOR_HOST ?? "127.0.0.1:9099";
const FUNCTIONS_PORT = Number(process.env.FUNCTIONS_EMULATOR_PORT ?? 5001);

const adminApp = initializeAdminApp({projectId: PROJECT_ID});
const adminDb = getAdminFirestore(adminApp);
const adminAuth = getAdminAuth(adminApp);

const clients = [];
let sequence = 0;

async function clearEmulators() {
  await fetch(`http://${FIRESTORE_HOST}/emulator/v1/projects/${PROJECT_ID}/databases/(default)/documents`, {method: "DELETE"});
  await fetch(`http://${AUTH_HOST}/emulator/v1/projects/${PROJECT_ID}/accounts`, {method: "DELETE"});
}

async function authenticatedClient() {
  sequence += 1;
  const app = initializeApp({apiKey: "demo-key", projectId: PROJECT_ID}, `client-${sequence}`);
  const auth = getAuth(app);
  connectAuthEmulator(auth, `http://${AUTH_HOST}`, {disableWarnings: true});
  const email = `user-${sequence}@example.com`;
  const credential = await createUserWithEmailAndPassword(auth, email, "password123");
  const functions = getFunctions(app, REGION);
  connectFunctionsEmulator(functions, "127.0.0.1", FUNCTIONS_PORT);
  clients.push({app, auth});
  return {uid: credential.user.uid, email, auth, functions};
}

async function seedUser(uid, email, overrides = {}) {
  await adminDb.doc(`users/${uid}`).set({
    name: "Usuário",
    email,
    level: 1,
    xp: 0,
    nextLevelXp: 100,
    ...overrides,
  });
}

async function seedLesson(id, {xpReward = 50, activityIds = [], moduleId = "", order = 1} = {}) {
  await adminDb.doc(`lessons/${id}`).set({title: "Lição", xpReward, order, moduleId});
  await Promise.all(activityIds.map((blockId, index) =>
    adminDb.doc(`lessons/${id}/blocks/${blockId}`).set({
      type: "ACTIVITY",
      order: index,
      activityType: "MULTIPLE_CHOICE",
      alternatives: {a: "A", b: "B"},
      correctAnswerIds: ["a"],
      feedback: "Tente novamente.",
    }),
  ));
}

async function seedModuleAchievement(id, moduleId) {
  await adminDb.doc(`achievements/${id}`).set({
    title: "Módulo concluído", category: "EDUCACAO", requirementType: "MODULE_COMPLETION",
    requirementValue: 1, referenceId: moduleId,
  });
}

before(async () => {
  await clearEmulators();
});

beforeEach(clearEmulators);

after(async () => {
  await Promise.all(clients.map(({auth}) => signOut(auth).catch(() => undefined)));
});

describe("completeLesson", () => {
  test("abre a lição sem revelar o gabarito e só registra acerto validado", async () => {
    const {uid, email, functions} = await authenticatedClient();
    await seedUser(uid, email);
    await seedLesson("lesson-1", {activityIds: ["activity-1"]});
    const opened = await httpsCallable(functions, "openLesson")({lessonId: "lesson-1"});
    assert.equal(opened.data.blocks.length, 1);
    assert.equal(opened.data.blocks[0].selectionMode, "SINGLE");
    assert.equal("correctAnswerIds" in opened.data.blocks[0], false);
    assert.equal("feedback" in opened.data.blocks[0], false);
    assert.deepEqual(opened.data.completedActivityIds, []);

    const answer = httpsCallable(functions, "submitLessonActivity");
    const wrong = await answer({lessonId: "lesson-1", blockId: "activity-1", selectedAnswerIds: ["b"]});
    assert.equal(wrong.data.correct, false);
    assert.deepEqual((await adminDb.doc(`users/${uid}/lessonProgress/lesson-1`).get()).data().completedActivityIds, []);
    const correct = await answer({lessonId: "lesson-1", blockId: "activity-1", selectedAnswerIds: ["a"]});
    assert.equal(correct.data.correct, true);
    const again = await answer({lessonId: "lesson-1", blockId: "activity-1", selectedAnswerIds: ["a"]});
    assert.equal(again.data.correct, true);
    const progress = (await adminDb.doc(`users/${uid}/lessonProgress/lesson-1`).get()).data();
    assert.deepEqual(progress.completedActivityIds, ["activity-1"]);
    assert.equal(progress.activityValidationVersion, 1);
  });

  test("aceita apenas o conjunto exato numa atividade de seleção múltipla", async () => {
    const {uid, email, functions} = await authenticatedClient();
    await seedUser(uid, email);
    await seedLesson("lesson-multi", {activityIds: ["activity-1"]});
    await adminDb.doc("lessons/lesson-multi/blocks/activity-1").update({
      alternatives: {a: "A", b: "B", c: "C"}, correctAnswerIds: ["a", "c"],
    });
    const opened = await httpsCallable(functions, "openLesson")({lessonId: "lesson-multi"});
    assert.equal(opened.data.blocks[0].selectionMode, "MULTIPLE");
    const answer = httpsCallable(functions, "submitLessonActivity");
    assert.equal((await answer({lessonId: "lesson-multi", blockId: "activity-1", selectedAnswerIds: ["a"]})).data.correct, false);
    assert.equal((await answer({lessonId: "lesson-multi", blockId: "activity-1", selectedAnswerIds: ["a", "b", "c"]})).data.correct, false);
    await assert.rejects(answer({lessonId: "lesson-multi", blockId: "activity-1", selectedAnswerIds: ["a", "a"]}));
    assert.equal((await answer({lessonId: "lesson-multi", blockId: "activity-1", selectedAnswerIds: ["c", "a"]})).data.correct, true);
  });

  test("desbloqueio é verificado no servidor antes de abrir, responder e concluir", async () => {
    const {uid, email, functions} = await authenticatedClient();
    await seedUser(uid, email);
    await adminDb.doc("modules/module-a").set({idModule: "module-a", order: 1});
    await adminDb.doc("modules/module-b").set({idModule: "module-b", order: 2});
    await seedLesson("lesson-first", {moduleId: "module-a", order: 1});
    await seedLesson("lesson-second", {moduleId: "module-a", order: 2, activityIds: ["activity-1"]});
    await seedLesson("lesson-other-module", {moduleId: "module-b", order: 1});
    await adminDb.doc(`users/${uid}/lessonProgress/lesson-second`).set({
      lessonId: "lesson-second", isCompleted: false, completedActivityIds: [], lastAccessed: Timestamp.now(),
    });
    await assert.rejects(httpsCallable(functions, "openLesson")({lessonId: "lesson-second"}));
    await assert.rejects(httpsCallable(functions, "submitLessonActivity")({
      lessonId: "lesson-second", blockId: "activity-1", selectedAnswerIds: ["a"],
    }));
    await assert.rejects(httpsCallable(functions, "completeLesson")({lessonId: "lesson-second"}));
    await httpsCallable(functions, "openLesson")({lessonId: "lesson-other-module"});
    await httpsCallable(functions, "openLesson")({lessonId: "lesson-first"});
    await httpsCallable(functions, "completeLesson")({lessonId: "lesson-first"});
    assert.equal((await httpsCallable(functions, "openLesson")({lessonId: "lesson-second"})).data.blocks.length, 1);
  });

  test("IDs antigos de atividade incompleta não provam acerto e devem ser refeitos", async () => {
    const {uid, email, functions} = await authenticatedClient();
    await seedUser(uid, email);
    await seedLesson("lesson-1", {activityIds: ["activity-1"]});
    await adminDb.doc(`users/${uid}/lessonProgress/lesson-1`).set({
      lessonId: "lesson-1", isCompleted: false, completedActivityIds: ["activity-1"],
      lastAccessed: Timestamp.now(), completedAt: null,
    });
    const opened = await httpsCallable(functions, "openLesson")({lessonId: "lesson-1"});
    assert.deepEqual(opened.data.completedActivityIds, []);
    await assert.rejects(httpsCallable(functions, "completeLesson")({lessonId: "lesson-1"}));
    await httpsCallable(functions, "submitLessonActivity")({lessonId: "lesson-1", blockId: "activity-1", selectedAnswerIds: ["a"]});
    assert.equal((await httpsCallable(functions, "completeLesson")({lessonId: "lesson-1"})).data.xpEarned, 50);
  });

  test("conclusão histórica não inventa completedAt nem recebe XP", async () => {
    const {uid, email, functions} = await authenticatedClient();
    await seedUser(uid, email);
    await seedLesson("lesson-legacy");
    await adminDb.doc(`users/${uid}/lessonProgress/legacy-id`).set({
      userId: uid, lessonId: "lesson-legacy", isCompleted: true, lastAccessed: Timestamp.now(),
    });
    const result = await httpsCallable(functions, "completeLesson")({lessonId: "lesson-legacy"});
    assert.equal(result.data.xpEarned, 0);
    assert.equal((await adminDb.doc(`users/${uid}/lessonProgress/legacy-id`).get()).data().completedAt, undefined);
  });

  test("rejeita chamada sem autenticação", async () => {
    const app = initializeApp({apiKey: "demo-key", projectId: PROJECT_ID}, `anon-${++sequence}`);
    const functions = getFunctions(app, REGION);
    connectFunctionsEmulator(functions, "127.0.0.1", FUNCTIONS_PORT);
    await assert.rejects(
      httpsCallable(functions, "completeLesson")({lessonId: "lesson-1"}),
      (error) => error.code === "functions/unauthenticated",
    );
  });

  test("valida atividades e concede o XP do catálogo com nível atualizado", async () => {
    const {uid, email, functions} = await authenticatedClient();
    await seedUser(uid, email, {xp: 90});
    await seedLesson("lesson-1", {xpReward: 50, activityIds: ["activity-1"]});
    await adminDb.doc(`users/${uid}/lessonProgress/lesson-1`).set({
      lessonId: "lesson-1",
      isCompleted: false,
      completedActivityIds: ["activity-1"],
      activityValidationVersion: 1,
      lastAccessed: Timestamp.now(),
      completedAt: null,
    });

    const result = await httpsCallable(functions, "completeLesson")({lessonId: "lesson-1", xpReward: 999999});
    assert.deepEqual(result.data, {xpEarned: 50, leveledUp: true, alreadyCompleted: false});
    const user = (await adminDb.doc(`users/${uid}`).get()).data();
    assert.equal(user.level, 2);
    assert.equal(user.xp, 40);
    assert.equal(user.nextLevelXp, 120);
    const progress = (await adminDb.doc(`users/${uid}/lessonProgress/lesson-1`).get()).data();
    assert.equal(progress.isCompleted, true);
    assert.ok(progress.completedAt instanceof Timestamp);
  });

  test("não duplica XP em repetição nem em chamadas concorrentes", async () => {
    const {uid, email, functions} = await authenticatedClient();
    await seedUser(uid, email);
    await seedLesson("lesson-1", {xpReward: 60});
    await adminDb.doc(`users/${uid}/lessonProgress/lesson-1`).set({
      lessonId: "lesson-1", isCompleted: false, completedActivityIds: [], lastAccessed: Timestamp.now(), completedAt: null,
    });
    const call = httpsCallable(functions, "completeLesson");
    const [first, second] = await Promise.all([call({lessonId: "lesson-1"}), call({lessonId: "lesson-1"})]);
    assert.equal([first.data.xpEarned, second.data.xpEarned].sort((a, b) => a - b).join(","), "0,60");
    const third = await call({lessonId: "lesson-1"});
    assert.equal(third.data.xpEarned, 0);
    assert.equal(third.data.alreadyCompleted, true);
    assert.equal((await adminDb.doc(`users/${uid}`).get()).data().xp, 60);
  });

  test("recusa conclusão com atividade pendente sem alterar XP", async () => {
    const {uid, email, functions} = await authenticatedClient();
    await seedUser(uid, email);
    await seedLesson("lesson-1", {xpReward: 50, activityIds: ["activity-1"]});
    await adminDb.doc(`users/${uid}/lessonProgress/lesson-1`).set({
      lessonId: "lesson-1", isCompleted: false, completedActivityIds: [], lastAccessed: Timestamp.now(), completedAt: null,
    });
    await assert.rejects(
      httpsCallable(functions, "completeLesson")({lessonId: "lesson-1"}),
      (error) => error.code === "functions/failed-precondition",
    );
    assert.equal((await adminDb.doc(`users/${uid}`).get()).data().level, 1);
    assert.equal((await adminDb.doc(`users/${uid}`).get()).data().xp, 0);
  });

  test("conclui o documento legado sem criar recompensa duplicada", async () => {
    const {uid, email, functions} = await authenticatedClient();
    await seedUser(uid, email);
    await seedLesson("lesson-legacy", {xpReward: 25});
    await adminDb.doc(`users/${uid}/lessonProgress/legacy-auto-id`).set({
      userId: uid, lessonId: "lesson-legacy", isCompleted: false, completed: false,
      completedActivityIds: [], lastAccessed: Timestamp.now(), completedAt: null,
    });
    const result = await httpsCallable(functions, "completeLesson")({lessonId: "lesson-legacy"});
    assert.equal(result.data.xpEarned, 25);
    assert.equal((await adminDb.doc(`users/${uid}/lessonProgress/legacy-auto-id`).get()).data().isCompleted, true);
    assert.equal((await adminDb.doc(`users/${uid}/lessonProgress/lesson-legacy`).get()).exists, false);
  });

  test("conclusão de outro módulo não desbloqueia o módulo incompleto", async () => {
    const {uid, email, functions} = await authenticatedClient();
    const other = await authenticatedClient();
    await seedUser(uid, email);
    await seedUser(other.uid, other.email);
    await adminDb.doc("modules/module-a").set({title: "A"});
    await adminDb.doc("modules/module-b").set({title: "B"});
    await adminDb.doc("modules/module-empty").set({title: "Sem lições"});
    await seedLesson("a-1", {moduleId: "module-a", order: 1});
    await seedLesson("a-2", {moduleId: "module-a", order: 2});
    await seedLesson("b-1", {moduleId: "module-b", order: 1});
    await seedModuleAchievement("badge-a", "module-a");
    await seedModuleAchievement("badge-b", "module-b");
    await seedModuleAchievement("badge-empty", "module-empty");
    const call = httpsCallable(functions, "completeLesson");

    await httpsCallable(functions, "openLesson")({lessonId: "a-1"});
    await call({lessonId: "a-1"});
    assert.equal((await adminDb.doc(`users/${uid}/achievementProgress/badge-a`).get()).exists, false);

    await httpsCallable(functions, "openLesson")({lessonId: "b-1"});
    await call({lessonId: "b-1", userId: other.uid});
    const badgeB = (await adminDb.doc(`users/${uid}/achievementProgress/badge-b`).get()).data();
    assert.equal(badgeB.achievementId, "badge-b");
    assert.equal(badgeB.currentProgress, 1);
    assert.equal(badgeB.isUnlocked, true);
    assert.ok(badgeB.unlockedAt instanceof Timestamp);
    assert.deepEqual(Object.keys(badgeB).sort(), ["achievementId", "currentProgress", "isUnlocked", "unlockedAt"].sort());
    assert.equal((await adminDb.doc(`users/${uid}/achievementProgress/badge-a`).get()).exists, false);
    assert.equal((await adminDb.doc(`users/${uid}/achievementProgress/badge-empty`).get()).exists, false);
    assert.equal((await adminDb.doc(`users/${other.uid}/achievementProgress/badge-b`).get()).exists, false);
    assert.equal((await adminDb.doc(`users/${uid}`).get()).data().level, 2);
    assert.equal((await adminDb.doc(`users/${uid}`).get()).data().xp, 0);
  });

  test("conclusão histórica sem validação do backend não prova conclusão de módulo", async () => {
    const {uid, email, functions} = await authenticatedClient();
    await seedUser(uid, email);
    await adminDb.doc("modules/module-a").set({title: "A"});
    await seedLesson("a-1", {moduleId: "module-a", order: 1, xpReward: 25});
    await seedLesson("a-2", {moduleId: "module-a", order: 2, xpReward: 30});
    await seedModuleAchievement("badge-a", "module-a");
    await seedModuleAchievement("wrong-module", "module-b");
    await adminDb.doc(`users/${uid}/lessonProgress/legacy-random-id`).set({
      userId: uid, lessonId: "a-1", isCompleted: true,
      completedAt: Timestamp.now(), lastAccessed: Timestamp.now(),
    });
    await adminDb.doc("achievementProgress/legacy-badge").set({
      userId: uid, achievementId: "badge-a", currentProgress: 1, isUnlocked: true,
      lastUpdated: Timestamp.now(),
    });
    assert.equal((await adminDb.doc(`users/${uid}/achievementProgress/badge-a`).get()).exists, false);
    await httpsCallable(functions, "openLesson")({lessonId: "a-2"});
    const result = await httpsCallable(functions, "completeLesson")({lessonId: "a-2", moduleId: "module-b"});
    assert.equal(result.data.xpEarned, 30);
    assert.equal((await adminDb.doc(`users/${uid}/achievementProgress/badge-a`).get()).exists, false);
    assert.equal((await adminDb.doc(`users/${uid}/achievementProgress/wrong-module`).get()).exists, false);
    assert.equal((await adminDb.doc(`users/${uid}`).get()).data().xp, 30);
  });

  test("conclusão validada pelo backend em progresso de ID legado prova conclusão de módulo", async () => {
    const {uid, email, functions} = await authenticatedClient();
    await seedUser(uid, email);
    await adminDb.doc("modules/module-a").set({title: "A"});
    await seedLesson("a-1", {moduleId: "module-a", order: 1, xpReward: 25});
    await seedLesson("a-2", {moduleId: "module-a", order: 2, xpReward: 30});
    await seedModuleAchievement("badge-a", "module-a");
    const legacyProgressRef = adminDb.doc(`users/${uid}/lessonProgress/legacy-random-id`);
    await legacyProgressRef.set({
      userId: uid, lessonId: "a-1", isCompleted: false, lastAccessed: Timestamp.now(),
    });
    const call = httpsCallable(functions, "completeLesson");
    await call({lessonId: "a-1"});
    assert.equal((await legacyProgressRef.get()).data().completionValidationVersion, 1);
    assert.equal((await adminDb.doc(`users/${uid}/achievementProgress/badge-a`).get()).exists, false);
    await httpsCallable(functions, "openLesson")({lessonId: "a-2"});
    await call({lessonId: "a-2"});
    assert.equal((await adminDb.doc(`users/${uid}/achievementProgress/badge-a`).get()).data().isUnlocked, true);
  });

  test("chamadas concorrentes desbloqueiam uma vez e preservam o primeiro unlockedAt", async () => {
    const {uid, email, functions} = await authenticatedClient();
    await seedUser(uid, email);
    await adminDb.doc("modules/module-a").set({title: "A"});
    await seedLesson("a-1", {moduleId: "module-a", xpReward: 40});
    await seedModuleAchievement("badge-a", "module-a");
    await httpsCallable(functions, "openLesson")({lessonId: "a-1"});
    const call = httpsCallable(functions, "completeLesson");
    const [first, second] = await Promise.all([call({lessonId: "a-1"}), call({lessonId: "a-1"})]);
    assert.deepEqual([first.data.xpEarned, second.data.xpEarned].sort((a, b) => a - b), [0, 40]);
    const progressRef = adminDb.doc(`users/${uid}/achievementProgress/badge-a`);
    const firstUnlock = (await progressRef.get()).data().unlockedAt.toMillis();
    await call({lessonId: "a-1"});
    assert.equal((await progressRef.get()).data().unlockedAt.toMillis(), firstUnlock);
    assert.equal((await adminDb.collection(`users/${uid}/achievementProgress`).get()).size, 1);
    assert.equal((await adminDb.doc(`users/${uid}`).get()).data().xp, 40);
  });

  test("lição historicamente concluída sem novo evento não gera conquista retroativa", async () => {
    const {uid, email, functions} = await authenticatedClient();
    await seedUser(uid, email);
    await adminDb.doc("modules/module-a").set({title: "A"});
    await seedLesson("a-1", {moduleId: "module-a"});
    await seedModuleAchievement("badge-a", "module-a");
    await adminDb.doc(`users/${uid}/lessonProgress/legacy-random-id`).set({
      userId: uid, lessonId: "a-1", isCompleted: true, lastAccessed: Timestamp.now(),
    });
    const result = await httpsCallable(functions, "completeLesson")({lessonId: "a-1"});
    assert.equal(result.data.xpEarned, 0);
    assert.equal((await adminDb.doc(`users/${uid}/achievementProgress/badge-a`).get()).exists, false);
  });
});

describe("metas autoritativas", () => {
  test("repetir o mesmo requestId não recria a meta nem concede XP outra vez", async () => {
    const {uid, email, functions} = await authenticatedClient();
    await seedUser(uid, email);
    const call = httpsCallable(functions, "createGoal");
    const request = {requestId: "request-1", title: "Reserva", targetAmount: 100,
      currentAmount: 100, deadline: "CURTO"};
    const [first, second] = await Promise.all([call(request), call(request)]);
    assert.equal(first.data.goalId, second.data.goalId);
    assert.deepEqual([first.data.xpEarned, second.data.xpEarned].sort((a, b) => a - b), [0, 200]);
    assert.equal((await adminDb.collection(`users/${uid}/goals`).get()).size, 1);
    const third = await call(request);
    assert.equal(third.data.xpEarned, 0);
    await adminDb.doc(`users/${uid}/goals/${first.data.goalId}`).delete();
    assert.equal((await call(request)).data.xpEarned, 0);
    assert.equal((await adminDb.collection(`users/${uid}/goals`).get()).size, 0);
    assert.equal((await adminDb.doc(`users/${uid}`).get()).data().xp, 100);
  });

  test("reutilizar requestId com conteúdo diferente falha sem criar meta", async () => {
    const {uid, email, functions} = await authenticatedClient();
    await seedUser(uid, email);
    const call = httpsCallable(functions, "createGoal");
    await call({requestId: "request-1", title: "Reserva", targetAmount: 100, currentAmount: 0, deadline: "CURTO"});
    await assert.rejects(call({requestId: "request-1", title: "Outra", targetAmount: 100,
      currentAmount: 100, deadline: "CURTO"}));
    assert.equal((await adminDb.collection(`users/${uid}/goals`).get()).size, 1);
    assert.equal((await adminDb.doc(`users/${uid}`).get()).data().xp, 0);
  });

  test("criação concluída concede exatamente 200 XP e registra completedAt", async () => {
    const {uid, email, functions} = await authenticatedClient();
    await seedUser(uid, email);
    const deadlineDate = ClientTimestamp.fromMillis(Date.now() + 86_400_000);
    const result = await httpsCallable(functions, "createGoal")({
      requestId: "request-1",
      title: "Reserva", targetAmount: 100, currentAmount: 100, deadline: "CURTO",
      deadlineDate, xpReward: 999999,
    });
    assert.equal(result.data.xpEarned, 200);
    assert.equal(result.data.leveledUp, true);
    const goal = (await adminDb.doc(`users/${uid}/goals/${result.data.goalId}`).get()).data();
    assert.ok(goal.completedAt instanceof Timestamp);
    assert.equal(goal.deadlineDate.toMillis(), deadlineDate.toMillis());
    assert.deepEqual((await adminDb.doc(`users/${uid}`).get()).data(), {
      name: "Usuário", email, level: 2, xp: 100, nextLevelXp: 120,
    });
  });

  test("cruzamento de progresso recompensa uma vez", async () => {
    const {uid, email, functions} = await authenticatedClient();
    await seedUser(uid, email);
    await adminDb.doc(`users/${uid}/goals/goal-1`).set({
      title: "Reserva", targetAmount: 100, currentAmount: 90, deadline: "CURTO", completedAt: null,
    });
    const call = httpsCallable(functions, "updateGoalProgress");
    const [first, second] = await Promise.all([
      call({goalId: "goal-1", amountToAdd: 10}),
      call({goalId: "goal-1", amountToAdd: 10}),
    ]);
    assert.deepEqual([first.data.xpEarned, second.data.xpEarned].sort((a, b) => a - b), [0, 200]);
    assert.equal((await adminDb.doc(`users/${uid}`).get()).data().xp, 100);
  });

  test("meta legada já concluída é marcada como histórica sem novo XP", async () => {
    const {uid, email, functions} = await authenticatedClient();
    await seedUser(uid, email);
    await adminDb.doc(`users/${uid}/goals/goal-legacy`).set({
      title: "Reserva", targetAmount: 100, currentAmount: 100, deadline: "CURTO",
    });
    const result = await httpsCallable(functions, "updateGoalProgress")({goalId: "goal-legacy", amountToAdd: 1});
    assert.equal(result.data.xpEarned, 0);
    assert.ok((await adminDb.doc(`users/${uid}/goals/goal-legacy`).get()).data().completedAt instanceof Timestamp);
    assert.equal((await adminDb.doc(`users/${uid}`).get()).data().xp, 0);
  });

  test("edição que torna meta concluída só a marca e não concede XP", async () => {
    const {uid, email, functions} = await authenticatedClient();
    await seedUser(uid, email);
    await adminDb.doc(`users/${uid}/goals/goal-1`).set({
      title: "Reserva", targetAmount: 1000, currentAmount: 500, deadline: "LONGO", completedAt: null,
    });
    const result = await httpsCallable(functions, "updateGoalDetails")({
      goalId: "goal-1", title: "Reserva", targetAmount: 400, deadline: "CURTO", deadlineDate: null,
    });
    assert.equal(result.data.xpEarned, 0);
    assert.ok((await adminDb.doc(`users/${uid}/goals/goal-1`).get()).data().completedAt instanceof Timestamp);
    assert.equal((await adminDb.doc(`users/${uid}`).get()).data().xp, 0);
  });
});

describe("deleteAccount", () => {
  test("rejeita chamada sem autenticação", async () => {
    const app = initializeApp({apiKey: "demo-key", projectId: PROJECT_ID}, `delete-anon-${++sequence}`);
    const functions = getFunctions(app, REGION);
    connectFunctionsEmulator(functions, "127.0.0.1", FUNCTIONS_PORT);
    await assert.rejects(
      httpsCallable(functions, "deleteAccount")({}),
      (error) => error.code === "functions/unauthenticated",
    );
  });

  test("remove dados privados, legado e Auth sem tocar outro usuário", async () => {
    const owner = await authenticatedClient();
    const other = await authenticatedClient();
    await seedUser(owner.uid, owner.email);
    await seedUser(other.uid, other.email);
    await adminDb.doc(`users/${owner.uid}/goals/goal-1`).set({title: "Meta"});
    await adminDb.doc(`users/${owner.uid}/achievementProgress/badge-a`).set({
      achievementId: "badge-a", currentProgress: 1, isUnlocked: true, unlockedAt: Timestamp.now(),
    });
    await adminDb.doc(`users/${owner.uid}/future/private/nested/doc-1`).set({secret: true});
    await adminDb.doc("achievementProgress/owner-progress").set({userId: owner.uid, achievementId: "a"});
    await adminDb.doc("achievementProgress/other-progress").set({userId: other.uid, achievementId: "a"});

    const result = await httpsCallable(owner.functions, "deleteAccount")({});
    assert.deepEqual(result.data, {deleted: true});
    assert.equal((await adminDb.doc(`users/${owner.uid}`).get()).exists, false);
    assert.equal((await adminDb.doc(`users/${owner.uid}/goals/goal-1`).get()).exists, false);
    assert.equal((await adminDb.doc(`users/${owner.uid}/achievementProgress/badge-a`).get()).exists, false);
    assert.equal((await adminDb.doc(`users/${owner.uid}/future/private/nested/doc-1`).get()).exists, false);
    assert.equal((await adminDb.doc("achievementProgress/owner-progress").get()).exists, false);
    assert.equal((await adminDb.doc(`users/${other.uid}`).get()).exists, true);
    assert.equal((await adminDb.doc("achievementProgress/other-progress").get()).exists, true);
    await assert.rejects(
      adminAuth.getUser(owner.uid),
      (error) => error.code === "auth/user-not-found",
    );
    assert.equal((await adminAuth.getUser(other.uid)).uid, other.uid);
  });
});
