import assert from 'node:assert/strict';
import {test} from 'node:test';
import {readFileSync, mkdtempSync, existsSync, writeFileSync} from 'node:fs';
import {tmpdir} from 'node:os';
import {join} from 'node:path';
import {spawnSync} from 'node:child_process';
import net from 'node:net';
import tls from 'node:tls';
import {scopePaths} from './educational-publication.mjs';

// Any accidental network fallback makes the test fail before a socket opens.
let socketAttempts = 0;
net.Socket.prototype.connect = function () { socketAttempts++; throw new Error('NETWORK_FORBIDDEN'); };
tls.connect = () => { socketAttempts++; throw new Error('NETWORK_FORBIDDEN'); };
const m = await import('./catalog-readonly.mjs').catch(e => {
  if (e.code !== 'ERR_MODULE_NOT_FOUND') throw e;
  return null;
});
const base = 'https://firestore.googleapis.com/v1/projects/granaxp-d7927/databases/(default)';
const crm = 'https://cloudresourcemanager.googleapis.com/v1/projects/granaxp-d7927';
const date = new Date().toUTCString();
const readTime = new Date(Date.parse(date) - 1000).toISOString();
const principal = 'reader@example.test';
const permissions = ['datastore.databases.getMetadata', 'datastore.entities.get', 'datastore.entities.list', 'resourcemanager.projects.get'];
const options = (scope = 'learning') => ({project: 'granaxp-d7927', database: '(default)',
  projectNumber: '136652496724', databaseUid: '81c02a82-fb39-40a3-b0b8-82439c8ba232',
  principal, scope, credentialFile: '/synthetic-not-read.json',
  confirmRead: `READ_ONLY/granaxp-d7927/(default)/${scope}/${principal}`});
const credentials = {type: 'authorized_user', client_id: 'synthetic.apps.googleusercontent.com',
  client_secret: 'SYNTHETIC_SECRET', refresh_token: 'SYNTHETIC_REFRESH'};
const env = {...process.env};
for (const key of Object.keys(process.env))
  if (/PROXY|EMULATOR|GOOGLE_|GCLOUD|GCE_|FIREBASE_|CLOUDSDK_/i.test(key)) delete process.env[key];
process.on('exit', () => Object.assign(process.env, env));
const reply = (data, status = 200) => ({status, ok: status === 200,
  headers: new Headers({date}), json: async () => structuredClone(data)});
const doc = (path, fields = {title: {stringValue: 'Public'}}) => ({
  name: `${base.slice('https://firestore.googleapis.com/v1/'.length)}/documents/${path}`,
  fields, createTime: '2026-10-16T12:00:00Z', updateTime: '2026-10-16T12:00:00.123456Z'});
function fixture({identity = principal, granted = permissions, docs = {}, handle, tokenInfo = {}} = {}) {
  const calls = [], authCalls = []; let loaded = 0;
  return {calls, authCalls, get loaded() { return loaded; }, deps: {
    loadCredentials: () => { loaded++; return structuredClone(credentials); },
    oauthAdapter: async opts => {
      authCalls.push(opts);
      const url = String(opts.url);
      assert.equal(opts.method, 'POST');
      assert.equal(opts.maxRedirects, 0);
      assert.equal(opts.retry, false);
      let data;
      if (url === 'https://oauth2.googleapis.com/token')
        data = {access_token: 'SYNTHETIC_ACCESS', expires_in: 3600, token_type: 'Bearer'};
      else {
        assert.equal(url, 'https://oauth2.googleapis.com/tokeninfo');
        assert.equal(new Headers(opts.headers).get('authorization'), 'Bearer SYNTHETIC_ACCESS');
        data = {email: identity, email_verified: 'true', expires_in: 3600,
          scope: 'https://www.googleapis.com/auth/cloud-platform', ...tokenInfo};
      }
      return {data, status: 200, statusText: 'OK', headers: {}, config: opts};
    },
    fetchImpl: async (url, init) => {
      calls.push({url: String(url), ...init});
      assert.equal(init.headers.Authorization, 'Bearer SYNTHETIC_ACCESS');
      assert.equal(init.redirect, 'error');
      const result = handle?.(String(url), init, calls); if (result) return result;
      if (String(url) === crm) return reply({projectId: 'granaxp-d7927', projectNumber: '136652496724', lifecycleState: 'ACTIVE'});
      if (String(url) === base) return reply({name: 'projects/granaxp-d7927/databases/(default)',
        uid: options().databaseUid, type: 'FIRESTORE_NATIVE', databaseEdition: 'STANDARD', locationId: 'southamerica-east1'});
      if (String(url) === `${crm}:testIamPermissions`) {
        assert.deepEqual(JSON.parse(init.body), {permissions}); return reply({permissions: granted});
      }
      const u = new URL(url), path = decodeURI(u.pathname).split('/documents/')[1];
      assert.equal(u.searchParams.get('readTime'), readTime);
      if (path === 'learningCatalogControl/current') return reply({}, 404);
      assert.equal(init.method, 'GET');
      return reply({documents: docs[path] ?? []});
    },
  }};
}
test('adapter exists and has an inventory entry point', () => assert.equal(typeof m?.inventoryCatalog, 'function'));
test('wrong target, scope, identity or confirmation fails before credentials/network', {skip: !m}, async () => {
  for (const change of [{project: 'other'}, {database: 'other'}, {projectNumber: '0'}, {databaseUid: 'wrong'},
    {scope: 'all'}, {principal: ''}, {confirmRead: 'yes'}, {confirmRead: undefined}]) {
    const f = fixture(); await assert.rejects(m.inventoryCatalog({...options(), ...change}, f.deps));
    assert.equal(f.loaded, 0); assert.equal(f.calls.length, 0); assert.equal(f.authCalls.length, 0);
  }
});
test('effective SDK access-token identity and scopes, not declared account, gate metadata reads', {skip: !m}, async () => {
  for (const spec of [{identity: 'other@example.test'}, {identity: undefined, tokenInfo: {email: undefined}},
    {tokenInfo: {email_verified: 'false'}}, {tokenInfo: {expires_in: -1}}, {tokenInfo: {scope: 'openid'}}]) {
    const f = fixture(spec); await assert.rejects(m.inventoryCatalog(options(), f.deps), /IDENTITY/);
    assert.equal(f.calls.length, 0);
  }
});
test('metadata mismatch and missing permission stop before any catalog read', {skip: !m}, async () => {
  for (const change of [
    {handle: u => u === crm && reply({projectId: 'wrong', projectNumber: '136652496724', lifecycleState: 'ACTIVE'})},
    {handle: u => u === base && reply({name: 'projects/granaxp-d7927/databases/(default)', uid: 'wrong'})},
    {granted: permissions.slice(1)},
  ]) {
    const f = fixture(change); await assert.rejects(m.inventoryCatalog(options(), f.deps));
    assert.ok(f.calls.every(c => !c.url.includes('/documents/')));
  }
});
test('inventory scopes stay independent and use a single fixed readTime, including all pages', {skip: !m}, async () => {
  for (const [scope, expected] of [['learning', ['modules', 'lessons',
    'lessons/2iT6j1pA0vvq62oX7jO7/blocks', 'lessons/gMmIv1XNtMhIrhBzDFKc/blocks',
    'lessons/Eo6rq9c4HDAtZRR6TAjp/blocks', 'learningCatalogControl/current']],
    ['daily', ['dailyMissions']], ['achievements', ['achievements']]]) {
    const f = fixture({handle: (u) => {
      const x = new URL(u);
      if (x.pathname.endsWith('/dailyMissions')) return reply(x.searchParams.has('pageToken')
        ? {documents: [doc('dailyMissions/dm_prioridades_02')]} :
        {documents: [doc('dailyMissions/dm_orcamento_01')], nextPageToken: 'opaque/+?'});
    }});
    const inv = await m.inventoryCatalog(options(scope), f.deps);
    assert.equal(inv.readTime, readTime); assert.equal(inv.scope, scope);
    const paths = f.calls.filter(c => c.url.includes('/documents/')).map(c => decodeURI(new URL(c.url).pathname).split('/documents/')[1]);
    assert.deepEqual([...new Set(paths)].sort(), expected.sort());
    if (scope === 'daily') assert.equal(inv.snapshot.documents.length, 2);
    assert.ok(!JSON.stringify(inv).includes('SYNTHETIC_'));
    assert.ok(!('apply' in inv));
  }
});
test('write verbs/actions, arbitrary paths, private collections, masks and foreign URLs are denied', {skip: !m}, () => {
  const urls = [`${base}/documents/users`, `${base}/documents/users/a/lessonProgress`,
    `${base}/documents/achievementProgress`, `${base}/documents/transactions`, `${base}/documents:commit`,
    `${base}/documents:batchWrite`, `${base}/documents:beginTransaction`, `${base}/documents:runQuery`,
    `${base}/documents/learningCatalogControl/other`, `${base}/documents/lessons/unknown/blocks`,
    'https://evil.example/', `${base}/documents/modules?mask.fieldPaths=email`, `${crm}:setIamPolicy`];
  for (const verb of ['POST', 'PUT', 'PATCH', 'DELETE', 'GET'])
    for (const url of urls) assert.throws(() => m.assertReadRequest('learning', verb, url, undefined, readTime));
  for (const verb of ['POST', 'PUT', 'PATCH', 'DELETE'])
    assert.throws(() => m.assertReadRequest('learning', verb,
      `${base}/documents/modules?showMissing=true&pageSize=100&readTime=${encodeURIComponent(readTime)}`, undefined, readTime));
  assert.throws(() => m.assertReadRequest('daily', 'GET', `${base}/documents/modules`, undefined, readTime));
  const query = new URLSearchParams({showMissing: 'true', pageSize: '100', readTime});
  for (const url of [`${base}/documents/modules?${query}&readTime=${encodeURIComponent(readTime)}`,
    `${base}/documents/modules?${query}&mask.fieldPaths=email`,
    `${base}/documents/%2e%2e/users?${query}`, `${base}/documents/lessons%2funknown%2fblocks?${query}`])
    assert.throws(() => m.assertReadRequest('learning', 'GET', url, undefined, readTime));
});
test('foreign response paths, duplicates, repeating page tokens and failures never become empty success', {skip: !m}, async () => {
  for (const handle of [
    u => u.includes('/documents/modules') && reply({documents: [doc('users/private')]}),
    u => u.includes('/documents/modules') && reply({documents: [doc('modules/investimentos'), doc('modules/investimentos')]}),
    u => u.includes('/documents/modules') && reply({nextPageToken: 'same'}),
    u => u.includes('/documents/modules') && reply({error: {message: 'SECRET_PAYLOAD'}}, 403),
    u => u.includes('/documents/modules') && reply({documents: 'invalid'}),
  ]) {
    const f = fixture({handle});
    await assert.rejects(m.inventoryCatalog(options(), f.deps), e => !e.message.includes('SECRET_PAYLOAD'));
  }
});
test('missing parents and unexpected catalog IDs are reported, with no traversal outside fixed collections', {skip: !m}, async () => {
  const orphan = {name: doc('lessons/unknown').name};
  const f = fixture({docs: {lessons: [orphan], modules: [doc('modules/new') ]}});
  const inv = await m.inventoryCatalog(options(), f.deps);
  assert.deepEqual(inv.missingParents, ['lessons/unknown']);
  assert.ok(inv.unexpectedPaths.includes('modules/new')); assert.ok(inv.unexpectedPaths.includes('lessons/unknown'));
  assert.ok(f.calls.every(c => !c.url.includes('/unknown/')));
});
test('Firestore values preserve canonical JSON, extras are visible, unsupported/lossy types abort', {skip: !m}, () => {
  assert.deepEqual(m.decodeFields({extra: {mapValue: {fields: {a: {integerValue: '7'},
    b: {arrayValue: {values: [{nullValue: null}, {booleanValue: false}, {doubleValue: 1.5}]}}}}}}),
  {extra: {a: 7, b: [null, false, 1.5]}});
  for (const v of [{integerValue: '9007199254740993'}, {timestampValue: '2026-10-16T00:00:00Z'},
    {referenceValue: 'users/a'}, {bytesValue: 'YQ=='}, {doubleValue: 'NaN'}, {stringValue: 'x', booleanValue: true}])
    assert.throws(() => m.decodeFields({x: v}));
});
test('offline comparison recognizes legacy, exact approved content, extras/conflicts and pending achievements', {skip: !m}, () => {
  const bundle = JSON.parse(readFileSync(process.env.GRANAXP_APPROVED_BUNDLE, 'utf8'));
  const snapshot = {documents: bundle.legacy.map(x => ({...structuredClone(x), updateTime: '1792152000.000000001'}))};
  const f = m.compareInventory({scope: 'learning', snapshot, unexpectedPaths: [], missingParents: []}, bundle);
  assert.equal(f.counts.legacy, 3); assert.equal(f.counts.absent, 37); assert.equal(f.blockers.length, 0);
  const daily = {scope: 'daily', snapshot: {documents: bundle.daily.map(x => ({...structuredClone(x), updateTime: '1.000000000'}))},
    unexpectedPaths: [], missingParents: []};
  assert.equal(m.compareInventory(daily, bundle).counts.exact, 7);
  daily.snapshot.documents[0].fields.extra = 'KEEP_ME';
  assert.equal(m.compareInventory(daily, bundle).counts.conflict, 1);
  const a = m.compareInventory({scope: 'achievements', snapshot: {documents: []}, unexpectedPaths: [], missingParents: []}, bundle);
  assert.equal(a.editorialStatus, 'PENDING_OWNER_REVIEW'); assert.equal(a.productionApproved, false);
  assert.ok(a.blockers.includes('HISTORICAL_ACHIEVEMENTS_MISSING'));
  assert.ok(!JSON.stringify(a).includes('KEEP_ME'));
});
test('CLI denies writes and unconfirmed inventory before touching credential paths, outputs no secrets', {skip: !m}, () => {
  const tmp = mkdtempSync(join(tmpdir(), 'GranaXP-E6.9I-cli-'));
  for (const command of ['apply', 'create', 'update', 'delete', 'transaction', 'inventory']) {
    const child = spawnSync(process.execPath, ['--import', 'data:text/javascript,' + encodeURIComponent(
      'import net from "node:net"; import tls from "node:tls"; let n=0; const deny=()=>{n++;throw Error("NETWORK_FORBIDDEN")}; net.Socket.prototype.connect=deny; tls.connect=deny; globalThis.fetch=deny; process.on("exit",()=>console.log(`SOCKET_ATTEMPTS=${n}`));'),
      'scripts/catalog-readonly.mjs', command,
      '--credential-file', 'SECRET_NONEXISTENT', '--out', join(tmp, 'never.json')], {encoding: 'utf8'});
    assert.equal(child.status, 1); assert.ok(!child.stderr.includes('SECRET_NONEXISTENT'));
    assert.ok(!child.stderr.includes(' at ')); assert.equal(existsSync(join(tmp, 'never.json')), false);
    assert.ok(child.stdout.includes('SOCKET_ATTEMPTS=0'));
    assert.ok(child.stderr.includes(command === 'inventory' ? 'TARGET_INVALID' : 'COMMAND_FORBIDDEN'));
  }
});
test('unsupported credentials and environment overrides are rejected without an OAuth request', {skip: !m}, async () => {
  for (const c of [{...credentials, type: 'service_account'}, {...credentials, type: 'external_account'},
    {...credentials, token_uri: 'https://evil.example'}, {...credentials, universe_domain: 'evil.example'},
    {...credentials, quota_project_id: 'wrong'}]) {
    const f = fixture(); f.deps.loadCredentials = () => c;
    await assert.rejects(m.inventoryCatalog(options(), f.deps), /IDENTITY/);
    assert.equal(f.authCalls.length, 0); assert.equal(f.calls.length, 0);
  }
  for (const key of ['HTTPS_PROXY', 'GOOGLE_APPLICATION_CREDENTIALS', 'FIRESTORE_EMULATOR_HOST', 'GCLOUD_PROJECT']) {
    process.env[key] = 'INVALID_OVERRIDE'; const f = fixture();
    try { await assert.rejects(m.inventoryCatalog(options(), f.deps), /ENVIRONMENT/); }
    finally { delete process.env[key]; }
    assert.equal(f.loaded, 0);
  }
});
test('a denied or failed read is not retried and never returns a partial snapshot', {skip: !m}, async () => {
  const f = fixture({handle: u => u.includes('/documents/lessons?') && reply({}, 503)});
  await assert.rejects(m.inventoryCatalog(options(), f.deps), /READ_FAILED/);
  assert.equal(f.calls.filter(c => c.url.includes('/documents/lessons?')).length, 1);
  assert.ok(!f.calls.some(c => c.url.includes('/blocks?')));
});
test('output metadata records the actually checked project/database, and timestamp precision survives', {skip: !m}, async () => {
  const f = fixture({docs: {modules: [doc('modules/investimentos')]}});
  const inv = await m.inventoryCatalog(options(), f.deps);
  assert.equal(inv.verification.database.name, 'projects/granaxp-d7927/databases/(default)');
  assert.equal(inv.verification.database.uid, '81c02a82-fb39-40a3-b0b8-82439c8ba232');
  assert.equal(inv.verification.project.lifecycleState, 'ACTIVE');
  assert.equal(inv.snapshot.documents[0].updateTime, '1792152000.123456000');
  const emptyHistorical = m.compareInventory({scope: 'learning', snapshot: {documents: []},
    missingParents: [], unexpectedPaths: []}, JSON.parse(readFileSync(process.env.GRANAXP_APPROVED_BUNDLE, 'utf8')));
  assert.ok(emptyHistorical.blockers.includes('LEGACY_LESSONS_MISSING'));
});
test('offline CLI comparison writes a private report, refuses overwrite and rejects altered inventory', {skip: !m}, async () => {
  const dir = mkdtempSync(join(tmpdir(), 'GranaXP-E6.9I-compare-'));
  const f = fixture(), inv = await m.inventoryCatalog(options('daily'), f.deps);
  const input = join(dir, 'inventory.json'), out = join(dir, 'comparison.json');
  writeFileSync(input, JSON.stringify(inv));
  const args = ['--import', 'data:text/javascript,' + encodeURIComponent(
    'import net from "node:net"; import tls from "node:tls"; const deny=()=>{throw Error("NETWORK_FORBIDDEN")}; net.Socket.prototype.connect=deny; tls.connect=deny; globalThis.fetch=deny;'),
    'scripts/catalog-readonly.mjs', 'compare', '--inventory', input, '--bundle',
    process.env.GRANAXP_APPROVED_BUNDLE, '--out', out];
  const child = spawnSync(process.execPath, args, {encoding: 'utf8'});
  assert.equal(child.status, 0, child.stderr);
  assert.equal(JSON.parse(readFileSync(out, 'utf8')).counts.absent, 7);
  assert.equal(spawnSync(process.execPath, args, {encoding: 'utf8'}).status, 1);
  inv.snapshot.documents.push({path: 'users/never', fields: {}, updateTime: '1.000000000'});
  writeFileSync(input, JSON.stringify(inv));
  args[args.length - 1] = join(dir, 'tampered.json');
  const bad = spawnSync(process.execPath, args, {encoding: 'utf8'});
  assert.equal(bad.status, 1); assert.ok(bad.stderr.includes('INVENTORY_INVALID'));
  assert.equal(existsSync(args.at(-1)), false);
});
test('invalid server clocks, redirects, page limits and expiring tokens stop the inventory', {skip: !m}, async () => {
  for (const stamp of [null, 'invalid', 'Wed, 01 Jan 2020 00:00:00 GMT']) {
    const f = fixture({handle: u => {
      if (u !== base) return;
      const r = reply({name: 'projects/granaxp-d7927/databases/(default)', uid: options().databaseUid,
        type: 'FIRESTORE_NATIVE', databaseEdition: 'STANDARD', locationId: 'southamerica-east1'});
      if (stamp === null) r.headers.delete('date'); else r.headers.set('date', stamp);
      return r;
    }});
    await assert.rejects(m.inventoryCatalog(options(), f.deps), /SERVER_TIME_INVALID/);
    assert.ok(!f.calls.some(c => c.url.includes('/documents/')));
  }
  const redirect = fixture({handle: u => u.includes('/documents/') && reply({}, 302)});
  await assert.rejects(m.inventoryCatalog(options(), redirect.deps), /READ_FAILED/);
  assert.equal(redirect.calls.filter(c => c.url.includes('/documents/')).length, 1);
  const pages = fixture({handle: (u, _init, calls) => u.includes('/documents/') &&
    reply({nextPageToken: `page-${calls.length}`})});
  await assert.rejects(m.inventoryCatalog(options(), pages.deps), /INVENTORY_LIMIT/);
  assert.equal(pages.calls.filter(c => c.url.includes('/documents/')).length, 100);
  const originalNow = Date.now;
  const expired = fixture({handle: u => {
    if (u.includes('/documents/')) Date.now = () => originalNow() + 3600000;
  }});
  try { await assert.rejects(m.inventoryCatalog(options(), expired.deps), /IDENTITY_EXPIRED/); }
  finally { Date.now = originalNow; }
  assert.equal(expired.calls.filter(c => c.url.includes('/documents/')).length, 1);
});
test('only absent control may be represented by 404; absent database or collection aborts', {skip: !m}, async () => {
  const f = fixture(), inv = await m.inventoryCatalog(options(), f.deps);
  assert.ok(!inv.snapshot.documents.some(d => d.path === 'learningCatalogControl/current'));
  for (const badPath of [base, `${base}/documents/modules?`]) {
    const broken = fixture({handle: u => (u === badPath || u.startsWith(badPath)) && reply({}, 404)});
    await assert.rejects(m.inventoryCatalog(options(), broken.deps), /READ_FAILED/);
  }
});
test('all SDK/auth/resource tests made zero attempted socket connections', () => assert.equal(socketAttempts, 0));
