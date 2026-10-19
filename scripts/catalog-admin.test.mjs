import assert from 'node:assert/strict';
import {readFileSync, mkdtempSync, writeFileSync, existsSync} from 'node:fs';
import {tmpdir} from 'node:os';
import {join} from 'node:path';
import {spawnSync} from 'node:child_process';
import {test} from 'node:test';
import {createPlan, hash} from './educational-publication.mjs';

const m = await import('./catalog-admin.mjs').catch(e => {
  if (e.code !== 'ERR_MODULE_NOT_FOUND') throw e;
  return null;
});
const bundle = JSON.parse(readFileSync(process.env.GRANAXP_APPROVED_BUNDLE, 'utf8'));
const clone = value => structuredClone(value);
const context = {project: 'demo-granaxp', database: '(default)', environment: 'emulator',
  host: '127.0.0.1:28080', principal: 'catalog-admin@example.test'};
const baseline = () => ({documents: bundle.legacy.map(x => ({...clone(x), updateTime: '1.000000001'})), dependencies: {documents: []}});
function publishedLearning() {
  const s = baseline();
  const p = createPlan(bundle, {documents: s.documents}, {project: 'demo-granaxp', environment: 'emulator'}, 'learning');
  for (const op of p.operations) {
    const old = s.documents.find(d => d.path === op.path);
    if (old) Object.assign(old.fields, op.fields);
    else s.documents.push({path: op.path, fields: clone(op.fields), updateTime: '2.000000001'});
  }
  return {documents: s.documents};
}
test('administrative adapter exists and exposes an unconditional production barrier', () => {
  assert.equal(typeof m?.createAdminPlan, 'function');
  assert.equal(m.PRODUCTION_EXECUTION, 'BLOCKED_E69H');
});
test('learning plan wraps E6.6 with fixed database, principal, before versions and scoped digest', {skip: !m}, () => {
  const s = baseline(), p = m.createAdminPlan(bundle, s, context, 'learning');
  assert.equal(p.publication.operations.length, 40);
  assert.equal(p.publication.operations.filter(o => o.operation === 'create').length, 37);
  assert.equal(p.publication.operations.filter(o => o.operation === 'update').length, 3);
  assert.equal(p.context.database, '(default)');
  const {digest, ...content} = p; assert.equal(digest, hash(content));
  assert.ok(m.adminConfirmation(p).includes(digest));
  const other = baseline(); other.documents[0].updateTime = '1.000000002';
  assert.notEqual(m.createAdminPlan(bundle, other, context, 'learning').digest, digest);
  assert.deepEqual(s, baseline());
});
test('daily requires complete learning and has a different approval', {skip: !m}, () => {
  const p = m.createAdminPlan(bundle, {documents: [], dependencies: publishedLearning()}, context, 'daily');
  assert.equal(p.publication.operations.length, 7);
  const learning = m.createAdminPlan(bundle, baseline(), context, 'learning');
  assert.notEqual(p.digest, learning.digest);
  assert.notEqual(m.adminConfirmation(p), m.adminConfirmation(learning));
  assert.throws(() => m.createAdminPlan(bundle, {documents: [], dependencies: {documents: []}}, context, 'daily'));
});
test('production planning is offline, fixed to the published metadata and remains blocked for execution', {skip: !m}, async () => {
  const production = {project: 'granaxp-d7927', database: '(default)', environment: 'production',
    principal: 'operator@example.test', projectNumber: '136652496724',
    databaseUid: '81c02a82-fb39-40a3-b0b8-82439c8ba232',
    ruleset: '6a53743b-1962-4c38-84e7-1d64737b5ee8',
    rulesSha256: '04c7b573854b50517ae625f9cbdb5c94a62ad41f9dde40a36b66c66150bd1341'};
  const p = m.createAdminPlan(bundle, baseline(), production, 'learning');
  assert.equal(p.productionExecution, 'BLOCKED_E69H');
  await assert.rejects(m.openLocalSession(production), /BLOCKED_E69H/);
  for (const change of [
    x => { x.project = 'other'; }, x => { x.database = 'other'; },
    x => { x.principal = ''; }, x => { x.rulesSha256 = 'bad'; }, x => { x.databaseUid = 'bad'; },
  ]) {
    const c = clone(production); change(c); assert.throws(() => m.createAdminPlan(bundle, baseline(), c, 'learning'));
  }
});
test('wrong project/database/principal, extra context, unknown scope and mixed snapshot are refused', {skip: !m}, () => {
  for (const c of [
    {...context, project: 'granaxp-d7927'}, {...context, database: 'other'},
    {...context, principal: ''}, {...context, principal: 'other@example.test'},
    {...context, endpoint: 'firestore.googleapis.com'}, {...context, host: 'localhost:28080'},
  ]) assert.throws(() => m.createAdminPlan(bundle, baseline(), c, 'learning'));
  for (const scope of ['all', '', 'users', '../daily'])
    assert.throws(() => m.createAdminPlan(bundle, baseline(), context, scope));
  const s = baseline(); s.documents.push({path: 'dailyMissions/dm_meta_04', fields: {}});
  assert.throws(() => m.createAdminPlan(bundle, s, context, 'learning'));
});
test('legacy missing, unknown fields, unsupported types and partial publication are refused', {skip: !m}, () => {
  for (const change of [
    s => s.documents.pop(),
    s => { s.documents[0].fields.extra = 'not discarded'; },
    s => { s.documents[0].fields.videoUrl = new Date(); },
    s => { delete s.documents[0].updateTime; },
    s => { s.documents[0].updateTime = '1.1'; },
    s => { s.documents[0].fields.duration = Number.MAX_SAFE_INTEGER + 1; },
    s => s.documents.push({...clone(bundle.learning[0]), updateTime: '2.000000001'}),
    s => s.documents.push({path: 'users/victim', fields: {xp: 100}, updateTime: '2.000000001'}),
  ]) { const s = baseline(); change(s); assert.throws(() => m.createAdminPlan(bundle, s, context, 'learning')); }
});
test('published lessons missing preserved legacy fields cannot become no-op or authorize dependent scopes', {skip: !m}, () => {
  for (const keys of [['category'], ['videoUrl'], ['category', 'videoUrl']]) {
    const published = publishedLearning();
    for (const key of keys) delete published.documents.find(d => d.path === bundle.legacy[0].path).fields[key];
    assert.throws(() => m.createAdminPlan(bundle, {...published, dependencies: {documents: []}}, context, 'learning'));
    assert.throws(() => m.createAdminPlan(bundle, {documents: [], dependencies: published}, context, 'daily'));
    assert.throws(() => m.createAdminPlan(bundle, {documents: [], dependencies: published}, context, 'achievements'));
  }
});
test('in-memory non-JSON extras, symbols, hidden fields and sparse arrays are rejected rather than dropped', {skip: !m}, () => {
  for (const mutate of [
    b => { b.daily[0].fields.correctAnswerIds.extra = 'PRIVATE'; },
    b => { Object.defineProperty(b.learning[0].fields, 'hidden', {value: 'PRIVATE'}); },
    b => { b[Symbol('hidden')] = 'PRIVATE'; },
    b => { delete b.daily[0].fields.correctAnswerIds[0]; },
  ]) {
    const changed = clone(bundle); mutate(changed);
    assert.throws(() => m.createAdminPlan(changed, baseline(), context, 'learning'));
  }
});
test('SDK identity is independently verified and never inferred from Firebase CLI', {skip: !m}, async () => {
  assert.equal(await m.verifySDKIdentity({getCredentials: async () => ({client_email: context.principal})}, context.principal), context.principal);
  for (const value of [undefined, {}, {client_email: 'firebase-cli@example.test'}])
    await assert.rejects(m.verifySDKIdentity({getCredentials: async () => value}, context.principal));
  await assert.rejects(m.verifySDKIdentity({getCredentials: async () => { throw new Error('SECRET_TOKEN'); }}, context.principal),
    e => e.message === 'SDK_IDENTITY_INVALID' && !JSON.stringify(e).includes('SECRET_TOKEN'));
});
test('production, aliases, credential environments and redirects fail before any network connection', {skip: !m}, async () => {
  const run = spawnSync(process.execPath, ['--input-type=module', '-e', `
    import net from 'node:net'; import tls from 'node:tls';
    import assert from 'node:assert/strict';
    import {openLocalSession} from './scripts/catalog-admin.mjs';
    let requests=0; net.Socket.prototype.connect=tls.connect=()=>{requests++;throw Error('NETWORK_FORBIDDEN')};
    globalThis.fetch=()=>{requests++;throw Error('NETWORK_FORBIDDEN')};
    const c=${JSON.stringify(context)};
    for(const bad of [{...c,environment:'production',project:'granaxp-d7927'},
      {...c,project:'granaxp-d7927'},{...c,host:'firestore.googleapis.com'},
      {...c,host:'localhost:28080'},{...c,database:'other'},{...c,principal:''}])
      await assert.rejects(openLocalSession(bad));
    process.env.GOOGLE_APPLICATION_CREDENTIALS='SECRET_TOKEN';
    await assert.rejects(openLocalSession(c));
    assert.equal(requests,0); console.log('ZERO_NETWORK');
  `], {encoding: 'utf8', env: {...process.env, FIRESTORE_EMULATOR_HOST: context.host}});
  assert.equal(run.status, 0, run.stderr); assert.match(run.stdout, /ZERO_NETWORK/);
});
test('CLI rejects real snapshot/apply before reading files and sanitizes errors', {skip: !m}, () => {
  for (const command of ['snapshot', 'apply', 'unknown']) {
    const r = spawnSync(process.execPath, ['scripts/catalog-admin.mjs', command, '--project', 'granaxp-d7927',
      '--environment', 'production', '--database', '(default)', '--principal', 'SECRET_TOKEN', '--bundle', 'SECRET_FILE'],
    {encoding: 'utf8'});
    assert.notEqual(r.status, 0); assert.ok(!r.stderr.includes('SECRET')); assert.ok(!r.stderr.includes('node:'));
  }
});
test('CLI offline preview writes a private exclusive plan and refuses in-repository output', {skip: !m}, () => {
  const dir = mkdtempSync(join(tmpdir(), 'granaxp-h-cli-'));
  const snapshot = join(dir, 'before.json'), out = join(dir, 'plan.private.json');
  writeFileSync(snapshot, JSON.stringify(baseline()));
  const args = ['scripts/catalog-admin.mjs', 'preview', '--bundle', process.env.GRANAXP_APPROVED_BUNDLE,
    '--snapshot', snapshot, '--project', context.project, '--database', context.database,
    '--environment', context.environment, '--host', context.host, '--principal', context.principal,
    '--scope', 'learning', '--out', out];
  const run = a => spawnSync(process.execPath, a, {encoding: 'utf8'});
  const r = run(args); assert.equal(r.status, 0, r.stderr);
  assert.equal(JSON.parse(r.stdout).writes, 40); assert.ok(!r.stdout.includes('correctAnswerIds'));
  const before = readFileSync(out, 'utf8'); assert.notEqual(run(args).status, 0); assert.equal(readFileSync(out, 'utf8'), before);
  const inside = join(process.cwd(), 'scripts', 'e69h-forbidden.private.json');
  assert.equal(existsSync(inside), false); assert.notEqual(run([...args.slice(0, -1), inside]).status, 0);
  assert.equal(existsSync(inside), false);
});
