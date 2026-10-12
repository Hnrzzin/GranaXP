import assert from 'node:assert/strict';
import {createRequire} from 'node:module';
import {readFileSync} from 'node:fs';
import {resolve} from 'node:path';
import {initializeApp as initializeClientApp, deleteApp} from 'firebase/app';
import {connectAuthEmulator, createUserWithEmailAndPassword, getAuth, signOut} from 'firebase/auth';
import {connectFirestoreEmulator, disableNetwork, enableNetwork, doc, getDoc, getDocs,
  getDocsFromServer, collection, query, where, updateDoc, setDoc} from 'firebase/firestore';
import {connectFunctionsEmulator, getFunctions, httpsCallable} from 'firebase/functions';
import {assertDemoEmulatorTarget, buildCatalogManifest} from './catalog-10-3.mjs';

const requireFunctions = createRequire(resolve('functions/package.json'));
const {initializeApp: initializeAdminApp} = requireFunctions('firebase-admin/app');
const {getFirestore} = requireFunctions('firebase-admin/firestore');
const {publishLearningCatalogMutation} = requireFunctions('./lib/learningCatalogPublication.js');
const projectId = 'demo-granaxp';
const firestoreHost = '127.0.0.1:8080';
const authHost = '127.0.0.1:9099';
const functionsHost = '127.0.0.1';
const functionsPort = 5001;
const region = 'southamerica-east1';

async function guard() {
  assertDemoEmulatorTarget(projectId, firestoreHost);
  for (const [name, desired] of [
    ['FIRESTORE_EMULATOR_HOST', firestoreHost],
    ['FIREBASE_AUTH_EMULATOR_HOST', authHost],
    ['GCLOUD_PROJECT', projectId],
  ]) {
    assert.ok(!process.env[name] || process.env[name] === desired,
      `${name} differs from required demo endpoint`);
    process.env[name] = desired;
  }
  const response = await fetch(`http://${firestoreHost}/emulator/v1/projects/${projectId}:ruleCoverage`,
    {redirect: 'error'});
  assert.equal(response.status, 200, 'Firestore Emulator-specific probe failed');
  const authProbe = await fetch(`http://${authHost}/emulator/v1/projects/${projectId}/accounts`,
    {redirect: 'error'});
  assert.equal(authProbe.status, 405, 'Auth Emulator-specific probe failed');
  const functionsProbe = await fetch(`http://${functionsHost}:${functionsPort}/${projectId}/${region}/openLesson`,
    {method: 'OPTIONS', redirect: 'error'});
  assert.ok(functionsProbe.status < 500, 'Functions Emulator probe failed');
}

async function client(name) {
  const app = initializeClientApp({apiKey: 'demo-key', projectId}, name);
  assert.equal(app.options.projectId, projectId);
  const auth = getAuth(app);
  connectAuthEmulator(auth, `http://${authHost}`, {disableWarnings: true});
  const email = `catalog103-${name}@example.test`;
  const password = 'local-test-password';
  const credential = await createUserWithEmailAndPassword(auth, email, password);
  const db = (await import('firebase/firestore')).getFirestore(app);
  connectFirestoreEmulator(db, '127.0.0.1', 8080);
  const functions = getFunctions(app, region);
  connectFunctionsEmulator(functions, functionsHost, functionsPort);
  return {app, auth, db, functions, uid: credential.user.uid, email};
}

async function call(functions, name, data) {
  return (await httpsCallable(functions, name, {timeout: 60_000})(data)).data;
}

async function rejectsPrecondition(operation) {
  await assert.rejects(operation, (error) => error.code === 'functions/failed-precondition');
}

function lessonBlocks(manifest, id) {
  return manifest.blocks.filter(({path}) => path.startsWith(`lessons/${id}/`));
}

async function exerciseLesson(user, manifest, lessonId, xpReward, achievementId) {
  const expected = lessonBlocks(manifest, lessonId);
  const opened = await call(user.functions, 'openLesson', {lessonId});
  assert.equal(opened.blocks.length, expected.length);
  assert.deepEqual(opened.blocks.map(({id}) => id), expected.map(({path}) => path.split('/').at(-1)));
  assert.deepEqual(opened.blocks.map(({order}) => order), expected.map(({fields}) => fields.order));
  assert.deepEqual(opened.completedActivityIds, []);
  for (const block of opened.blocks) {
    assert.equal('correctAnswerIds' in block, false);
    assert.equal('feedback' in block, false);
    const source = expected.find(({path}) => path.endsWith(`/${block.id}`)).fields;
    assert.equal(block.type, source.type);
    assert.equal(block.title, source.title);
    assert.equal(block.content, source.content);
    if (source.type === 'ACTIVITY') {
      assert.equal(block.activityType, source.activityType);
      assert.equal(block.selectionMode, 'SINGLE');
      assert.deepEqual(block.alternatives, source.alternatives);
    }
  }
  await rejectsPrecondition(() => call(user.functions, 'completeLesson', {lessonId}));
  const activities = expected.filter(({fields}) => fields.type === 'ACTIVITY');
  for (let index = 0; index < activities.length; index++) {
    const {path, fields} = activities[index];
    const blockId = path.split('/').at(-1);
    const right = fields.correctAnswerIds[0];
    const wrong = Object.keys(fields.alternatives).find((id) => id !== right);
    const error = await call(user.functions, 'submitLessonActivity',
      {lessonId, blockId, selectedAnswerIds: [wrong]});
    assert.equal(error.correct, false);
    assert.equal(error.feedback, fields.feedback);
    const progressRef = getFirestore().doc(`users/${user.uid}/lessonProgress/${lessonId}`);
    assert.equal((await progressRef.get()).data().completedActivityIds.length, index);
    const answer = await call(user.functions, 'submitLessonActivity',
      {lessonId, blockId, selectedAnswerIds: [right]});
    assert.equal(answer.correct, true);
    assert.equal('feedback' in answer, false);
    assert.equal(answer.completedActivityIds.length, index + 1);
    const persisted = (await progressRef.get()).data();
    assert.equal(persisted.activityValidationVersion, 3);
    assert.equal(persisted.activityValidationProofs[blockId].length, 1);
    assert.equal('activityValidationProofs' in answer, false);
    assert.equal(persisted.completedActivityIds.length, index + 1);
    const reopened = await call(user.functions, 'openLesson', {lessonId});
    assert.equal(reopened.completedActivityIds.length, index + 1);
    if (index < activities.length - 1) {
      await rejectsPrecondition(() => call(user.functions, 'completeLesson', {lessonId}));
    }
    console.log(`ACTIVITY PASS ${lessonId}/${blockId}: wrong, feedback, retry, persisted.`);
  }
  const first = await call(user.functions, 'completeLesson', {lessonId});
  assert.deepEqual([first.xpEarned, first.alreadyCompleted], [xpReward, false]);
  const progressRef = getFirestore().doc(`users/${user.uid}/lessonProgress/${lessonId}`);
  const progress = (await progressRef.get()).data();
  assert.equal(progress.isCompleted, true);
  assert.equal(progress.completionValidationVersion, 3);
  assert.ok(progress.completedAt);
  const repeat = await call(user.functions, 'completeLesson', {lessonId});
  assert.deepEqual([repeat.xpEarned, repeat.alreadyCompleted], [0, true]);
  assert.equal((await progressRef.get()).data().completedAt.toMillis(), progress.completedAt.toMillis());
  const review = await call(user.functions, 'openLesson', {lessonId});
  assert.equal(review.blocks.length, expected.length);
  assert.equal(review.completedActivityIds.length, 3);
  if (achievementId) {
    const achievement = await getFirestore().doc(`users/${user.uid}/achievementProgress/${achievementId}`).get();
    assert.equal(achievement.data()?.isUnlocked, true);
    assert.equal(achievement.data()?.currentProgress, 1);
    assert.ok(achievement.data()?.unlockedAt);
  }
  console.log(`LESSON PASS ${lessonId}: ${expected.length} blocks, ${xpReward} XP, review, no duplicate XP.`);
}

async function main() {
  const [draftsDir, manifestPath] = process.argv.slice(2);
  assert.ok(draftsDir && manifestPath, 'Usage: catalog-10-3-emulator.mjs DRAFTS_DIR MANIFEST_PATH');
  const {manifest} = buildCatalogManifest(draftsDir);
  assert.deepEqual(JSON.parse(readFileSync(manifestPath, 'utf8')), manifest,
    'Manifest differs from the six approved drafts');
  await guard(); // No Firebase write, Admin initialization or user creation before this point.
  const adminApp = initializeAdminApp({projectId});
  const adminDb = getFirestore(adminApp);
  for (const collectionName of ['modules', 'lessons', 'achievements']) {
    assert.equal((await adminDb.collection(collectionName).limit(1).get()).empty, true,
      `Demo ${collectionName} must be empty before seed`);
  }
  const achievementFixtures = [
    ['catalog103_module_organizacao_financeira', 'organizacao_financeira', 'Organização Financeira'],
    ['catalog103_module_investimentos', 'investimentos', 'Investimentos'],
  ];
  await adminDb.doc('learningCatalogControl/current').create({protocolVersion: 1, generation: 1});
  const mutations = [];
  for (const {path, fields} of [...manifest.modules, ...manifest.lessons, ...manifest.blocks]) {
    mutations.push({operation: 'create', path, data: fields});
  }
  for (const [id, referenceId, name] of achievementFixtures) {
    mutations.push({operation: 'create', path: `achievements/${id}`, data: {
      title: `${name} concluído`, description: `Conclua as lições do módulo ${name}.`,
      icon: 'school', requirementType: 'MODULE_COMPLETION', requirementValue: 1,
      referenceId, category: 'EDUCACAO',
    }});
  }
  await publishLearningCatalogMutation(adminDb, 1, mutations);
  for (const {path, fields} of [...manifest.modules, ...manifest.lessons, ...manifest.blocks]) {
    assert.deepEqual((await adminDb.doc(path).get()).data(), fields, `${path} differs after seed`);
  }
  console.log('SEED PASS: demo-granaxp only, 2 modules, 3 lessons, 34 blocks, 2 achievement fixtures.');

  const first = await client('a');
  const second = await client('b');
  const third = await client('c');
  try {
    await setDoc(doc(first.db, `users/${first.uid}`),
      {name: 'Teste catálogo A', email: first.email, level: 1, xp: 0, nextLevelXp: 100});
    await setDoc(doc(second.db, `users/${second.uid}`),
      {name: 'Teste catálogo B', email: second.email, level: 1, xp: 0, nextLevelXp: 100});
    await setDoc(doc(third.db, `users/${third.uid}`),
      {name: 'Teste prova V3', email: third.email, level: 1, xp: 0, nextLevelXp: 100});
    const modules = await getDocs(collection(first.db, 'modules'));
    assert.deepEqual(modules.docs.sort((a, b) => a.data().order - b.data().order)
      .map((entry) => entry.id), ['organizacao_financeira', 'investimentos']);
    const globalLessons = await getDocsFromServer(collection(first.db, 'lessons'));
    assert.equal(globalLessons.size, 3);
    for (const [moduleId, ids] of [
      ['organizacao_financeira', ['2iT6j1pA0vvq62oX7jO7', 'gMmIv1XNtMhIrhBzDFKc']],
      ['investimentos', ['Eo6rq9c4HDAtZRR6TAjp']],
    ]) {
      const result = await getDocs(query(collection(first.db, 'lessons'), where('moduleId', '==', moduleId)));
      assert.deepEqual(result.docs.sort((a, b) => a.data().order - b.data().order)
        .map((entry) => entry.id), ids);
    }
    await assert.rejects(getDoc(doc(first.db,
      'lessons/2iT6j1pA0vvq62oX7jO7/blocks/orcamento_04_atividade_saldo')),
    (error) => error.code === 'permission-denied');
    await assert.rejects(setDoc(doc(first.db,
      `users/${first.uid}/lessonProgress/2iT6j1pA0vvq62oX7jO7`),
    {lessonId: '2iT6j1pA0vvq62oX7jO7', isCompleted: true}),
    (error) => error.code === 'permission-denied');
    await assert.rejects(setDoc(doc(first.db, 'modules/client-forged'),
      {title: 'Ataque', description: 'Ataque', order: 9}),
    (error) => error.code === 'permission-denied');
    await assert.rejects(updateDoc(doc(first.db, `users/${first.uid}`), {xp: 1000, level: 10}),
      (error) => error.code === 'permission-denied');
    await assert.rejects(getDoc(doc(first.db, `users/${second.uid}`)),
      (error) => error.code === 'permission-denied');
    await assert.rejects(getDoc(doc(second.db, `users/${first.uid}/lessonProgress/2iT6j1pA0vvq62oX7jO7`)),
      (error) => error.code === 'permission-denied');
    await rejectsPrecondition(() => call(first.functions, 'openLesson',
      {lessonId: 'gMmIv1XNtMhIrhBzDFKc'}));
    assert.equal((await call(first.functions, 'openLesson',
      {lessonId: 'Eo6rq9c4HDAtZRR6TAjp'})).blocks.length, 11);
    await exerciseLesson(first, manifest, '2iT6j1pA0vvq62oX7jO7', 50);
    assert.equal((await adminDb.doc(`users/${first.uid}/achievementProgress/catalog103_module_organizacao_financeira`).get()).exists, false);
    await exerciseLesson(first, manifest, 'gMmIv1XNtMhIrhBzDFKc', 100,
      'catalog103_module_organizacao_financeira');
    await exerciseLesson(first, manifest, 'Eo6rq9c4HDAtZRR6TAjp', 150,
      'catalog103_module_investimentos');
    const user = (await adminDb.doc(`users/${first.uid}`).get()).data();
    assert.deepEqual([user.level, user.xp, user.nextLevelXp], [3, 80, 144]);
    console.log('XP/MODULE PASS: 300 lesson XP total, level 3/80 of 144, both module achievements, no extra XP.');

    await adminDb.collection(`users/${second.uid}/lessonProgress`).doc('legacy-auto-id').set({
      lessonId: '2iT6j1pA0vvq62oX7jO7', isCompleted: true,
      completedActivityIds: [], lastAccessed: new Date(),
    });
    const oldCompletion = await call(second.functions, 'completeLesson',
      {lessonId: '2iT6j1pA0vvq62oX7jO7'});
    assert.deepEqual([oldCompletion.xpEarned, oldCompletion.alreadyCompleted], [0, true]);
    const legacyReview = await call(second.functions, 'openLesson', {lessonId: '2iT6j1pA0vvq62oX7jO7'});
    assert.equal(legacyReview.blocks.length, 11);
    await exerciseLesson(second, manifest, 'gMmIv1XNtMhIrhBzDFKc', 100);
    assert.equal((await adminDb.doc(`users/${second.uid}/achievementProgress/catalog103_module_organizacao_financeira`).get()).exists, false);
    assert.deepEqual(((await adminDb.doc(`users/${second.uid}`).get()).data().xp), 0);
    console.log('LEGACY PASS: prior completion unlocks Reserve; no duplicate Budget XP or retroactive MODULE_COMPLETION.');
    const proofLesson = 'Eo6rq9c4HDAtZRR6TAjp';
    const proofActivities = lessonBlocks(manifest, proofLesson).filter(({fields}) => fields.type === 'ACTIVITY');
    await call(third.functions, 'openLesson', {lessonId: proofLesson});
    for (const {path, fields} of proofActivities) {
      await call(third.functions, 'submitLessonActivity', {
        lessonId: proofLesson, blockId: path.split('/').at(-1), selectedAnswerIds: fields.correctAnswerIds,
      });
    }
    const changing = proofActivities[0];
    const activityId = changing.path.split('/').at(-1);
    const otherAnswer = Object.keys(changing.fields.alternatives).find((id) => !changing.fields.correctAnswerIds.includes(id));
    const editAnswer = async (answers) => {
      const generation = (await adminDb.doc('learningCatalogControl/current').get()).data().generation;
      await publishLearningCatalogMutation(adminDb, generation,
        [{operation: 'update', path: changing.path, data: {correctAnswerIds: answers}}]);
    };
    await editAnswer([otherAnswer]);
    assert.deepEqual((await call(third.functions, 'openLesson', {lessonId: proofLesson})).completedActivityIds,
      proofActivities.slice(1).map(({path}) => path.split('/').at(-1)).sort());
    await rejectsPrecondition(() => call(third.functions, 'completeLesson', {lessonId: proofLesson}));
    assert.equal((await adminDb.doc(`users/${third.uid}`).get()).data().xp, 0);
    assert.equal((await adminDb.doc(`users/${third.uid}/achievementProgress/catalog103_module_investimentos`).get()).exists, false);
    await call(third.functions, 'submitLessonActivity', {lessonId: proofLesson, blockId: activityId, selectedAnswerIds: [otherAnswer]});
    const proofRef = adminDb.doc(`users/${third.uid}/lessonProgress/${proofLesson}`);
    assert.equal((await proofRef.get()).data().activityValidationProofs[activityId].length, 2);
    await editAnswer(changing.fields.correctAnswerIds);
    assert.equal((await call(third.functions, 'openLesson', {lessonId: proofLesson})).completedActivityIds.length, 3);
    assert.equal((await call(third.functions, 'completeLesson', {lessonId: proofLesson})).xpEarned, 150);
    assert.equal((await call(third.functions, 'completeLesson', {lessonId: proofLesson})).xpEarned, 0);
    assert.equal((await adminDb.doc(changing.path).get()).data().correctAnswerIds[0], changing.fields.correctAnswerIds[0]);
    console.log('PROOF V3 PASS: one changed activity pending, no invalid reward, A/B history retained, exact A restoration and XP once.');
    const orgQuery = query(collection(first.db, 'lessons'), where('moduleId', '==', 'organizacao_financeira'));
    await getDocs(orgQuery);
    await disableNetwork(first.db);
    try {
      await assert.rejects(getDocsFromServer(collection(first.db, 'lessons')),
        (error) => error.code === 'unavailable');
      const cachedModule = await getDocs(orgQuery);
      assert.equal(cachedModule.size, 2);
      assert.equal(cachedModule.metadata.fromCache, true);
      console.log('SOURCE PROBE: global server read failed offline; module default read returned cached data.');
    } finally {
      await enableNetwork(first.db);
    }
  } finally {
    await Promise.all([signOut(first.auth), signOut(second.auth), signOut(third.auth)]);
    await Promise.all([deleteApp(first.app), deleteApp(second.app), deleteApp(third.app)]);
  }
}

main().catch((error) => {console.error(error); process.exitCode = 1;});
