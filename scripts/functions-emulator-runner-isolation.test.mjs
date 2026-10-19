import assert from 'node:assert/strict';
import {test} from 'node:test';
import {spawnSync} from 'node:child_process';
import {mkdtempSync, mkdirSync, readFileSync, writeFileSync, existsSync, symlinkSync, unlinkSync, rmSync} from 'node:fs';
import {tmpdir} from 'node:os';
import {dirname, join, resolve} from 'node:path';
import {fileURLToPath, pathToFileURL} from 'node:url';

const repository = resolve(dirname(fileURLToPath(import.meta.url)), '..');
const source = readFileSync(join(repository, 'scripts/functions-emulator-runner.mjs'));
const sentinel = 'OFFLINE_CHILD_BOUNDARY';

function fixture(t, rc, {preserve = false, version = '15.32.0', completeBuild = false, changeAlias = false} = {}) {
  const root = mkdtempSync(join(tmpdir(), 'granaxp-runner-isolation-'));
  const dependency = join(root, 'node_modules/cjson');
  t.after(() => {
    assert.equal(dirname(resolve(root)), resolve(tmpdir()));
    assert.ok(root.includes('granaxp-runner-isolation-'));
    unlinkSync(dependency); // Remove the junction before deleting only our disposable tree.
    rmSync(root, {recursive: true, force: true});
  });
  for (const path of ['scripts', 'functions', 'node_modules/firebase-tools/lib/bin', 'jbr/bin']) {
    mkdirSync(join(root, path), {recursive: true});
  }
  symlinkSync(join(repository, 'node_modules/cjson'), dependency, 'junction');
  writeFileSync(join(root, 'scripts/functions-emulator-runner.mjs'), source);
  writeFileSync(join(root, 'firebase.json'), JSON.stringify({firebase: 'prod-fixture',
    emulators: {auth: {host: '127.0.0.1', port: 9099}, firestore: {host: '127.0.0.1', port: 8080},
      functions: {host: '127.0.0.1', port: 5001}}}));
  writeFileSync(join(root, '.gitignore'), 'functions/.secret.local\n');
  writeFileSync(join(root, 'jbr/bin/java.exe'), 'not executable');
  writeFileSync(join(root, 'node_modules/firebase-tools/package.json'), JSON.stringify({version}));
  writeFileSync(join(root, 'node_modules/firebase-tools/lib/bin/firebase.js'), 'throw new Error("CLI must never run");');
  if (rc === 'directory') mkdirSync(join(root, '.firebaserc'));
  else if (rc !== undefined) writeFileSync(join(root, '.firebaserc'), rc);
  if (preserve) {
    writeFileSync(join(root, 'functions/.env.local'), 'PREEXISTING_CONFIG\n');
    writeFileSync(join(root, 'functions/.secret.local'), 'PREEXISTING_SECRET\n');
  }
  const preload = join(root, 'offline-preload.mjs');
  writeFileSync(preload, `
    import {createRequire, syncBuiltinESMExports} from 'node:module';
    import {writeFileSync} from 'node:fs';
    import {join} from 'node:path';
    import {EventEmitter} from 'node:events';
    import {PassThrough} from 'node:stream';
    const require = createRequire(import.meta.url);
    require('node:child_process').spawn = (...args) => {
      const [, argv, options] = args;
      if (process.env.ISOLATION_COMPLETE_BUILD === '1' &&
          argv[0] === join(process.env.ISOLATION_FIXTURE_ROOT, 'functions/node_modules/typescript/bin/tsc')) {
        writeFileSync(join(process.env.ISOLATION_FIXTURE_ROOT, 'build-attempt'), 'simulated');
        if (process.env.ISOLATION_CHANGE_ALIAS === '1') {
          writeFileSync(join(process.env.ISOLATION_FIXTURE_ROOT, '.firebaserc'),
            '{"projects":{"demo-granaxp":"prod-fixture"}}');
        }
        const child = new EventEmitter();
        child.stdout = new PassThrough(); child.stderr = new PassThrough();
        process.nextTick(() => { child.stdout.end(); child.stderr.end(); child.emit('exit', 0, null); });
        return child;
      }
      writeFileSync(join(process.env.ISOLATION_FIXTURE_ROOT, 'child-attempt.json'),
        JSON.stringify({argv, cwd: options.cwd, project: options.env.GCLOUD_PROJECT}));
      throw new Error('${sentinel}');
    };
    const blocked = () => {
      writeFileSync(join(process.env.ISOLATION_FIXTURE_ROOT, 'network-attempt'), 'unexpected');
      throw new Error('Offline test attempted network access');
    };
    for (const module of ['node:http', 'node:https']) {
      require(module).request = blocked; require(module).get = blocked;
    }
    require('node:net').connect = blocked;
    require('node:tls').connect = blocked;
    require('node:dns').lookup = blocked;
    globalThis.fetch = blocked;
    syncBuiltinESMExports();
  `);
  const env = {...process.env, JAVA_HOME: join(root, 'jbr'), ISOLATION_FIXTURE_ROOT: root,
    GCLOUD_PROJECT: 'demo-granaxp', FIRESTORE_EMULATOR_HOST: '127.0.0.1:8080',
    FIREBASE_AUTH_EMULATOR_HOST: '127.0.0.1:9099', FUNCTIONS_EMULATOR_PORT: '5001',
    GRANAXP_TEST_ACCESS_MODE: 'open', GRANAXP_TEST_INVALID_KEYRING: '0',
    ISOLATION_COMPLETE_BUILD: completeBuild ? '1' : '0', ISOLATION_CHANGE_ALIAS: changeAlias ? '1' : '0'};
  const result = spawnSync(process.execPath, ['--import', pathToFileURL(preload).href,
    join(root, 'scripts/functions-emulator-runner.mjs'), 'functions'], {cwd: tmpdir(), env,
    encoding: 'utf8', timeout: 10_000, windowsHide: true});
  assert.equal(result.error, undefined);
  assert.equal(result.status, 1); // The boundary stops even an admitted runner before a real child starts.
  assert.equal(existsSync(join(root, 'network-attempt')), false);
  if (rc !== undefined && rc !== 'directory') assert.equal(readFileSync(join(root, '.firebaserc'), 'utf8'), rc);
  if (preserve) {
    assert.equal(readFileSync(join(root, 'functions/.env.local'), 'utf8'), 'PREEXISTING_CONFIG\n');
    assert.equal(readFileSync(join(root, 'functions/.secret.local'), 'utf8'), 'PREEXISTING_SECRET\n');
  } else {
    assert.equal(existsSync(join(root, 'functions/.env.local')), false);
    assert.equal(existsSync(join(root, 'functions/.secret.local')), false);
  }
  return {root, result};
}

// Removing the project preflight or moving it after spawn must make these refusal tests fail.
for (const [name, rc] of [
  ['alias para outro projeto', '{"projects":{"demo-granaxp":"prod-fixture"}}'],
  ['JSON inválido sem revelar conteúdo', '{"credential":"SECRET_FIXTURE_MUST_NOT_LEAK",'],
  ['arquivo vazio', ''],
  ['raiz em lista', '[]'],
  ['projects nulo', '{"projects":null}'],
  ['projects em lista', '{"projects":[]}'],
  ['alias não textual', '{"projects":{"demo-granaxp":123}}'],
  ['alias vazio', '{"projects":{"demo-granaxp":""}}'],
  ['projects duplicado', '{"projects":{"demo-granaxp":"prod-fixture"},"projects":{}}'],
  ['alias duplicado com escape', '{"projects":{"demo-granaxp":"prod-fixture","demo\\u002dgranaxp":"demo-granaxp"}}'],
  ['caminho RC não regular', 'directory'],
]) {
  test(`isolamento recusa ${name} antes de build, filhos, fixtures e rede`, (t) => {
    const {root, result} = fixture(t, rc);
    assert.equal(existsSync(join(root, 'child-attempt.json')), false, result.stderr);
    assert.match(result.stderr, /Isolamento:/);
    assert.ok(!result.stderr.includes('SECRET_FIXTURE_MUST_NOT_LEAK'));
  });
}

for (const [name, rc] of [
  ['demo sem RC', undefined],
  ['demo sem alias conflitante e default real ignorado', '{"projects":{"default":"prod-fixture"}}'],
  ['alias identidade demo', '{"projects":{"demo-granaxp":"demo-granaxp"}}'],
  ['CJSON e BOM conforme parser instalado', '\ufeff{/* local */"projects":{"demo-granaxp":"demo-granaxp"}}'],
]) {
  test(`isolamento admite ${name}, independentemente do cwd do invocador`, (t) => {
    const {root, result} = fixture(t, rc);
    assert.match(result.stderr, new RegExp(sentinel));
    const {argv, cwd, project} = JSON.parse(readFileSync(join(root, 'child-attempt.json'), 'utf8'));
    assert.match(argv[0], /typescript[\\/]bin[\\/]tsc$/);
    assert.equal(cwd, root);
    assert.equal(project, 'demo-granaxp');
  });
}

test('isolamento recusa alias conflitante e preserva configuração/segredo preexistentes', (t) => {
  const {root, result} = fixture(t, '{"projects":{"demo-granaxp":"prod-fixture"}}', {preserve: true});
  assert.equal(existsSync(join(root, 'child-attempt.json')), false);
  assert.match(result.stderr, /Isolamento:/);
});

test('isolamento recusa versão de CLI não inspecionada antes de qualquer filho', (t) => {
  const {root, result} = fixture(t, undefined, {version: '0.0.0'});
  assert.equal(existsSync(join(root, 'child-attempt.json')), false);
  assert.match(result.stderr, /Isolamento:/);
});

test('isolamento fixa config e projeto no lançamento admitido e limpa suas fixtures', (t) => {
  const {root, result} = fixture(t, undefined, {completeBuild: true});
  assert.match(result.stderr, new RegExp(sentinel));
  const {argv, cwd, project} = JSON.parse(readFileSync(join(root, 'child-attempt.json'), 'utf8'));
  assert.deepEqual(argv.slice(1, -1), ['emulators:exec', '--config', join(root, 'firebase.json'),
    '--only', 'auth,firestore,functions', '--project', 'demo-granaxp']);
  assert.equal(cwd, root);
  assert.equal(project, 'demo-granaxp');
});

test('isolamento revalida alias alterado durante build simulado antes da CLI', (t) => {
  const {root, result} = fixture(t, undefined, {completeBuild: true, changeAlias: true});
  assert.equal(existsSync(join(root, 'build-attempt')), true);
  assert.equal(existsSync(join(root, 'child-attempt.json')), false);
  assert.match(result.stderr, /Isolamento:/);
});
