// E6.9I: separate read-only inventory. Never imports or unlocks the H executor.
import {readFileSync, writeFileSync, realpathSync, existsSync} from 'node:fs';
import {createRequire} from 'node:module';
import {dirname, isAbsolute, relative, resolve} from 'node:path';
import {fileURLToPath, pathToFileURL} from 'node:url';
import {parseArgs} from 'node:util';
import {hash, scopePaths, validateBundle, CONTROL} from './educational-publication.mjs';
import {achievementPaths, validateAchievementDefinitions, HISTORICAL_ACHIEVEMENTS} from './achievement-publication.mjs';

const target = Object.freeze({project: 'granaxp-d7927', database: '(default)', projectNumber: '136652496724',
  databaseUid: '81c02a82-fb39-40a3-b0b8-82439c8ba232'});
const resource = 'projects/granaxp-d7927/databases/(default)';
const base = `https://firestore.googleapis.com/v1/${resource}`;
const crm = 'https://cloudresourcemanager.googleapis.com/v1/projects/granaxp-d7927';
const requiredPermissions = Object.freeze(['datastore.databases.getMetadata', 'datastore.entities.get',
  'datastore.entities.list', 'resourcemanager.projects.get']);
const requireFunctions = createRequire(new URL('../functions/package.json', import.meta.url));
const root = realpathSync(fileURLToPath(new URL('..', import.meta.url)));
class ReadError extends Error { constructor(code) { super(code); this.code = code; } }
const check = (ok, code) => { if (!ok) throw new ReadError(code); };
const object = x => x !== null && typeof x === 'object' && !Array.isArray(x);
const paths = scope => scope === 'achievements' ? achievementPaths() : scopePaths(scope);
function collections(scope) {
  check(['learning', 'daily', 'achievements'].includes(scope), 'SCOPE_INVALID');
  return [...new Set(paths(scope).filter(p => p !== CONTROL).map(p => p.split('/').slice(0, -1).join('/')))];
}
function environment() {
  for (const key of ['FIRESTORE_EMULATOR_HOST', 'FIREBASE_AUTH_EMULATOR_HOST', 'GOOGLE_APPLICATION_CREDENTIALS',
    'GOOGLE_OAUTH_ACCESS_TOKEN', 'FIREBASE_TOKEN', 'CLOUDSDK_AUTH_ACCESS_TOKEN', 'CLOUDSDK_AUTH_CREDENTIAL_FILE_OVERRIDE',
    'GOOGLE_CLOUD_QUOTA_PROJECT', 'GOOGLE_CLOUD_UNIVERSE_DOMAIN', 'GCE_METADATA_HOST', 'GCE_METADATA_IP',
    'FIREBASE_CONFIG', 'HTTP_PROXY', 'HTTPS_PROXY', 'ALL_PROXY', 'http_proxy', 'https_proxy', 'all_proxy'])
    check(!process.env[key], 'ENVIRONMENT_INVALID');
  for (const key of ['GCLOUD_PROJECT', 'GOOGLE_CLOUD_PROJECT'])
    check(!process.env[key] || process.env[key] === target.project, 'ENVIRONMENT_INVALID');
}
function context(options) {
  for (const [key, value] of Object.entries(target)) check(options[key] === value, 'TARGET_INVALID');
  collections(options.scope);
  check(typeof options.principal === 'string' && /^[^\s@]+@[^\s@]+\.[^\s@]+$/.test(options.principal), 'IDENTITY_INVALID');
  check(options.confirmRead === `READ_ONLY/${target.project}/${target.database}/${options.scope}/${options.principal}`,
    'READ_AUTHORIZATION_REQUIRED');
  check(typeof options.credentialFile === 'string' && isAbsolute(options.credentialFile), 'CREDENTIAL_FILE_INVALID');
  environment();
}
async function authenticate(credentials, expected, oauthAdapter) {
  try {
    // Explicit authorized_user only. Never discover ADC, metadata, CLI accounts or external-account executables.
    check(credentials?.type === 'authorized_user' && Object.keys(credentials).every(k =>
      ['type', 'client_id', 'client_secret', 'refresh_token', 'quota_project_id', 'universe_domain'].includes(k)), 'IDENTITY_INVALID');
    for (const k of ['client_id', 'client_secret', 'refresh_token']) check(typeof credentials[k] === 'string' && credentials[k], 'IDENTITY_INVALID');
    check(!credentials.quota_project_id || credentials.quota_project_id === target.project, 'IDENTITY_INVALID');
    check(!credentials.universe_domain || credentials.universe_domain === 'googleapis.com', 'IDENTITY_INVALID');
    const {GoogleAuth} = requireFunctions('google-auth-library');
    const client = new GoogleAuth({projectId: target.project}).fromJSON(credentials);
    if (oauthAdapter) client.transporter.defaults.adapter = oauthAdapter; // Local tests replace HTTP, not SDK identity logic.
    const send = client.transporter.request.bind(client.transporter);
    client.transporter.request = opts => {
      environment();
      check(opts.method === 'POST' && ['https://oauth2.googleapis.com/token',
        'https://oauth2.googleapis.com/tokeninfo'].includes(String(opts.url)), 'IDENTITY_INVALID');
      return send({...opts, maxRedirects: 0, retry: false, retryConfig: {retry: 0}, timeout: 15000});
    };
    const {token} = await client.getAccessToken(); check(typeof token === 'string' && token, 'IDENTITY_INVALID');
    const info = await client.getTokenInfo(token);
    check(info.email === expected && [true, 'true'].includes(info.email_verified) &&
      info.expiry_date > Date.now() + 60000 && info.scopes?.includes('https://www.googleapis.com/auth/cloud-platform'),
    'IDENTITY_INVALID');
    return {token, expiresAt: info.expiry_date};
  } catch { throw new ReadError('SDK_IDENTITY_INVALID'); }
}

// Every resource HTTP request passes this gate, including requests made after pagination.
export function assertReadRequest(scope, method, url, body, readTime) {
  const allowed = collections(scope), u = new URL(url);
  check(u.href === url && !u.username && !u.password && !u.hash, 'REQUEST_FORBIDDEN');
  if (method === 'POST' && url === `${crm}:testIamPermissions`) {
    check(body === JSON.stringify({permissions: requiredPermissions}), 'REQUEST_FORBIDDEN'); return;
  }
  check(method === 'GET' && body === undefined, 'REQUEST_FORBIDDEN');
  if ([base, crm].includes(url)) return;
  check(typeof readTime === 'string' && !Number.isNaN(Date.parse(readTime)), 'REQUEST_FORBIDDEN');
  const prefix = `${base}/documents/`, query = u.searchParams;
  check(url.startsWith(prefix), 'REQUEST_FORBIDDEN');
  const path = url.slice(prefix.length).split('?')[0];
  const control = scope === 'learning' && path === CONTROL;
  check(control || allowed.includes(path), 'REQUEST_FORBIDDEN');
  const names = [...query.keys()];
  check(new Set(names).size === names.length && query.get('readTime') === readTime &&
    names.every(k => (control ? ['readTime'] : ['readTime', 'pageSize', 'showMissing', 'pageToken']).includes(k)), 'REQUEST_FORBIDDEN');
  check(control ? names.length === 1 : query.get('pageSize') === '100' && query.get('showMissing') === 'true', 'REQUEST_FORBIDDEN');
}
export function decodeFields(fields) {
  check(object(fields), 'UNSUPPORTED_FIELDS');
  const value = v => {
    check(object(v) && Object.keys(v).length === 1, 'UNSUPPORTED_VALUE');
    const [k] = Object.keys(v), x = v[k];
    if (k === 'nullValue') { check(x === null || x === 'NULL_VALUE', 'UNSUPPORTED_VALUE'); return null; }
    if (k === 'stringValue') { check(typeof x === 'string', 'UNSUPPORTED_VALUE'); return x; }
    if (k === 'booleanValue') { check(typeof x === 'boolean', 'UNSUPPORTED_VALUE'); return x; }
    if (k === 'integerValue') {
      check(typeof x === 'string' && /^-?(0|[1-9]\d*)$/.test(x) && Number.isSafeInteger(Number(x)), 'UNSUPPORTED_VALUE');
      return Number(x);
    }
    if (k === 'doubleValue') {
      check(typeof x === 'number' && Number.isFinite(x) && (!Number.isInteger(x) || Number.isSafeInteger(x)), 'UNSUPPORTED_VALUE'); return x;
    }
    if (k === 'arrayValue') {
      check(object(x) && Object.keys(x).every(k => k === 'values') && (!Object.hasOwn(x, 'values') || Array.isArray(x.values)), 'UNSUPPORTED_VALUE');
      return (x.values ?? []).map(value);
    }
    if (k === 'mapValue') {
      check(object(x) && Object.keys(x).every(k => k === 'fields'), 'UNSUPPORTED_VALUE'); return decodeFields(x.fields ?? {});
    }
    throw new ReadError('UNSUPPORTED_VALUE'); // Timestamp, reference, bytes and geo never silently become plain strings.
  };
  return Object.fromEntries(Object.entries(fields).map(([k, v]) => [k, value(v)]));
}
function version(time) {
  const m = /^(\d{4}-\d\d-\d\dT\d\d:\d\d:\d\d)(?:\.(\d{3}|\d{6}|\d{9}))?Z$/.exec(time);
  check(m && Number.isFinite(Date.parse(time)), 'DOCUMENT_INVALID');
  return `${Date.parse(m[1] + 'Z') / 1000}.${(m[2] ?? '').padEnd(9, '0')}`;
}
export async function inventoryCatalog(input, dependencies = {}) {
  try {
    const options = structuredClone(input); context(options);
    const load = dependencies.loadCredentials ?? (file => JSON.parse(readFileSync(file, 'utf8').replace(/^\uFEFF/, '')));
    const {token, expiresAt} = await authenticate(load(options.credentialFile), options.principal, dependencies.oauthAdapter);
    const fetchImpl = dependencies.fetchImpl ?? globalThis.fetch;
    let readTime;
    const request = async (method, url, body) => {
      context(options); check(Date.now() + 10000 < expiresAt, 'IDENTITY_EXPIRED');
      assertReadRequest(options.scope, method, url, body, readTime);
      const response = await fetchImpl(url, {method, body, redirect: 'error', signal: AbortSignal.timeout(15000),
        headers: {Authorization: `Bearer ${token}`, ...(body ? {'Content-Type': 'application/json'} : {})}});
      if (response.status === 404 && options.scope === 'learning' &&
        url.startsWith(`${base}/documents/${CONTROL}?`)) return {data: null, response};
      check(response.ok && response.status === 200, 'READ_FAILED');
      const data = await response.json(); check(object(data), 'RESPONSE_INVALID'); return {data, response};
    };
    const project = (await request('GET', crm)).data;
    check(project.projectId === target.project && project.projectNumber === target.projectNumber &&
      project.lifecycleState === 'ACTIVE', 'PROJECT_MISMATCH');
    const database = await request('GET', base), d = database.data;
    check(d.name === resource && d.uid === target.databaseUid && d.type === 'FIRESTORE_NATIVE' &&
      d.databaseEdition === 'STANDARD' && d.locationId === 'southamerica-east1', 'DATABASE_MISMATCH');
    const serverTime = Date.parse(database.response.headers.get('date'));
    check(Number.isFinite(serverTime) && Math.abs(serverTime - Date.now()) < 300000, 'SERVER_TIME_INVALID');
    readTime = new Date(serverTime - 1000).toISOString();
    const iam = (await request('POST', `${crm}:testIamPermissions`, JSON.stringify({permissions: requiredPermissions}))).data;
    check(Array.isArray(iam.permissions) && requiredPermissions.every(p => iam.permissions.includes(p)), 'PERMISSIONS_MISSING');
    const documents = [], missingParents = [], seen = new Set(), unexpectedPaths = [];
    const consume = (document, collection, exact) => {
      check(object(document) && typeof document.name === 'string' && document.name.startsWith(`${resource}/documents/`), 'DOCUMENT_INVALID');
      const path = document.name.slice(`${resource}/documents/`.length), parts = path.split('/');
      check(exact ? path === exact : parts.slice(0, -1).join('/') === collection &&
        parts.at(-1) && !['.', '..'].includes(parts.at(-1)), 'DOCUMENT_OUTSIDE_SCOPE');
      check(!seen.has(path), 'DUPLICATE_DOCUMENT'); seen.add(path);
      if (!paths(options.scope).includes(path)) unexpectedPaths.push(path);
      if (!Object.hasOwn(document, 'updateTime')) {
        check(!Object.hasOwn(document, 'fields') && !Object.hasOwn(document, 'createTime'), 'DOCUMENT_INVALID');
        missingParents.push(path); return;
      }
      documents.push({path, fields: decodeFields(document.fields ?? {}), updateTime: version(document.updateTime)});
    };
    for (const collection of collections(options.scope)) {
      let pageToken; const tokens = new Set(); let pages = 0;
      do {
        check(++pages <= 100, 'INVENTORY_LIMIT');
        const query = new URLSearchParams({pageSize: '100', showMissing: 'true', readTime});
        if (pageToken) query.set('pageToken', pageToken);
        const page = (await request('GET', `${base}/documents/${collection}?${query}`)).data;
        check(!Object.hasOwn(page, 'documents') || Array.isArray(page.documents), 'RESPONSE_INVALID');
        for (const document of page.documents ?? []) consume(document, collection);
        pageToken = page.nextPageToken;
        check(pageToken === undefined || typeof pageToken === 'string', 'RESPONSE_INVALID');
        if (pageToken) { check(!tokens.has(pageToken), 'PAGE_TOKEN_REPEATED'); tokens.add(pageToken); }
        check(seen.size <= 10000, 'INVENTORY_LIMIT');
      } while (pageToken);
    }
    if (options.scope === 'learning') {
      const control = (await request('GET', `${base}/documents/${CONTROL}?${new URLSearchParams({readTime})}`)).data;
      if (control) consume(control, null, CONTROL);
    }
    const result = {version: 1, mode: 'READ_ONLY_E69I', target: {...target}, scope: options.scope,
      readTime, completedAt: new Date().toISOString(), verification: {principal: options.principal,
        oauthExpiresAt: new Date(expiresAt).toISOString(), permissions: [...requiredPermissions],
        permissionResource: 'projects/granaxp-d7927',
        project: {projectId: project.projectId, projectNumber: project.projectNumber, lifecycleState: project.lifecycleState},
        database: {name: d.name, uid: d.uid, type: d.type, databaseEdition: d.databaseEdition, locationId: d.locationId}},
      snapshot: {documents: documents.sort((a, b) => a.path.localeCompare(b.path))},
      missingParents: missingParents.sort(), unexpectedPaths: unexpectedPaths.sort()};
    return {...result, digest: hash(result)};
  } catch (error) { throw error instanceof ReadError ? error : new ReadError('INVENTORY_FAILED'); }
}
export function compareInventory(inventory, bundle) {
  validateBundle(bundle); const scope = inventory.scope; collections(scope);
  const manifest = validateAchievementDefinitions();
  const expected = scope === 'achievements' ? manifest.definitions.map(d => ({path: `achievements/${d.id}`, fields: d.fields})) :
    scope === 'daily' ? bundle.daily : [...bundle.learning.map(d => ({path: d.path,
      fields: {...(bundle.legacy.find(l => l.path === d.path)?.fields ?? {}), ...d.fields}})),
    {path: CONTROL, fields: {protocolVersion: 1, generation: 2}}];
  const current = new Map(inventory.snapshot.documents.map(d => [d.path, d]));
  check(current.size === inventory.snapshot.documents.length, 'DUPLICATE_DOCUMENT');
  const entries = expected.map(d => {
    const actual = current.get(d.path), legacy = bundle.legacy.find(l => l.path === d.path);
    const status = !actual ? 'absent' : hash(actual.fields) === hash(d.fields) ? 'exact' :
      legacy && hash(actual.fields) === hash(legacy.fields) ? 'legacy' : 'conflict';
    return {path: d.path, status, expectedHash: hash(d.fields), observedHash: actual ? hash(actual.fields) : null};
  });
  const blockers = [];
  if (inventory.missingParents.length) blockers.push('ORPHAN_DOCUMENTS');
  if (inventory.unexpectedPaths.length || [...current.keys()].some(p => !paths(scope).includes(p))) blockers.push('UNEXPECTED_DOCUMENTS');
  if (entries.some(e => e.status === 'conflict')) blockers.push('CONTENT_CONFLICT');
  if (scope === 'learning' && bundle.legacy.some(d => !current.has(d.path))) blockers.push('LEGACY_LESSONS_MISSING');
  if (scope === 'achievements') {
    for (const [id, [type, count]] of Object.entries(HISTORICAL_ACHIEVEMENTS)) {
      const old = current.get(`achievements/${id}`);
      if (!old) { if (!blockers.includes('HISTORICAL_ACHIEVEMENTS_MISSING')) blockers.push('HISTORICAL_ACHIEVEMENTS_MISSING'); }
      else {
        check(old.fields.requirementType === type && old.fields.requirementValue === count, 'HISTORICAL_ACHIEVEMENT_CHANGED');
        entries.push({path: old.path, status: 'historical_preserve_required', observedHash: hash(old.fields)});
      }
    }
    blockers.push('EDITORIAL_APPROVAL_PENDING');
  }
  return {scope, entries, counts: Object.fromEntries(['absent', 'exact', 'legacy', 'conflict', 'historical_preserve_required']
    .map(s => [s, entries.filter(e => e.status === s).length])), blockers,
  ...(scope === 'achievements' ? {editorialStatus: manifest.editorialApproval.status, productionApproved: false,
    manifestDigest: manifest.digest} : {}),
  publicationAuthorized: false, dependenciesVerified: false};
}
function outputPath(path) {
  check(typeof path === 'string' && isAbsolute(path), 'OUTPUT_INVALID');
  const resolved = resolve(realpathSync(dirname(path)), path.split(/[\\/]/).at(-1));
  const rel = relative(root, resolved);
  check((rel === '..' || rel.startsWith('../') || rel.startsWith('..\\')) && !existsSync(resolved), 'OUTPUT_INVALID');
  return resolved;
}
async function cli() {
  const names = ['project', 'database', 'project-number', 'database-uid', 'principal', 'scope',
    'credential-file', 'confirm-read', 'out', 'inventory', 'bundle'];
  const {values: v, positionals: p} = parseArgs({allowPositionals: true,
    options: Object.fromEntries(names.map(n => [n, {type: 'string'}]))});
  check(p.length === 1 && ['inventory', 'compare'].includes(p[0]), 'COMMAND_FORBIDDEN');
  let result;
  if (p[0] === 'inventory') {
    const options = {project: v.project, database: v.database, projectNumber: v['project-number'],
      databaseUid: v['database-uid'], principal: v.principal, scope: v.scope,
      credentialFile: v['credential-file'], confirmRead: v['confirm-read']};
    context(options); const path = outputPath(v.out);
    result = await inventoryCatalog(options);
    writeFileSync(path, JSON.stringify(result, null, 2) + '\n', {flag: 'wx', mode: 0o600});
  } else {
    const path = outputPath(v.out), read = file => JSON.parse(readFileSync(file, 'utf8').replace(/^\uFEFF/, ''));
    const inventory = read(v.inventory), {digest, ...content} = inventory;
    check(digest === hash(content) && inventory.mode === 'READ_ONLY_E69I' && inventory.version === 1 &&
      hash(inventory.target) === hash(target), 'INVENTORY_INVALID');
    result = compareInventory(inventory, read(v.bundle));
    writeFileSync(path, JSON.stringify(result, null, 2) + '\n', {flag: 'wx', mode: 0o600});
  }
  console.log(JSON.stringify({saved: true, scope: result.scope, digest: hash(result)}));
}
if (process.argv[1] && import.meta.url === pathToFileURL(resolve(process.argv[1])).href)
  cli().catch(error => { console.error(JSON.stringify({error: error instanceof ReadError ? error.code : 'READONLY_FAILED'})); process.exitCode = 1; });
