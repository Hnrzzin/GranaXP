import {spawn} from 'node:child_process';
import {randomUUID} from 'node:crypto';
import {appendFileSync, mkdirSync, readFileSync, writeFileSync} from 'node:fs';
import {dirname, join, resolve} from 'node:path';
import {fileURLToPath} from 'node:url';
import {currentRunLogcat, expectedTests, validateInstrumentation} from './instrumentation-result.mjs';

const root = resolve(dirname(fileURLToPath(import.meta.url)), '..');
const options = {adb: 'adb', mode: 'local'};
for (let index = 2; index < process.argv.length; index += 2) {
  const key = process.argv[index];
  const value = process.argv[index + 1];
  if (!['--serial', '--adb', '--mode'].includes(key) || !value) throw new Error(`Invalid option: ${key}`);
  options[key.slice(2)] = value;
}
if (!options.serial || !/^[\w.:-]+$/.test(options.serial)) throw new Error('Provide --serial explicitly.');
expectedTests(options.mode);
const token = randomUUID();
const logs = join(root, 'app/build/reports/instrumented-gate', `${options.mode}-${Date.now()}-${token}`);
mkdirSync(logs, {recursive: true});
writeFileSync(join(logs, 'request.json'), JSON.stringify({mode: options.mode, serial: options.serial,
  token, expected: expectedTests(options.mode), startedAt: new Date().toISOString()}, null, 2));

function run(command, args, name, timeout = 180_000) {
  return new Promise((resolveRun, reject) => {
    writeFileSync(join(logs, `${name}.stdout.log`), '');
    writeFileSync(join(logs, `${name}.stderr.log`), '');
    const child = spawn(command, args, {cwd: root, windowsHide: true, stdio: ['ignore', 'pipe', 'pipe']});
    let stdout = '', stderr = '', timedOut = false;
    child.stdout.setEncoding('utf8');
    child.stderr.setEncoding('utf8');
    child.stdout.on('data', chunk => { stdout += chunk; appendFileSync(join(logs, `${name}.stdout.log`), chunk); });
    child.stderr.on('data', chunk => { stderr += chunk; appendFileSync(join(logs, `${name}.stderr.log`), chunk); });
    const timer = setTimeout(() => { timedOut = true; child.kill(); }, timeout);
    child.on('error', error => { clearTimeout(timer); reject(error); });
    child.on('close', (exitCode, signal) => {
      clearTimeout(timer);
      writeFileSync(join(logs, `${name}.result.json`), JSON.stringify({command, args, exitCode, signal, timedOut}, null, 2));
      resolveRun({stdout, stderr, exitCode, timedOut, signal});
    });
  });
}
const adb = (args, name, timeout) => run(options.adb, ['-s', options.serial, ...args], name, timeout);
function requireSuccess(result, name) {
  if (result.exitCode !== 0 || result.timedOut) throw new Error(`${name} failed; inspect ${logs}`);
}

async function gate() {
  console.log(`Evidence: ${logs}`);
  const device = await adb(['get-state'], 'device');
  requireSuccess(device, 'Device check');
  if (device.stdout.trim() !== 'device') throw new Error('Device is unavailable.');
  if (options.mode === 'local') {
    const forwards = await adb(['reverse', '--list'], 'forwards');
    requireSuccess(forwards, 'ADB forwarding inventory');
    for (const port of [9099, 8080, 5001]) {
      if (!new RegExp(`^\\S+\\s+tcp:${port}\\s+tcp:${port}\\s*$`, 'm').test(forwards.stdout)) {
        throw new Error(`Missing GranaXP forwarding: ${port}`);
      }
    }
  }
  const java = process.env.JAVA_HOME
    ? join(process.env.JAVA_HOME, 'bin', process.platform === 'win32' ? 'java.exe' : 'java') : 'java';
  console.log(`Building debug APKs (${options.mode})...`);
  // Same wrapper as gradlew, without passing a Windows batch command through a shell.
  const build = await run(java, ['-Dorg.gradle.appname=gradlew', '-jar', join(root, 'gradle/wrapper/gradle-wrapper.jar'),
    `-PuseFirebaseEmulators=${options.mode === 'local'}`, ':app:assembleDebug',
    ':app:assembleDebugAndroidTest', '--console=plain'], 'gradle', 600_000);
  requireSuccess(build, 'Gradle build');
  for (const [name, path, testApk] of [
    ['install-app', 'app/build/outputs/apk/debug/app-debug.apk', false],
    ['install-tests', 'app/build/outputs/apk/androidTest/debug/app-debug-androidTest.apk', true],
  ]) {
    const install = await adb(['install', '-r', ...(testApk ? ['-t'] : []), join(root, path)], name);
    requireSuccess(install, name);
    if (!/^Success\s*$/m.test(install.stdout)) throw new Error(`${name} supplied no installation confirmation.`);
  }
  const clock = await adb(['shell', 'date', '+%s'], 'device-clock');
  requireSuccess(clock, 'Device clock');
  const since = Number(clock.stdout.trim());
  if (!/^\d+$/.test(clock.stdout.trim()) || !Number.isSafeInteger(since) || since <= 0) {
    throw new Error('Missing device clock evidence.');
  }
  writeFileSync(join(logs, 'logcat.log'), '');
  writeFileSync(join(logs, 'logcat.stderr.log'), '');
  const logcat = spawn(options.adb, ['-s', options.serial, 'logcat', '-v', 'epoch', '-T', '1',
    '-b', 'main', '-b', 'system', '-b', 'crash', 'AndroidRuntime:E', 'AndroidJUnitRunner:D',
    'TestRunner:I', 'ActivityManager:I', 'libc:F', 'DEBUG:F', '*:S'],
  {windowsHide: true, stdio: ['ignore', 'pipe', 'pipe']});
  let stoppingLogcat = false, earlyLogcatExit = false;
  logcat.stdout.on('data', chunk => appendFileSync(join(logs, 'logcat.log'), chunk));
  logcat.stderr.on('data', chunk => appendFileSync(join(logs, 'logcat.stderr.log'), chunk));
  const logcatClosed = new Promise(resolveClose => {
    logcat.on('error', () => { earlyLogcatExit = true; resolveClose(); });
    logcat.on('close', () => { if (!stoppingLogcat) earlyLogcatExit = true; resolveClose(); });
  });
  let result;
  try {
    console.log(`Executing ${expectedTests(options.mode).length} instrumented tests...`);
    // Local mode runs the whole package, without class/size/log discovery filters.
    const args = ['shell', 'am', 'instrument', '-r', '-w', '-e', 'granaxpGateToken', token,
      '-e', 'granaxpGateMode', options.mode];
    // Normal mode runs only the existing read-only configuration tests.
    if (options.mode === 'normal') args.push('-e', 'class', expectedTests('normal').join(','));
    args.push('com.hnrzzin.granaxp.test/androidx.test.runner.AndroidJUnitRunner');
    result = await adb(args, 'instrumentation');
    await new Promise(resolveDelay => setTimeout(resolveDelay, 250));
  } finally {
    stoppingLogcat = true;
    logcat.kill();
    await logcatClosed;
  }
  if (earlyLogcatExit) throw new Error('Logcat capture terminated prematurely.');
  const currentLogcat = currentRunLogcat(readFileSync(join(logs, 'logcat.log'), 'utf8'), since);
  const summary = validateInstrumentation({...result, token, mode: options.mode, logcat: currentLogcat});
  writeFileSync(join(logs, 'success.json'), JSON.stringify(summary, null, 2));
  console.log(`PASS: ${summary.tests.length} tests executed, witnessed and passed.`);
}

gate().catch(error => {
  writeFileSync(join(logs, 'failure.json'), JSON.stringify({message: error.message, stack: error.stack}, null, 2));
  console.error(`FAIL: ${error.message}\nEvidence: ${logs}`);
  process.exitCode = 1;
});
