import assert from 'node:assert/strict';
import {test} from 'node:test';
import {currentRunLogcat, validateInstrumentation} from './instrumentation-result.mjs';

// Literal contract, independent of the validator's implementation.
const ids = [
  'com.hnrzzin.granaxp.ExampleInstrumentedTest#useAppContext',
  'com.hnrzzin.granaxp.FirebaseEnvironmentTest#authenticationWorksThroughAdbLoopbackWithoutLocalNetworkPermission',
  'com.hnrzzin.granaxp.FirebaseEnvironmentTest#startupSelectsOneCoherentEnvironmentBeforeRepositoriesRun',
  'com.hnrzzin.granaxp.LessonCatalogIntegrationTest#realSdkDistinguishesEmptyLoadedAndFailedCatalogsAcrossTheLearningFlow',
];
const token = 'fresh-execution-token';
const frame = (fields, code) => Object.entries(fields)
  .map(([key, value]) => `INSTRUMENTATION_STATUS: ${key}=${value}`).join('\n')
  + `\nINSTRUMENTATION_STATUS_CODE: ${code}\n`;
const record = (id, index, {proof = true, code = 0, proofToken = token} = {}) => {
  const [className, method] = id.split('#');
  const fields = {class: className, test: method, current: index + 1, numtests: 4};
  return frame(fields, 1)
    + (proof ? frame({granaxpGateProof: id, granaxpGateToken: proofToken, granaxpGateMode: 'local'}, 2) : '')
    + frame(fields, code);
};
const terminal = '\nINSTRUMENTATION_RESULT: stream=\nOK (4 tests)\nINSTRUMENTATION_CODE: -1\n';
const valid = ids.map((id, index) => record(id, index)).join('') + terminal;
const validate = (stdout, extra = {}) => validateInstrumentation({
  stdout, stderr: '', exitCode: 0, token, mode: 'local', ...extra,
});

test('passes only the four completed, witnessed tests', () => {
  assert.deepEqual(validate(valid).tests, ids);
});
test('rejects zero tests even with a successful summary', () => {
  assert.throws(() => validate(terminal));
});
test('rejects fewer than four tests', () => {
  assert.throws(() => validate(ids.slice(0, 3).map((id, i) => record(id, i)).join('') + terminal));
});
test('rejects incomplete execution without a terminal result', () => {
  assert.throws(() => validate(valid.replace(terminal, '')));
});
test('rejects a started test that never finishes', () => {
  assert.throws(() => validate(record(ids[0], 0).replace(/INSTRUMENTATION_STATUS_CODE: 0\n$/, '') + terminal));
});
test('rejects abort or crash even when ADB returns zero', () => {
  assert.throws(() => validate('INSTRUMENTATION_RESULT: shortMsg=Process crashed.\nINSTRUMENTATION_CODE: 0\n'));
  assert.throws(() => validate(valid + 'INSTRUMENTATION_FAILED: runner aborted\n'));
});
test('rejects a failed or skipped test', () => {
  for (const code of [-1, -2, -3, -4]) {
    assert.throws(() => validate(record(ids[0], 0, {code}) + ids.slice(1).map((id, i) => record(id, i + 1)).join('') + terminal));
  }
});
test('rejects listing-only output, even if it lists four successful tests', () => {
  assert.throws(() => validate(ids.map((id, i) => record(id, i, {proof: false})).join('') + terminal));
});
test('rejects stale evidence, duplicate tests and an incorrect mandatory set', () => {
  assert.throws(() => validate(ids.map((id, i) => record(id, i, {proofToken: 'older-run'})).join('') + terminal));
  assert.throws(() => validate(record(ids[0], 0) + valid));
  assert.throws(() => validate(valid.replaceAll(ids[3].split('#')[1], 'anotherMethod')));
});
test('rejects transport failure, timeout and an app crash in logcat', () => {
  assert.throws(() => validate(valid, {exitCode: 1}));
  assert.throws(() => validate(valid, {timedOut: true}));
  assert.throws(() => validate(valid, {logcat: 'Crash of app com.hnrzzin.granaxp running instrumentation'}));
});
test('keeps current epoch logcat despite leading spaces and excludes earlier crashes', () => {
  const logcat = '    100.100 1 1 W ActivityManager: Crash of app com.hnrzzin.granaxp running instrumentation\n'
    + '    200.100 2 2 W ActivityManager: Crash of app com.hnrzzin.granaxp running instrumentation';
  const current = currentRunLogcat(logcat, 200);
  assert.equal(current.split('\n').length, 1);
  assert.match(current, /200\.100/);
  assert.throws(() => validate(valid, {logcat: current}));
});
