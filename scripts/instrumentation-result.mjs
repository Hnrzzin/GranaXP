const environmentClass = 'com.hnrzzin.granaxp.FirebaseEnvironmentTest';
export const mandatoryTests = [
  'com.hnrzzin.granaxp.ExampleInstrumentedTest#useAppContext',
  `${environmentClass}#authenticationWorksThroughAdbLoopbackWithoutLocalNetworkPermission`,
  `${environmentClass}#startupSelectsOneCoherentEnvironmentBeforeRepositoriesRun`,
  'com.hnrzzin.granaxp.LessonCatalogIntegrationTest#realSdkDistinguishesEmptyLoadedAndFailedCatalogsAcrossTheLearningFlow',
];
export const sparkTests = [
  mandatoryTests[0],
  mandatoryTests[1],
  mandatoryTests[2],
  ...[
    'opensInNativeDialog',
    'closeButtonDismissesWithoutChangingUnderlyingState',
    'backDismissesAndReopeningKeepsSelectedAnswer',
    'submissionWaitsForConfirmationAndDisablesRepeatedInput',
    'incorrectFeedbackAllowsAnotherAttempt',
    'completedMissionShowsStreakAndNoRewardAction',
    'loadingErrorRetryAndUnavailableRemainInDialog',
    'longContentScrollsInsideDialogAndCloseRemainsAccessible',
  ].map(name => `com.hnrzzin.granaxp.DailyMissionModalTest#${name}`),
  ...[
    'showsOnlyCurrentBlockAndBackReviewsWithoutCompleting',
    'incorrectOrUnconfirmedCorrectFeedbackCannotAdvance',
    'lastActivityDoesNotCompleteOnViewingOrWrongAnswer',
    'completedLessonAllowsHistoricalReviewWithoutRewardCallback',
    'reopeningRetainsConfirmedActivitiesAndRestartsPresentation',
    'revokedConfirmationCannotLeaveCursorBeyondPendingActivity',
    'longBlockScrollDoesNotRevealNextBlockAndControlsRemainAvailable',
    'loadingErrorsOrMismatchedLessonNeverExposeCompletion',
  ].map(name => `com.hnrzzin.granaxp.LessonStepNavigationTest#${name}`),
  'com.hnrzzin.granaxp.LessonProgressiveIntegrationTest#threeLessonsAdvanceReopenValidateAndRewardOnlyOnCompletion',
  'com.hnrzzin.granaxp.SparkMissionIntegrationTest#wrongCorrectReopenAndReplayKeepOneCompletionAndZeroXp',
  'com.hnrzzin.granaxp.SparkGoalIntegrationTest#ownerCanManageGoalWithoutMintingXp',
];
export function expectedTests(mode) {
  if (mode === 'local') return [...mandatoryTests];
  if (mode === 'spark') return [...sparkTests];
  if (mode === 'normal') return [mandatoryTests[0], mandatoryTests[2]];
  throw new Error(`Invalid gate mode: ${mode}`);
}

export function currentRunLogcat(logcat, since) {
  return logcat.split(/\r?\n/)
    .filter(line => Number(line.match(/^\s*(\d+\.\d+)/)?.[1]) >= since).join('\n');
}

export function validateInstrumentation({stdout, stderr = '', exitCode, timedOut = false,
  token, mode, logcat = ''}) {
  const expected = expectedTests(mode);
  const proofMode = mode === 'spark' ? 'local' : mode;
  const reject = message => { throw new Error(`Instrumentation rejected: ${message}`); };
  if (timedOut || exitCode !== 0) reject('transport failure or timeout');
  if (!token) reject('missing execution token');
  if (/INSTRUMENTATION_FAILED:|shortMsg=|Process crashed|INSTRUMENTATION_ABORTED/.test(stdout + stderr)) {
    reject('instrumentation abort/crash');
  }
  if (/Crash of app com\.hnrzzin\.granaxp running instrumentation/.test(logcat)) reject('app crash in logcat');

  const started = new Map();
  const witnessed = new Set();
  const passed = new Set();
  let fields = {};
  let terminalCount = 0;
  let resultStream = false;
  for (const line of stdout.split(/\r?\n/)) {
    const field = line.match(/^INSTRUMENTATION_STATUS: ([^=]+)=(.*)$/);
    if (field) {
      if (terminalCount) reject('status after completion');
      fields[field[1]] = field[2];
      continue;
    }
    const status = line.match(/^INSTRUMENTATION_STATUS_CODE: (-?\d+)$/);
    if (status) {
      const code = Number(status[1]);
      if (code === 2) {
        const id = fields.granaxpGateProof;
        if (!started.has(id) || passed.has(id) || witnessed.has(id)
          || fields.granaxpGateToken !== token || fields.granaxpGateMode !== proofMode) {
          reject('missing, stale, duplicated or misplaced execution proof');
        }
        witnessed.add(id);
      } else {
        const id = `${fields.class}#${fields.test}`;
        if (!expected.includes(id) || Number(fields.numtests) !== expected.length) reject('unexpected test set');
        const index = Number(fields.current);
        if (code === 1) {
          if (started.has(id) || index !== started.size + 1) reject('duplicated or invalid test start');
          started.set(id, index);
        } else if (code === 0) {
          if (started.get(id) !== index || !witnessed.has(id) || passed.has(id)) reject('unproven test completion');
          passed.add(id);
        } else {
          reject(`test failed, errored or skipped (${id}, status ${code})`);
        }
      }
      fields = {};
      continue;
    }
    if (line.startsWith('INSTRUMENTATION_RESULT: stream=')) resultStream = true;
    const terminal = line.match(/^INSTRUMENTATION_CODE: (-?\d+)$/);
    if (terminal) {
      if (Number(terminal[1]) !== -1 || ++terminalCount !== 1) reject('abnormal or duplicate terminal result');
    }
  }
  if (Object.keys(fields).length || terminalCount !== 1 || !resultStream
    || started.size !== expected.length || passed.size !== expected.length
    || expected.some(id => !passed.has(id))) reject('zero, incomplete or insufficient execution evidence');
  return {tests: expected, mode, token};
}
