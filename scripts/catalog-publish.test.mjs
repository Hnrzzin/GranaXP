import assert from 'node:assert/strict';
import {test} from 'node:test';
import {readFileSync, mkdtempSync, existsSync} from 'node:fs';
import {join} from 'node:path';
import {tmpdir} from 'node:os';
import {spawnSync} from 'node:child_process';
import net from 'node:net';
import tls from 'node:tls';
import {hash} from './educational-publication.mjs';

let networkAttempts = 0;
const deny = () => { networkAttempts++; throw Error('NETWORK_FORBIDDEN'); };
net.Socket.prototype.connect = deny; tls.connect = deny; globalThis.fetch = deny;
for (const key of Object.keys(process.env))
  if (/PROXY|EMULATOR|GOOGLE_|GCLOUD|GCE_|FIREBASE_|CLOUDSDK_/i.test(key)) delete process.env[key];
const m = await import('./catalog-publish.mjs').catch(e => {
  if (e.code !== 'ERR_MODULE_NOT_FOUND') throw e;
  return null;
});
const dir = process.env.GRANAXP_SPARK_PROPOSAL_DIR;
const read = name => JSON.parse(readFileSync(join(dir, name), 'utf8'));
const proposals = () => ({learning: read('learning-proposal.private.json'), daily: read('daily-proposal.private.json')});
const bundle = JSON.parse(readFileSync(process.env.GRANAXP_APPROVED_BUNDLE, 'utf8'));
const clone = x => structuredClone(x);
const target = {project: 'granaxp-d7927', database: '(default)', projectNumber: '136652496724',
  databaseUid: '81c02a82-fb39-40a3-b0b8-82439c8ba232'};
const principal = read('learning-proposal.private.json').principal;
const options = () => ({...target, principal, credentialFile: join(tmpdir(), 'synthetic-never-read.json'),
  confirm: 'PUBLISH/granaxp-d7927/(default)/0f00b4000eda98a3a7c6d3a6d524fa9347eadc811364dc72bc66746ae07e9d7e/35e7b55e338965e3bfee1a8454513aaafe8f6fd54cd57821b0b7c8b5c6ef2b6a',
  adminWindow: 'exclusive'});
const resource = 'projects/granaxp-d7927/databases/(default)';
const base = `https://firestore.googleapis.com/v1/${resource}`;
const crm = 'https://cloudresourcemanager.googleapis.com/v1/projects/granaxp-d7927';
const credentials = {type: 'authorized_user', client_id: 'SYNTHETIC.apps.googleusercontent.com',
  client_secret: 'SYNTHETIC_SECRET', refresh_token: 'SYNTHETIC_REFRESH'};
function fields(value) {
  return Object.fromEntries(Object.entries(value).map(([key, x]) => [key, x === null ? {nullValue: null} :
    Array.isArray(x) ? {arrayValue: {values: x.map(v => fields({v}).v)}} :
    typeof x === 'object' ? {mapValue: {fields: fields(x)}} :
    typeof x === 'string' ? {stringValue: x} : typeof x === 'boolean' ? {booleanValue: x} :
    Number.isInteger(x) ? {integerValue: String(x)} : {doubleValue: x}]));
}
function timestamp(version) {
  const [seconds, nanos] = version.split('.');
  return new Date(Number(seconds) * 1000).toISOString().slice(0, 19) + '.' + nanos + 'Z';
}
function fake({identity = principal, permissions, beforeCommit, afterCommit, fail, badMetadata, tokenInfo = {}} = {}) {
  const approved = proposals(), state = new Map(approved.learning.publication.before.documents
    .map(d => [d.path, clone(d)]));
  const calls = [], authCalls = [], commits = [], events = [], transactions = new Map();
  let serial = 0, clock = 1792190000;
  // Sentinels are never enumerated/read by the executor.
  state.set('users/PRIVATE_SENTINEL', {path: 'users/PRIVATE_SENTINEL', fields: {xp: 77}, updateTime: '1.000000001'});
  state.set('achievements/HISTORICAL_SENTINEL', {path: 'achievements/HISTORICAL_SENTINEL', fields: {keep: true}, updateTime: '2.000000001'});
  const reply = (data, status = 200) => ({ok: status === 200, status, json: async () => clone(data)});
  const apiDoc = d => ({name: `${resource}/documents/${d.path}`, fields: fields(d.fields),
    updateTime: timestamp(d.updateTime), createTime: '2026-10-16T00:00:00Z'});
  const replace = d => state.set(d.path, clone(d));
  const deps = {loadCredentials: () => clone(credentials), onEvent: event => events.push(clone(event)),
    oauthAdapter: async opts => {
      authCalls.push(opts); assert.equal(opts.maxRedirects, 0); assert.equal(opts.retry, false); assert.equal(opts.method, 'POST');
      const url = String(opts.url);
      assert.ok(['https://oauth2.googleapis.com/token', 'https://oauth2.googleapis.com/tokeninfo'].includes(url));
      if (url.endsWith('/tokeninfo')) assert.equal(new Headers(opts.headers).get('authorization'), 'Bearer SYNTHETIC_ACCESS');
      const data = url === 'https://oauth2.googleapis.com/token' ?
        {access_token: 'SYNTHETIC_ACCESS', expires_in: 3600, token_type: 'Bearer'} :
        {email: identity, email_verified: true, expires_in: 3600,
          scope: 'https://www.googleapis.com/auth/cloud-platform', ...tokenInfo};
      return {data, status: 200, headers: {}, config: opts};
    },
    fetchImpl: async (url, init) => {
      assert.equal(init.redirect, 'error'); assert.equal(init.headers.Authorization, 'Bearer SYNTHETIC_ACCESS');
      const u = new URL(url), body = init.body ? JSON.parse(init.body) : undefined;
      calls.push({url, method: init.method, body});
      const failure = fail?.({url, body, state, transactions, calls, commits, reply});
      if (failure) return failure;
      if (url === crm) return reply(badMetadata === 'project' ? {} :
        {projectId: target.project, projectNumber: target.projectNumber, lifecycleState: 'ACTIVE'});
      if (url === base) return reply(badMetadata === 'database' ? {} :
        {name: resource, uid: target.databaseUid, type: 'FIRESTORE_NATIVE', databaseEdition: 'STANDARD', locationId: 'southamerica-east1'});
      if (url === `${crm}:testIamPermissions`) return reply({permissions: permissions ?? body.permissions});
      if (url === `${base}/documents:beginTransaction`) {
        const transaction = Buffer.from(`transaction-${++serial}`).toString('base64');
        transactions.set(transaction, {readOnly: Object.hasOwn(body.options, 'readOnly'), snapshot: clone(state), reads: new Set()});
        return reply({transaction});
      }
      if (url === `${base}/documents:rollback`) { transactions.delete(body.transaction); return reply({}); }
      if (url === `${base}/documents:commit`) {
        commits.push(clone(body));
        const tx = transactions.get(body.transaction); assert.ok(tx && !tx.readOnly);
        beforeCommit?.({body, state, replace, commits});
        // Emulate read-version and write-precondition rejection atomically.
        for (const path of tx.reads)
          if ((state.get(path)?.updateTime ?? null) !== (tx.snapshot.get(path)?.updateTime ?? null)) return reply({}, 409);
        for (const w of body.writes) {
          const path = w.update.name.slice(`${resource}/documents/`.length), old = state.get(path);
          if (w.currentDocument.exists === false && old) return reply({}, 409);
          if (w.currentDocument.updateTime && (!old || timestamp(old.updateTime) !== w.currentDocument.updateTime)) return reply({}, 409);
        }
        const updateTime = `${++clock}.000000001`;
        for (const w of body.writes) {
          const path = w.update.name.slice(`${resource}/documents/`.length);
          const decoded = (await import('./catalog-readonly.mjs')).decodeFields(w.update.fields);
          state.set(path, {path, fields: w.updateMask ? {...state.get(path).fields, ...decoded} : decoded, updateTime});
        }
        transactions.delete(body.transaction);
        const response = afterCommit?.({body, state, replace, commits, reply});
        if (response) return response;
        return reply({commitTime: timestamp(updateTime), writeResults: body.writes.map(() => ({updateTime: timestamp(updateTime)}))});
      }
      assert.equal(init.method, 'GET');
      const path = u.pathname.split('/documents/')[1], tx = transactions.get(u.searchParams.get('transaction'));
      assert.ok(tx, 'All catalog reads must belong to a transaction');
      assert.ok(path !== 'users' && !path.startsWith('users/') && !path.startsWith('achievements'));
      if (path === 'learningCatalogControl/current') {
        tx.reads.add(path); const d = tx.snapshot.get(path); return d ? reply(apiDoc(d)) : reply({}, 404);
      }
      assert.equal(u.searchParams.get('showMissing'), 'true');
      const docs = [...tx.snapshot.values()].filter(d => d.path.split('/').slice(0, -1).join('/') === path);
      for (const d of docs) tx.reads.add(d.path);
      return reply({documents: docs.map(apiDoc)});
    }};
  return {state, calls, authCalls, commits, events, deps, replace, transactions};
}
test('separate publisher exposes proposal verification and controlled execution', () => {
  assert.equal(typeof m?.validateProposals, 'function'); assert.equal(typeof m?.publishCatalog, 'function');
});
test('full approved proposals validate, but altered digest/target/path/operation/precondition never opens auth', {skip: !m}, async () => {
  const valid = m.validateProposals(bundle, proposals());
  assert.equal(valid.learning.digest, '0f00b4000eda98a3a7c6d3a6d524fa9347eadc811364dc72bc66746ae07e9d7e');
  for (const mutate of [p => p.learning.digest = '0'.repeat(64), p => p.learning.target.project = 'other',
    p => p.daily.publication.operations[0].path = 'users/PRIVATE_SENTINEL',
    p => p.learning.publication.operations[0].operation = 'delete',
    p => p.learning.preconditions.existing[0].updateTime = '9.000000000']) {
    const p = proposals(); mutate(p); const f = fake();
    await assert.rejects(m.publishCatalog(bundle, p, options(), f.deps));
    assert.equal(f.authCalls.length, 0); assert.equal(f.calls.length, 0);
  }
});
test('exact confirmation, target/principal and exclusive admin window are mandatory before networking', {skip: !m}, async () => {
  for (const change of [{confirm: 'yes'}, {confirm: ''}, {project: 'other'}, {database: 'other'},
    {principal: 'other@example.test'}, {adminWindow: undefined}]) {
    const f = fake(); await assert.rejects(m.publishCatalog(bundle, proposals(), {...options(), ...change}, f.deps));
    assert.equal(f.calls.length, 0); assert.equal(f.authCalls.length, 0);
  }
});
test('wire allowlist permits 37 create-only and 3 masked/versioned updates, then 7 create-only', {skip: !m}, () => {
  const p = proposals(), a = m.buildWrites(p.learning), b = m.buildWrites(p.daily);
  assert.equal(a.length, 40); assert.equal(b.length, 7);
  assert.equal(a.filter(w => w.currentDocument.exists === false).length, 37);
  const updates = a.filter(w => w.currentDocument.updateTime);
  assert.equal(updates.length, 3);
  for (const w of updates) {
    assert.deepEqual(w.updateMask.fieldPaths.sort(), ['description', 'duration', 'moduleId', 'order', 'title', 'xpReward']);
    assert.ok(!('category' in w.update.fields)); assert.ok(!('videoUrl' in w.update.fields));
  }
  assert.ok(b.every(w => w.currentDocument.exists === false && w.update.name.includes('/dailyMissions/')));
  assert.ok([...a, ...b].every(w => !w.delete && !w.transform && !w.updateTransforms));
});
test('learning commit40, successful read reconciliation and daily commit7 preserve all historical/user state', {skip: !m}, async () => {
  const f = fake(), sentinels = [...f.state.values()].filter(d => !d.path.startsWith('lessons/'));
  const result = await m.publishCatalog(bundle, proposals(), options(), f.deps);
  assert.equal(result.status, 'PUBLISHED_VERIFIED'); assert.deepEqual(result.writes, {learning: 40, daily: 7});
  assert.deepEqual(f.commits.map(c => c.writes.length), [40, 7]);
  assert.notEqual(f.commits[0].transaction, f.commits[1].transaction);
  const commitIndices = f.calls.map((c, i) => c.url.endsWith(':commit') ? i : -1).filter(i => i >= 0);
  const between = f.calls.slice(commitIndices[0] + 1, commitIndices[1]);
  assert.ok(between.some(c => c.url.endsWith(':beginTransaction') && c.body.options.readOnly));
  assert.ok(between.some(c => c.method === 'GET' && c.url.includes('/documents/')));
  assert.ok(f.events.some(e => e.stage === 'LEARNING_RECONCILED'));
  for (const legacy of bundle.legacy) {
    const actual = f.state.get(legacy.path).fields;
    assert.equal(actual.category, legacy.fields.category); assert.equal(actual.videoUrl, legacy.fields.videoUrl);
  }
  for (const d of sentinels) assert.deepEqual(f.state.get(d.path), d);
  assert.equal(f.transactions.size, 0);
  assert.ok(!JSON.stringify(result).includes('SYNTHETIC_')); assert.ok(!JSON.stringify(f.events).includes('SYNTHETIC_'));
  assert.deepEqual(f.calls.find(c => c.url === `${crm}:testIamPermissions`).body.permissions,
    ['datastore.databases.getMetadata', 'datastore.entities.get', 'datastore.entities.list',
      'resourcemanager.projects.get', 'datastore.databases.get', 'datastore.entities.create', 'datastore.entities.update']);
});
test('SDK identity/scopes and IAM metadata/write permission gates reject before a write transaction', {skip: !m}, async () => {
  for (const spec of [{identity: 'other@example.test'}, {tokenInfo: {email_verified: false}},
    {tokenInfo: {scope: 'openid'}}, {permissions: ['datastore.entities.get']}, {badMetadata: 'project'}, {badMetadata: 'database'}]) {
    const f = fake(spec); await assert.rejects(m.publishCatalog(bundle, proposals(), options(), f.deps));
    assert.equal(f.commits.length, 0);
    assert.ok(!f.calls.some(c => c.url.endsWith(':beginTransaction')));
  }
});
test('stale legacy version, content conflict, orphan or unexpected path stops all writes', {skip: !m}, async () => {
  for (const change of [f => f.state.get(bundle.legacy[0].path).updateTime = '2.000000009',
    f => f.state.get(bundle.legacy[0].path).fields.category = 'CONFLICT',
    f => f.replace({path: 'modules/unexpected', fields: {}, updateTime: '1.000000001'})]) {
    const f = fake(); change(f);
    await assert.rejects(m.publishCatalog(bundle, proposals(), options(), f.deps)); assert.equal(f.commits.length, 0);
  }
});
test('concurrent change during learning commit is atomic and never retried or followed by daily', {skip: !m}, async () => {
  const f = fake({beforeCommit: ({state}) => { state.get(bundle.legacy[0].path).updateTime = '9.000000001'; }});
  await assert.rejects(m.publishCatalog(bundle, proposals(), options(), f.deps), /COMMIT_UNCERTAIN/);
  assert.equal(f.commits.length, 1);
  assert.equal([...f.state.keys()].filter(k => k.startsWith('modules/')).length, 0);
  assert.equal([...f.state.keys()].filter(k => k.startsWith('dailyMissions/')).length, 0);
});
test('lost learning commit response reconciles by reads and stops without any second write', {skip: !m}, async () => {
  const f = fake({afterCommit: () => { throw Error('SECRET_SDK_FAILURE'); }});
  await assert.rejects(m.publishCatalog(bundle, proposals(), options(), f.deps), e =>
    e.code === 'COMMIT_UNCERTAIN' && e.observed?.learning === 'APPLIED' && !e.message.includes('SECRET'));
  assert.equal(f.commits.length, 1); assert.ok(f.state.has('modules/investimentos'));
  assert.ok(![...f.state.keys()].some(p => p.startsWith('dailyMissions/')));
});
test('reconciliation divergence after learning prevents daily, preserving learning for forward recovery', {skip: !m}, async () => {
  const f = fake({afterCommit: ({state}) => { state.get('modules/investimentos').updateTime = '9.000000001'; }});
  await assert.rejects(m.publishCatalog(bundle, proposals(), options(), f.deps), /RECONCILIATION_DIVERGED/);
  assert.equal(f.commits.length, 1); assert.ok(f.state.has('modules/investimentos'));
});
test('daily read dependency versions stop concurrent learning changes and do not undo learning', {skip: !m}, async () => {
  const f = fake({beforeCommit: ({commits, state}) => {
    if (commits.length === 2) state.get('modules/investimentos').updateTime = '9.000000001';
  }});
  await assert.rejects(m.publishCatalog(bundle, proposals(), options(), f.deps), /COMMIT_UNCERTAIN/);
  assert.equal(f.commits.length, 2); assert.ok(f.state.has('modules/investimentos'));
  assert.ok(![...f.state.keys()].some(p => p.startsWith('dailyMissions/')));
});
test('read-only reconcile classifies state without commit, and repeated complete publication is a no-op', {skip: !m}, async () => {
  const f = fake();
  const {confirm, ...readOptions} = options();
  const observed = await m.reconcilePublication(bundle, proposals(), readOptions, f.deps);
  assert.deepEqual(observed, {learning: 'UNCHANGED', daily: 'UNCHANGED'}); assert.equal(f.commits.length, 0);
  await m.publishCatalog(bundle, proposals(), options(), f.deps);
  const second = await m.publishCatalog(bundle, proposals(), options(), f.deps);
  assert.equal(second.status, 'ALREADY_APPLIED'); assert.equal(f.commits.length, 2);
});
test('unsupported JSON, credential types and proxy overrides cannot escape validation', {skip: !m}, async () => {
  for (const mutate of [p => Object.defineProperty(p.learning, 'hidden', {value: 'secret'}),
    p => p.learning[Symbol('extra')] = true,
    p => Object.defineProperty(p.learning, 'digest', {get: () => proposals().learning.digest, enumerable: true})]) {
    const p = proposals(); mutate(p); const f = fake();
    await assert.rejects(m.publishCatalog(bundle, p, options(), f.deps)); assert.equal(f.authCalls.length, 0);
  }
  const c = fake(); c.deps.loadCredentials = () => ({...credentials, type: 'external_account', token_uri: 'https://evil.example'});
  await assert.rejects(m.publishCatalog(bundle, proposals(), options(), c.deps), /SDK_IDENTITY_INVALID/);
  assert.equal(c.authCalls.length, 0);
  process.env.HTTPS_PROXY = 'https://evil.example';
  const e = fake();
  try { await assert.rejects(m.publishCatalog(bundle, proposals(), options(), e.deps), /ENVIRONMENT_INVALID/); }
  finally { delete process.env.HTTPS_PROXY; }
  assert.equal(e.authCalls.length, 0);
});
test('root getters and concurrent caller mutation cannot change the approved in-memory intent', {skip: !m}, async () => {
  const p = proposals(), f = fake();
  Object.defineProperty(p, 'learning', {enumerable: true, get: () => proposals().learning});
  await assert.rejects(m.publishCatalog(bundle, p, options(), f.deps), /PROPOSAL_INVALID/);
  assert.equal(f.authCalls.length, 0);
  const input = proposals(), safe = fake();
  safe.deps.loadCredentials = () => {
    input.learning.publication.operations[0].path = 'users/PRIVATE_SENTINEL';
    return clone(credentials);
  };
  const result = await m.publishCatalog(bundle, input, options(), safe.deps);
  assert.equal(result.status, 'PUBLISHED_VERIFIED');
  assert.ok(safe.commits.every(c => c.writes.every(w => !w.update.name.includes('/users/'))));
});
test('orphan parents, foreign responses, repeating pages and read failures abort before commit', {skip: !m}, async () => {
  for (const response of [
    reply => reply({documents: [{name: `${resource}/documents/modules/investimentos`}]}),
    reply => reply({documents: [{name: `${resource}/documents/users/PRIVATE_SENTINEL`, fields: {}, updateTime: '2026-10-16T00:00:00Z'}]}),
    reply => reply({nextPageToken: 'same/+?'}),
    reply => reply({message: 'SECRET_PAYLOAD'}, 503),
    reply => reply({}, 302),
  ]) {
    const f = fake({fail: ({url, reply}) => new URL(url).pathname.endsWith('/modules') && response(reply)});
    await assert.rejects(m.publishCatalog(bundle, proposals(), options(), f.deps), e => !e.message.includes('SECRET'));
    assert.equal(f.commits.length, 0);
  }
});
test('failure before sending a commit leaves all originals intact and closes the write transaction', {skip: !m}, async () => {
  const f = fake({fail: ({url, transactions, reply}) => {
    if (new URL(url).pathname.endsWith('/modules') && [...transactions.values()].some(tx => !tx.readOnly)) return reply({}, 503);
  }});
  const before = clone(f.state);
  await assert.rejects(m.publishCatalog(bundle, proposals(), options(), f.deps));
  assert.equal(f.commits.length, 0); assert.deepEqual(f.state, before); assert.equal(f.transactions.size, 0);
});
test('a lost daily response never repeats either commit and reports the fully applied observed state', {skip: !m}, async () => {
  const f = fake({afterCommit: ({commits}) => { if (commits.length === 2) throw Error('PRIVATE_REPLY_LOST'); }});
  await assert.rejects(m.publishCatalog(bundle, proposals(), options(), f.deps), e =>
    e.code === 'COMMIT_UNCERTAIN' && e.observed.learning === 'APPLIED' && e.observed.daily === 'APPLIED');
  assert.equal(f.commits.length, 2);
  assert.equal([...f.state.keys()].filter(p => p.startsWith('dailyMissions/')).length, 7);
});

test('malformed commit replies after application are reconciled and never retried', {skip: !m}, async () => {
  for (const mode of ['count', 'time', 'json']) {
    const f = fake({afterCommit: ({body, reply}) => mode === 'json' ?
      {ok: true, status: 200, json: async () => { throw Error('PRIVATE_JSON'); }} :
      reply({commitTime: mode === 'time' ? 'invalid' : '2026-10-16T00:00:00Z',
        writeResults: body.writes.slice(mode === 'count' ? 1 : 0).map(() => ({updateTime: '2026-10-16T00:00:00Z'}))})});
    await assert.rejects(m.publishCatalog(bundle, proposals(), options(), f.deps), e =>
      e.code === 'COMMIT_UNCERTAIN' && e.observed.learning === 'APPLIED' && e.observed.daily === 'UNCHANGED');
    assert.equal(f.commits.length, 1);
  }
});

test('valid pagination keeps the snapshot transaction and encodes opaque page tokens', {skip: !m}, async () => {
  const pageToken = 'opaque/+?=& token';
  const f = fake({fail: ({url, transactions, reply}) => {
    const u = new URL(url); if (!u.pathname.endsWith('/lessons')) return;
    const tx = transactions.get(u.searchParams.get('transaction'));
    const docs = [...tx.snapshot.values()].filter(d => d.path.startsWith('lessons/'));
    for (const d of docs) tx.reads.add(d.path);
    const next = u.searchParams.get('pageToken');
    if (next) assert.equal(next, pageToken);
    return reply({documents: (next ? docs.slice(1) : docs.slice(0, 1)).map(d =>
      ({name: `${resource}/documents/${d.path}`, fields: fields(d.fields), updateTime: timestamp(d.updateTime)})),
      ...(!next ? {nextPageToken: pageToken} : {})});
  }});
  const result = await m.reconcilePublication(bundle, proposals(), options(), f.deps);
  assert.deepEqual(result, {learning: 'UNCHANGED', daily: 'UNCHANGED'});
  const reads = f.calls.filter(c => new URL(c.url).pathname.endsWith('/lessons'));
  assert.equal(reads.length, 2);
  assert.equal(new URL(reads[0].url).searchParams.get('transaction'), new URL(reads[1].url).searchParams.get('transaction'));
  assert.ok(reads[1].url.includes('pageToken=opaque%2F%2B%3F%3D%26+token'));
  assert.equal(f.commits.length, 0);
});

test('rollback failure during preflight stops before writes; an uncertain commit with unreadable reconciliation reports UNKNOWN', {skip: !m}, async () => {
  const f = fake({fail: ({url, reply}) => url.endsWith(':rollback') && reply({}, 503)});
  await assert.rejects(m.publishCatalog(bundle, proposals(), options(), f.deps)); assert.equal(f.commits.length, 0);
  const g = fake({afterCommit: () => { throw Error('LOST'); }, fail: ({url, commits, reply}) =>
    commits.length && new URL(url).pathname.endsWith('/modules') && reply({}, 503)});
  await assert.rejects(m.publishCatalog(bundle, proposals(), options(), g.deps), e =>
    e.code === 'COMMIT_UNCERTAIN' && e.observed.learning === 'UNKNOWN' && e.observed.daily === 'UNKNOWN');
  assert.equal(g.commits.length, 1);
});
test('partial publication stops for review, and no operator can silently resume daily', {skip: !m}, async () => {
  const f = fake({afterCommit: ({commits}) => { if (commits.length === 1) throw Error('LOST'); }});
  await assert.rejects(m.publishCatalog(bundle, proposals(), options(), f.deps));
  await assert.rejects(m.publishCatalog(bundle, proposals(), options(), f.deps), /RESUME_REQUIRES_REVIEW/);
  assert.equal(f.commits.length, 1);
});

test('journal failure before commit sends nothing; after either commit or reconciliation it reports observed state and stops', {skip: !m}, async () => {
  for (const stage of ['LEARNING_COMMIT_SENDING', 'LEARNING_COMMITTED', 'LEARNING_RECONCILED',
    'DAILY_COMMIT_SENDING', 'DAILY_COMMITTED', 'DAILY_RECONCILED']) {
    const f = fake();
    f.deps.onEvent = e => { if (e.stage === stage) throw Error('PRIVATE_DISK_ERROR'); };
    await assert.rejects(m.publishCatalog(bundle, proposals(), options(), f.deps), e => {
      assert.equal(e.code, 'JOURNAL_FAILED');
      if (stage === 'LEARNING_COMMIT_SENDING') assert.equal(e.observed, undefined);
      else {
        assert.equal(e.observed.learning, 'APPLIED');
        assert.equal(e.observed.daily, ['DAILY_COMMITTED', 'DAILY_RECONCILED'].includes(stage) ? 'APPLIED' : 'UNCHANGED');
      }
      assert.ok(!e.message.includes('PRIVATE')); return true;
    });
    assert.equal(f.commits.length, stage === 'LEARNING_COMMIT_SENDING' ? 0 :
      ['DAILY_COMMITTED', 'DAILY_RECONCILED'].includes(stage) ? 2 : 1);
    assert.equal(f.transactions.size, 0);
  }
});

test('CLI journal writes every UTF-8 byte even if the underlying synchronous write is partial', {skip: !m}, () => {
  const out = join(mkdtempSync(join(tmpdir(), 'GranaXP-short-write-')), 'audit.jsonl');
  const guard = 'import fs from "node:fs";import {syncBuiltinESMExports} from "node:module";import net from "node:net";import tls from "node:tls";' +
    'const orig=fs.writeSync;fs.writeSync=function(fd,data,offset,length,pos){if(typeof data==="string")return orig(fd,data.slice(0,13),offset,length);return orig(fd,data,offset,Math.min(length,13),pos)};' +
    'syncBuiltinESMExports();let n=0;const deny=()=>{n++;throw Error("NETWORK_FORBIDDEN")};net.Socket.prototype.connect=deny;tls.connect=deny;globalThis.fetch=deny;process.on("exit",()=>console.log(`NETWORK_ATTEMPTS=${n}`));';
  const o = options();
  const r = spawnSync(process.execPath, ['--import', 'data:text/javascript,' + encodeURIComponent(guard),
    'scripts/catalog-publish.mjs', 'publish', '--bundle', process.env.GRANAXP_APPROVED_BUNDLE,
    '--learning', join(dir, 'learning-proposal.private.json'), '--daily', join(dir, 'daily-proposal.private.json'),
    '--project', o.project, '--database', o.database, '--project-number', o.projectNumber, '--database-uid', o.databaseUid,
    '--principal', o.principal, '--credential-file', join(tmpdir(), 'ABSENT_SYNTHETIC_CREDENTIAL.json'),
    '--confirm', o.confirm, '--admin-window', 'exclusive', '--audit', out], {encoding: 'utf8'});
  assert.equal(r.status, 1); assert.ok(r.stdout.includes('NETWORK_ATTEMPTS=0'));
  const lines = readFileSync(out, 'utf8').trim().split('\n').map(line => JSON.parse(line));
  assert.equal(lines[0].stage, 'COMMAND_STARTED'); assert.equal(lines.at(-1).stage, 'COMMAND_FAILED');
});
test('CLI refuses absent/incorrect approval and unknown commands before credentials or network', {skip: !m}, () => {
  const out = join(mkdtempSync(join(tmpdir(), 'GranaXP-publish-cli-')), 'audit.jsonl');
  const guard = 'import net from "node:net";import tls from "node:tls";let n=0;const deny=()=>{n++;throw Error("NETWORK_FORBIDDEN")};net.Socket.prototype.connect=deny;tls.connect=deny;globalThis.fetch=deny;process.on("exit",()=>console.log(`NETWORK_ATTEMPTS=${n}`));';
  for (const command of ['publish', 'delete', 'all', 'deploy']) {
    const r = spawnSync(process.execPath, ['--import', 'data:text/javascript,' + encodeURIComponent(guard),
      'scripts/catalog-publish.mjs', command, '--credential-file', 'SECRET_NOT_READ', '--audit', out], {encoding: 'utf8'});
    assert.equal(r.status, 1); assert.ok(r.stdout.includes('NETWORK_ATTEMPTS=0'));
    assert.ok(!r.stderr.includes('SECRET_NOT_READ')); assert.ok(!r.stderr.includes(' at '));
    assert.equal(existsSync(out), false);
  }
});
test('simulated production execution made zero attempted socket connections', () => assert.equal(networkAttempts, 0));
