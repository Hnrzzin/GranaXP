import {randomBytes, createHash} from 'node:crypto';
import {existsSync, readFileSync, writeFileSync, unlinkSync} from 'node:fs';
import {dirname, resolve, isAbsolute} from 'node:path';
import {fileURLToPath} from 'node:url';
import {spawn} from 'node:child_process';

const root = resolve(dirname(fileURLToPath(import.meta.url)), '..');
const secretPath = resolve(root, 'functions/.secret.local');
const [mode = 'functions', ...args] = process.argv.slice(2);
const hosts = {GCLOUD_PROJECT: 'demo-granaxp', FIRESTORE_EMULATOR_HOST: '127.0.0.1:8080',
  FIREBASE_AUTH_EMULATOR_HOST: '127.0.0.1:9099', FUNCTIONS_EMULATOR_PORT: '5001'};
const keys = {k1: randomBytes(32).toString('base64'), k2: randomBytes(32).toString('base64')};
const ring = JSON.stringify({activeKeyId: 'k1', keys});
const invalidConfig = process.env.GRANAXP_TEST_INVALID_KEYRING === '1';
const fixture = `LESSON_ACTIVITY_PROOF_KEYRING=${invalidConfig ? '{}' : ring}\n`;
const ownedDigest = createHash('sha256').update(fixture).digest('hex');
let created = false;

const redact = (text) => {
  for (const value of Object.values(keys)) text = text.split(value).join('[REDACTED_KEY]');
  return text.replace(/hmac-sha256:3:[A-Za-z0-9_-]{1,32}:[0-9a-f]{64}/g, '[REDACTED_PROOF]');
};
function forward(stream, destination) {
  let pending = '';
  stream.setEncoding('utf8');
  stream.on('data', (chunk) => {
    pending += chunk;
    const end = pending.lastIndexOf('\n');
    if (end >= 0) { destination.write(redact(pending.slice(0, end + 1))); pending = pending.slice(end + 1); }
  });
  stream.on('end', () => { if (pending) destination.write(redact(pending)); });
}
async function runNode(argv, env) {
  return await new Promise((resolveRun, rejectRun) => {
    const child = spawn(process.execPath, argv, {cwd: root, env, stdio: ['inherit', 'pipe', 'pipe'], windowsHide: true});
    forward(child.stdout, process.stdout); forward(child.stderr, process.stderr);
    child.on('error', rejectRun);
    child.on('exit', (code, signal) => resolveRun(code ?? (signal ? 1 : 0)));
  });
}
const quoted = (path) => {
  if (!isAbsolute(path) || /["\r\n%!]/.test(path)) throw new Error('Caminho de ensaio inválido.');
  return `"${path}"`;
};
async function main() {
  if (Number(process.versions.node.split('.')[0]) !== 22) throw new Error('O runner exige Node 22.');
  if (!process.env.JAVA_HOME || !existsSync(resolve(process.env.JAVA_HOME, 'bin/java.exe'))) {
    throw new Error('Configure JAVA_HOME para o JBR local antes do emulador.');
  }
  for (const [name, expected] of Object.entries(hosts)) {
    if (process.env[name] && process.env[name] !== expected) throw new Error(`Ambiente ${name} fora do demo/loopback.`);
  }
  const config = JSON.parse(readFileSync(resolve(root, 'firebase.json'), 'utf8'));
  for (const [service, port] of [['auth', 9099], ['firestore', 8080], ['functions', 5001]]) {
    if (config.emulators?.[service]?.host !== '127.0.0.1' || config.emulators?.[service]?.port !== port) {
      throw new Error('Configuração de emulador fora do loopback aprovado.');
    }
  }
  if (existsSync(secretPath)) throw new Error('Arquivo de segredo local preexistente: preservado; execução recusada.');
  let command;
  if (mode === 'functions' && args.length <= 1) {
    if (args[0] && !/^[A-Za-z0-9_:-]+$/.test(args[0])) throw new Error('Filtro de teste inválido.');
    command = args[0] ? `node --test --test-name-pattern=${args[0]} functions/test/functions.integration.test.mjs` :
      'npm --prefix functions test';
  } else if (mode === 'catalog' && args.length === 2 && !invalidConfig) {
    command = `node scripts/catalog-10-3-emulator.mjs ${quoted(args[0])} ${quoted(args[1])}`;
  } else throw new Error('Use functions [filtro] ou catalog DRAFTS_DIR MANIFEST_PATH.');
  if (invalidConfig && args[0] !== 'v3_indisponivel') throw new Error('Configuração inválida exige teste isolado v3_indisponivel.');
  const ignore = readFileSync(resolve(root, '.gitignore'), 'utf8');
  if (!ignore.split(/\r?\n/).includes('functions/.secret.local')) throw new Error('Arquivo de segredo não está ignorado.');
  const cli = resolve(root, 'node_modules/firebase-tools/lib/bin/firebase.js');
  if (!existsSync(cli)) throw new Error('Firebase CLI local não encontrado.');
  const env = {...process.env, ...hosts, GRANAXP_TEST_KEYRING: ring,
    PATH: resolve(process.env.JAVA_HOME, 'bin') + ';' + dirname(process.execPath) + ';' + process.env.PATH};
  const build = await runNode([resolve(root, 'functions/node_modules/typescript/bin/tsc'), '-p', 'functions/tsconfig.json'], env);
  if (build !== 0) return build;
  writeFileSync(secretPath, fixture, {flag: 'wx', mode: 0o600}); created = true;
  return await runNode([cli, 'emulators:exec', '--only', 'auth,firestore,functions', '--project', 'demo-granaxp', command], env);
}
try { process.exitCode = await main(); }
catch (error) { console.error(redact(error.message)); process.exitCode = 1; }
finally {
  if (created && existsSync(secretPath)) {
    const current = createHash('sha256').update(readFileSync(secretPath)).digest('hex');
    if (current === ownedDigest) unlinkSync(secretPath);
    else { console.error('Segredo local modificado por outro processo: preservado.'); process.exitCode = 1; }
  }
}
