import { after, before, beforeEach, describe, test } from "node:test";
import { readFileSync } from "node:fs";
import { fileURLToPath } from "node:url";
import { dirname, resolve } from "node:path";
import {
  assertFails,
  assertSucceeds,
  initializeTestEnvironment,
} from "@firebase/rules-unit-testing";
import {
  Timestamp,
  collection,
  deleteDoc,
  doc,
  getDoc,
  getDocs,
  query,
  setDoc,
  updateDoc,
  where,
} from "firebase/firestore";

const PROJECT_ID = "demo-granaxp";
const USER_A = "user-a";
const USER_B = "user-b";
const __dirname = dirname(fileURLToPath(import.meta.url));
const rulesPath = resolve(__dirname, "..", "firestore.rules");

let testEnv;

const validUser = (overrides = {}) => ({
  name: "Usuário de teste",
  email: "user-a@example.com",
  level: 1,
  xp: 0,
  nextLevelXp: 100,
  ...overrides,
});

const userDb = (uid, email = `${uid}@example.com`) =>
  testEnv.authenticatedContext(uid, { email }).firestore();

const seed = async (path, data) => {
  await testEnv.withSecurityRulesDisabled(async (context) => {
    await setDoc(doc(context.firestore(), path), data);
  });
};

before(async () => {
  testEnv = await initializeTestEnvironment({
    projectId: PROJECT_ID,
    firestore: { rules: readFileSync(rulesPath, "utf8") },
  });
});

beforeEach(async () => {
  await testEnv.clearFirestore();
});

after(async () => {
  await testEnv.cleanup();
});

describe("dados privados da V1", () => {
  test("usuário autenticado cria, lê e atualiza o próprio perfil", async () => {
    const db = userDb(USER_A);
    const ref = doc(db, `users/${USER_A}`);

    await assertSucceeds(setDoc(ref, validUser()));
    await assertSucceeds(getDoc(ref));
    await assertSucceeds(updateDoc(ref, { name: "Novo nome" }));
  });

  test("preserva a regravação do perfil usada pelo bônus atual de 200 XP de metas", async () => {
    const db = userDb(USER_A);
    const ref = doc(db, `users/${USER_A}`);
    await assertSucceeds(setDoc(ref, validUser()));

    // XpUtils: 0 + 200 cruza 100 XP, resultando em nível 2, 100 XP e próximo nível 120.
    await assertSucceeds(
      setDoc(ref, validUser({ level: 2, xp: 100, nextLevelXp: 120 })),
    );
  });

  test("usuário A não lê nem escreve o perfil do usuário B", async () => {
    await seed(`users/${USER_B}`, validUser({ email: "user-b@example.com" }));
    const dbA = userDb(USER_A);
    const refB = doc(dbA, `users/${USER_B}`);

    await assertFails(getDoc(refB));
    await assertFails(updateDoc(refB, { name: "Ataque" }));
    await assertFails(deleteDoc(refB));
    await assertFails(getDocs(collection(dbA, "users")));
  });

  test("usuário não autenticado não acessa dados privados", async () => {
    await seed(`users/${USER_A}`, validUser());
    const db = testEnv.unauthenticatedContext().firestore();

    await assertFails(getDoc(doc(db, `users/${USER_A}`)));
    await assertFails(setDoc(doc(db, `users/${USER_A}/goals/goal-1`), {
      title: "Meta",
      targetAmount: 1000,
      currentAmount: 0,
      deadline: "CURTO",
    }));
  });

  const currentPrivateDocuments = [
    {
      name: "transactions",
      data: {
        title: "Salário",
        category: "Renda",
        amount: 1500,
        type: "RECEITA",
        date: Timestamp.now(),
        automatic: false,
      },
    },
    {
      name: "goals",
      data: {
        title: "Reserva",
        targetAmount: 5000,
        currentAmount: 500,
        deadline: "MEDIO",
        deadlineDate: Timestamp.now(),
      },
    },
    {
      name: "budgets",
      data: {
        category: "Moradia",
        limitAmount: 1200,
        spentAmount: 0,
        type: "FIXO",
        dueDay: 10,
        isPaid: false,
        lastPaymentDate: null,
        lastClosedMonth: null,
      },
    },
    {
      name: "reminders",
      data: {
        title: "Conta de luz",
        description: "Vencimento mensal",
        amount: 120,
        date: Timestamp.now(),
        time: "09:00",
        isCompleted: false,
      },
    },
    {
      name: "lessonProgress",
      data: {
        userId: USER_A,
        lessonId: "lesson-1",
        isCompleted: false,
        lastAccessed: Timestamp.now(),
      },
    },
  ];

  for (const scenario of currentPrivateDocuments) {
    test(`permite CRUD do dono em users/{uid}/${scenario.name}`, async () => {
      const dbA = userDb(USER_A);
      const ref = doc(dbA, `users/${USER_A}/${scenario.name}/doc-1`);

      await assertSucceeds(setDoc(ref, scenario.data));
      await assertSucceeds(getDoc(ref));
      await assertSucceeds(getDocs(collection(dbA, `users/${USER_A}/${scenario.name}`)));
      await assertSucceeds(deleteDoc(ref));
    });

    test(`bloqueia leitura e escrita cruzada em ${scenario.name}`, async () => {
      await seed(`users/${USER_B}/${scenario.name}/doc-1`, {
        ...scenario.data,
        ...(scenario.name === "lessonProgress" ? { userId: USER_B } : {}),
      });
      const dbA = userDb(USER_A);
      const ref = doc(dbA, `users/${USER_B}/${scenario.name}/doc-1`);

      await assertFails(getDoc(ref));
      await assertFails(setDoc(ref, scenario.data));
    });
  }

  test("mantém compatibilidade com campos booleanos legados da V1", async () => {
    await seed(`users/${USER_A}/transactions/transaction-1`, {
      title: "Salário",
      category: "Renda",
      amount: 1500,
      type: "RECEITA",
      date: Timestamp.now(),
      automatic: false,
    });
    await seed(`users/${USER_A}/budgets/budget-1`, {
      category: "Moradia",
      limitAmount: 1200,
      spentAmount: 0,
      type: "FIXO",
      dueDay: 10,
      paid: false,
      isPaid: false,
      lastPaymentDate: null,
      lastClosedMonth: null,
    });
    await seed(`users/${USER_A}/lessonProgress/progress-1`, {
      userId: USER_A,
      lessonId: "lesson-1",
      completed: false,
      isCompleted: true,
      lastAccessed: Timestamp.now(),
    });
    const dbA = userDb(USER_A);

    // TransactionRepository atualmente adiciona isAutomatic em updates,
    // enquanto documentos criados pelo model usam automatic.
    await assertSucceeds(updateDoc(
      doc(dbA, `users/${USER_A}/transactions/transaction-1`),
      { isAutomatic: true },
    ));
    await assertSucceeds(updateDoc(
      doc(dbA, `users/${USER_A}/budgets/budget-1`),
      { isPaid: true },
    ));
    await assertSucceeds(updateDoc(
      doc(dbA, `users/${USER_A}/lessonProgress/progress-1`),
      { isCompleted: true },
    ));
  });
});

describe("catálogos globais", () => {
  test("usuário autenticado lê catálogos e blocos, mas não os altera", async () => {
    await seed("lessons/lesson-1", {
      title: "Aula",
      description: "Resumo",
      duration: 3,
      category: "Orçamento",
      videoUrl: null,
      xpReward: 50,
      order: 0,
    });
    await seed("lessons/lesson-1/blocks/block-1", {
      id: "block-1",
      type: "TEXT",
      order: 0,
      title: "Introdução",
      content: "Conteúdo",
    });
    await seed("modules/module-1", {
      idModule: "module-1",
      title: "Módulo",
      description: "Descrição",
      order: 0,
    });
    await seed("achievements/achievement-1", {
      title: "Primeiro passo",
      description: "Descrição",
      icon: "",
      requirementType: "TRANSACTION_COUNT",
      requirementValue: 1,
      category: "FINANCAS",
    });
    await seed("dailyMissions/mission-1", {
      id: "mission-1",
      title: "Missão",
      description: "Descrição",
      activityType: "TRUE_FALSE",
      alternatives: { true: "Verdadeiro", false: "Falso" },
      correctAnswerIds: ["true"],
      feedback: "Feedback",
      chartImageUrl: null,
      order: 0,
    });
    const db = userDb(USER_A);

    for (const path of [
      "lessons/lesson-1",
      "lessons/lesson-1/blocks/block-1",
      "modules/module-1",
      "achievements/achievement-1",
      "dailyMissions/mission-1",
    ]) {
      await assertSucceeds(getDoc(doc(db, path)));
      await assertFails(setDoc(doc(db, path), { ataque: true }));
    }
    await assertSucceeds(getDocs(query(collection(db, "lessons"))));
  });

  test("usuário não autenticado não lê catálogos", async () => {
    await seed("lessons/lesson-1", { title: "Aula", order: 0 });
    const db = testEnv.unauthenticatedContext().firestore();

    await assertFails(getDoc(doc(db, "lessons/lesson-1")));
    await assertFails(getDocs(collection(db, "lessons")));
  });
});

describe("achievementProgress legado", () => {
  test("preserva a consulta V1 filtrada por userId e as escritas do dono", async () => {
    await seed("achievementProgress/progress-a", {
      userId: USER_A,
      achievementId: "achievement-1",
      currentProgress: 1,
      isUnlocked: false,
      unlocked: false,
      lastUpdated: Timestamp.now(),
    });
    await seed("achievementProgress/progress-b", {
      userId: USER_B,
      achievementId: "achievement-1",
      currentProgress: 0,
      isUnlocked: false,
      lastUpdated: Timestamp.now(),
    });
    const dbA = userDb(USER_A);
    const ownQuery = query(
      collection(dbA, "achievementProgress"),
      where("userId", "==", USER_A),
    );

    await assertSucceeds(getDocs(ownQuery));
    await assertFails(getDocs(collection(dbA, "achievementProgress")));
    await assertSucceeds(updateDoc(
      doc(dbA, "achievementProgress/progress-a"),
      { currentProgress: 2, isUnlocked: true },
    ));
    await assertSucceeds(setDoc(doc(dbA, "achievementProgress/progress-new"), {
      userId: USER_A,
      achievementId: "achievement-2",
      currentProgress: 0,
      isUnlocked: false,
      lastUpdated: Timestamp.now(),
    }));
    await assertSucceeds(deleteDoc(
      doc(dbA, "achievementProgress/progress-new"),
    ));
  });

  test("bloqueia leitura, criação e alteração cruzadas no caminho legado", async () => {
    await seed("achievementProgress/progress-b", {
      userId: USER_B,
      achievementId: "achievement-1",
      currentProgress: 0,
      isUnlocked: false,
      lastUpdated: Timestamp.now(),
    });
    const dbA = userDb(USER_A);

    await assertFails(getDoc(doc(dbA, "achievementProgress/progress-b")));
    await assertFails(updateDoc(
      doc(dbA, "achievementProgress/progress-b"),
      { currentProgress: 999 },
    ));
    await assertFails(setDoc(doc(dbA, "achievementProgress/forged"), {
      userId: USER_B,
      achievementId: "achievement-1",
      currentProgress: 999,
      isUnlocked: true,
      lastUpdated: Timestamp.now(),
    }));
  });
});

describe("caminhos planejados da Fase 1", () => {
  test("aceita LessonProgress determinístico sem userId", async () => {
    const db = userDb(USER_A);
    await assertSucceeds(setDoc(doc(db, `users/${USER_A}/lessonProgress/lesson-2`), {
      lessonId: "lesson-2",
      isCompleted: false,
      completedActivityIds: ["activity-1"],
      lastAccessed: Timestamp.now(),
      completedAt: null,
    }));

    await assertFails(setDoc(doc(db, `users/${USER_A}/lessonProgress/wrong-id`), {
      lessonId: "lesson-2",
      isCompleted: false,
      completedActivityIds: [],
      lastAccessed: Timestamp.now(),
      completedAt: null,
    }));
  });

  test("aceita AchievementProgress futuro somente no caminho e ID do dono", async () => {
    const dbA = userDb(USER_A);
    const data = {
      achievementId: "achievement-1",
      currentProgress: 1,
      isUnlocked: false,
      unlockedAt: null,
    };

    await assertSucceeds(setDoc(
      doc(dbA, `users/${USER_A}/achievementProgress/achievement-1`),
      data,
    ));
    await assertFails(setDoc(
      doc(dbA, `users/${USER_A}/achievementProgress/wrong-id`),
      data,
    ));
    await assertFails(setDoc(
      doc(dbA, `users/${USER_B}/achievementProgress/achievement-1`),
      data,
    ));
  });

  test("DailyMissionProgress e streak ficam legíveis só pelo dono e sem escrita cliente por enquanto", async () => {
    await seed(`users/${USER_A}/dailyMissionProgress/2026-10-01`, {
      missionId: "mission-1",
      date: "2026-10-01",
      isCompleted: true,
      completedAt: Timestamp.now(),
    });
    await seed(`users/${USER_A}/learningStats/streak`, {
      currentStreak: 1,
      highestStreak: 1,
      lastMissionCompletedDate: "2026-10-01",
      claimedMilestones: [],
    });
    const dbA = userDb(USER_A);
    const dbB = userDb(USER_B);

    await assertSucceeds(getDoc(
      doc(dbA, `users/${USER_A}/dailyMissionProgress/2026-10-01`),
    ));
    await assertSucceeds(getDoc(
      doc(dbA, `users/${USER_A}/learningStats/streak`),
    ));
    await assertFails(getDoc(
      doc(dbB, `users/${USER_A}/dailyMissionProgress/2026-10-01`),
    ));
    await assertFails(updateDoc(
      doc(dbA, `users/${USER_A}/dailyMissionProgress/2026-10-01`),
      { isCompleted: false },
    ));
    await assertFails(updateDoc(
      doc(dbA, `users/${USER_A}/learningStats/streak`),
      { currentStreak: 30 },
    ));
  });
});

describe("validação e negação por padrão", () => {
  test("rejeita poluição de esquema e tipos inválidos", async () => {
    const db = userDb(USER_A);

    await assertFails(setDoc(doc(db, `users/${USER_A}`), {
      ...validUser(),
      role: "admin",
    }));
    await assertFails(setDoc(doc(db, `users/${USER_A}`), validUser({
      name: "x".repeat(121),
    })));
    await assertFails(setDoc(doc(db, `users/${USER_A}/transactions/invalid`), {
      title: "Inválida",
      category: "Teste",
      amount: "100",
      type: "RECEITA",
      date: Timestamp.now(),
      automatic: false,
    }));
  });

  test("rejeita update bypass com campo arbitrário", async () => {
    const db = userDb(USER_A);
    const ref = doc(db, `users/${USER_A}/transactions/transaction-1`);
    await assertSucceeds(setDoc(ref, {
      title: "Salário",
      category: "Renda",
      amount: 1500,
      type: "RECEITA",
      date: Timestamp.now(),
      automatic: false,
    }));

    await assertFails(updateDoc(ref, { extraData: "ataque" }));
    await assertFails(updateDoc(ref, { date: Timestamp.now() }));
  });

  test("nega caminhos não declarados", async () => {
    const db = userDb(USER_A);

    await assertFails(setDoc(doc(db, "admin/config"), { enabled: true }));
    await assertFails(getDoc(doc(db, "admin/config")));
  });

  test("documenta a limitação temporária: o dono ainda consegue manipular XP próprio", async () => {
    const db = userDb(USER_A);
    const ref = doc(db, `users/${USER_A}`);
    await assertSucceeds(setDoc(ref, validUser()));

    await assertSucceeds(updateDoc(ref, {
      xp: 999999,
      level: 999,
      nextLevelXp: 999999,
    }));
  });
});
