import assert from 'node:assert/strict';
import {test} from 'node:test';
assert.equal(process.env.GCLOUD_PROJECT,'demo-granaxp');
assert.equal(process.env.FIRESTORE_EMULATOR_HOST,'127.0.0.1:18080');
assert.equal(process.env.FIREBASE_AUTH_EMULATOR_HOST,'127.0.0.1:19099');
assert.ok(!process.env.GOOGLE_APPLICATION_CREDENTIALS&&!process.env.FIREBASE_TOKEN);
const api=await import('../functions/lib/index.js');
const names=['openLesson','submitLessonActivity','completeLesson','openDailyMission','submitDailyMissionAnswer',
  'createGoal','updateGoalProgress','updateGoalDetails','deleteAccount'];
const request=uid=>({auth:uid?{uid,token:{}}:undefined,data:{deviceTimeMillis:0,userId:'spoof',uid:'spoof',
  BACKEND_ACCESS_MODE:'open',BACKEND_PILOT_UID:'spoof'}});
for(const [label,mode,pilot,uid,expected] of [
  ['disabled','disabled','owner','owner','failed-precondition'],
  ['default',undefined,'owner','owner','failed-precondition'],
  ['unknown','OPEN','owner','owner','failed-precondition'],
  ['pilot missing UID','pilot','','owner','failed-precondition'],
  ['pilot malformed UID','pilot',' owner ','owner','failed-precondition'],
  ['pilot other user','pilot','owner','other','permission-denied'],
  ['open still requires auth','open','','','unauthenticated'],
])test(`actual nine callable guards: ${label}`,async()=>{
  if(mode===undefined)delete process.env.BACKEND_CALLABLE_MODES;else process.env.BACKEND_CALLABLE_MODES=JSON.stringify(Object.fromEntries(names.map(name=>[name,mode])));
  process.env.BACKEND_PILOT_UID=pilot;
  for(const name of names) await assert.rejects(api[name].run(request(uid)),error=>error.code===expected,name);
});
for(const mode of ['pilot','open'])test(`actual ${mode} admission ignores client authority fields`,async()=>{
  process.env.BACKEND_CALLABLE_MODES=JSON.stringify(Object.fromEntries(names.map(name=>[name,mode])));process.env.BACKEND_PILOT_UID='owner';
  assert.deepEqual(await api.openDailyMission.run(request('owner')),{available:false});
});
