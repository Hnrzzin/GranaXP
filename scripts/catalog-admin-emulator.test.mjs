import assert from 'node:assert/strict';
import net from 'node:net';
import {createRequire} from 'node:module';
import {readFileSync} from 'node:fs';
import {before, beforeEach, after, test} from 'node:test';
import {createPlan, hash} from './educational-publication.mjs';
import * as admin from './catalog-admin.mjs';
import {validateAchievementDefinitions, HISTORICAL_ACHIEVEMENTS} from './achievement-publication.mjs';

assert.equal(process.env.GRANAXP_ADMIN_INTEGRATION, '1', 'Dedicated local integration must be explicit');
assert.ok(['127.0.0.1:18080', '127.0.0.1:28080'].includes(process.env.FIRESTORE_EMULATOR_HOST));
const host = process.env.FIRESTORE_EMULATOR_HOST;
const context = {project: 'demo-granaxp', database: '(default)', environment: 'emulator',
  host, principal: admin.LOCAL_PRINCIPAL};
const bundle = JSON.parse(readFileSync(process.env.GRANAXP_APPROVED_BUNDLE, 'utf8'));
const requireFunctions = createRequire(new URL('../functions/package.json', import.meta.url));
const {Firestore, Timestamp} = requireFunctions('@google-cloud/firestore');
// A second, independent transport guard covers the integration process, including SDK faults.
const connect = net.Socket.prototype.connect;
let connections = 0;
net.Socket.prototype.connect = function (...args) {
  const options = Array.isArray(args[0]) ? args[0][0] : args[0];
  const address = typeof options === 'object' ? options.host : args[1];
  const port = typeof options === 'object' ? options.port : options;
  assert.equal(address, '127.0.0.1', 'NON_LOOPBACK_CONNECTION_BLOCKED');
  assert.equal(Number(port), Number(host.split(':')[1]), 'OTHER_PORT_BLOCKED');
  connections++;
  return connect.apply(this, args);
};
let db, session, sentinel;
before(async () => {
  db = new Firestore({projectId: 'demo-granaxp', databaseId: '(default)', host, ssl: false});
  session = await admin.openLocalSession(context);
});
beforeEach(async () => {
  const response = await fetch(`http://${host}/emulator/v1/projects/demo-granaxp/databases/(default)/documents`,
    {method: 'DELETE', redirect: 'error'});
  assert.equal(response.status, 200);
  await db.doc('users/e69h-sentinel').create({xp: 19, level: 1, historical: true});
  await db.doc('achievementProgress/e69h-sentinel').create({isUnlocked: true, fixture: true});
  sentinel = await db.getAll(db.doc('users/e69h-sentinel'), db.doc('achievementProgress/e69h-sentinel'));
});
after(async () => {
  if (session) await admin.closeSession(session);
  if (db) await db.terminate();
  net.Socket.prototype.connect = connect;
  assert.ok(connections > 0, 'Integration must execute actual local SDK requests');
});
async function seedLegacy() {
  const batch = db.batch();
  for (const d of bundle.legacy) batch.create(db.doc(d.path), d.fields);
  await batch.commit();
}
async function seedLearning() {
  await seedLegacy();
  const p = createPlan(bundle, {documents: bundle.legacy}, {project: 'demo-granaxp', environment: 'emulator'}, 'learning');
  const batch = db.batch();
  for (const op of p.operations) batch[op.operation](db.doc(op.path), op.fields);
  await batch.commit();
}
async function seedAchievements() {
  const batch = db.batch();
  for (const [id, [requirementType, requirementValue]] of Object.entries(HISTORICAL_ACHIEVEMENTS))
    batch.create(db.doc(`achievements/${id}`), {title: 'Synthetic history', description: 'Local witness',
      icon: 'EmojiEvents', category: 'FINANCAS', requirementType, requirementValue});
  await batch.commit();
}
async function plan(scope) {
  return admin.createAdminPlan(bundle, await admin.readCatalogSnapshot(session, scope), context, scope);
}
const approval = p => ({digest: p.digest, confirm: admin.adminConfirmation(p)});
async function untouched() {
  const now = await db.getAll(...sentinel.map(d => d.ref));
  for (let i = 0; i < now.length; i++) {
    assert.deepEqual(now[i].data(), sentinel[i].data());
    assert.ok(now[i].updateTime.isEqual(sentinel[i].updateTime));
  }
}
test('actual SDK applies learning/control atomically, preserves legacy and sentinels, then no-op/replay', async () => {
  await seedLegacy();
  const p = await plan('learning'), old = p.before.documents;
  assert.equal((await admin.applyApprovedPlan(session, bundle, p, approval(p))).writes, 40);
  assert.equal((await db.doc('learningCatalogControl/current').get()).get('generation'), 2);
  for (const d of old) {
    const current = await db.doc(d.path).get();
    assert.equal(current.get('category'), d.fields.category);
    assert.equal(current.get('videoUrl'), d.fields.videoUrl);
  }
  assert.equal((await plan('learning')).publication.operations.length, 0);
  const noOp = await plan('learning');
  assert.equal((await admin.applyApprovedPlan(session, bundle, noOp, approval(noOp))).status, 'NO_OP');
  await assert.rejects(admin.applyApprovedPlan(session, bundle, p, approval(p)), /STALE_PLAN/);
  await untouched();
});
test('absent/wrong digest, cross-scope confirmation, tampering, delete and XP writes are rejected', async () => {
  await seedLegacy();
  const p = await plan('learning');
  for (const a of [{}, {digest: 'bad', confirm: admin.adminConfirmation(p)},
    {...approval(p), confirm: admin.adminConfirmation(p).replace('/learning/', '/daily/')}])
    await assert.rejects(admin.applyApprovedPlan(session, bundle, p, a), /APPROVAL_INVALID/);
  for (const mutation of [
    x => { x.publication.operations[0].operation = 'delete'; },
    x => { x.publication.operations[0].path = 'users/e69h-sentinel'; },
    x => { x.publication.operations[0].fields = {xp: 999}; },
    x => { x.digest = 'bad'; },
  ]) {
    const changed = structuredClone(p); mutation(changed);
    await assert.rejects(admin.applyApprovedPlan(session, bundle, changed, approval(changed)), /PLAN_INVALID/);
  }
  assert.equal((await db.collection('modules').get()).size, 0);
  assert.equal((await db.doc('learningCatalogControl/current').get()).exists, false);
  await untouched();
});
test('same-value rewrite changes updateTime and makes the plan stale without any publication', async () => {
  await seedLegacy(); const p = await plan('learning');
  const d = bundle.legacy[0];
  await db.doc(d.path).update({duration: d.fields.duration + 1});
  await db.doc(d.path).update(d.fields);
  const version = await db.doc(d.path).get();
  assert.notEqual(`${version.updateTime.seconds}.${String(version.updateTime.nanoseconds).padStart(9, '0')}`,
    p.before.documents.find(x => x.path === d.path).updateTime);
  await assert.rejects(admin.applyApprovedPlan(session, bundle, p, approval(p)), /STALE_PLAN/);
  assert.equal((await db.collection('modules').get()).size, 0);
  assert.equal((await db.doc('learningCatalogControl/current').get()).exists, false);
});
test('unexpected orphan parent, extra fields and unsupported timestamps stop inventory/planning', async () => {
  await seedLegacy();
  await db.doc('lessons/orphan/blocks/extra').create({fixture: true});
  await assert.rejects(admin.readCatalogSnapshot(session, 'learning'), /SNAPSHOT_FAILED/);
  await db.doc('lessons/orphan/blocks/extra').delete();
  await db.doc(bundle.legacy[0].path).update({extra: 'PRIVATE_VALUE'});
  assert.throws(() => admin.createAdminPlan(bundle, {documents: [], dependencies: {documents: []}}, context, 'learning'));
  await assert.rejects(plan('learning'), e => e.message === 'PLAN_INVALID' && !JSON.stringify(e).includes('PRIVATE_VALUE'));
  await db.doc(bundle.legacy[0].path).set({...bundle.legacy[0].fields, videoUrl: Timestamp.fromMillis(0)});
  await assert.rejects(admin.readCatalogSnapshot(session, 'learning'), /SNAPSHOT_FAILED/);
});
test('orphan module, daily, block and control parents are detected outside ordinary queries', async () => {
  await seedLearning(); await seedAchievements();
  for (const [scope, path] of [
    ['learning', 'modules/extra/sub/x'],
    ['daily', 'dailyMissions/extra/sub/x'],
    ['learning', `${bundle.legacy[0].path}/blocks/extra/sub/x`],
    ['learning', 'learningCatalogControl/extra/sub/x'],
    ['achievements', 'achievements/extra/sub/x'],
    ['daily', 'dailyMissions/dm_meta_04/sub/x'],
  ]) {
    await db.doc(path).create({fixture: true});
    try { await assert.rejects(admin.readCatalogSnapshot(session, scope), /SNAPSHOT_FAILED/); }
    finally { await db.doc(path).delete(); }
  }
});
test('failure after staging writes rolls back every learning operation including control', async () => {
  await seedLegacy(); const p = await plan('learning'), original = Firestore.prototype.runTransaction;
  Firestore.prototype.runTransaction = function (callback, options) {
    return original.call(this, tx => callback(new Proxy(tx, {get(target, key) {
      if (key === 'create') return (ref, fields) => {
        if (ref.path === 'learningCatalogControl/current') throw Error('PRIVATE_TOKEN');
        target.create(ref, fields); return target;
      };
      const value = Reflect.get(target, key); return typeof value === 'function' ? value.bind(target) : value;
    }})), options);
  };
  try { await assert.rejects(admin.applyApprovedPlan(session, bundle, p, approval(p)), /COMMIT_UNCERTAIN/); }
  finally { Firestore.prototype.runTransaction = original; }
  assert.equal((await db.collection('modules').get()).size, 0);
  assert.equal((await db.doc('learningCatalogControl/current').get()).exists, false);
  for (const d of bundle.legacy) assert.deepEqual((await db.doc(d.path).get()).data(), d.fields);
  await untouched();
});
test('SDK callback retry is bounded and commits the approved intent once', async () => {
  await seedLegacy(); const p = await plan('learning'), original = Firestore.prototype.runTransaction;
  let callbacks = 0;
  Firestore.prototype.runTransaction = function (callback, options) {
    assert.equal(options.maxAttempts, 3);
    return original.call(this, async tx => {
      callbacks++;
      if (callbacks === 1) { const error = Error('synthetic contention'); error.code = 10; throw error; }
      return callback(tx);
    }, options);
  };
  try { assert.equal((await admin.applyApprovedPlan(session, bundle, p, approval(p))).writes, 40); }
  finally { Firestore.prototype.runTransaction = original; }
  assert.equal(callbacks, 2);
  assert.equal((await db.doc('learningCatalogControl/current').get()).get('generation'), 2);
});
test('lost response after a real local commit triggers read-only verification, never second apply', async () => {
  await seedLegacy(); const p = await plan('learning'), original = Firestore.prototype.runTransaction;
  let transactions = 0;
  Firestore.prototype.runTransaction = async function (...args) {
    transactions++; await original.apply(this, args);
    const error = Error('PRIVATE_ACCESS_TOKEN'); error.code = 14; throw error;
  };
  let result;
  try { result = await admin.applyApprovedPlan(session, bundle, p, approval(p)); }
  finally { Firestore.prototype.runTransaction = original; }
  assert.equal(result.status, 'COMMITTED_VERIFIED_AFTER_UNCERTAIN');
  assert.equal(transactions, 1); assert.ok(!JSON.stringify(result).includes('PRIVATE_ACCESS_TOKEN'));
  assert.equal((await admin.reconcilePlan(session, bundle, p)).status, 'APPLIED');
  assert.equal((await db.doc('learningCatalogControl/current').get()).get('generation'), 2);
});
test('deadline before commit stays uncertain and never retries the entire application', async () => {
  await seedLegacy(); const p = await plan('learning'), original = Firestore.prototype.runTransaction;
  let transactions = 0;
  Firestore.prototype.runTransaction = async () => {
    transactions++; const error = Error('SECRET_DEADLINE'); error.code = 4; throw error;
  };
  try { await assert.rejects(admin.applyApprovedPlan(session, bundle, p, approval(p)),
    e => e.message === 'COMMIT_UNCERTAIN' && !JSON.stringify(e).includes('SECRET_DEADLINE')); }
  finally { Firestore.prototype.runTransaction = original; }
  assert.equal(transactions, 1);
  assert.equal((await admin.reconcilePlan(session, bundle, p)).status, 'UNCHANGED');
  assert.equal((await db.collection('modules').get()).size, 0);
});
test('a changed approved version during SDK retry aborts instead of recalculating intent', async () => {
  await seedLegacy(); const p = await plan('learning'), original = Firestore.prototype.runTransaction;
  let callbacks = 0;
  Firestore.prototype.runTransaction = function (callback, options) {
    return original.call(this, async tx => {
      callbacks++;
      if (callbacks === 1) {
        const d = bundle.legacy[0];
        await db.doc(d.path).update({duration: d.fields.duration + 1});
        await db.doc(d.path).update(d.fields);
        const error = Error('synthetic retry'); error.code = 10; throw error;
      }
      return callback(tx);
    }, options);
  };
  try { await assert.rejects(admin.applyApprovedPlan(session, bundle, p, approval(p)), /STALE_PLAN/); }
  finally { Firestore.prototype.runTransaction = original; }
  assert.equal(callbacks, 2);
  assert.equal((await db.doc('learningCatalogControl/current').get()).exists, false);
  assert.equal((await db.collection('modules').get()).size, 0);
});
test('daily is separately approved and its conflict/failure never undoes learning', async () => {
  await seedLearning();
  const p = await plan('daily');
  assert.equal(p.publication.operations.length, 7);
  await db.doc('dailyMissions/dm_meta_04').create({conflict: true});
  await assert.rejects(admin.applyApprovedPlan(session, bundle, p, approval(p)), /STALE_PLAN/);
  assert.equal((await db.collection('dailyMissions').get()).size, 1);
  assert.equal((await db.doc('learningCatalogControl/current').get()).get('generation'), 2);
  await db.doc('dailyMissions/dm_meta_04').delete();
  assert.equal((await admin.applyApprovedPlan(session, bundle, p, approval(p))).writes, 7);
  assert.equal((await plan('daily')).publication.operations.length, 0);
  await untouched();
});
test('achievement candidate creates five definitions locally, preserves three histories/versions and eight cards', async () => {
  await seedLearning(); await seedAchievements();
  const historical = await db.collection('achievements').get(), p = await plan('achievements');
  assert.equal(p.manifestDigest, validateAchievementDefinitions().digest);
  assert.equal((await admin.applyApprovedPlan(session, bundle, p, approval(p))).writes, 5);
  assert.equal((await db.collection('achievements').get()).size, 8);
  for (const old of historical.docs) {
    const current = await old.ref.get(); assert.deepEqual(current.data(), old.data());
    assert.ok(current.updateTime.isEqual(old.updateTime));
  }
  const again = await plan('achievements');
  assert.equal((await admin.applyApprovedPlan(session, bundle, again, approval(again))).writes, 0);
  await untouched();
});
test('module dependency and historical-definition races abort all five achievement creations', async () => {
  await seedLearning(); await seedAchievements();
  const p = await plan('achievements');
  const historical = await db.doc('achievements/Yf1Gf6ez5AkPUUECOePP').get();
  await historical.ref.update({description: 'Temporary concurrent change'});
  await historical.ref.update(historical.data());
  assert.equal((await historical.ref.get()).updateTime.isEqual(historical.updateTime), false);
  await assert.rejects(admin.applyApprovedPlan(session, bundle, p, approval(p)), /STALE_PLAN/);
  assert.equal((await db.collection('achievements').get()).size, 3);
  const fresh = await plan('achievements');
  await db.doc('modules/investimentos').update({description: 'Synthetic concurrent update'});
  await assert.rejects(admin.applyApprovedPlan(session, bundle, fresh, approval(fresh)), /STALE_PLAN/);
  assert.equal((await db.collection('achievements').get()).size, 3);
});
test('postcheck rejects a same-value rewrite of an unwritten historical definition', async () => {
  await seedLearning(); await seedAchievements();
  const p = await plan('achievements'), original = Firestore.prototype.runTransaction;
  Firestore.prototype.runTransaction = async function (...args) {
    const result = await original.apply(this, args);
    const historical = await db.doc('achievements/Yf1Gf6ez5AkPUUECOePP').get();
    await historical.ref.update({description: 'Temporary postcommit concurrent change'});
    await historical.ref.update(historical.data());
    assert.equal((await historical.ref.get()).updateTime.isEqual(historical.updateTime), false);
    return result;
  };
  try { await assert.rejects(admin.applyApprovedPlan(session, bundle, p, approval(p)), /POSTCHECK_DIVERGED/); }
  finally { Firestore.prototype.runTransaction = original; }
  assert.equal((await admin.reconcilePlan(session, bundle, p)).status, 'DIVERGED');
});
