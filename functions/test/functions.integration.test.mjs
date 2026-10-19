import assert from "node:assert/strict";
import {after, before, beforeEach, describe, test} from "node:test";
import {initializeApp as initializeAdminApp} from "firebase-admin/app";
import {getAuth as getAdminAuth} from "firebase-admin/auth";
import {getFirestore as getAdminFirestore, Query, Timestamp, Transaction} from "firebase-admin/firestore";
import {initializeApp} from "firebase/app";
import {
  connectAuthEmulator,
  createUserWithEmailAndPassword,
  getAuth,
  signInWithEmailAndPassword,
  signOut,
} from "firebase/auth";
import {connectFunctionsEmulator, getFunctions, httpsCallable} from "firebase/functions";
import {Timestamp as ClientTimestamp} from "firebase/firestore";

const PROJECT_ID = "demo-granaxp";
const REGION = "southamerica-east1";
const FIRESTORE_HOST = process.env.FIRESTORE_EMULATOR_HOST ?? "127.0.0.1:8080";
const AUTH_HOST = process.env.FIREBASE_AUTH_EMULATOR_HOST ?? "127.0.0.1:9099";
const FUNCTIONS_PORT = Number(process.env.FUNCTIONS_EMULATOR_PORT ?? 5001);
assert.ok(["127.0.0.1:8080", "127.0.0.1:18080"].includes(FIRESTORE_HOST));
assert.ok(["127.0.0.1:9099", "127.0.0.1:19099"].includes(AUTH_HOST));
assert.ok([5001, 15001].includes(FUNCTIONS_PORT));

process.env.GCLOUD_PROJECT = PROJECT_ID;
const adminApp = initializeAdminApp();
const adminDb = getAdminFirestore(adminApp);
const adminAuth = getAdminAuth(adminApp);

const clients = [];
let sequence = 0;

async function ensureCatalogControl() {
  assert.equal(process.env.FIRESTORE_EMULATOR_HOST, FIRESTORE_HOST);
  assert.equal(process.env.GCLOUD_PROJECT, PROJECT_ID);
  await adminDb.runTransaction(async (transaction) => {
    const ref = adminDb.doc("learningCatalogControl/current");
    if (!(await transaction.get(ref)).exists) transaction.create(ref, {protocolVersion: 1, generation: 1});
  });
}

async function catalogWrite(path, operation, data) {
  await ensureCatalogControl();
  const {publishLearningCatalogMutation} = await import("../lib/learningCatalogPublication.js");
  const generation = (await adminDb.doc("learningCatalogControl/current").get()).data().generation;
  return await publishLearningCatalogMutation(adminDb, generation, [{operation, path, data}]);
}

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
  await ensureCatalogControl();
  const {publishLearningCatalogMutation} = await import("../lib/learningCatalogPublication.js");
  const generation = (await adminDb.doc("learningCatalogControl/current").get()).data().generation;
  await publishLearningCatalogMutation(adminDb, generation, [
    {operation: "set", path: `lessons/${id}`, data: {title: "Lição", xpReward, order, moduleId}},
    ...activityIds.map((blockId, index) => ({operation: "set", path: `lessons/${id}/blocks/${blockId}`, data: {
      type: "ACTIVITY",
      order: index,
      activityType: "MULTIPLE_CHOICE",
      alternatives: {a: "A", b: "B"},
      correctAnswerIds: ["a"],
      feedback: "Tente novamente.",
    }})),
  ]);
}

async function seedModuleAchievement(id, moduleId) {
  await catalogWrite(`achievements/${id}`, "set", {
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

if (process.env.GRANAXP_TEST_INVALID_KEYRING === "1") {
  test("v3_indisponivel configuração inválida recusa operações novas e preserva retorno histórico", async () => {
    const {uid, email, functions} = await authenticatedClient();
    await seedUser(uid, email, {xp: 90});
    await seedLesson("lesson-no-key", {activityIds: ["activity-1"]});
    const ref = adminDb.doc(`users/${uid}/lessonProgress/lesson-no-key`);
    await ref.set({lessonId: "lesson-no-key", isCompleted: false, completedActivityIds: []});
    for (const name of ["openLesson", "submitLessonActivity", "completeLesson"]) {
      await assert.rejects(httpsCallable(functions, name)({lessonId: "lesson-no-key", blockId: "activity-1", selectedAnswerIds: ["a"]}),
        (error) => error.code === "functions/failed-precondition");
    }
    assert.equal((await ref.get()).data().isCompleted, false);
    assert.equal((await ref.get()).data().activityValidationProofs, undefined);
    const historical = {lessonId: "lesson-no-key", isCompleted: true, completedActivityIds: ["historical"]};
    await ref.set(historical);
    await adminDb.doc("learningCatalogControl/current").delete();
    assert.deepEqual((await httpsCallable(functions, "completeLesson")({lessonId: "lesson-no-key"})).data,
      {xpEarned: 0, leveledUp: false, alreadyCompleted: true});
    assert.deepEqual((await ref.get()).data(), historical);
    assert.equal((await adminDb.doc(`users/${uid}`).get()).data().xp, 90);
    assert.equal((await adminDb.doc(`users/${uid}`).get()).data().level, 1);
    assert.equal((await adminDb.collection(`users/${uid}/achievementProgress`).get()).empty, true);
  });
}

async function seedDailyMission(id, order, overrides = {}) {
  await adminDb.doc(`dailyMissions/${id}`).set({
    id, title: `Missão ${id}`, description: "Escolha a resposta.",
    activityType: "MULTIPLE_CHOICE", alternatives: {a: "A", b: "B", c: "C"},
    correctAnswerIds: ["a"], feedback: "Releia a explicação.",
    chartImageUrl: null, order, ...overrides,
  });
}

const OCT_1 = Date.parse("2026-10-01T03:00:00.000Z");
const OCT_2 = Date.parse("2026-10-02T03:00:00.000Z");

describe("protocolo de publicação v3", () => {
  const controlRef = () => adminDb.doc("learningCatalogControl/current");
  const publisher = async () => await import("../lib/learningCatalogPublication.js");
  const latch = () => {
    let resolve;
    const promise = new Promise((done) => { resolve = done; });
    return {promise, resolve};
  };

  test("v3_publicacao recusa controle ausente e operação fora do catálogo", async () => {
    const {publishLearningCatalogMutation} = await publisher();
    await assert.rejects(publishLearningCatalogMutation(adminDb, 1,
      [{operation: "create", path: "lessons/l", data: {title: "L"}}]),
    (error) => error.code === "failed-precondition");
    assert.equal((await adminDb.doc("lessons/l").get()).exists, false);
    await controlRef().create({protocolVersion: 1, generation: 1});
    await assert.rejects(publishLearningCatalogMutation(adminDb, 1,
      [{operation: "set", path: "users/not-allowed", data: {xp: 50}}]),
    (error) => error.code === "invalid-argument");
    assert.equal((await controlRef().get()).data().generation, 1);
  });

  test("v3_publicacao grava catálogo e geração juntos, sem publicação parcial em erro", async () => {
    const {publishLearningCatalogMutation} = await publisher();
    await controlRef().create({protocolVersion: 1, generation: 1});
    await adminDb.doc("lessons/already-exists").set({title: "Existente"});
    await assert.rejects(publishLearningCatalogMutation(adminDb, 1, [
      {operation: "create", path: "lessons/new", data: {title: "Nova"}},
      {operation: "create", path: "lessons/already-exists", data: {title: "Duplicada"}},
    ]));
    assert.equal((await adminDb.doc("lessons/new").get()).exists, false);
    assert.equal((await controlRef().get()).data().generation, 1);
    assert.equal(await publishLearningCatalogMutation(adminDb, 1, [
      {operation: "create", path: "lessons/new", data: {title: "Nova"}},
      {operation: "create", path: "lessons/new/blocks/a", data: {type: "ACTIVITY"}},
    ]), 2);
    assert.equal((await adminDb.doc("lessons/new/blocks/a").get()).exists, true);
  });

  test("v3_publicacao duas publicações concorrentes rejeitam geração obsoleta", async () => {
    const {publishLearningCatalogMutation} = await publisher();
    await controlRef().create({protocolVersion: 1, generation: 1});
    const outcomes = await Promise.allSettled(["one", "two"].map((id) =>
      publishLearningCatalogMutation(adminDb, 1, [{operation: "create", path: `lessons/${id}`, data: {title: id}}])));
    assert.equal(outcomes.filter((result) => result.status === "fulfilled").length, 1);
    assert.equal(outcomes.find((result) => result.status === "rejected").reason.code, "failed-precondition");
    assert.equal((await controlRef().get()).data().generation, 2);
    assert.equal((await adminDb.collection("lessons").get()).size, 1);
  });

  test("v3_publicacao leitor coordenado não aceita inclusão e remoção parciais", {timeout: 60000}, async () => {
    const {publishLearningCatalogMutation, readLearningCatalogControl} = await publisher();
    await controlRef().create({protocolVersion: 1, generation: 1});
    await adminDb.doc("lessons/l/blocks/old").create({type: "ACTIVITY"});
    const readerReady = latch();
    const publisherAttempt = latch();
    let readerTransaction;
    const originalGet = Transaction.prototype.get;
    Transaction.prototype.get = function (reference, ...args) {
      if (this !== readerTransaction && reference?.path === "learningCatalogControl/current") publisherAttempt.resolve();
      return originalGet.call(this, reference, ...args);
    };
    try {
      const reader = adminDb.runTransaction(async (transaction) => {
        readerTransaction = transaction;
        const generation = await readLearningCatalogControl(transaction, adminDb);
        readerReady.resolve();
        await publisherAttempt.promise;
        const blocks = await transaction.get(adminDb.collection("lessons/l/blocks"));
        return {generation, ids: blocks.docs.map((doc) => doc.id)};
      });
      await readerReady.promise;
      const publication = publishLearningCatalogMutation(adminDb, 1, [
        {operation: "delete", path: "lessons/l/blocks/old"},
        {operation: "create", path: "lessons/l/blocks/new", data: {type: "ACTIVITY"}},
      ]);
      assert.deepEqual(await reader, {generation: 1, ids: ["old"]});
      assert.equal(await publication, 2);
      const after = await adminDb.runTransaction(async (transaction) => {
        const generation = await readLearningCatalogControl(transaction, adminDb);
        const blocks = await transaction.get(adminDb.collection("lessons/l/blocks"));
        return {generation, ids: blocks.docs.map((doc) => doc.id)};
      });
      assert.deepEqual(after, {generation: 2, ids: ["new"]});
    } finally { Transaction.prototype.get = originalGet; publisherAttempt.resolve(); }
  });
});

describe("quinta auditoria: ordenamentos determinísticos do catálogo", () => {
  // Exercise the actual callable handlers with Firestore transactions. Only the
  // transport/auth envelope is supplied locally; HTTP coverage remains above/below.
  for (const name of ["openLesson", "submitLessonActivity", "completeLesson"]) {
    for (const ordering of ["reader-first", "publisher-first", "retry"]) {
      test(`audit5_barreira ${name} ${ordering}`, {timeout: 60000}, async () => {
        const {AsyncLocalStorage} = await import("node:async_hooks");
        const roles = new AsyncLocalStorage();
        const latch = () => {
          let resolve;
          const promise = new Promise((done) => { resolve = done; });
          return {promise, resolve};
        };
        const reached = latch();
        const attempted = latch();
        const published = latch();
        const {uid, email, functions} = await authenticatedClient();
        await seedUser(uid, email, {xp: 90});
        await catalogWrite("modules/audit5-module", "set", {title: "Audit"});
        await seedModuleAchievement("audit5-badge", "audit5-module");
        await seedLesson("audit5-lesson", {moduleId: "audit5-module", activityIds: ["a1"]});
        await httpsCallable(functions, "openLesson")({lessonId: "audit5-lesson"});
        if (name !== "submitLessonActivity") {
          await httpsCallable(functions, "submitLessonActivity")({
            lessonId: "audit5-lesson", blockId: "a1", selectedAnswerIds: ["a"],
          });
        }
        const progressRef = adminDb.doc(`users/${uid}/lessonProgress/audit5-lesson`);
        const beforeProgress = (await progressRef.get()).data();
        const beforeProfile = (await adminDb.doc(`users/${uid}`).get()).data();
        const controlPath = "learningCatalogControl/current";
        const generation = (await adminDb.doc(controlPath).get()).data().generation;
        const oldSecret = process.env.LESSON_ACTIVITY_PROOF_KEYRING;
        assert.equal(process.env.GCLOUD_PROJECT, "demo-granaxp");
        assert.equal(process.env.FIRESTORE_EMULATOR_HOST, FIRESTORE_HOST);
        assert.ok(process.env.GRANAXP_TEST_KEYRING);
        process.env.LESSON_ACTIVITY_PROOF_KEYRING = process.env.GRANAXP_TEST_KEYRING;
        const backend = await import("../lib/index.js");
        const {publishLearningCatalogMutation} = await import("../lib/learningCatalogPublication.js");
        const originalGet = Transaction.prototype.get;
        let guardReads = 0;
        let catalogReads = 0;
        let injectedAbort = false;
        Transaction.prototype.get = async function(reference, ...args) {
          const role = roles.getStore();
          const path = reference?.path;
          if (role === "publisher" && path === controlPath) {
            attempted.resolve();
            const snapshot = await originalGet.call(this, reference, ...args);
            if (ordering === "publisher-first") {
              reached.resolve();
              await attempted.promise;
            }
            return snapshot;
          }
          if (role === "reader" && path === controlPath) {
            guardReads += 1;
            if (ordering === "publisher-first" || injectedAbort) {
              attempted.resolve();
              await published.promise;
            }
            const snapshot = await originalGet.call(this, reference, ...args);
            if (ordering === "reader-first" && guardReads === 1) {
              reached.resolve();
              await attempted.promise;
            }
            return snapshot;
          }
          const snapshot = await originalGet.call(this, reference, ...args);
          if (role === "reader" && path === "lessons/audit5-lesson/blocks") {
            catalogReads += 1;
            assert.ok(guardReads > 0, "catálogo foi lido antes do controle");
            if (ordering === "retry" && !injectedAbort) {
              injectedAbort = true;
              reached.resolve();
              await attempted.promise;
              throw Object.assign(new Error("audit-only transaction abort"), {code: 10});
            }
          }
          return snapshot;
        };
        let reader;
        let publication;
        const call = () => roles.run("reader", () => backend[name].run({auth: {uid}, data: {
          lessonId: "audit5-lesson", blockId: "a1", selectedAnswerIds: ["a"],
        }})).then((value) => ({value}), (error) => ({error}));
        const publish = () => roles.run("publisher", () => publishLearningCatalogMutation(adminDb, generation, [
          {operation: "update", path: "lessons/audit5-lesson/blocks/a1", data: {correctAnswerIds: ["b"]}},
          {operation: "create", path: "lessons/audit5-lesson/blocks/a2", data: {
            type: "ACTIVITY", activityType: "CHART_ANALYSIS", alternatives: {a: "A", b: "B"},
            correctAnswerIds: ["a"], feedback: "Reveja.",
          }},
        ])).finally(() => published.resolve());
        try {
          if (ordering === "publisher-first") {
            publication = publish();
            await reached.promise;
            reader = call();
          } else {
            reader = call();
            await reached.promise;
            publication = publish();
          }
          const outcome = await reader;
          assert.equal(await publication, generation + 1);
          if (ordering === "retry") {
            assert.ok(injectedAbort);
            assert.ok(guardReads >= 2 && catalogReads >= 2, "retry não releu controle e catálogo");
          }
          if (ordering === "reader-first") {
            assert.equal(outcome.error, undefined);
            if (name === "openLesson") assert.deepEqual(outcome.value.blocks.map((block) => block.id), ["a1"]);
            if (name === "submitLessonActivity") assert.equal(outcome.value.correct, true);
            if (name === "completeLesson") {
              assert.equal(outcome.value.xpEarned, 50);
              assert.equal((await progressRef.get()).data().completionValidationVersion, 3);
              assert.equal((await adminDb.doc(`users/${uid}`).get()).data().level, 2);
              assert.equal((await adminDb.doc(`users/${uid}/achievementProgress/audit5-badge`).get()).data().isUnlocked, true);
              assert.equal((await httpsCallable(functions, "completeLesson")({lessonId: "audit5-lesson"})).data.xpEarned, 0);
            }
          } else {
            if (name === "openLesson") {
              assert.equal(outcome.error, undefined);
              assert.deepEqual(outcome.value.completedActivityIds, []);
              assert.deepEqual(outcome.value.blocks.map((block) => block.id).sort(), ["a1", "a2"]);
            } else if (name === "submitLessonActivity") {
              assert.equal(outcome.error, undefined);
              assert.equal(outcome.value.correct, false);
              assert.ok(JSON.stringify((await progressRef.get()).data()) === JSON.stringify(beforeProgress));
            } else {
              assert.equal(outcome.error?.code, "failed-precondition");
              assert.ok(JSON.stringify((await progressRef.get()).data()) === JSON.stringify(beforeProgress));
            }
            assert.deepEqual((await adminDb.doc(`users/${uid}`).get()).data(), beforeProfile);
            assert.equal((await adminDb.doc(`users/${uid}/achievementProgress/audit5-badge`).get()).exists, false);
          }
          if (name !== "completeLesson" || ordering !== "reader-first") {
            await assert.rejects(httpsCallable(functions, "completeLesson")({lessonId: "audit5-lesson"}),
              (error) => error.code === "functions/failed-precondition");
          }
        } finally {
          reached.resolve(); attempted.resolve(); published.resolve();
          Transaction.prototype.get = originalGet;
          await Promise.allSettled([reader, publication].filter(Boolean));
          if (oldSecret === undefined) delete process.env.LESSON_ACTIVITY_PROOF_KEYRING;
          else process.env.LESSON_ACTIVITY_PROOF_KEYRING = oldSecret;
        }
      });
    }
  }
});

describe("Daily Mission", () => {
  test("não disponibiliza missão antes da data-base nem com catálogo vazio", async () => {
    const {functions} = await authenticatedClient();
    await seedDailyMission("first", 1);
    const open = httpsCallable(functions, "openDailyMission");
    assert.deepEqual((await open({deviceTimeMillis: OCT_1 - 1})).data, {available: false});
    await adminDb.doc("dailyMissions/first").delete();
    assert.deepEqual((await open({deviceTimeMillis: OCT_1})).data, {available: false});
  });

  test("rotaciona pelo dia civil de São Paulo e devolve só a projeção pública", async () => {
    const {uid, email, functions} = await authenticatedClient();
    await seedUser(uid, email);
    await seedDailyMission("second", 2);
    await seedDailyMission("first", 1);
    const open = httpsCallable(functions, "openDailyMission");
    const first = (await open({deviceTimeMillis: OCT_1})).data;
    assert.equal(first.date, "2026-10-01");
    assert.equal(first.mission.id, "first");
    assert.equal(first.mission.selectionMode, "SINGLE");
    assert.equal(first.isCompleted, false);
    assert.equal("correctAnswerIds" in first.mission, false);
    assert.equal("feedback" in first.mission, false);
    assert.equal((await open({deviceTimeMillis: OCT_2 - 1})).data.mission.id, "first");
    assert.equal((await open({deviceTimeMillis: OCT_2})).data.mission.id, "second");
    assert.equal((await open({deviceTimeMillis: OCT_2 + 86400000})).data.mission.id, "first");
    assert.equal((await adminDb.doc(`users/${uid}/dailyMissionProgress/2026-10-01`).get()).exists, false);
  });

  test("desempata missões com a mesma ordem pelo ID", async () => {
    const {functions} = await authenticatedClient();
    await seedDailyMission("zeta", 1);
    await seedDailyMission("alpha", 1);
    const open = httpsCallable(functions, "openDailyMission");
    assert.equal((await open({deviceTimeMillis: OCT_1})).data.mission.id, "alpha");
    assert.equal((await open({deviceTimeMillis: OCT_2})).data.mission.id, "zeta");
  });

  test("valida resposta no backend, mostra feedback e não concede XP básico", async () => {
    const {uid, email, functions} = await authenticatedClient();
    await seedUser(uid, email);
    await seedDailyMission("first", 1);
    const answer = httpsCallable(functions, "submitDailyMissionAnswer");
    const request = {deviceTimeMillis: OCT_1, expectedDate: "2026-10-01", expectedMissionId: "first"};
    const wrong = (await answer({...request, selectedAnswerIds: ["b"]})).data;
    assert.equal(wrong.correct, false);
    assert.equal(wrong.feedback, "Releia a explicação.");
    assert.equal((await adminDb.doc(`users/${uid}/dailyMissionProgress/2026-10-01`).get()).exists, false);
    const correct = (await answer({...request, selectedAnswerIds: ["a"]})).data;
    assert.equal(correct.correct, true);
    assert.equal(correct.alreadyCompleted, false);
    const progressRef = adminDb.doc(`users/${uid}/dailyMissionProgress/2026-10-01`);
    const first = (await progressRef.get()).data();
    assert.deepEqual({missionId: first.missionId, date: first.date, isCompleted: first.isCompleted},
      {missionId: "first", date: "2026-10-01", isCompleted: true});
    assert.ok(first.completedAt instanceof Timestamp);
    const again = (await answer({...request, selectedAnswerIds: ["b"]})).data;
    assert.equal(again.alreadyCompleted, true);
    assert.equal((await progressRef.get()).data().completedAt.toMillis(), first.completedAt.toMillis());
    assert.equal((await adminDb.doc(`users/${uid}`).get()).data().xp, 0);
    assert.equal((await adminDb.doc(`users/${uid}/learningStats/streak`).get()).data().currentStreak, 1);
    const reopened = (await httpsCallable(functions, "openDailyMission")({deviceTimeMillis: OCT_1})).data;
    assert.equal(reopened.isCompleted, true);
  });

  test("seleção múltipla exige conjunto exato e evita duplicação concorrente", async () => {
    const {uid, email, functions} = await authenticatedClient();
    await seedUser(uid, email);
    await seedDailyMission("multi", 1, {correctAnswerIds: ["a", "c"]});
    const open = (await httpsCallable(functions, "openDailyMission")({deviceTimeMillis: OCT_1})).data;
    assert.equal(open.mission.selectionMode, "MULTIPLE");
    const answer = httpsCallable(functions, "submitDailyMissionAnswer");
    const request = {deviceTimeMillis: OCT_1, expectedDate: "2026-10-01", expectedMissionId: "multi"};
    assert.equal((await answer({...request, selectedAnswerIds: ["a"]})).data.correct, false);
    assert.equal((await answer({...request, selectedAnswerIds: ["a", "b", "c"]})).data.correct, false);
    await assert.rejects(answer({...request, selectedAnswerIds: ["a", "a"]}));
    const results = await Promise.all(Array.from({length: 4}, () =>
      answer({...request, selectedAnswerIds: ["c", "a"]})));
    assert.equal(results.filter((result) => result.data.alreadyCompleted === false).length, 1);
    assert.equal(results.filter((result) => result.data.alreadyCompleted === true).length, 3);
    assert.equal((await adminDb.collection(`users/${uid}/dailyMissionProgress`).get()).size, 1);
  });

  test("rejeita tela de outro dia e mantém conclusões independentes por data e usuário", async () => {
    const a = await authenticatedClient();
    const b = await authenticatedClient();
    await seedUser(a.uid, a.email);
    await seedUser(b.uid, b.email);
    await seedDailyMission("first", 1);
    await seedDailyMission("second", 2);
    const answerA = httpsCallable(a.functions, "submitDailyMissionAnswer");
    const answerB = httpsCallable(b.functions, "submitDailyMissionAnswer");
    await assert.rejects(answerA({deviceTimeMillis: OCT_2, expectedDate: "2026-10-01",
      expectedMissionId: "first", selectedAnswerIds: ["a"]}));
    await assert.rejects(answerA({deviceTimeMillis: OCT_1, expectedDate: "2026-10-01",
      expectedMissionId: "second", selectedAnswerIds: ["a"]}));
    for (const [answer, time, date, missionId] of [
      [answerA, OCT_1, "2026-10-01", "first"],
      [answerA, OCT_2, "2026-10-02", "second"],
      [answerB, OCT_1, "2026-10-01", "first"],
    ]) {
      assert.equal((await answer({deviceTimeMillis: time, expectedDate: date,
        expectedMissionId: missionId, selectedAnswerIds: ["a"]})).data.correct, true);
    }
    assert.equal((await adminDb.collection(`users/${a.uid}/dailyMissionProgress`).get()).size, 2);
    assert.equal((await adminDb.collection(`users/${b.uid}/dailyMissionProgress`).get()).size, 1);
  });

  test("exige autenticação e rejeita data inválida", async () => {
    const {functions, auth} = await authenticatedClient();
    await signOut(auth);
    await assert.rejects(httpsCallable(functions, "openDailyMission")({deviceTimeMillis: OCT_1}),
      (error) => error.code === "functions/unauthenticated");
    const signedIn = await authenticatedClient();
    await assert.rejects(httpsCallable(signedIn.functions, "openDailyMission")({deviceTimeMillis: "2026-10-01"}));
  });
});

describe("Streak", () => {
  const missionAt = (day) => OCT_1 + day * 86_400_000;
  const missionDate = (day) => new Date(missionAt(day) - 3 * 60 * 60 * 1000).toISOString().slice(0, 10);
  const submit = (functions, day, selectedAnswerIds = ["a"]) =>
    httpsCallable(functions, "submitDailyMissionAnswer")({
      deviceTimeMillis: missionAt(day), expectedDate: missionDate(day),
      expectedMissionId: "first", selectedAnswerIds,
    });
  const streakRef = (uid) => adminDb.doc(`users/${uid}/learningStats/streak`);

  test("resposta errada não inicia streak e a primeira conclusão o inicia uma vez", async () => {
    const {uid, email, functions} = await authenticatedClient();
    await seedUser(uid, email);
    await seedDailyMission("first", 1);
    assert.equal((await submit(functions, 0, ["b"])).data.correct, false);
    assert.equal((await streakRef(uid).get()).exists, false);
    const first = (await submit(functions, 0)).data;
    assert.equal(first.xpEarned, 0);
    assert.equal(first.streak.currentStreak, 1);
    assert.deepEqual((await streakRef(uid).get()).data(), {
      currentStreak: 1, highestStreak: 1,
      lastMissionCompletedDate: "2026-10-01", claimedMilestones: [],
    });
    const repeated = (await submit(functions, 0, ["b"])).data;
    assert.equal(repeated.alreadyCompleted, true);
    assert.equal(repeated.xpEarned, 0);
    assert.equal((await streakRef(uid).get()).data().currentStreak, 1);
    const reopened = (await httpsCallable(functions, "openDailyMission")({deviceTimeMillis: OCT_1})).data;
    assert.equal(reopened.isCompleted, true);
    assert.equal(reopened.streak.currentStreak, 1);
  });

  test("tolera três dias completos, reinicia após quatro e não recompensa marco repetido", async () => {
    const {uid, email, functions} = await authenticatedClient();
    await seedUser(uid, email);
    await seedDailyMission("first", 1);
    assert.equal((await submit(functions, 0)).data.streak.currentStreak, 1);
    assert.equal((await submit(functions, 4)).data.streak.currentStreak, 2);
    assert.equal((await submit(functions, 9)).data.streak.currentStreak, 1);
    assert.equal((await submit(functions, 10)).data.streak.currentStreak, 2);
    const milestone = (await submit(functions, 11)).data;
    assert.equal(milestone.streak.currentStreak, 3);
    assert.equal(milestone.xpEarned, 100);
    assert.deepEqual(milestone.streak.claimedMilestones, [3]);
    const openedAfterFourMissedDays = (await httpsCallable(functions, "openDailyMission")({
      deviceTimeMillis: missionAt(16),
    })).data;
    assert.equal(openedAfterFourMissedDays.streak.currentStreak, 0);
    assert.equal((await submit(functions, 16)).data.streak.currentStreak, 1);
    await submit(functions, 17);
    const repeatedMilestone = (await submit(functions, 18)).data;
    assert.equal(repeatedMilestone.streak.currentStreak, 3);
    assert.equal(repeatedMilestone.streak.highestStreak, 3);
    assert.equal(repeatedMilestone.xpEarned, 0);
    assert.deepEqual(repeatedMilestone.streak.claimedMilestones, [3]);
    assert.equal((await adminDb.doc(`users/${uid}`).get()).data().level, 2);
  });

  test("concessão de marco é atômica sob chamadas concorrentes e isolada por usuário", async () => {
    const owner = await authenticatedClient();
    const other = await authenticatedClient();
    await seedUser(owner.uid, owner.email);
    await seedUser(other.uid, other.email);
    await seedDailyMission("first", 1);
    await submit(owner.functions, 0);
    await submit(owner.functions, 1);
    const results = await Promise.all(Array.from({length: 4}, () => submit(owner.functions, 2)));
    assert.deepEqual(results.map((result) => result.data.xpEarned).sort((a, b) => a - b), [0, 0, 0, 100]);
    assert.equal((await streakRef(owner.uid).get()).data().currentStreak, 3);
    assert.deepEqual((await streakRef(owner.uid).get()).data().claimedMilestones, [3]);
    assert.equal((await adminDb.doc(`users/${owner.uid}`).get()).data().level, 2);
    assert.equal((await streakRef(other.uid).get()).exists, false);
    assert.equal((await adminDb.doc(`users/${other.uid}`).get()).data().xp, 0);
  });

  test("falha da consulta STREAK não cancela missão, streak ou XP do marco", async () => {
    const {uid, email, functions} = await authenticatedClient();
    await seedUser(uid, email);
    await seedDailyMission("first", 1);
    await adminDb.doc("achievements/streak-7").set({
      title: "Sete dias", description: "Sequência de missões", icon: "fire",
      category: "EDUCACAO", requirementType: "STREAK", requirementValue: 7,
    });
    for (let day = 0; day < 6; day++) await submit(functions, day);

    const isStreakCatalogQuery = (query) =>
      query?._queryOptions?.collectionId === "achievements" &&
      query._queryOptions.filters?.some((filter) => filter.value === "STREAK");
    const transactionGet = Transaction.prototype.get;
    const queryGet = Query.prototype.get;
    Transaction.prototype.get = function(ref) {
      if (isStreakCatalogQuery(ref)) return Promise.reject(new Error("Falha da consulta STREAK"));
      return transactionGet.call(this, ref);
    };
    Query.prototype.get = function(...args) {
      if (isStreakCatalogQuery(this)) return Promise.reject(new Error("Falha da consulta STREAK"));
      return queryGet.apply(this, args);
    };
    try {
      const {submitDailyMissionAnswer} = await import("../lib/index.js");
      const result = await submitDailyMissionAnswer.run({
        auth: {uid},
        data: {deviceTimeMillis: missionAt(6), expectedDate: missionDate(6),
          expectedMissionId: "first", selectedAnswerIds: ["a"]},
      });
      assert.equal(result.correct, true);
      assert.equal(result.xpEarned, 100);
      assert.equal(result.streak.currentStreak, 7);
    } finally {
      Transaction.prototype.get = transactionGet;
      Query.prototype.get = queryGet;
    }

    const progress = (await adminDb.doc(`users/${uid}/dailyMissionProgress/${missionDate(6)}`).get()).data();
    assert.equal(progress.isCompleted, true);
    const streak = (await streakRef(uid).get()).data();
    assert.equal(streak.currentStreak, 7);
    assert.equal(streak.highestStreak, 7);
    assert.equal(streak.lastMissionCompletedDate, missionDate(6));
    assert.deepEqual(streak.claimedMilestones, [3, 7]);
    const user = (await adminDb.doc(`users/${uid}`).get()).data();
    assert.deepEqual({level: user.level, xp: user.xp, nextLevelXp: user.nextLevelXp},
      {level: 2, xp: 100, nextLevelXp: 120});
    const badgeRef = adminDb.doc(`users/${uid}/achievementProgress/streak-7`);
    const pendingBadge = (await badgeRef.get()).data();
    assert.equal(pendingBadge.isUnlocked, false);
    assert.equal(pendingBadge.unlockedAt, undefined);

    const beforeUnlock = Date.now();
    assert.equal((await submit(functions, 7)).data.xpEarned, 0);
    const badge = (await badgeRef.get()).data();
    assert.equal(badge.isUnlocked, true);
    assert.ok(badge.unlockedAt.toMillis() >= beforeUnlock);
    assert.deepEqual((await streakRef(uid).get()).data().claimedMilestones, [3, 7]);
  });

  test("catálogo STREAK ausente ou inválido não perde missão nem XP e conclusão futura reconhece highestStreak", async () => {
    const {uid, email, functions} = await authenticatedClient();
    await seedUser(uid, email);
    await seedDailyMission("first", 1);
    for (let day = 0; day < 7; day++) await submit(functions, day);
    assert.equal((await streakRef(uid).get()).data().highestStreak, 7);
    assert.deepEqual((await streakRef(uid).get()).data().claimedMilestones, [3, 7]);
    assert.equal((await adminDb.doc(`users/${uid}/achievementProgress/streak-7`).get()).exists, false);
    await adminDb.doc("achievements/streak-7").set({
      title: "Sete dias", description: "Persistência", icon: "fire", category: "EDUCACAO",
      requirementType: "STREAK", requirementValue: "7",
    });
    assert.equal((await submit(functions, 7)).data.correct, true);
    assert.equal((await adminDb.doc(`users/${uid}/achievementProgress/streak-7`).get()).exists, false);
    await adminDb.doc("achievements/streak-7").update({requirementValue: 7});
    const before = Date.now();
    const reconciled = (await submit(functions, 13)).data;
    assert.equal(reconciled.xpEarned, 0);
    assert.equal(reconciled.streak.currentStreak, 1);
    assert.equal(reconciled.streak.highestStreak, 8);
    const badge = (await adminDb.doc(`users/${uid}/achievementProgress/streak-7`).get()).data();
    assert.equal(badge.isUnlocked, true);
    assert.equal(badge.currentProgress, 7);
    assert.ok(badge.unlockedAt.toMillis() >= before);
    const firstUnlock = badge.unlockedAt.toMillis();
    await submit(functions, 14);
    assert.equal((await adminDb.doc(`users/${uid}/achievementProgress/streak-7`).get()).data()
      .unlockedAt.toMillis(), firstUnlock);
  });

  test("cinco marcos pagam no máximo 500 XP e badges 7 15 30 permanecem desbloqueados", async () => {
    const {uid, email, functions} = await authenticatedClient();
    await seedUser(uid, email);
    await seedDailyMission("first", 1);
    for (const threshold of [7, 15, 30]) {
      await adminDb.doc(`achievements/streak-${threshold}`).set({
        title: `${threshold} dias`, description: "Sequência de missões", icon: "fire",
        category: "EDUCACAO", requirementType: "STREAK", requirementValue: threshold,
      });
    }
    let earned = 0;
    for (let day = 0; day < 30; day++) earned += (await submit(functions, day)).data.xpEarned;
    assert.equal(earned, 500);
    const streak = (await streakRef(uid).get()).data();
    assert.equal(streak.currentStreak, 30);
    assert.equal(streak.highestStreak, 30);
    assert.deepEqual(streak.claimedMilestones, [3, 7, 10, 15, 30]);
    const firstUnlocks = new Map();
    for (const threshold of [7, 15, 30]) {
      const badge = (await adminDb.doc(`users/${uid}/achievementProgress/streak-${threshold}`).get()).data();
      assert.equal(badge.achievementId, `streak-${threshold}`);
      assert.equal(badge.currentProgress, threshold);
      assert.equal(badge.isUnlocked, true);
      assert.ok(badge.unlockedAt instanceof Timestamp);
      firstUnlocks.set(threshold, badge.unlockedAt.toMillis());
    }
    assert.equal((await adminDb.collection(`users/${uid}/achievementProgress`).get()).size, 3);
    assert.equal((await submit(functions, 30)).data.xpEarned, 0);
    for (const threshold of [7, 15, 30]) {
      const badge = (await adminDb.doc(`users/${uid}/achievementProgress/streak-${threshold}`).get()).data();
      assert.equal(badge.unlockedAt.toMillis(), firstUnlocks.get(threshold));
    }
    const user = (await adminDb.doc(`users/${uid}`).get()).data();
    assert.deepEqual({level: user.level, xp: user.xp, nextLevelXp: user.nextLevelXp},
      {level: 4, xp: 136, nextLevelXp: 172});
  });

  test("concorrência no desbloqueio cria somente um badge e preserva unlockedAt", async () => {
    const {uid, email, functions} = await authenticatedClient();
    await seedUser(uid, email);
    await seedDailyMission("first", 1);
    await adminDb.doc("achievements/streak-7").set({
      title: "Sete dias", description: "Sequência de missões", icon: "fire",
      category: "EDUCACAO", requirementType: "STREAK", requirementValue: 7,
    });
    for (let day = 0; day < 6; day++) await submit(functions, day);
    const results = await Promise.all(Array.from({length: 4}, () => submit(functions, 6)));
    assert.deepEqual(results.map((result) => result.data.xpEarned).sort((a, b) => a - b), [0, 0, 0, 100]);
    const badgeRef = adminDb.doc(`users/${uid}/achievementProgress/streak-7`);
    const firstUnlock = (await badgeRef.get()).data().unlockedAt.toMillis();
    await submit(functions, 7);
    assert.equal((await badgeRef.get()).data().unlockedAt.toMillis(), firstUnlock);
    assert.equal((await adminDb.collection(`users/${uid}/achievementProgress`).get()).size, 1);
  });

  test("progresso diário antigo não é recompensado retroativamente e data anterior não avança streak", async () => {
    const {uid, email, functions} = await authenticatedClient();
    await seedUser(uid, email);
    await seedDailyMission("first", 1);
    await adminDb.doc(`users/${uid}/dailyMissionProgress/2026-10-01`).set({
      missionId: "first", date: "2026-10-01", isCompleted: true, completedAt: Timestamp.now(),
    });
    assert.equal((await submit(functions, 0)).data.alreadyCompleted, true);
    assert.equal((await streakRef(uid).get()).exists, false);
    assert.equal((await submit(functions, 1)).data.streak.currentStreak, 1);
    await submit(functions, 3);
    await assert.rejects(submit(functions, 2), (error) => error.code === "functions/failed-precondition");
    assert.equal((await streakRef(uid).get()).data().currentStreak, 2);
  });
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
    assert.deepEqual(progress.validatedActivityIds, ["activity-1"]);
    assert.equal(progress.activityValidationVersion, 3);
  });

  test("aceita apenas o conjunto exato numa atividade de seleção múltipla", async () => {
    const {uid, email, functions} = await authenticatedClient();
    await seedUser(uid, email);
    await seedLesson("lesson-multi", {activityIds: ["activity-1"]});
    await catalogWrite("lessons/lesson-multi/blocks/activity-1", "update", {
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
    await catalogWrite("modules/module-a", "set", {idModule: "module-a", order: 1});
    await catalogWrite("modules/module-b", "set", {idModule: "module-b", order: 2});
    await seedLesson("lesson-first", {moduleId: "module-a", order: 1, activityIds: ["activity-1"]});
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
    await httpsCallable(functions, "submitLessonActivity")({
      lessonId: "lesson-first", blockId: "activity-1", selectedAnswerIds: ["a"],
    });
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
    assert.equal((await adminDb.doc(`users/${uid}/lessonProgress/legacy-id`).get()).data().completionValidationVersion, undefined);
  });

  test("auditoria: responder lição histórica não valida IDs antigos sem resposta", async () => {
    const {uid, email, functions} = await authenticatedClient();
    await seedUser(uid, email, {xp: 90});
    await seedLesson("lesson-historical", {activityIds: ["activity-1", "activity-2"]});
    const progressRef = adminDb.doc(`users/${uid}/lessonProgress/legacy-id`);
    const completedAt = Timestamp.now();
    await progressRef.set({
      userId: uid, lessonId: "lesson-historical", isCompleted: true,
      completedActivityIds: ["activity-1"], completedAt, lastAccessed: Timestamp.now(),
    });
    await httpsCallable(functions, "openLesson")({lessonId: "lesson-historical"});
    const answer = await httpsCallable(functions, "submitLessonActivity")({
      lessonId: "lesson-historical", blockId: "activity-2", selectedAnswerIds: ["a"],
    });
    assert.equal(answer.data.correct, true);
    const retry = await httpsCallable(functions, "completeLesson")({lessonId: "lesson-historical"});
    assert.equal(retry.data.xpEarned, 0);
    const progress = (await progressRef.get()).data();
    const user = (await adminDb.doc(`users/${uid}`).get()).data();
    assert.equal(progress.completionValidationVersion, undefined);
    assert.equal(progress.completedAt.toMillis(), completedAt.toMillis());
    assert.equal(user.xp, 90);
    assert.equal(user.level, 1);
    assert.deepEqual(progress.completedActivityIds, ["activity-1", "activity-2"]);
    assert.deepEqual(progress.validatedActivityIds, ["activity-2"],
      `ID histórico foi marcado como validado sem resposta: ${JSON.stringify(progress)}`);
    assert.equal(progress.activityValidationVersion, 3);
  });

  test("progresso misto exige revalidar cada ID legado antes da nova conclusão", async () => {
    const {uid, email, functions} = await authenticatedClient();
    await seedUser(uid, email, {xp: 90});
    await seedLesson("lesson-mixed", {activityIds: ["activity-1", "activity-2"]});
    const ref = adminDb.doc(`users/${uid}/lessonProgress/lesson-mixed`);
    await ref.set({lessonId: "lesson-mixed", isCompleted: false,
      completedActivityIds: ["activity-1", "ghost"], activityValidationVersion: 1});
    const submit = httpsCallable(functions, "submitLessonActivity");
    const complete = httpsCallable(functions, "completeLesson");
    await assert.rejects(complete({lessonId: "lesson-mixed"}),
      (error) => error.code === "functions/failed-precondition");
    assert.deepEqual((await httpsCallable(functions, "openLesson")({lessonId: "lesson-mixed"})).data.completedActivityIds, []);
    assert.deepEqual((await submit({lessonId: "lesson-mixed", blockId: "activity-2", selectedAnswerIds: ["a"]})).data.completedActivityIds,
      ["activity-2"]);
    assert.deepEqual((await ref.get()).data().completedActivityIds, ["activity-1", "activity-2", "ghost"]);
    assert.deepEqual((await ref.get()).data().validatedActivityIds, ["activity-2"]);
    await assert.rejects(complete({lessonId: "lesson-mixed"}),
      (error) => error.code === "functions/failed-precondition");
    assert.equal((await adminDb.doc(`users/${uid}`).get()).data().xp, 90);
    await submit({lessonId: "lesson-mixed", blockId: "activity-1", selectedAnswerIds: ["a"]});
    assert.deepEqual((await ref.get()).data().validatedActivityIds, ["activity-1", "activity-2"]);
    assert.equal((await complete({lessonId: "lesson-mixed"})).data.xpEarned, 50);
    assert.equal((await ref.get()).data().completionValidationVersion, 3);
  });

  test("v3_restauracao preserva A e B, invalida só atividade editada e restaura A após responder B", async () => {
    const {uid, email, functions} = await authenticatedClient();
    await seedUser(uid, email, {xp: 90});
    await catalogWrite("modules/module-restore", "set", {title: "Módulo"});
    await seedModuleAchievement("badge-restore", "module-restore");
    await seedLesson("lesson-restore", {moduleId: "module-restore", activityIds: ["a1", "a2"]});
    const ref = adminDb.doc(`users/${uid}/lessonProgress/lesson-restore`);
    await ref.set({lessonId: "lesson-restore", isCompleted: false, completedActivityIds: ["ghost"]});
    const answer = (id, selection = "a") => httpsCallable(functions, "submitLessonActivity")({
      lessonId: "lesson-restore", blockId: id, selectedAnswerIds: [selection],
    });
    await httpsCallable(functions, "openLesson")({lessonId: "lesson-restore"});
    const publicAnswer = await answer("a1");
    assert.deepEqual(Object.keys(publicAnswer.data).sort(), ["completedActivityIds", "correct"]);
    await answer("a2");
    const before = (await ref.get()).data();
    assert.equal(before.activityValidationVersion, 3);
    assert.equal(before.activityValidationProofs.a1.length, 1);
    const {publishLearningCatalogMutation} = await import("../lib/learningCatalogPublication.js");
    const edit = async (fields) => {
      const generation = (await adminDb.doc("learningCatalogControl/current").get()).data().generation;
      await publishLearningCatalogMutation(adminDb, generation,
        [{operation: "update", path: "lessons/lesson-restore/blocks/a1", data: fields}]);
    };
    await edit({correctAnswerIds: ["b"]});
    assert.deepEqual((await httpsCallable(functions, "openLesson")({lessonId: "lesson-restore"})).data.completedActivityIds, ["a2"]);
    await assert.rejects(httpsCallable(functions, "completeLesson")({lessonId: "lesson-restore"}),
      (error) => error.code === "functions/failed-precondition");
    assert.equal((await adminDb.doc(`users/${uid}`).get()).data().xp, 90);
    assert.equal((await adminDb.doc(`users/${uid}/achievementProgress/badge-restore`).get()).exists, false);
    assert.equal((await answer("a1", "a")).data.correct, false);
    assert.equal((await ref.get()).data().activityValidationProofs.a1.length, 1);
    await answer("a1", "b");
    await answer("a1", "b");
    assert.equal((await ref.get()).data().activityValidationProofs.a1.length, 2);
    assert.equal((await ref.get()).data().activityValidationProofs.a2.length, 1);
    await edit({correctAnswerIds: ["a"]});
    assert.deepEqual((await httpsCallable(functions, "openLesson")({lessonId: "lesson-restore"})).data.completedActivityIds, ["a1", "a2"]);
    const completed = await httpsCallable(functions, "completeLesson")({lessonId: "lesson-restore"});
    assert.deepEqual(completed.data, {xpEarned: 50, leveledUp: true, alreadyCompleted: false});
    const after = (await ref.get()).data();
    assert.ok(after.completedActivityIds.includes("ghost"));
    assert.equal(after.validatedActivityIds.includes("ghost"), false);
    assert.equal(after.completionValidationVersion, 3);
    const badgeRef = adminDb.doc(`users/${uid}/achievementProgress/badge-restore`);
    const unlock = (await badgeRef.get()).data().unlockedAt.toMillis();
    await edit({correctAnswerIds: ["b"]});
    assert.equal((await httpsCallable(functions, "completeLesson")({lessonId: "lesson-restore"})).data.xpEarned, 0);
    assert.equal((await ref.get()).data().completedAt.toMillis(), after.completedAt.toMillis());
    assert.equal((await badgeRef.get()).data().unlockedAt.toMillis(), unlock);
  });

  test("v3_seguranca não aceita provas copiadas ou campos extras do cliente", async () => {
    const first = await authenticatedClient();
    const other = await authenticatedClient();
    await seedUser(first.uid, first.email); await seedUser(other.uid, other.email);
    await seedLesson("lesson-copy", {activityIds: ["activity-1"]});
    await httpsCallable(first.functions, "openLesson")({lessonId: "lesson-copy"});
    await httpsCallable(first.functions, "submitLessonActivity")({lessonId: "lesson-copy", blockId: "activity-1", selectedAnswerIds: ["a"]});
    const source = (await adminDb.doc(`users/${first.uid}/lessonProgress/lesson-copy`).get()).data();
    await adminDb.doc(`users/${other.uid}/lessonProgress/lesson-copy`).set(source);
    assert.deepEqual((await httpsCallable(other.functions, "openLesson")({lessonId: "lesson-copy"})).data.completedActivityIds, []);
    await assert.rejects(httpsCallable(other.functions, "completeLesson")({lessonId: "lesson-copy"}),
      (error) => error.code === "functions/failed-precondition");
    const wrong = await httpsCallable(other.functions, "submitLessonActivity")({
      lessonId: "lesson-copy", blockId: "activity-1", selectedAnswerIds: ["b"],
      correct: true, activityValidationVersion: 3, activityValidationProofs: source.activityValidationProofs,
    });
    assert.equal(wrong.data.correct, false);
    assert.equal((await adminDb.doc(`users/${other.uid}`).get()).data().xp, 0);
    await httpsCallable(other.functions, "submitLessonActivity")({lessonId: "lesson-copy", blockId: "activity-1", selectedAnswerIds: ["a"]});
    assert.equal((await httpsCallable(other.functions, "completeLesson")({lessonId: "lesson-copy"})).data.xpEarned, 50);
  });

  test("v3_catalogo recusa mídia não verificável e controle ausente sem efeito parcial", async () => {
    const {uid, email, functions} = await authenticatedClient();
    await seedUser(uid, email, {xp: 90});
    await seedLesson("lesson-media", {activityIds: ["activity-1"]});
    await httpsCallable(functions, "openLesson")({lessonId: "lesson-media"});
    await httpsCallable(functions, "submitLessonActivity")({lessonId: "lesson-media", blockId: "activity-1", selectedAnswerIds: ["a"]});
    await catalogWrite("lessons/lesson-media/blocks/activity-1", "update", {chartImageUrl: "https://example.invalid/mutable.png"});
    const ref = adminDb.doc(`users/${uid}/lessonProgress/lesson-media`);
    const before = (await ref.get()).data();
    for (const name of ["openLesson", "completeLesson"]) {
      await assert.rejects(httpsCallable(functions, name)({lessonId: "lesson-media"}),
        (error) => error.code === "functions/failed-precondition");
    }
    await assert.rejects(httpsCallable(functions, "submitLessonActivity")({lessonId: "lesson-media", blockId: "activity-1", selectedAnswerIds: ["a"]}),
      (error) => error.code === "functions/failed-precondition");
    assert.ok(JSON.stringify((await ref.get()).data()) === JSON.stringify(before), "mídia inválida alterou progresso");
    await adminDb.doc("learningCatalogControl/current").delete();
    await assert.rejects(httpsCallable(functions, "completeLesson")({lessonId: "lesson-media"}),
      (error) => error.code === "functions/failed-precondition");
    assert.equal((await adminDb.doc(`users/${uid}`).get()).data().xp, 90);
  });

  test("v3_campos materiais invalidam prova; ordem e feedback válidos preservam elegibilidade", async () => {
    const {uid, email, functions} = await authenticatedClient();
    await seedUser(uid, email); await seedLesson("lesson-fields", {activityIds: ["activity-1"]});
    await httpsCallable(functions, "openLesson")({lessonId: "lesson-fields"});
    await httpsCallable(functions, "submitLessonActivity")({lessonId: "lesson-fields", blockId: "activity-1", selectedAnswerIds: ["a"]});
    const original = (await adminDb.doc("lessons/lesson-fields/blocks/activity-1").get()).data();
    for (const fields of [{title: "Outro título"}, {content: "Outra condição"},
      {activityType: "TRUE_FALSE"}, {alternatives: {a: "Outro significado", b: "B"}}, {correctAnswerIds: ["b"]}]) {
      await catalogWrite("lessons/lesson-fields/blocks/activity-1", "set", {...original, ...fields});
      assert.deepEqual((await httpsCallable(functions, "openLesson")({lessonId: "lesson-fields"})).data.completedActivityIds, []);
      await assert.rejects(httpsCallable(functions, "completeLesson")({lessonId: "lesson-fields"}),
        (error) => error.code === "functions/failed-precondition");
      assert.equal((await adminDb.doc(`users/${uid}`).get()).data().xp, 0);
    }
    await catalogWrite("lessons/lesson-fields/blocks/activity-1", "set", {...original, order: 17, feedback: "Novo feedback"});
    assert.deepEqual((await httpsCallable(functions, "openLesson")({lessonId: "lesson-fields"})).data.completedActivityIds, ["activity-1"]);
    assert.equal((await httpsCallable(functions, "completeLesson")({lessonId: "lesson-fields"})).data.xpEarned, 50);
  });

  test("v3_provas rigorosas recusam corrupção e aceitam chave de verificação não ativa", async () => {
    const {uid, email, functions} = await authenticatedClient();
    await seedUser(uid, email); await seedLesson("lesson-keys", {activityIds: ["activity-1"]});
    await httpsCallable(functions, "openLesson")({lessonId: "lesson-keys"});
    await httpsCallable(functions, "submitLessonActivity")({lessonId: "lesson-keys", blockId: "activity-1", selectedAnswerIds: ["a"]});
    const ref = adminDb.doc(`users/${uid}/lessonProgress/lesson-keys`);
    const valid = (await ref.get()).data();
    const token = valid.activityValidationProofs["activity-1"][0];
    for (const corruption of [
      {activityValidationVersion: "3"}, {validatedActivityIds: []},
      {activityValidationProofs: {"activity-1": [token, token]}},
      {activityValidationProofs: {"activity-1": "bad"}}, {activityValidationProofs: {"activity-1": []}},
    ]) {
      await ref.set({...valid, ...corruption});
      const snapshot = (await ref.get()).data();
      for (const name of ["openLesson", "completeLesson", "submitLessonActivity"]) {
        await assert.rejects(httpsCallable(functions, name)({lessonId: "lesson-keys", blockId: "activity-1", selectedAnswerIds: ["a"]}),
          (error) => error.code === "functions/failed-precondition");
      }
      assert.ok(JSON.stringify((await ref.get()).data()) === JSON.stringify(snapshot), "corrupção produziu escrita parcial");
    }
    for (const fake of [token.replace(":k1:", ":unknown:"), "hmac-sha256:3:k1:" + "0".repeat(64)]) {
      await ref.set({...valid, activityValidationProofs: {"activity-1": [fake]}});
      assert.deepEqual((await httpsCallable(functions, "openLesson")({lessonId: "lesson-keys"})).data.completedActivityIds, []);
      await assert.rejects(httpsCallable(functions, "completeLesson")({lessonId: "lesson-keys"}),
        (error) => error.code === "functions/failed-precondition");
      assert.equal((await adminDb.doc(`users/${uid}`).get()).data().xp, 0);
    }
    const {parseLessonActivityProofKeyring, signLessonActivityProof, proofActivityFromBlock} = await import("../lib/lessonActivityProofs.js");
    const local = JSON.parse(process.env.GRANAXP_TEST_KEYRING);
    const rotated = parseLessonActivityProofKeyring(JSON.stringify({...local, activeKeyId: "k2"}));
    const block = (await adminDb.doc("lessons/lesson-keys/blocks/activity-1").get()).data();
    const rotatedToken = signLessonActivityProof({uid, lessonId: "lesson-keys", activityId: "activity-1"},
      proofActivityFromBlock(block, block.correctAnswerIds), rotated);
    await ref.set({...valid, activityValidationProofs: {"activity-1": [rotatedToken]}});
    assert.deepEqual((await httpsCallable(functions, "openLesson")({lessonId: "lesson-keys"})).data.completedActivityIds, ["activity-1"]);
    assert.equal((await httpsCallable(functions, "completeLesson")({lessonId: "lesson-keys"})).data.xpEarned, 50);
  });

  test("v3_concorrencia resposta concorrente com edição não concede XP usando snapshot antigo", async () => {
    const {uid, email, functions} = await authenticatedClient();
    await seedUser(uid, email); await seedLesson("lesson-edit-race", {activityIds: ["activity-1"]});
    await httpsCallable(functions, "openLesson")({lessonId: "lesson-edit-race"});
    await Promise.all([
      httpsCallable(functions, "submitLessonActivity")({lessonId: "lesson-edit-race", blockId: "activity-1", selectedAnswerIds: ["a"]}),
      catalogWrite("lessons/lesson-edit-race/blocks/activity-1", "update", {correctAnswerIds: ["b"]}),
    ]);
    assert.deepEqual((await httpsCallable(functions, "openLesson")({lessonId: "lesson-edit-race"})).data.completedActivityIds, []);
    await assert.rejects(httpsCallable(functions, "completeLesson")({lessonId: "lesson-edit-race"}),
      (error) => error.code === "functions/failed-precondition");
    assert.equal((await adminDb.doc(`users/${uid}`).get()).data().xp, 0);
  });

  test("v3_concorrencia inclusão versus conclusão aceita só conclusão anterior à publicação", async () => {
    const {uid, email, functions} = await authenticatedClient();
    await seedUser(uid, email); await seedLesson("lesson-include-race", {activityIds: ["activity-1"]});
    await httpsCallable(functions, "openLesson")({lessonId: "lesson-include-race"});
    await httpsCallable(functions, "submitLessonActivity")({lessonId: "lesson-include-race", blockId: "activity-1", selectedAnswerIds: ["a"]});
    const [outcome] = await Promise.all([
      httpsCallable(functions, "completeLesson")({lessonId: "lesson-include-race"})
        .then((result) => ({accepted: true, result: result.data}), (error) => ({accepted: false, code: error.code})),
      catalogWrite("lessons/lesson-include-race/blocks/activity-2", "create", {
        type: "ACTIVITY", activityType: "MULTIPLE_CHOICE", alternatives: {a: "A", b: "B"}, correctAnswerIds: ["a"], feedback: "Reveja.",
      }),
    ]);
    const progress = (await adminDb.doc(`users/${uid}/lessonProgress/lesson-include-race`).get()).data();
    const control = await adminDb.doc("learningCatalogControl/current").get();
    if (outcome.accepted) {
      const nanos = (time) => BigInt(time.seconds) * 1_000_000_000n + BigInt(time.nanoseconds);
      assert.ok(nanos(progress.completedAt) <= nanos(control.updateTime), "conclusão ignorou inclusão já publicada");
      assert.equal(outcome.result.xpEarned, 50);
    } else {
      assert.equal(outcome.code, "functions/failed-precondition");
      assert.equal(progress.isCompleted, false);
      assert.equal((await adminDb.doc(`users/${uid}`).get()).data().xp, 0);
    }
  });

  test("v3_concorrencia respostas iguais, remoção e conclusão concorrente não duplicam provas ou XP", async () => {
    const {uid, email, functions} = await authenticatedClient();
    await seedUser(uid, email); await seedLesson("lesson-remove", {activityIds: ["activity-1", "pending"]});
    await httpsCallable(functions, "openLesson")({lessonId: "lesson-remove"});
    const answer = () => httpsCallable(functions, "submitLessonActivity")({lessonId: "lesson-remove", blockId: "activity-1", selectedAnswerIds: ["a"]});
    await Promise.all([answer(), answer()]);
    const ref = adminDb.doc(`users/${uid}/lessonProgress/lesson-remove`);
    assert.equal((await ref.get()).data().activityValidationProofs["activity-1"].length, 1);
    await assert.rejects(httpsCallable(functions, "completeLesson")({lessonId: "lesson-remove"}),
      (error) => error.code === "functions/failed-precondition");
    await catalogWrite("lessons/lesson-remove/blocks/pending", "delete");
    const results = await Promise.all([answer(), httpsCallable(functions, "completeLesson")({lessonId: "lesson-remove"}),
      httpsCallable(functions, "completeLesson")({lessonId: "lesson-remove"})]);
    assert.deepEqual(results.slice(1).map((result) => result.data.xpEarned).sort((a, b) => a - b), [0, 50]);
    assert.equal((await ref.get()).data().activityValidationProofs["activity-1"].length, 1);
    assert.equal((await adminDb.doc(`users/${uid}`).get()).data().xp, 50);
    assert.equal((await adminDb.collection(`users/${uid}/lessonProgress`).get()).size, 1);
  });

  test("documento legado no ID canônico e duplicatas usam alvo determinístico sem promover histórico", async () => {
    const {uid, email, functions} = await authenticatedClient();
    await seedUser(uid, email);
    await seedLesson("lesson-duplicates", {activityIds: ["activity-1", "activity-2"]});
    const collection = adminDb.collection(`users/${uid}/lessonProgress`);
    const canonical = collection.doc("lesson-duplicates");
    await canonical.set({lessonId: "lesson-duplicates", isCompleted: false,
      completedActivityIds: ["activity-1"], activityValidationVersion: 1});
    await collection.doc("z-legacy").set({lessonId: "lesson-duplicates", isCompleted: false,
      completedActivityIds: ["ghost"]});
    const submit = httpsCallable(functions, "submitLessonActivity");
    await submit({lessonId: "lesson-duplicates", blockId: "activity-2", selectedAnswerIds: ["a"]});
    assert.deepEqual((await canonical.get()).data().completedActivityIds, ["activity-1", "activity-2", "ghost"]);
    assert.deepEqual((await canonical.get()).data().validatedActivityIds, ["activity-2"]);
    assert.equal((await collection.doc("z-legacy").get()).data().validatedActivityIds, undefined);
    await assert.rejects(httpsCallable(functions, "completeLesson")({lessonId: "lesson-duplicates"}),
      (error) => error.code === "functions/failed-precondition");
    await submit({lessonId: "lesson-duplicates", blockId: "activity-1", selectedAnswerIds: ["a"]});
    assert.equal((await httpsCallable(functions, "completeLesson")({lessonId: "lesson-duplicates"})).data.xpEarned, 50);
    assert.equal((await canonical.get()).data().completionValidationVersion, 3);
    assert.equal((await collection.doc("z-legacy").get()).data().isCompleted, false);
  });

  test("duas respostas corretas simultâneas preservam ambas as provas e retry não duplica IDs", async () => {
    const {uid, email, functions} = await authenticatedClient();
    await seedUser(uid, email);
    await seedLesson("lesson-concurrent", {activityIds: ["activity-1", "activity-2"]});
    await httpsCallable(functions, "openLesson")({lessonId: "lesson-concurrent"});
    const submit = httpsCallable(functions, "submitLessonActivity");
    const answer = (blockId) => submit({lessonId: "lesson-concurrent", blockId, selectedAnswerIds: ["a"]});
    const [first, second] = await Promise.all([answer("activity-1"), answer("activity-2")]);
    assert.equal(first.data.correct, true);
    assert.equal(second.data.correct, true);
    await answer("activity-1");
    const progress = (await adminDb.doc(`users/${uid}/lessonProgress/lesson-concurrent`).get()).data();
    assert.deepEqual(progress.validatedActivityIds, ["activity-1", "activity-2"]);
    assert.deepEqual(progress.completedActivityIds, ["activity-1", "activity-2"]);
    assert.equal(progress.activityValidationVersion, 3);
    assert.equal((await httpsCallable(functions, "completeLesson")({lessonId: "lesson-concurrent"})).data.xpEarned, 50);
  });

  for (const version of [undefined, 1, "2", 3]) {
    test(`versão de atividade ${String(version)} não autentica IDs históricos`, async () => {
      const {uid, email, functions} = await authenticatedClient();
      await seedUser(uid, email);
      await seedLesson("lesson-version", {activityIds: ["activity-1"]});
      const ref = adminDb.doc(`users/${uid}/lessonProgress/lesson-version`);
      await ref.set({lessonId: "lesson-version", isCompleted: false,
        completedActivityIds: ["activity-1"], validatedActivityIds: ["activity-1"],
        ...(version === undefined ? {} : {activityValidationVersion: version})});
      await assert.rejects(httpsCallable(functions, "completeLesson")({lessonId: "lesson-version"}),
        (error) => error.code === "functions/failed-precondition");
      assert.equal((await ref.get()).data().isCompleted, false);
    });
  }

  for (const validatedActivityIds of [undefined, "activity-1", ["activity-1", 7], [""]]) {
    test(`prova v2 ausente ou malformada ${JSON.stringify(validatedActivityIds)} é recusada`, async () => {
      const {uid, email, functions} = await authenticatedClient();
      await seedUser(uid, email);
      await seedLesson("lesson-malformed", {activityIds: ["activity-1"]});
      const ref = adminDb.doc(`users/${uid}/lessonProgress/lesson-malformed`);
      await ref.set({lessonId: "lesson-malformed", isCompleted: false,
        completedActivityIds: ["activity-1"], activityValidationVersion: 2,
        ...(validatedActivityIds === undefined ? {} : {validatedActivityIds})});
      await assert.rejects(httpsCallable(functions, "completeLesson")({lessonId: "lesson-malformed"}),
        (error) => error.code === "functions/failed-precondition");
      assert.equal((await ref.get()).data().isCompleted, false);
    });
  }

  test("lista histórica malformada é recusada sem apagar dados ou conceder recompensa", async () => {
    const {uid, email, functions} = await authenticatedClient();
    await seedUser(uid, email);
    await seedLesson("lesson-bad-history", {activityIds: ["activity-1"]});
    const ref = adminDb.doc(`users/${uid}/lessonProgress/lesson-bad-history`);
    await ref.set({lessonId: "lesson-bad-history", isCompleted: false,
      completedActivityIds: ["activity-1", 42], validatedActivityIds: ["activity-1"],
      activityValidationVersion: 2});
    const before = (await ref.get()).data();
    await assert.rejects(httpsCallable(functions, "completeLesson")({lessonId: "lesson-bad-history"}),
      (error) => error.code === "functions/failed-precondition");
    await assert.rejects(httpsCallable(functions, "submitLessonActivity")({
      lessonId: "lesson-bad-history", blockId: "activity-1", selectedAnswerIds: ["a"],
    }), (error) => error.code === "functions/failed-precondition");
    assert.deepEqual((await ref.get()).data(), before);
    assert.equal((await adminDb.doc(`users/${uid}`).get()).data().xp, 0);
  });

  test("prova v3 real distribuída em duplicatas pode ser consolidada sem promover IDs legados", async () => {
    const {uid, email, functions} = await authenticatedClient();
    await seedUser(uid, email);
    await seedLesson("lesson-proof-duplicates", {activityIds: ["activity-1", "activity-2"]});
    const collection = adminDb.collection(`users/${uid}/lessonProgress`);
    await collection.doc("a-proof").set({lessonId: "lesson-proof-duplicates", isCompleted: false,
      completedActivityIds: ["activity-1", "ghost"]});
    await collection.doc("b-proof").set({lessonId: "lesson-proof-duplicates", isCompleted: false,
      completedActivityIds: ["activity-2"]});
    for (const blockId of ["activity-1", "activity-2"]) {
      await httpsCallable(functions, "submitLessonActivity")({
        lessonId: "lesson-proof-duplicates", blockId, selectedAnswerIds: ["a"],
      });
    }
    const real = (await collection.doc("a-proof").get()).data();
    await collection.doc("a-proof").update({activityValidationProofs: {"activity-1": real.activityValidationProofs["activity-1"]}});
    await collection.doc("b-proof").update({activityValidationVersion: 3,
      completedActivityIds: ["activity-2"], validatedActivityIds: ["activity-2"],
      activityValidationProofs: {"activity-2": real.activityValidationProofs["activity-2"]}});
    const result = await httpsCallable(functions, "completeLesson")({lessonId: "lesson-proof-duplicates"});
    assert.equal(result.data.xpEarned, 50);
    assert.equal((await collection.doc("a-proof").get()).data().completionValidationVersion, 3);
    assert.equal((await collection.doc("b-proof").get()).data().isCompleted, false);
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
    await httpsCallable(functions, "openLesson")({lessonId: "lesson-1"});
    await httpsCallable(functions, "submitLessonActivity")({
      lessonId: "lesson-1", blockId: "activity-1", selectedAnswerIds: ["a"],
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
    assert.equal(progress.completionValidationVersion, 3);
  });

  test("não duplica XP em repetição nem em chamadas concorrentes", async () => {
    const {uid, email, functions} = await authenticatedClient();
    await seedUser(uid, email);
    await seedLesson("lesson-1", {xpReward: 60, activityIds: ["activity-1"]});
    await adminDb.doc(`users/${uid}/lessonProgress/lesson-1`).set({
      lessonId: "lesson-1", isCompleted: false, completedActivityIds: [], lastAccessed: Timestamp.now(), completedAt: null,
    });
    await httpsCallable(functions, "submitLessonActivity")({
      lessonId: "lesson-1", blockId: "activity-1", selectedAnswerIds: ["a"],
    });
    const call = httpsCallable(functions, "completeLesson");
    const [first, second] = await Promise.all([call({lessonId: "lesson-1"}), call({lessonId: "lesson-1"})]);
    assert.equal([first.data.xpEarned, second.data.xpEarned].sort((a, b) => a - b).join(","), "0,60");
    const third = await call({lessonId: "lesson-1"});
    assert.equal(third.data.xpEarned, 0);
    assert.equal(third.data.alreadyCompleted, true);
    assert.equal((await adminDb.doc(`users/${uid}`).get()).data().xp, 60);
    assert.equal((await adminDb.collection(`users/${uid}/lessonProgress`).get()).size, 1);
  });

  test("recusa lição sem atividades sem concluir, recompensar ou alterar progresso legado", async () => {
    const {uid, email, functions} = await authenticatedClient();
    await seedUser(uid, email, {xp: 90});
    await catalogWrite("modules/module-a", "set", {title: "A"});
    await seedLesson("lesson-empty", {moduleId: "module-a", xpReward: 50});
    await seedModuleAchievement("badge-a", "module-a");
    const progressRef = adminDb.doc(`users/${uid}/lessonProgress/legacy-auto-id`);
    await progressRef.set({
      userId: uid, lessonId: "lesson-empty", isCompleted: false,
      completedActivityIds: ["ghost"], lastAccessed: Timestamp.now(), completedAt: null,
    });
    const userBefore = (await adminDb.doc(`users/${uid}`).get()).data();
    const progressBefore = (await progressRef.get()).data();

    await assert.rejects(
      httpsCallable(functions, "completeLesson")({lessonId: "lesson-empty"}),
      (error) => error.code === "functions/failed-precondition" && /catálogo/i.test(error.message),
    );

    assert.deepEqual((await adminDb.doc(`users/${uid}`).get()).data(), userBefore);
    assert.deepEqual((await progressRef.get()).data(), progressBefore);
    assert.equal((await adminDb.collection(`users/${uid}/lessonProgress`).get()).size, 1);
    assert.equal((await adminDb.doc(`users/${uid}/achievementProgress/badge-a`).get()).exists, false);
  });

  test("recusa atividade do catálogo inválida mesmo após resposta antes validada", async () => {
    const {uid, email, functions} = await authenticatedClient();
    await seedUser(uid, email, {xp: 90});
    await seedLesson("lesson-invalid", {xpReward: 50, activityIds: ["activity-1"]});
    await httpsCallable(functions, "openLesson")({lessonId: "lesson-invalid"});
    await httpsCallable(functions, "submitLessonActivity")({
      lessonId: "lesson-invalid", blockId: "activity-1", selectedAnswerIds: ["a"],
    });
    await catalogWrite("lessons/lesson-invalid/blocks/activity-1", "update", {correctAnswerIds: []});
    const progressRef = adminDb.doc(`users/${uid}/lessonProgress/lesson-invalid`);
    const progressBefore = (await progressRef.get()).data();

    await assert.rejects(
      httpsCallable(functions, "completeLesson")({lessonId: "lesson-invalid"}),
      (error) => error.code === "functions/failed-precondition" && /catálogo/i.test(error.message),
    );

    assert.deepEqual((await adminDb.doc(`users/${uid}`).get()).data(), {
      name: "Usuário", email, level: 1, xp: 90, nextLevelXp: 100,
    });
    assert.deepEqual((await progressRef.get()).data(), progressBefore);
  });

  test("auditoria: tipo de atividade desconhecido não autoriza conclusão e XP", async () => {
    const {uid, email, functions} = await authenticatedClient();
    await seedUser(uid, email, {xp: 90});
    await catalogWrite("modules/module-unknown-type", "set", {title: "Módulo"});
    await seedModuleAchievement("badge-unknown-type", "module-unknown-type");
    await seedLesson("lesson-unknown-type", {
      moduleId: "module-unknown-type", xpReward: 50, activityIds: ["activity-1"],
    });
    await httpsCallable(functions, "openLesson")({lessonId: "lesson-unknown-type"});
    await httpsCallable(functions, "submitLessonActivity")({
      lessonId: "lesson-unknown-type", blockId: "activity-1", selectedAnswerIds: ["a"],
    });
    const userRef = adminDb.doc(`users/${uid}`);
    const progressRef = adminDb.doc(`users/${uid}/lessonProgress/lesson-unknown-type`);
    const userBefore = (await userRef.get()).data();
    const progressBefore = (await progressRef.get()).data();
    await catalogWrite("lessons/lesson-unknown-type/blocks/activity-1", "update", {
      activityType: "UNKNOWN_TYPE",
    });
    const invalidCatalog = (error) =>
      error.code === "functions/failed-precondition" && /catálogo/i.test(error.message);
    await assert.rejects(
      httpsCallable(functions, "openLesson")({lessonId: "lesson-unknown-type"}),
      invalidCatalog,
    );
    await assert.rejects(
      httpsCallable(functions, "submitLessonActivity")({
        lessonId: "lesson-unknown-type", blockId: "activity-1", selectedAnswerIds: ["a"],
      }),
      invalidCatalog,
    );
    await assert.rejects(
      httpsCallable(functions, "completeLesson")({lessonId: "lesson-unknown-type"}),
      invalidCatalog,
    );
    assert.deepEqual((await userRef.get()).data(), userBefore);
    assert.deepEqual((await progressRef.get()).data(), progressBefore);
    assert.equal((await adminDb.collection(`users/${uid}/lessonProgress`).get()).size, 1);
    assert.equal(progressBefore.isCompleted, false);
    assert.equal(progressBefore.completionValidationVersion, undefined);
    assert.equal((await adminDb.doc(`users/${uid}/achievementProgress/badge-unknown-type`).get()).exists, false);
  });

  test("auditoria: alternativa impossível de submeter não autoriza conclusão", async () => {
    const {uid, email, functions} = await authenticatedClient();
    await seedUser(uid, email, {xp: 90});
    await catalogWrite("modules/module-unanswerable", "set", {title: "Módulo"});
    await seedModuleAchievement("badge-unanswerable", "module-unanswerable");
    await seedLesson("lesson-unanswerable", {
      moduleId: "module-unanswerable", xpReward: 50, activityIds: ["activity-1"],
    });
    await httpsCallable(functions, "openLesson")({lessonId: "lesson-unanswerable"});
    await httpsCallable(functions, "submitLessonActivity")({
      lessonId: "lesson-unanswerable", blockId: "activity-1", selectedAnswerIds: ["a"],
    });
    const userRef = adminDb.doc(`users/${uid}`);
    const progressRef = adminDb.doc(`users/${uid}/lessonProgress/lesson-unanswerable`);
    const userBefore = (await userRef.get()).data();
    const progressBefore = (await progressRef.get()).data();
    const oversizedAnswerId = "x".repeat(201);
    await catalogWrite("lessons/lesson-unanswerable/blocks/activity-1", "update", {
      alternatives: {[oversizedAnswerId]: "Resposta"},
      correctAnswerIds: [oversizedAnswerId],
    });

    const opened = await httpsCallable(functions, "openLesson")({lessonId: "lesson-unanswerable"})
      .then((result) => ({accepted: true, result: result.data}), (error) => ({accepted: false, error}));
    const submitted = await httpsCallable(functions, "submitLessonActivity")({
      lessonId: "lesson-unanswerable", blockId: "activity-1", selectedAnswerIds: [oversizedAnswerId],
    }).then((result) => ({accepted: true, result: result.data}), (error) => ({accepted: false, error}));
    const submittedShort = await httpsCallable(functions, "submitLessonActivity")({
      lessonId: "lesson-unanswerable", blockId: "activity-1", selectedAnswerIds: ["a"],
    }).then((result) => ({accepted: true, result: result.data}), (error) => ({accepted: false, error}));
    const completed = await httpsCallable(functions, "completeLesson")({lessonId: "lesson-unanswerable"})
      .then((result) => ({accepted: true, result: result.data}), (error) => ({accepted: false, error}));
    const userAfter = (await userRef.get()).data();
    const progressAfter = (await progressRef.get()).data();

    assert.equal(opened.accepted, false,
      `openLesson aceitou alternativa impossível: ${JSON.stringify({opened, submitted, submittedShort, completed, userAfter, progressAfter})}`);
    assert.equal(opened.error.code, "functions/failed-precondition");
    assert.equal(submitted.accepted, false);
    assert.equal(submitted.error.code, "functions/invalid-argument");
    assert.equal(submittedShort.accepted, false);
    assert.equal(submittedShort.error.code, "functions/failed-precondition");
    assert.equal(completed.accepted, false);
    assert.equal(completed.error.code, "functions/failed-precondition");
    assert.deepEqual(userAfter, userBefore);
    assert.deepEqual(progressAfter, progressBefore);
    assert.equal(progressAfter.completionValidationVersion, undefined);
    assert.equal((await adminDb.collection(`users/${uid}/lessonProgress`).get()).size, 1);
    assert.equal((await adminDb.doc(`users/${uid}/achievementProgress/badge-unanswerable`).get()).exists, false);
  });

  test("auditoria_gabarito_alterado: acerto antigo não autoriza recompensa após troca válida do gabarito", async () => {
    const {uid, email, functions} = await authenticatedClient();
    await seedUser(uid, email, {xp: 90});
    await catalogWrite("modules/module-answer-change", "set", {title: "Módulo"});
    await seedModuleAchievement("badge-answer-change", "module-answer-change");
    await seedLesson("lesson-answer-change", {
      moduleId: "module-answer-change", xpReward: 50, activityIds: ["activity-1"],
    });
    await httpsCallable(functions, "openLesson")({lessonId: "lesson-answer-change"});
    const submitted = await httpsCallable(functions, "submitLessonActivity")({
      lessonId: "lesson-answer-change", blockId: "activity-1", selectedAnswerIds: ["a"],
    });
    assert.equal(submitted.data.correct, true);
    await catalogWrite("lessons/lesson-answer-change/blocks/activity-1", "update", {correctAnswerIds: ["b"]});

    const completion = await httpsCallable(functions, "completeLesson")({lessonId: "lesson-answer-change"})
      .then((result) => ({accepted: true, result: result.data}), (error) => ({accepted: false, code: error.code}));
    const user = (await adminDb.doc(`users/${uid}`).get()).data();
    const progress = (await adminDb.doc(`users/${uid}/lessonProgress/lesson-answer-change`).get()).data();
    const badge = await adminDb.doc(`users/${uid}/achievementProgress/badge-answer-change`).get();
    assert.equal(completion.accepted, false,
      `Conclusão aceitou prova do gabarito anterior: ${JSON.stringify({completion, user, progress, badge: badge.data()})}`);
    assert.equal(user.level, 1);
    assert.equal(user.xp, 90);
    assert.equal(progress.isCompleted, false);
    assert.equal(progress.completionValidationVersion, undefined);
    assert.equal(badge.exists, false);
  });

  test("aceita ID de alternativa com 200 caracteres em abertura, resposta e conclusão", async () => {
    const {uid, email, functions} = await authenticatedClient();
    await seedUser(uid, email);
    await seedLesson("lesson-id-limit", {activityIds: ["activity-1"]});
    const answerId = "x".repeat(200);
    await catalogWrite("lessons/lesson-id-limit/blocks/activity-1", "update", {
      alternatives: {[answerId]: "Resposta"}, correctAnswerIds: [answerId],
    });
    const opened = await httpsCallable(functions, "openLesson")({lessonId: "lesson-id-limit"});
    assert.equal(Object.keys(opened.data.blocks[0].alternatives)[0], answerId);
    const submitted = await httpsCallable(functions, "submitLessonActivity")({
      lessonId: "lesson-id-limit", blockId: "activity-1", selectedAnswerIds: [answerId],
    });
    assert.equal(submitted.data.correct, true);
    const completed = await httpsCallable(functions, "completeLesson")({lessonId: "lesson-id-limit"});
    assert.equal(completed.data.xpEarned, 50);
    assert.equal((await adminDb.doc(`users/${uid}/lessonProgress/lesson-id-limit`).get()).data().completionValidationVersion, 3);
  });

  test("recusa ID de resposta vazio sem validar atividade ou concluir", async () => {
    const {uid, email, functions} = await authenticatedClient();
    await seedUser(uid, email, {xp: 90});
    await seedLesson("lesson-empty-id", {activityIds: ["activity-1"]});
    await httpsCallable(functions, "openLesson")({lessonId: "lesson-empty-id"});
    const userRef = adminDb.doc(`users/${uid}`);
    const progressRef = adminDb.doc(`users/${uid}/lessonProgress/lesson-empty-id`);
    const userBefore = (await userRef.get()).data();
    const progressBefore = (await progressRef.get()).data();
    await assert.rejects(httpsCallable(functions, "submitLessonActivity")({
      lessonId: "lesson-empty-id", blockId: "activity-1", selectedAnswerIds: [""],
    }), (error) => error.code === "functions/invalid-argument");
    await assert.rejects(httpsCallable(functions, "completeLesson")({lessonId: "lesson-empty-id"}),
      (error) => error.code === "functions/failed-precondition");
    assert.deepEqual((await userRef.get()).data(), userBefore);
    assert.deepEqual((await progressRef.get()).data(), progressBefore);
    assert.equal((await adminDb.collection(`users/${uid}/lessonProgress`).get()).size, 1);
  });

  for (const activityType of [
    "MULTIPLE_CHOICE", "TRUE_FALSE", "CLASSIFICATION", "FINANCIAL_SCENARIO", "CHART_ANALYSIS",
  ]) {
    test(`aceita tipo Android ${activityType} em abertura, resposta e conclusão`, async () => {
      const {uid, email, functions} = await authenticatedClient();
      await seedUser(uid, email);
      await seedLesson("lesson-type", {xpReward: 50, activityIds: ["activity-1"]});
      const answerId = activityType === "TRUE_FALSE" ? "true" : "a";
      await catalogWrite("lessons/lesson-type/blocks/activity-1", "update", {
        activityType,
        alternatives: activityType === "TRUE_FALSE" ? {true: "Verdadeiro", false: "Falso"} : {a: "A", b: "B"},
        correctAnswerIds: [answerId],
      });

      const opened = await httpsCallable(functions, "openLesson")({lessonId: "lesson-type"});
      assert.equal(opened.data.blocks[0].activityType, activityType);
      assert.equal(opened.data.blocks[0].selectionMode, "SINGLE");
      assert.equal("correctAnswerIds" in opened.data.blocks[0], false);
      const answer = await httpsCallable(functions, "submitLessonActivity")({
        lessonId: "lesson-type", blockId: "activity-1", selectedAnswerIds: [answerId],
      });
      assert.equal(answer.data.correct, true);
      const completed = await httpsCallable(functions, "completeLesson")({lessonId: "lesson-type"});
      assert.equal(completed.data.xpEarned, 50);
      const progress = (await adminDb.doc(`users/${uid}/lessonProgress/lesson-type`).get()).data();
      assert.equal(progress.isCompleted, true);
      assert.equal(progress.completionValidationVersion, 3);
    });
  }

  test("recusa conclusão com atividade pendente sem alterar XP", async () => {
    const {uid, email, functions} = await authenticatedClient();
    await seedUser(uid, email);
    await seedLesson("lesson-1", {xpReward: 50, activityIds: ["activity-1"]});
    await adminDb.doc(`users/${uid}/lessonProgress/lesson-1`).set({
      lessonId: "lesson-1", isCompleted: false, completedActivityIds: [], lastAccessed: Timestamp.now(), completedAt: null,
    });
    const progressBefore = (await adminDb.doc(`users/${uid}/lessonProgress/lesson-1`).get()).data();
    await assert.rejects(
      httpsCallable(functions, "completeLesson")({lessonId: "lesson-1"}),
      (error) => error.code === "functions/failed-precondition",
    );
    assert.equal((await adminDb.doc(`users/${uid}`).get()).data().level, 1);
    assert.equal((await adminDb.doc(`users/${uid}`).get()).data().xp, 0);
    assert.deepEqual((await adminDb.doc(`users/${uid}/lessonProgress/lesson-1`).get()).data(), progressBefore);
  });

  test("conclui o documento legado sem criar recompensa duplicada", async () => {
    const {uid, email, functions} = await authenticatedClient();
    await seedUser(uid, email);
    await seedLesson("lesson-legacy", {xpReward: 25, activityIds: ["activity-1"]});
    await adminDb.doc(`users/${uid}/lessonProgress/legacy-auto-id`).set({
      userId: uid, lessonId: "lesson-legacy", isCompleted: false, completed: false,
      completedActivityIds: [], lastAccessed: Timestamp.now(), completedAt: null,
    });
    await httpsCallable(functions, "submitLessonActivity")({
      lessonId: "lesson-legacy", blockId: "activity-1", selectedAnswerIds: ["a"],
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
    await catalogWrite("modules/module-a", "set", {title: "A"});
    await catalogWrite("modules/module-b", "set", {title: "B"});
    await catalogWrite("modules/module-empty", "set", {title: "Sem lições"});
    await seedLesson("a-1", {moduleId: "module-a", order: 1, activityIds: ["activity-1"]});
    await seedLesson("a-2", {moduleId: "module-a", order: 2, activityIds: ["activity-1"]});
    await seedLesson("b-1", {moduleId: "module-b", order: 1, activityIds: ["activity-1"]});
    await seedModuleAchievement("badge-a", "module-a");
    await seedModuleAchievement("badge-b", "module-b");
    await seedModuleAchievement("badge-empty", "module-empty");
    const call = httpsCallable(functions, "completeLesson");

    await httpsCallable(functions, "openLesson")({lessonId: "a-1"});
    await httpsCallable(functions, "submitLessonActivity")({
      lessonId: "a-1", blockId: "activity-1", selectedAnswerIds: ["a"],
    });
    await call({lessonId: "a-1"});
    assert.equal((await adminDb.doc(`users/${uid}/achievementProgress/badge-a`).get()).exists, false);

    await httpsCallable(functions, "openLesson")({lessonId: "b-1"});
    await httpsCallable(functions, "submitLessonActivity")({
      lessonId: "b-1", blockId: "activity-1", selectedAnswerIds: ["a"],
    });
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
    await catalogWrite("modules/module-a", "set", {title: "A"});
    await seedLesson("a-1", {moduleId: "module-a", order: 1, xpReward: 25, activityIds: ["activity-1"]});
    await seedLesson("a-2", {moduleId: "module-a", order: 2, xpReward: 30, activityIds: ["activity-1"]});
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
    await httpsCallable(functions, "submitLessonActivity")({
      lessonId: "a-2", blockId: "activity-1", selectedAnswerIds: ["a"],
    });
    const result = await httpsCallable(functions, "completeLesson")({lessonId: "a-2", moduleId: "module-b"});
    assert.equal(result.data.xpEarned, 30);
    assert.equal((await adminDb.doc(`users/${uid}/achievementProgress/badge-a`).get()).exists, false);
    assert.equal((await adminDb.doc(`users/${uid}/achievementProgress/wrong-module`).get()).exists, false);
    assert.equal((await adminDb.doc(`users/${uid}`).get()).data().xp, 30);
  });

  test("marcador de conclusão v1 não prova MODULE_COMPLETION no fluxo v2", async () => {
    const {uid, email, functions} = await authenticatedClient();
    await seedUser(uid, email);
    await catalogWrite("modules/module-v1", "set", {title: "Módulo"});
    await seedLesson("v1-first", {moduleId: "module-v1", order: 1, activityIds: ["activity-1"]});
    await seedLesson("v1-second", {moduleId: "module-v1", order: 2, activityIds: ["activity-1"]});
    await seedModuleAchievement("badge-v1", "module-v1");
    await adminDb.doc(`users/${uid}/lessonProgress/v1-first`).set({
      lessonId: "v1-first", isCompleted: true, completedActivityIds: ["activity-1"],
      activityValidationVersion: 1, completionValidationVersion: 1,
    });
    await httpsCallable(functions, "openLesson")({lessonId: "v1-second"});
    await httpsCallable(functions, "submitLessonActivity")({
      lessonId: "v1-second", blockId: "activity-1", selectedAnswerIds: ["a"],
    });
    assert.equal((await httpsCallable(functions, "completeLesson")({lessonId: "v1-second"})).data.xpEarned, 50);
    assert.equal((await adminDb.doc(`users/${uid}/achievementProgress/badge-v1`).get()).exists, false);
    assert.equal((await adminDb.doc(`users/${uid}/lessonProgress/v1-first`).get()).data().completionValidationVersion, 1);
  });

  test("conclusão validada pelo backend em progresso de ID legado prova conclusão de módulo", async () => {
    const {uid, email, functions} = await authenticatedClient();
    await seedUser(uid, email);
    await catalogWrite("modules/module-a", "set", {title: "A"});
    await seedLesson("a-1", {moduleId: "module-a", order: 1, xpReward: 25, activityIds: ["activity-1"]});
    await seedLesson("a-2", {moduleId: "module-a", order: 2, xpReward: 30, activityIds: ["activity-1"]});
    await seedModuleAchievement("badge-a", "module-a");
    const legacyProgressRef = adminDb.doc(`users/${uid}/lessonProgress/legacy-random-id`);
    await legacyProgressRef.set({
      userId: uid, lessonId: "a-1", isCompleted: false, lastAccessed: Timestamp.now(),
    });
    const call = httpsCallable(functions, "completeLesson");
    await httpsCallable(functions, "submitLessonActivity")({
      lessonId: "a-1", blockId: "activity-1", selectedAnswerIds: ["a"],
    });
    await call({lessonId: "a-1"});
    assert.equal((await legacyProgressRef.get()).data().completionValidationVersion, 3);
    assert.equal((await adminDb.doc(`users/${uid}/achievementProgress/badge-a`).get()).exists, false);
    await httpsCallable(functions, "openLesson")({lessonId: "a-2"});
    await httpsCallable(functions, "submitLessonActivity")({
      lessonId: "a-2", blockId: "activity-1", selectedAnswerIds: ["a"],
    });
    await call({lessonId: "a-2"});
    assert.equal((await adminDb.doc(`users/${uid}/achievementProgress/badge-a`).get()).data().isUnlocked, true);
  });

  test("chamadas concorrentes desbloqueiam uma vez e preservam o primeiro unlockedAt", async () => {
    const {uid, email, functions} = await authenticatedClient();
    await seedUser(uid, email);
    await catalogWrite("modules/module-a", "set", {title: "A"});
    await seedLesson("a-1", {moduleId: "module-a", xpReward: 40, activityIds: ["activity-1"]});
    await seedModuleAchievement("badge-a", "module-a");
    await httpsCallable(functions, "openLesson")({lessonId: "a-1"});
    await httpsCallable(functions, "submitLessonActivity")({
      lessonId: "a-1", blockId: "activity-1", selectedAnswerIds: ["a"],
    });
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
    await catalogWrite("modules/module-a", "set", {title: "A"});
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
    assert.deepEqual([first.data.xpEarned, second.data.xpEarned].sort((a, b) => a - b), [0, 0]);
    assert.equal((await adminDb.collection(`users/${uid}/goals`).get()).size, 1);
    const third = await call(request);
    assert.equal(third.data.xpEarned, 0);
    await adminDb.doc(`users/${uid}/goals/${first.data.goalId}`).delete();
    assert.equal((await call(request)).data.xpEarned, 0);
    assert.equal((await adminDb.collection(`users/${uid}/goals`).get()).size, 0);
    assert.equal((await adminDb.doc(`users/${uid}`).get()).data().xp, 0);
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

  test("criação concluída autodeclarada registra conclusão sem conceder XP", async () => {
    const {uid, email, functions} = await authenticatedClient();
    await seedUser(uid, email);
    const deadlineDate = ClientTimestamp.fromMillis(Date.now() + 86_400_000);
    const result = await httpsCallable(functions, "createGoal")({
      requestId: "request-1",
      title: "Reserva", targetAmount: 100, currentAmount: 100, deadline: "CURTO",
      deadlineDate, xpReward: 999999,
    });
    assert.equal(result.data.xpEarned, 0);
    assert.equal(result.data.leveledUp, false);
    const goal = (await adminDb.doc(`users/${uid}/goals/${result.data.goalId}`).get()).data();
    assert.ok(goal.completedAt instanceof Timestamp);
    assert.equal(goal.deadlineDate.toMillis(), deadlineDate.toMillis());
    assert.deepEqual((await adminDb.doc(`users/${uid}`).get()).data(), {
      name: "Usuário", email, level: 1, xp: 0, nextLevelXp: 100,
    });
  });

  test("cruzamento legado sem autorização mantém progresso sem recompensa retroativa", async () => {
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
    assert.deepEqual([first.data.xpEarned, second.data.xpEarned].sort((a, b) => a - b), [0, 0]);
    assert.equal((await adminDb.doc(`users/${uid}`).get()).data().xp, 0);
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
    await adminDb.doc(`users/${owner.uid}/dailyMissionProgress/2026-10-01`).set({
      missionId: "mission-1", date: "2026-10-01", isCompleted: true, completedAt: Timestamp.now(),
    });
    await adminDb.doc(`users/${owner.uid}/learningStats/streak`).set({
      currentStreak: 7, highestStreak: 7, lastMissionCompletedDate: "2026-10-07",
      claimedMilestones: [3, 7],
    });
    await adminDb.doc(`users/${owner.uid}/future/private/nested/doc-1`).set({secret: true});
    await adminDb.doc("achievementProgress/owner-progress").set({userId: owner.uid, achievementId: "a"});
    await adminDb.doc("achievementProgress/other-progress").set({userId: other.uid, achievementId: "a"});

    const result = await httpsCallable(owner.functions, "deleteAccount")({});
    assert.deepEqual(result.data, {deleted: true});
    assert.equal((await adminDb.doc(`users/${owner.uid}`).get()).exists, false);
    assert.equal((await adminDb.doc(`users/${owner.uid}/goals/goal-1`).get()).exists, false);
    assert.equal((await adminDb.doc(`users/${owner.uid}/achievementProgress/badge-a`).get()).exists, false);
    assert.equal((await adminDb.doc(`users/${owner.uid}/dailyMissionProgress/2026-10-01`).get()).exists, false);
    assert.equal((await adminDb.doc(`users/${owner.uid}/learningStats/streak`).get()).exists, false);
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

describe("guard de piloto do backend", () => {
  const at = Date.parse("2026-10-03T03:00:00.000Z");
  const goalData = {requestId: "pilot-request-1", title: "Reserva", targetAmount: 100,
    currentAmount: 90, deadline: "CURTO", deadlineDate: null};
  const calls = {
    openLesson: {lessonId: "pilot-lesson"},
    submitLessonActivity: {lessonId: "pilot-lesson", blockId: "activity-1", selectedAnswerIds: ["a"]},
    completeLesson: {lessonId: "pilot-lesson"},
    createGoal: goalData,
    updateGoalProgress: {goalId: "pilot-goal", amountToAdd: 10},
    updateGoalDetails: {goalId: "pilot-goal", title: "Editada", targetAmount: 50, deadline: "CURTO"},
    deleteAccount: {},
    openDailyMission: {deviceTimeMillis: at},
    submitDailyMissionAnswer: {deviceTimeMillis: at, expectedDate: "2026-10-03",
      expectedMissionId: "pilot-mission", selectedAnswerIds: ["a"]},
  };

  function configure(t, mode, pilotUid) {
    const names = ["BACKEND_CALLABLE_MODES", "BACKEND_PILOT_UID", "LESSON_ACTIVITY_PROOF_KEYRING"];
    const previous = names.map((name) => process.env[name]);
    t.after(() => names.forEach((name, i) => {
      if (previous[i] === undefined) delete process.env[name];
      else process.env[name] = previous[i];
    }));
    for (const [name, value] of [[names[0], mode === undefined ? undefined : JSON.stringify(Object.fromEntries(Object.keys(calls).map(name => [name, mode])))], [names[1], pilotUid]]) {
      if (value === undefined) delete process.env[name];
      else process.env[name] = value;
    }
    process.env.LESSON_ACTIVITY_PROOF_KEYRING = process.env.GRANAXP_TEST_KEYRING;
  }

  async function state() {
    const documents = [];
    async function collect(parent) {
      for (const collection of await parent.listCollections()) {
        for (const document of (await collection.get()).docs) {
          documents.push([document.ref.path, document.data(), document.updateTime]);
          await collect(document.ref);
        }
      }
    }
    await collect(adminDb);
    const users = (await adminAuth.listUsers()).users.map((user) => user.toJSON());
    return {documents: documents.sort(([a], [b]) => a.localeCompare(b)),
      users: users.sort((a, b) => a.uid.localeCompare(b.uid))};
  }

  async function fixtures() {
    const owner = await authenticatedClient();
    const other = await authenticatedClient();
    await seedUser(owner.uid, owner.email);
    await seedUser(other.uid, other.email);
    await seedLesson("pilot-lesson", {activityIds: ["activity-1"]});
    await seedDailyMission("pilot-mission", 0);
    await adminDb.doc(`users/${owner.uid}/goals/pilot-goal`).set(goalData);
    await adminDb.doc(`users/${owner.uid}/lessonProgress/pilot-lesson`).set({
      lessonId: "pilot-lesson", isCompleted: true, completedActivityIds: ["activity-1"],
    });
    await adminDb.doc(`users/${owner.uid}/learningStats/streak`).set({
      currentStreak: 2, highestStreak: 2, lastMissionCompletedDate: "2026-10-02", claimedMilestones: [],
    });
    await adminDb.doc(`users/${owner.uid}/dailyMissionProgress/2026-10-03`).set({
      missionId: "pilot-mission", date: "2026-10-03", isCompleted: true,
    });
    await adminDb.doc(`users/${owner.uid}/goalCreationRequests/${goalData.requestId}`).set({
      goalId: "pilot-goal", fingerprint: JSON.stringify({...goalData, requestId: undefined}),
    });
    return {owner, other};
  }

  // These tests fail if any callable bypasses authorization, even on a historical/replay path.
  for (const [label, mode, pilot, code] of [
    ["disabled", "disabled", "owner", "failed-precondition"],
    ["modo ausente", undefined, "owner", "failed-precondition"],
    ["modo vazio", "", "owner", "failed-precondition"],
    ["modo inválido", "OPEN", "owner", "failed-precondition"],
    ["modo com espaços", " open ", "owner", "failed-precondition"],
    ["pilot sem UID", "pilot", undefined, "failed-precondition"],
    ["pilot UID vazio", "pilot", "", "failed-precondition"],
    ["pilot UID em branco", "pilot", "   ", "failed-precondition"],
    ["pilot UID com espaços externos", "pilot", " padded ", "failed-precondition"],
    ["pilot UID longo", "pilot", "x".repeat(129), "failed-precondition"],
    ["pilot UID incorreto", "pilot", "other", "permission-denied"],
  ]) {
    test(`pilot_guard ${label}: nove callables negadas antes de dados, sem efeitos`, async (t) => {
      const {owner, other} = await fixtures();
      const pilotUid = pilot === "owner" ? owner.uid : pilot === "other" ? other.uid : pilot;
      configure(t, mode, pilotUid);
      const backend = await import("../lib/index.js");
      const beforeState = await state();
      // Tripwires at the actual Admin boundary catch reads as well as writes/deletion.
      const touched = () => assert.fail("Callable negada alcançou o Admin SDK.");
      for (const method of ["doc", "collection", "runTransaction", "recursiveDelete", "bulkWriter"]) {
        t.mock.method(adminDb, method, touched);
      }
      t.mock.method(adminAuth, "deleteUser", touched);
      try {
        for (const [name, data] of Object.entries(calls)) {
          await assert.rejects(backend[name].run({auth: {uid: owner.uid}, data: {...data,
            userId: pilotUid, uid: pilotUid, mode: "open", BACKEND_ACCESS_MODE: "open",
            BACKEND_PILOT_UID: owner.uid}}), (error) => {
            assert.equal(error.code, code, name);
            assert.equal(error.details, undefined);
            assert.ok(!error.message.includes(owner.uid) && !error.message.includes(other.uid));
            return true;
          });
        }
        await assert.rejects(backend.openDailyMission.run({auth: {uid: owner.uid},
          data: {deviceTimeMillis: 0}}), (error) => error.code === code);
        await assert.rejects(backend.createGoal.run({auth: {uid: owner.uid}, data: null}),
          (error) => error.code === code);
      } finally { t.mock.restoreAll(); }
      assert.deepEqual(await state(), beforeState);
    });
  }

  for (const mode of [undefined, "disabled", "pilot", "open"]) {
    test(`pilot_guard sem autenticação em ${mode ?? "ausente"}: payload não concede identidade`, async (t) => {
      configure(t, mode, "fixture-pilot-uid");
      const backend = await import("../lib/index.js");
      for (const [name, data] of Object.entries(calls)) {
        await assert.rejects(backend[name].run({data: {...data, uid: "fixture-pilot-uid",
          userId: "fixture-pilot-uid", BACKEND_ACCESS_MODE: "open"}}),
        (error) => error.code === "unauthenticated");
      }
    });
  }

  for (const mode of ["pilot", "open"]) {
    test(`pilot_guard ${mode}: identidade admitida percorre as nove, XP e exclusão preservados`, async (t) => {
      const {owner, other} = await fixtures();
      configure(t, mode, mode === "pilot" ? owner.uid : undefined);
      const backend = await import("../lib/index.js");
      const call = (name, data = calls[name]) => backend[name].run({auth: {uid: owner.uid},
        data: {...data, userId: other.uid, uid: other.uid, BACKEND_ACCESS_MODE: "disabled",
          BACKEND_PILOT_UID: other.uid}});
      const otherBefore = (await adminDb.doc(`users/${other.uid}`).get()).data();
      await adminDb.doc(`users/${owner.uid}/lessonProgress/pilot-lesson`).delete();
      await adminDb.doc(`users/${owner.uid}/dailyMissionProgress/2026-10-03`).delete();
      await adminDb.doc(`users/${owner.uid}/goalCreationRequests/${goalData.requestId}`).delete();
      assert.equal((await call("openLesson")).blocks.length, 1);
      assert.equal((await call("submitLessonActivity")).correct, true);
      assert.equal((await call("completeLesson")).xpEarned, 50);
      assert.equal((await call("completeLesson")).xpEarned, 0);
      const created = await call("createGoal", {...goalData, currentAmount: 100});
      assert.equal(created.xpEarned, 0);
      assert.equal((await call("createGoal", {...goalData, currentAmount: 100})).xpEarned, 0);
      assert.equal((await call("updateGoalProgress")).xpEarned, 0);
      assert.equal((await call("updateGoalDetails")).xpEarned, 0);
      assert.equal((await adminDb.doc(`users/${owner.uid}/goals/pilot-goal`).get()).data().title, "Editada");
      assert.equal((await call("openDailyMission")).available, true);
      const mission = await call("submitDailyMissionAnswer");
      assert.equal(mission.xpEarned, 100);
      assert.equal(mission.streak.currentStreak, 3);
      assert.deepEqual(mission.streak.claimedMilestones, [3]);
      assert.equal((await call("submitDailyMissionAnswer")).xpEarned, 0);
      assert.equal((await adminDb.doc(`users/${owner.uid}/lessonProgress/pilot-lesson`).get()).get("completionValidationVersion"), 3);
      assert.equal((await adminDb.doc(`users/${owner.uid}/dailyMissionProgress/2026-10-03`).get()).get("isCompleted"), true);
      assert.deepEqual(await call("deleteAccount"), {deleted: true});
      assert.equal((await adminDb.doc(`users/${owner.uid}`).get()).exists, false);
      for (const path of ["goals", "lessonProgress", "goalCreationRequests", "dailyMissionProgress", "learningStats"]) {
        assert.equal((await adminDb.collection(`users/${owner.uid}/${path}`).get()).empty, true);
      }
      await assert.rejects(adminAuth.getUser(owner.uid), (error) => error.code === "auth/user-not-found");
      assert.deepEqual((await adminDb.doc(`users/${other.uid}`).get()).data(), otherBefore);
      assert.equal((await adminAuth.getUser(other.uid)).uid, other.uid);
    });
  }

  test("pilot_transport parâmetros reais do emulador admitem ou negam sem override cliente", async () => {
    const mode = process.env.GRANAXP_TEST_ACCESS_MODE ?? "open";
    assert.ok(["disabled", "pilot", "open"].includes(mode));
    await adminAuth.createUser({uid: "fixture-pilot-transport", email: "pilot@example.com", password: "password123"});
    const app = initializeApp({apiKey: "demo-key", projectId: PROJECT_ID}, `pilot-transport-${++sequence}`);
    const auth = getAuth(app);
    connectAuthEmulator(auth, `http://${AUTH_HOST}`, {disableWarnings: true});
    await signInWithEmailAndPassword(auth, "pilot@example.com", "password123");
    const functions = getFunctions(app, REGION);
    connectFunctionsEmulator(functions, "127.0.0.1", FUNCTIONS_PORT);
    clients.push({app, auth});
    const other = await authenticatedClient();
    await seedUser("fixture-pilot-transport", "pilot@example.com");
    await seedUser(other.uid, other.email);
    const beforeState = await state();
    for (const [client, isPilot] of [[functions, true], [other.functions, false]]) {
      const denied = mode === "disabled" || (mode === "pilot" && !isPilot);
      const data = {deviceTimeMillis: 0, uid: "fixture-pilot-transport", userId: "fixture-pilot-transport",
        BACKEND_ACCESS_MODE: "open", BACKEND_PILOT_UID: other.uid};
      if (denied) {
        for (const name of Object.keys(calls)) {
          await assert.rejects(httpsCallable(client, name)(data), (error) => {
            assert.equal(error.code, mode === "disabled" ? "functions/failed-precondition" : "functions/permission-denied", name);
            assert.equal(error.details, undefined);
            assert.ok(!error.message.includes("fixture-pilot-transport") && !error.message.includes(other.uid));
            return true;
          });
        }
      } else {
        assert.deepEqual((await httpsCallable(client, "openDailyMission")(data)).data, {available: false});
      }
    }
    assert.deepEqual(await state(), beforeState);
  });
});
