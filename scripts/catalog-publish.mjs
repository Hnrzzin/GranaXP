// Controlled production publisher. Separate from H (still blocked) and I (read-only).
import {readFileSync, realpathSync, existsSync, openSync, writeFileSync, fsyncSync, closeSync} from 'node:fs';
import {createRequire} from 'node:module';
import {dirname, isAbsolute, relative, resolve} from 'node:path';
import {fileURLToPath, pathToFileURL} from 'node:url';
import {parseArgs} from 'node:util';
import {createPlan, hash, scopePaths, validateBundle, CONTROL} from './educational-publication.mjs';
import {decodeFields} from './catalog-readonly.mjs';

const pins = Object.freeze({learning: '0f00b4000eda98a3a7c6d3a6d524fa9347eadc811364dc72bc66746ae07e9d7e',
  daily: '35e7b55e338965e3bfee1a8454513aaafe8f6fd54cd57821b0b7c8b5c6ef2b6a'});
const target = Object.freeze({project: 'granaxp-d7927', database: '(default)', projectNumber: '136652496724',
  databaseUid: '81c02a82-fb39-40a3-b0b8-82439c8ba232'});
const resource = 'projects/granaxp-d7927/databases/(default)';
const base = `https://firestore.googleapis.com/v1/${resource}`;
const crm = 'https://cloudresourcemanager.googleapis.com/v1/projects/granaxp-d7927';
const requireFunctions = createRequire(new URL('../functions/package.json', import.meta.url));
const root = realpathSync(fileURLToPath(new URL('..', import.meta.url)));
class PublishError extends Error {
  constructor(code, observed) { super(code); this.code = code; if (observed) this.observed = observed; }
}
const check = (ok, code) => { if (!ok) throw new PublishError(code); };
const same = (a, b) => hash(a) === hash(b);
function copy(value, seen = new Set()) {
  if (value === null || ['string', 'boolean'].includes(typeof value)) return value;
  if (typeof value === 'number') {
    check(Number.isFinite(value) && (!Number.isInteger(value) || Number.isSafeInteger(value)), 'JSON_INVALID'); return value;
  }
  check(value && typeof value === 'object' && !seen.has(value), 'JSON_INVALID'); seen.add(value);
  const array = Array.isArray(value), keys = Reflect.ownKeys(value);
  check(Object.getPrototypeOf(value) === (array ? Array.prototype : Object.prototype), 'JSON_INVALID');
  if (array) check(keys.length === value.length + 1, 'JSON_INVALID');
  const entries = [];
  for (const key of keys) {
    if (array && key === 'length') continue;
    const d = Object.getOwnPropertyDescriptor(value, key);
    check(typeof key === 'string' && d.enumerable && Object.hasOwn(d, 'value') &&
      (!array || /^(0|[1-9]\d*)$/.test(key)), 'JSON_INVALID'); entries.push([key, copy(d.value, seen)]);
  }
  seen.delete(value); return array ? entries.map(([, v]) => v) : Object.fromEntries(entries);
}
function pinned(proposal) {
  const p = copy(proposal), {digest, ...body} = p;
  check(Object.hasOwn(pins, p.scope) && digest === pins[p.scope] && hash(body) === digest &&
    same(p.target, target) && p.kind === 'SPARK_PUBLICATION_PROPOSAL', 'PROPOSAL_INVALID'); return p;
}
function content(snapshot) { return snapshot.documents.map(({path, fields}) => ({path, fields})); }
function afterLearning(learning) {
  const docs = new Map(learning.publication.before.documents.map(d => [d.path, copy(d.fields)]));
  for (const op of learning.publication.operations) {
    if (op.operation === 'create') { check(!docs.has(op.path), 'PROPOSAL_INVALID'); docs.set(op.path, copy(op.fields)); }
    else Object.assign(docs.get(op.path), op.fields);
  }
  return [...docs].sort(([a], [b]) => a.localeCompare(b)).map(([path, fields]) => ({path, fields}));
}
export function validateProposals(inputBundle, input) {
  try {
    const bundle = copy(inputBundle); validateBundle(bundle);
    const incoming = copy(input);
    check(same(Object.keys(incoming).sort(), ['daily', 'learning']), 'PROPOSAL_INVALID');
    const learning = pinned(incoming.learning), daily = pinned(incoming.daily);
    for (const p of [learning, daily]) {
      check(same(p.publication, createPlan(bundle, p.publication.before,
        {project: target.project, environment: 'production'}, p.scope)), 'PROPOSAL_INVALID');
      check(p.bundleSha256 === hash(bundle) && p.principal === learning.principal, 'PROPOSAL_INVALID');
      check(p.publication.operations.every(o => scopePaths(p.scope).includes(o.path) &&
        ['create', 'update'].includes(o.operation)), 'PROPOSAL_INVALID');
    }
    check(learning.publication.operations.length === 40 && daily.publication.operations.length === 7 &&
      learning.publication.operations.filter(o => o.operation === 'update').length === 3 &&
      daily.publication.operations.every(o => o.operation === 'create'), 'PROPOSAL_INVALID');
    for (const legacy of bundle.legacy) {
      check(same(learning.publication.before.documents.find(d => d.path === legacy.path)?.fields, legacy.fields), 'PROPOSAL_INVALID');
      const update = learning.publication.operations.find(d => d.path === legacy.path);
      check(update?.operation === 'update' && same(Object.keys(update.fields).sort(),
        ['description', 'duration', 'moduleId', 'order', 'title', 'xpReward']), 'PROPOSAL_INVALID');
    }
    check(learning.expectedAfterContentHash === hash(afterLearning(learning)) &&
      daily.dependency.learningProposalDigest === learning.digest &&
      daily.dependency.expectedLearningContentHash === learning.expectedAfterContentHash, 'PROPOSAL_INVALID');
    return {learning, daily};
  } catch { throw new PublishError('PROPOSAL_INVALID'); }
}
function toRFC(value) {
  check(typeof value === 'string' && /^\d{1,12}\.\d{9}$/.test(value), 'VERSION_INVALID');
  const [seconds, nanos] = value.split('.');
  return new Date(Number(seconds) * 1000).toISOString().slice(0, 19) + '.' + nanos + 'Z';
}
function version(value) {
  check(typeof value === 'string', 'VERSION_INVALID');
  const m = /^(\d{4}-\d\d-\d\dT\d\d:\d\d:\d\d)(?:\.(\d{3}|\d{6}|\d{9}))?Z$/.exec(value);
  check(m && Number.isFinite(Date.parse(value)), 'VERSION_INVALID');
  return `${Date.parse(m[1] + 'Z') / 1000}.${(m[2] ?? '').padEnd(9, '0')}`;
}
function encode(fields) {
  const value = x => x === null ? {nullValue: null} : Array.isArray(x) ? {arrayValue: {values: x.map(value)}} :
    typeof x === 'object' ? {mapValue: {fields: encode(x)}} : typeof x === 'string' ? {stringValue: x} :
    typeof x === 'boolean' ? {booleanValue: x} : Number.isInteger(x) ? {integerValue: String(x)} : {doubleValue: x};
  return Object.fromEntries(Object.entries(fields).map(([k, v]) => [k, value(v)]));
}
export function buildWrites(input) {
  const p = pinned(input);
  return p.publication.operations.map(op => {
    check(scopePaths(p.scope).includes(op.path), 'WRITE_FORBIDDEN');
    const update = {name: `${resource}/documents/${op.path}`, fields: encode(op.fields)};
    if (op.operation === 'create') return {update, currentDocument: {exists: false}};
    check(p.scope === 'learning' && /^lessons\/[^/]+$/.test(op.path) &&
      same(Object.keys(op.fields).sort(), ['description', 'duration', 'moduleId', 'order', 'title', 'xpReward']), 'WRITE_FORBIDDEN');
    const existing = p.publication.before.documents.find(d => d.path === op.path);
    check(existing, 'WRITE_FORBIDDEN');
    return {update, updateMask: {fieldPaths: Object.keys(op.fields).sort()},
      currentDocument: {updateTime: toRFC(existing.updateTime)}};
  });
}
export function publicationConfirmation() { return `PUBLISH/${target.project}/${target.database}/${pins.learning}/${pins.daily}`; }
function environment() {
  for (const key of ['FIRESTORE_EMULATOR_HOST', 'FIREBASE_AUTH_EMULATOR_HOST', 'GOOGLE_APPLICATION_CREDENTIALS',
    'GOOGLE_OAUTH_ACCESS_TOKEN', 'FIREBASE_TOKEN', 'CLOUDSDK_AUTH_ACCESS_TOKEN', 'CLOUDSDK_AUTH_CREDENTIAL_FILE_OVERRIDE',
    'GOOGLE_CLOUD_QUOTA_PROJECT', 'GOOGLE_CLOUD_UNIVERSE_DOMAIN', 'GCE_METADATA_HOST', 'GCE_METADATA_IP',
    'FIREBASE_CONFIG', 'HTTP_PROXY', 'HTTPS_PROXY', 'ALL_PROXY', 'http_proxy', 'https_proxy', 'all_proxy'])
    check(!process.env[key], 'ENVIRONMENT_INVALID');
  for (const key of ['GCLOUD_PROJECT', 'GOOGLE_CLOUD_PROJECT'])
    check(!process.env[key] || process.env[key] === target.project, 'ENVIRONMENT_INVALID');
}
function context(options, p, write) {
  environment();
  for (const [key, value] of Object.entries(target)) check(options[key] === value, 'TARGET_INVALID');
  check(options.principal === p.learning.principal && options.principal === p.daily.principal, 'SDK_IDENTITY_INVALID');
  check(typeof options.credentialFile === 'string' && isAbsolute(options.credentialFile), 'CREDENTIAL_FILE_INVALID');
  if (write) check(options.confirm === publicationConfirmation() && options.adminWindow === 'exclusive', 'FINAL_AUTHORIZATION_REQUIRED');
}
async function authenticate(credentials, expected, adapter) {
  try {
    const c = copy(credentials);
    check(c.type === 'authorized_user' && Object.keys(c).every(k => ['type', 'client_id', 'client_secret',
      'refresh_token', 'quota_project_id', 'universe_domain'].includes(k)), 'SDK_IDENTITY_INVALID');
    for (const key of ['client_id', 'client_secret', 'refresh_token']) check(typeof c[key] === 'string' && c[key], 'SDK_IDENTITY_INVALID');
    check((!c.quota_project_id || c.quota_project_id === target.project) &&
      (!c.universe_domain || c.universe_domain === 'googleapis.com'), 'SDK_IDENTITY_INVALID');
    const {GoogleAuth} = requireFunctions('google-auth-library');
    const client = new GoogleAuth({projectId: target.project}).fromJSON(c);
    if (adapter) client.transporter.defaults.adapter = adapter;
    const send = client.transporter.request.bind(client.transporter);
    client.transporter.request = opts => {
      environment(); check(opts.method === 'POST' && ['https://oauth2.googleapis.com/token',
        'https://oauth2.googleapis.com/tokeninfo'].includes(String(opts.url)), 'SDK_IDENTITY_INVALID');
      return send({...opts, maxRedirects: 0, retry: false, retryConfig: {retry: 0}, timeout: 15000});
    };
    const {token} = await client.getAccessToken();
    check(typeof token === 'string' && token, 'SDK_IDENTITY_INVALID');
    const info = await client.getTokenInfo(token);
    check(info.email === expected && [true, 'true'].includes(info.email_verified) &&
      info.expiry_date > Date.now() + 60000 && info.scopes?.includes('https://www.googleapis.com/auth/cloud-platform'), 'SDK_IDENTITY_INVALID');
    return {token, expiresAt: info.expiry_date};
  } catch { throw new PublishError('SDK_IDENTITY_INVALID'); }
}
async function session(options, p, write, deps) {
  context(options, p, write);
  const load = deps.loadCredentials ?? (file => JSON.parse(readFileSync(file, 'utf8').replace(/^\uFEFF/, '')));
  const auth = await authenticate(load(options.credentialFile), options.principal, deps.oauthAdapter);
  const fetchImpl = deps.fetchImpl ?? globalThis.fetch, txs = new Map(); let commitSent = false;
  const permissions = ['datastore.databases.getMetadata', 'datastore.entities.get', 'datastore.entities.list',
    'resourcemanager.projects.get', 'datastore.databases.get', ...(write ? ['datastore.entities.create', 'datastore.entities.update'] : [])];
  const event = data => {
    try { deps.onEvent?.({...data, at: new Date().toISOString()}); }
    catch { throw new PublishError('JOURNAL_FAILED'); }
  };
  const allPaths = [...scopePaths('learning'), ...scopePaths('daily')];
  const collections = new Set(allPaths.filter(path => path !== CONTROL).map(path => path.split('/').slice(0, -1).join('/')));
  async function request(method, url, body) {
    context(options, p, write); check(auth.expiresAt > Date.now() + 10000, 'SDK_IDENTITY_EXPIRED');
    const u = new URL(url); check(u.href === url && !u.hash && !u.username && !u.password, 'REQUEST_FORBIDDEN');
    let tx;
    if (method === 'GET' && [crm, base].includes(url)) check(body === undefined, 'REQUEST_FORBIDDEN');
    else if (method === 'POST' && url === `${crm}:testIamPermissions`)
      check(same(body, {permissions}), 'REQUEST_FORBIDDEN');
    else if (method === 'POST' && url === `${base}/documents:beginTransaction`)
      check(same(body, {options: {readOnly: {}}}) || write && same(body, {options: {readWrite: {}}}), 'REQUEST_FORBIDDEN');
    else if (method === 'POST' && url === `${base}/documents:rollback`) {
      tx = txs.get(body?.transaction);
      check(tx && !tx.sent && same(Object.keys(body), ['transaction']), 'REQUEST_FORBIDDEN');
    } else if (method === 'POST' && url === `${base}/documents:commit`) {
      tx = txs.get(body?.transaction);
      check(write && tx && !tx.readOnly && tx.checked && !tx.sent &&
        same(Object.keys(body).sort(), ['transaction', 'writes']) && same(body.writes, buildWrites(p[tx.scope])), 'WRITE_FORBIDDEN');
      check(Date.now() - tx.started < 120000, 'TRANSACTION_TIMEOUT');
    } else {
      check(method === 'GET' && body === undefined && url.startsWith(`${base}/documents/`), 'REQUEST_FORBIDDEN');
      const path = url.slice(`${base}/documents/`.length).split('?')[0], query = u.searchParams;
      tx = txs.get(query.get('transaction'));
      const keys = [...query.keys()];
      check(tx && new Set(keys).size === keys.length && (path === CONTROL || collections.has(path)) &&
        keys.every(k => (path === CONTROL ? ['transaction'] : ['transaction', 'pageSize', 'showMissing', 'pageToken']).includes(k)) &&
        (path === CONTROL ? keys.length === 1 : query.get('pageSize') === '100' && query.get('showMissing') === 'true'), 'REQUEST_FORBIDDEN');
    }
    if (url === `${base}/documents:commit`) { tx.sent = true; commitSent = true; } // Never send this transaction's commit twice.
    const response = await fetchImpl(url, {method, redirect: 'error', signal: AbortSignal.timeout(15000),
      headers: {Authorization: `Bearer ${auth.token}`, ...(body ? {'Content-Type': 'application/json'} : {})},
      ...(body ? {body: JSON.stringify(body)} : {})});
    if (method === 'GET' && url.startsWith(`${base}/documents/${CONTROL}?`) && response.status === 404) return null;
    check(response.ok && response.status === 200, 'REMOTE_REQUEST_FAILED');
    const result = await response.json(); check(result && typeof result === 'object' && !Array.isArray(result), 'RESPONSE_INVALID');
    return result;
  }
  const project = await request('GET', crm);
  check(project.projectId === target.project && project.projectNumber === target.projectNumber && project.lifecycleState === 'ACTIVE', 'PROJECT_MISMATCH');
  const db = await request('GET', base);
  check(db.name === resource && db.uid === target.databaseUid && db.type === 'FIRESTORE_NATIVE' &&
    db.databaseEdition === 'STANDARD' && db.locationId === 'southamerica-east1', 'DATABASE_MISMATCH');
  const iam = await request('POST', `${crm}:testIamPermissions`, {permissions});
  check(Array.isArray(iam.permissions) && permissions.every(x => iam.permissions.includes(x)), 'PERMISSIONS_MISSING');
  event({stage: 'IDENTITY_TARGET_IAM_VERIFIED'});
  async function begin(readOnly, scope) {
    const data = await request('POST', `${base}/documents:beginTransaction`, {options: {[readOnly ? 'readOnly' : 'readWrite']: {}}});
    const id = data.transaction;
    check(typeof id === 'string' && /^[A-Za-z0-9+/]+={0,2}$/.test(id) && id.length <= 4096 && !txs.has(id), 'TRANSACTION_INVALID');
    const tx = {id, readOnly, scope, sent: false, checked: false, started: Date.now()}; txs.set(id, tx); return tx;
  }
  async function rollback(tx) {
    await request('POST', `${base}/documents:rollback`, {transaction: tx.id}); txs.delete(tx.id);
  }
  async function read(tx, scope) {
    const allowed = scopePaths(scope), docs = [], seen = new Set();
    function consume(doc, collection) {
      check(doc && typeof doc.name === 'string' && doc.name.startsWith(`${resource}/documents/`), 'DOCUMENT_INVALID');
      const path = doc.name.slice(`${resource}/documents/`.length);
      check(allowed.includes(path) && !seen.has(path) &&
        (collection ? path.split('/').slice(0, -1).join('/') === collection : path === CONTROL), 'UNEXPECTED_DOCUMENT');
      seen.add(path); check(Object.hasOwn(doc, 'updateTime'), 'ORPHAN_DOCUMENT');
      docs.push({path, fields: decodeFields(doc.fields ?? {}), updateTime: version(doc.updateTime)});
    }
    const lists = new Set(allowed.filter(path => path !== CONTROL).map(path => path.split('/').slice(0, -1).join('/')));
    for (const collection of lists) {
      let pageToken; const tokens = new Set(); let pages = 0;
      do {
        check(++pages <= 100, 'INVENTORY_LIMIT');
        const query = new URLSearchParams({transaction: tx.id, pageSize: '100', showMissing: 'true'});
        if (pageToken) query.set('pageToken', pageToken);
        const data = await request('GET', `${base}/documents/${collection}?${query}`);
        check(!Object.hasOwn(data, 'documents') || Array.isArray(data.documents), 'RESPONSE_INVALID');
        for (const doc of data.documents ?? []) consume(doc, collection);
        pageToken = data.nextPageToken;
        check(pageToken === undefined || typeof pageToken === 'string', 'RESPONSE_INVALID');
        if (pageToken) { check(!tokens.has(pageToken), 'PAGE_TOKEN_REPEATED'); tokens.add(pageToken); }
      } while (pageToken);
    }
    if (scope === 'learning') {
      const doc = await request('GET', `${base}/documents/${CONTROL}?${new URLSearchParams({transaction: tx.id})}`);
      if (doc) consume(doc);
    }
    return {documents: docs.sort((a, b) => a.path.localeCompare(b.path))};
  }
  async function snapshot() {
    const tx = await begin(true);
    try { return {learning: await read(tx, 'learning'), daily: await read(tx, 'daily')}; }
    finally { await rollback(tx); }
  }
  function classify(current) {
    return Object.fromEntries(['learning', 'daily'].map(scope => [scope,
      same(current[scope], p[scope].publication.before) ? 'UNCHANGED' :
        hash(content(current[scope])) === (scope === 'learning' ? p.learning.expectedAfterContentHash :
          hash(p.daily.publication.operations.map(o => ({path: o.path, fields: o.fields})).sort((a, b) => a.path.localeCompare(b.path)))) ? 'APPLIED' : 'DIVERGED']));
  }
  async function observed() { try { return classify(await snapshot()); } catch { return {learning: 'UNKNOWN', daily: 'UNKNOWN'}; } }
  async function apply(scope, learningSnapshot) {
    const tx = await begin(false, scope);
    try {
      const current = await read(tx, scope);
      check(same(current, p[scope].publication.before), 'PRECONDITIONS_CHANGED');
      if (scope === 'daily') check(same(await read(tx, 'learning'), learningSnapshot), 'DEPENDENCY_CHANGED');
      tx.checked = true;
      event({stage: `${scope.toUpperCase()}_COMMIT_SENDING`, digest: p[scope].digest, writes: p[scope].publication.operations.length});
      let result;
      try {
        result = await request('POST', `${base}/documents:commit`, {transaction: tx.id, writes: buildWrites(p[scope])});
        version(result.commitTime);
        check(Array.isArray(result.writeResults) && result.writeResults.length === p[scope].publication.operations.length, 'COMMIT_RESPONSE_INVALID');
        for (const r of result.writeResults) version(r.updateTime);
      } catch { throw new PublishError('COMMIT_UNCERTAIN', await observed()); }
      txs.delete(tx.id);
      event({stage: `${scope.toUpperCase()}_COMMITTED`, writes: result.writeResults.length});
      return new Map(p[scope].publication.operations.map((op, i) => [op.path, version(result.writeResults[i].updateTime)]));
    } catch (error) {
      if (!tx.sent) { try { await rollback(tx); } catch { event({stage: 'ROLLBACK_UNCONFIRMED'}); } }
      throw error;
    }
  }
  return {snapshot, classify, apply, event, observed, hasSent: () => commitSent}; // Private; no SDK/token/request escapes.
}
function checkedOptions(bundle, input, rawOptions, write) {
  const p = validateProposals(bundle, input), options = copy(rawOptions); context(options, p, write); return {p, options};
}
export async function reconcilePublication(bundle, input, rawOptions, deps = {}) {
  try {
    const {p, options} = checkedOptions(bundle, input, rawOptions, false);
    const s = await session(options, p, false, deps); return s.classify(await s.snapshot());
  } catch (e) { throw e instanceof PublishError ? e : new PublishError('RECONCILIATION_FAILED'); }
}
export async function publishCatalog(bundle, input, rawOptions, deps = {}) {
  let s;
  try {
    const {p, options} = checkedOptions(bundle, input, rawOptions, true);
    s = await session(options, p, true, deps);
    const before = await s.snapshot(), status = s.classify(before);
    if (status.learning === 'APPLIED' && status.daily === 'APPLIED') return {status: 'ALREADY_APPLIED', writes: {learning: 0, daily: 0}};
    check(status.learning === 'UNCHANGED' && status.daily === 'UNCHANGED',
      status.learning === 'APPLIED' && status.daily === 'UNCHANGED' ? 'RESUME_REQUIRES_REVIEW' : 'PRECONDITIONS_CHANGED');
    const learningVersions = await s.apply('learning');
    const middle = await s.snapshot(), reconciled = s.classify(middle);
    check(reconciled.learning === 'APPLIED' && reconciled.daily === 'UNCHANGED' &&
      middle.learning.documents.every(d => d.updateTime === learningVersions.get(d.path)), 'RECONCILIATION_DIVERGED');
    s.event({stage: 'LEARNING_RECONCILED'});
    const dailyVersions = await s.apply('daily', middle.learning);
    const after = await s.snapshot(), final = s.classify(after);
    check(final.learning === 'APPLIED' && final.daily === 'APPLIED' && same(after.learning, middle.learning) &&
      after.daily.documents.every(d => d.updateTime === dailyVersions.get(d.path)), 'RECONCILIATION_DIVERGED');
    s.event({stage: 'DAILY_RECONCILED'});
    return {status: 'PUBLISHED_VERIFIED', writes: {learning: 40, daily: 7}, digests: {...pins}};
  } catch (e) {
    if (e?.code === 'JOURNAL_FAILED' && s?.hasSent()) throw new PublishError(e.code, await s.observed());
    throw e instanceof PublishError ? e : new PublishError('PUBLICATION_FAILED');
  }
}
function auditFile(path) {
  check(typeof path === 'string' && isAbsolute(path), 'AUDIT_INVALID');
  const resolved = resolve(realpathSync(dirname(path)), path.split(/[\\/]/).at(-1)), rel = relative(root, resolved);
  check((rel === '..' || rel.startsWith('../') || rel.startsWith('..\\')) && !existsSync(resolved), 'AUDIT_INVALID');
  return openSync(resolved, 'wx', 0o600);
}
async function cli() {
  const names = ['bundle', 'learning', 'daily', 'project', 'database', 'project-number', 'database-uid',
    'principal', 'credential-file', 'confirm', 'admin-window', 'audit'];
  const {values: v, positionals: commands} = parseArgs({allowPositionals: true,
    options: Object.fromEntries(names.map(n => [n, {type: 'string'}]))});
  check(commands.length === 1 && ['verify', 'publish', 'reconcile'].includes(commands[0]), 'COMMAND_FORBIDDEN');
  const command = commands[0];
  if (command === 'publish') check(v.confirm === publicationConfirmation() && v['admin-window'] === 'exclusive', 'FINAL_AUTHORIZATION_REQUIRED');
  const read = path => JSON.parse(readFileSync(path, 'utf8').replace(/^\uFEFF/, ''));
  const bundle = read(v.bundle), input = {learning: read(v.learning), daily: read(v.daily)};
  const p = validateProposals(bundle, input);
  if (command === 'verify') {
    console.log(JSON.stringify({valid: true, digests: {...pins}, writes: {learning: 40, daily: 7}, productionExecuted: false})); return;
  }
  const options = {project: v.project, database: v.database, projectNumber: v['project-number'], databaseUid: v['database-uid'],
    principal: v.principal, credentialFile: v['credential-file'],
    ...(command === 'publish' ? {confirm: v.confirm, adminWindow: v['admin-window']} : {})};
  context(options, p, command === 'publish');
  const fd = auditFile(v.audit), record = event => { writeFileSync(fd, Buffer.from(JSON.stringify(event) + '\n', 'utf8')); fsyncSync(fd); };
  try {
    record({stage: 'COMMAND_STARTED', command, digests: {...pins}, target});
    const result = await (command === 'publish' ? publishCatalog : reconcilePublication)(bundle, p, options, {onEvent: record});
    record({stage: 'COMMAND_RESULT', result}); console.log(JSON.stringify(result));
  } catch (e) {
    try { record({stage: 'COMMAND_FAILED', code: e instanceof PublishError ? e.code : 'PUBLICATION_FAILED', ...(e.observed ? {observed: e.observed} : {})}); } catch { /* stderr still carries the safe failure. */ }
    throw e;
  } finally { closeSync(fd); }
}
if (process.argv[1] && import.meta.url === pathToFileURL(resolve(process.argv[1])).href)
  cli().catch(e => { console.error(JSON.stringify({error: e instanceof PublishError ? e.code : 'PUBLICATION_FAILED',
    ...(e instanceof PublishError && e.observed ? {observed: e.observed} : {})})); process.exitCode = 1; });
