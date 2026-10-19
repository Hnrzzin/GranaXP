// E6.9H: production is an offline planning target, never a connection target.
import assert from 'node:assert/strict';
import {readFileSync, writeFileSync, realpathSync} from 'node:fs';
import {createRequire} from 'node:module';
import {dirname, isAbsolute, relative, resolve} from 'node:path';
import {fileURLToPath, pathToFileURL} from 'node:url';
import {parseArgs} from 'node:util';
import {createPlan, hash, scopePaths, validateBundle} from './educational-publication.mjs';
import {readScope, assertEmulatorHost} from './educational-publication-emulator.mjs';
import {createAchievementPlan, validateAchievementDefinitions, achievementPaths,
  ACHIEVEMENT_MANIFEST_DIGEST} from './achievement-publication.mjs';

export const PRODUCTION_EXECUTION = 'BLOCKED_E69H';
export const LOCAL_PRINCIPAL = 'catalog-admin@example.test';
const requireFunctions = createRequire(new URL('../functions/package.json', import.meta.url));
const root = realpathSync(fileURLToPath(new URL('..', import.meta.url)));
const sessions = new WeakMap();
const production = Object.freeze({project: 'granaxp-d7927', database: '(default)', environment: 'production',
  projectNumber: '136652496724', databaseUid: '81c02a82-fb39-40a3-b0b8-82439c8ba232',
  ruleset: '6a53743b-1962-4c38-84e7-1d64737b5ee8',
  rulesSha256: '04c7b573854b50517ae625f9cbdb5c94a62ad41f9dde40a36b66c66150bd1341'});
class AdminError extends Error {
  constructor(code) { super(code); this.code = code; }
}
function check(condition, code) { if (!condition) throw new AdminError(code); }
function only(value, names) {
  check(value && Object.getPrototypeOf(value) === Object.prototype &&
    Object.keys(value).length === names.length && names.every(n => Object.hasOwn(value, n)), 'INVALID_SHAPE');
}
function json(value) {
  if (typeof value === 'number') check(Number.isFinite(value) &&
    (!Number.isInteger(value) || Number.isSafeInteger(value)), 'UNSUPPORTED_VALUE');
  else if (value !== null && typeof value === 'object') {
    const array = Array.isArray(value), keys = Reflect.ownKeys(value);
    check(Object.getPrototypeOf(value) === (array ? Array.prototype : Object.prototype), 'UNSUPPORTED_VALUE');
    if (array) check(keys.length === value.length + 1, 'UNSUPPORTED_VALUE');
    for (const key of keys) {
      if (array && key === 'length') continue;
      check(typeof key === 'string' && (!array || /^(0|[1-9]\d*)$/.test(key)), 'UNSUPPORTED_VALUE');
      const d = Object.getOwnPropertyDescriptor(value, key);
      check(d.enumerable && Object.hasOwn(d, 'value'), 'UNSUPPORTED_VALUE'); json(d.value);
    }
  } else check(value === null || ['string', 'boolean'].includes(typeof value), 'UNSUPPORTED_VALUE');
}
export function assertProductionContext(context) {
  json(context);
  only(context, [...Object.keys(production), 'principal']);
  for (const [key, value] of Object.entries(production)) check(context[key] === value, 'CONTEXT_INVALID');
  check(typeof context.principal === 'string' && /^[^\s@]+@[^\s@]+$/.test(context.principal), 'SDK_IDENTITY_INVALID');
  return structuredClone(context); // These offline assertions are not live verification.
}
function validateContext(context) {
  json(context);
  if (context?.environment === 'production') return assertProductionContext(context);
  only(context, ['project', 'database', 'environment', 'host', 'principal']);
  check(context.project === 'demo-granaxp' && context.database === '(default)' &&
    context.environment === 'emulator' && ['127.0.0.1:18080', '127.0.0.1:28080'].includes(context.host),
  'CONTEXT_INVALID');
  check(context.principal === LOCAL_PRINCIPAL, 'SDK_IDENTITY_INVALID');
  return structuredClone(context);
}
function localEnvironment(context) {
  check(context?.environment === 'emulator', PRODUCTION_EXECUTION);
  validateContext(context);
  try { assertEmulatorHost(context.host); } catch { throw new AdminError('LOCAL_ENVIRONMENT_INVALID'); }
  for (const name of ['CLOUDSDK_AUTH_CREDENTIAL_FILE_OVERRIDE', 'GOOGLE_CLOUD_QUOTA_PROJECT',
    'GCE_METADATA_HOST', 'GCE_METADATA_IP', 'GOOGLE_CLOUD_UNIVERSE_DOMAIN', 'FIREBASE_CONFIG',
    'HTTP_PROXY', 'HTTPS_PROXY', 'ALL_PROXY', 'http_proxy', 'https_proxy', 'all_proxy', 'grpc_proxy', 'GRPC_PROXY'])
    check(!process.env[name], 'LOCAL_ENVIRONMENT_INVALID');
}
export async function verifySDKIdentity(auth, expected) {
  try {
    check(typeof expected === 'string' && expected.length > 0, 'SDK_IDENTITY_INVALID');
    const credentials = await auth.getCredentials();
    check(credentials?.client_email === expected, 'SDK_IDENTITY_INVALID');
    return credentials.client_email;
  } catch { throw new AdminError('SDK_IDENTITY_INVALID'); }
}
export async function openLocalSession(context) {
  // The check precedes SDK loading, ADC discovery, construction and network I/O.
  localEnvironment(context);
  const frozen = Object.freeze(validateContext(context));
  const {GoogleAuth} = requireFunctions('google-auth-library');
  const auth = new GoogleAuth({projectId: 'demo-granaxp', credentials: {type: 'service_account',
    project_id: 'demo-granaxp', client_email: LOCAL_PRINCIPAL, private_key: 'E69H_SYNTHETIC_NOT_A_REAL_KEY'}});
  await verifySDKIdentity(auth, frozen.principal);
  localEnvironment(frozen);
  const {Firestore} = requireFunctions('@google-cloud/firestore');
  const db = new Firestore({projectId: frozen.project, databaseId: frozen.database,
    host: frozen.host, ssl: false, auth});
  const session = Object.freeze({context: frozen});
  sessions.set(session, {db, auth, context: frozen});
  return session;
}
async function sessionState(session) {
  const state = sessions.get(session); check(state, 'SESSION_INVALID');
  localEnvironment(state.context);
  check(state.db.projectId === state.context.project && state.db.databaseId === '(default)' &&
    state.db._settings.servicePath === '127.0.0.1' && state.db._settings.ssl === false &&
    state.db._settings.port === Number(state.context.host.split(':')[1]) &&
    state.db._settings.auth === state.auth, 'CONNECTION_BLOCKED');
  await verifySDKIdentity(state.auth, state.context.principal);
  localEnvironment(state.context);
  return state;
}
export async function closeSession(session) {
  const state = sessions.get(session); check(state, 'SESSION_INVALID');
  sessions.delete(session); await state.db.terminate();
}
function validateDocuments(snapshot, allowed) {
  only(snapshot, ['documents']); check(Array.isArray(snapshot.documents), 'SNAPSHOT_INVALID');
  const seen = new Set();
  for (const d of snapshot.documents) {
    only(d, ['path', 'fields', 'updateTime']); json(d.fields);
    check(allowed.includes(d.path) && !seen.has(d.path) &&
      typeof d.updateTime === 'string' && /^\d+\.\d{9}$/.test(d.updateTime), 'SNAPSHOT_INVALID');
    seen.add(d.path);
  }
}
function learningPlan(bundle, snapshot, context) {
  validateDocuments(snapshot, scopePaths('learning'));
  for (const legacy of bundle.legacy) {
    const existing = snapshot.documents.find(x => x.path === legacy.path);
    check(existing, 'LEGACY_MISSING');
    const published = {...legacy.fields, ...bundle.learning.find(x => x.path === legacy.path).fields};
    check(hash(existing.fields) === hash(legacy.fields) || hash(existing.fields) === hash(published),
      'LEGACY_CHANGED');
  }
  const p = createPlan(bundle, snapshot, {project: context.project, environment: context.environment}, 'learning');
  check([0, 40].includes(p.operations.length), 'PARTIAL_CATALOG');
  return p;
}
export function createAdminPlan(bundle, snapshot, context, scope) {
  try {
    json(bundle); json(snapshot); validateBundle(bundle); context = validateContext(context);
    check(['learning', 'daily', 'achievements'].includes(scope), 'SCOPE_INVALID');
    only(snapshot, ['documents', 'dependencies']);
    const before = structuredClone(snapshot);
    before.documents.sort((a, b) => a.path.localeCompare(b.path));
    before.dependencies.documents.sort((a, b) => a.path.localeCompare(b.path));
    const documents = {documents: before.documents};
    let publication;
    if (scope === 'learning') {
      validateDocuments(before.dependencies, []);
      publication = learningPlan(bundle, documents, context);
    } else {
      check(learningPlan(bundle, before.dependencies, context).operations.length === 0, 'LEARNING_NOT_PUBLISHED');
      if (scope === 'daily') {
        validateDocuments(documents, scopePaths('daily'));
        publication = createPlan(bundle, documents, {project: context.project, environment: context.environment}, scope);
        check([0, 7].includes(publication.operations.length), 'PARTIAL_CATALOG');
      } else {
        validateDocuments(documents, achievementPaths());
        publication = createAchievementPlan(validateAchievementDefinitions(), documents, before.dependencies);
      }
    }
    const content = {version: 1, productionExecution: PRODUCTION_EXECUTION, context, scope, before, publication,
      resourcesHash: hash(publication.resources),
      manifestDigest: scope === 'achievements' ? ACHIEVEMENT_MANIFEST_DIGEST : null};
    return {...content, digest: hash(content)};
  } catch { throw new AdminError('PLAN_INVALID'); }
}
export function adminConfirmation(plan) {
  return `${plan.context.project}/${plan.context.database}/${plan.scope}/${plan.digest}/${plan.resourcesHash}`;
}
function validatePlan(bundle, plan) {
  try {
    json(plan);
    const fresh = createAdminPlan(bundle, plan.before, plan.context, plan.scope);
    check(hash(fresh) === hash(plan), 'PLAN_INVALID');
    return fresh;
  } catch { throw new AdminError('PLAN_INVALID'); }
}
async function readState(state, reader, scope) {
  check(['learning', 'daily', 'achievements'].includes(scope), 'SCOPE_INVALID');
  const {db} = state;
  let documents;
  if (scope !== 'achievements') documents = (await readScope(db, reader, scope)).documents;
  else {
    const allowed = achievementPaths();
    for (const ref of await db.collection('achievements').listDocuments())
      check(allowed.includes(ref.path), 'UNEXPECTED_DOCUMENT');
    const ref = db.collection('achievements');
    const result = reader === db ? await ref.get() : await reader.get(ref);
    documents = result.docs.map(d => ({path: d.ref.path, fields: d.data(),
      updateTime: `${d.updateTime.seconds}.${String(d.updateTime.nanoseconds).padStart(9, '0')}`}));
  }
  const dependencies = scope === 'learning' ? {documents: []} : await readScope(db, reader, 'learning');
  await verifyParents(db, documents, scope === 'achievements' ? achievementPaths() : scopePaths(scope));
  if (scope !== 'learning') await verifyParents(db, dependencies.documents, scopePaths('learning'));
  json(documents); json(dependencies);
  return {documents: documents.sort((a, b) => a.path.localeCompare(b.path)), dependencies};
}
async function verifyParents(db, documents, allowed) {
  const present = new Set(documents.map(d => d.path));
  const collections = new Set(allowed.map(path => path.split('/').slice(0, -1).join('/')));
  for (const path of collections)
    for (const ref of await db.collection(path).listDocuments())
      check(allowed.includes(ref.path) && present.has(ref.path), 'UNEXPECTED_OR_ORPHAN_DOCUMENT');
}
export async function readCatalogSnapshot(session, scope) {
  const state = await sessionState(session);
  try { return await readState(state, state.db, scope); }
  catch { throw new AdminError('SNAPSHOT_FAILED'); }
}
function afterFields(plan) {
  const docs = new Map(plan.before.documents.map(d => [d.path, structuredClone(d.fields)]));
  for (const op of plan.publication.operations) {
    if (op.operation === 'create') docs.set(op.path, structuredClone(op.fields));
    else Object.assign(docs.get(op.path), op.fields);
  }
  return [...docs].sort(([a], [b]) => a.localeCompare(b)).map(([path, fields]) => ({path, fields}));
}
function matchesAfter(current, plan) {
  const written = new Set(plan.publication.operations.map(op => op.path));
  const versions = new Map(current.documents.map(d => [d.path, d.updateTime]));
  // Historical documents not written by this plan must retain their exact version.
  const preserved = plan.before.documents.filter(d => !written.has(d.path))
    .every(d => versions.get(d.path) === d.updateTime);
  return preserved &&
    hash(current.documents.map(({path, fields}) => ({path, fields}))) === hash(afterFields(plan)) &&
    hash(current.dependencies) === hash(plan.before.dependencies);
}
export async function reconcilePlan(session, bundle, input) {
  const plan = validatePlan(bundle, input);
  check(hash(plan.context) === hash(session.context), 'CONTEXT_INVALID');
  const current = await readCatalogSnapshot(session, plan.scope);
  if (hash(current) === hash(plan.before)) return {status: 'UNCHANGED'};
  return {status: matchesAfter(current, plan) ? 'APPLIED' : 'DIVERGED'};
}
export async function applyApprovedPlan(session, bundle, input, approval) {
  const plan = validatePlan(bundle, input);
  check(approval?.digest === plan.digest && approval?.confirm === adminConfirmation(plan), 'APPROVAL_INVALID');
  check(hash(plan.context) === hash(session.context), 'CONTEXT_INVALID');
  const state = await sessionState(session);
  let attempts = 0;
  try {
    const result = await state.db.runTransaction(async transaction => {
      attempts++;
      await sessionState(session);
      const current = await readState(state, transaction, plan.scope);
      check(hash(current) === hash(plan.before), 'STALE_PLAN');
      const fresh = createAdminPlan(bundle, current, state.context, plan.scope);
      check(fresh.digest === plan.digest, 'STALE_PLAN');
      for (const op of fresh.publication.operations) {
        const ref = state.db.doc(op.path);
        if (op.operation === 'create') transaction.create(ref, op.fields);
        else {
          check(plan.scope === 'learning' && (op.path === 'learningCatalogControl/current' ||
            bundle.legacy.some(d => d.path === op.path)), 'OPERATION_FORBIDDEN');
          transaction.update(ref, op.fields);
        }
      }
      return {status: fresh.publication.operations.length ? 'COMMITTED' : 'NO_OP',
        writes: fresh.publication.operations.length, digest: plan.digest};
    }, {maxAttempts: 3});
    // Inventory again, including metadata scans not protected by a global lock.
    const current = await readCatalogSnapshot(session, plan.scope);
    check(matchesAfter(current, plan), 'POSTCHECK_DIVERGED');
    return {...result, attempts};
  } catch (error) {
    if (error instanceof AdminError) throw error;
    // No outer apply retry. Reconciliation performs reads only.
    try {
      const observed = await reconcilePlan(session, bundle, plan);
      if (observed.status === 'APPLIED') return {status: 'COMMITTED_VERIFIED_AFTER_UNCERTAIN',
        writes: plan.publication.operations.length, digest: plan.digest, attempts};
    } catch { /* Remains uncertain; never echo the SDK error or retry blindly. */ }
    throw new AdminError('COMMIT_UNCERTAIN');
  }
}
function output(path, value) {
  check(path && isAbsolute(path), 'OUTPUT_INVALID');
  const target = resolve(realpathSync(dirname(path)), path.split(/[\\/]/).at(-1));
  const rel = relative(root, target);
  check(rel === '..' || rel.startsWith('../') || rel.startsWith('..\\'), 'OUTPUT_INVALID');
  writeFileSync(target, JSON.stringify(value, null, 2) + '\n', {flag: 'wx', mode: 0o600});
}
async function cli() {
  const names = ['bundle', 'snapshot', 'plan', 'project', 'database', 'environment', 'host', 'principal',
    'scope', 'out', 'digest', 'confirm', 'project-number', 'database-uid', 'ruleset', 'rules-sha256'];
  const {values: v, positionals} = parseArgs({allowPositionals: true,
    options: Object.fromEntries(names.map(n => [n, {type: 'string'}]))});
  check(positionals.length === 1 && ['snapshot', 'preview', 'apply', 'reconcile'].includes(positionals[0]), 'COMMAND_INVALID');
  const command = positionals[0];
  if (command !== 'preview') check(v.environment === 'emulator' && v.project === 'demo-granaxp', PRODUCTION_EXECUTION);
  const context = validateContext(v.environment === 'production' ? {
    project: v.project, database: v.database, environment: v.environment, principal: v.principal,
    projectNumber: v['project-number'], databaseUid: v['database-uid'], ruleset: v.ruleset, rulesSha256: v['rules-sha256'],
  } : {project: v.project, database: v.database, environment: v.environment, host: v.host, principal: v.principal});
  const read = path => JSON.parse(readFileSync(path, 'utf8').replace(/^\uFEFF/, ''));
  if (command === 'preview') {
    const p = createAdminPlan(read(v.bundle), read(v.snapshot), context, v.scope);
    output(v.out, p);
    console.log(JSON.stringify({digest: p.digest, confirmation: adminConfirmation(p), resourcesHash: p.resourcesHash,
      scope: p.scope, writes: p.publication.operations.length, productionExecution: PRODUCTION_EXECUTION}));
    return;
  }
  localEnvironment(context);
  // Invalid or unapproved plans fail before constructing even the local SDK.
  let bundle, plan;
  if (command !== 'snapshot') {
    bundle = read(v.bundle); plan = validatePlan(bundle, read(v.plan));
    check(hash(plan.context) === hash(context) && plan.scope === v.scope, 'CONTEXT_INVALID');
    if (command === 'apply')
      check(v.digest === plan.digest && v.confirm === adminConfirmation(plan), 'APPROVAL_INVALID');
  } else check(['learning', 'daily', 'achievements'].includes(v.scope), 'SCOPE_INVALID');
  const session = await openLocalSession(context);
  try {
    if (command === 'snapshot') { output(v.out, await readCatalogSnapshot(session, v.scope)); console.log('{"snapshotSaved":true}'); }
    else console.log(JSON.stringify(command === 'apply' ?
      await applyApprovedPlan(session, bundle, plan, {digest: v.digest, confirm: v.confirm}) :
      await reconcilePlan(session, bundle, plan)));
  } finally { await closeSession(session); }
}
if (process.argv[1] && import.meta.url === pathToFileURL(resolve(process.argv[1])).href)
  cli().catch(error => {
    console.error(JSON.stringify({error: error instanceof AdminError ? error.code : 'ADMIN_FAILED'}));
    process.exitCode = 1;
  });
