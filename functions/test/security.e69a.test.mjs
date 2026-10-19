import assert from 'node:assert/strict';
import {beforeEach, test} from 'node:test';
import {initializeApp, getApps} from 'firebase-admin/app';
import {getFirestore, Timestamp, Transaction} from 'firebase-admin/firestore';

assert.equal(process.env.GCLOUD_PROJECT, 'demo-granaxp');
assert.equal(process.env.FIRESTORE_EMULATOR_HOST, '127.0.0.1:18080');
assert.ok(!process.env.GOOGLE_APPLICATION_CREDENTIALS && !process.env.FIREBASE_TOKEN);
const api = await import('../lib/index.js');
const db = getFirestore(getApps()[0] ?? initializeApp());
const names = ['openLesson','submitLessonActivity','completeLesson','createGoal','updateGoalProgress',
  'updateGoalDetails','deleteAccount','openDailyMission','submitDailyMissionAnswer'];
const allOpen = Object.fromEntries(names.map(name => [name, 'open']));
const call = (name, data, uid = 'owner') => api[name].run({auth:{uid}, data});
const goalData = (requestId, currentAmount = 0) => ({requestId, title:'Reserva', targetAmount:100, currentAmount, deadline:'CURTO'});
beforeEach(async () => {
  process.env.BACKEND_CALLABLE_MODES = JSON.stringify(allOpen);
  process.env.BACKEND_PILOT_UID = 'owner';
  process.env.ACHIEVEMENT_SYNC_MODE = 'open';
  await fetch('http://127.0.0.1:18080/emulator/v1/projects/demo-granaxp/databases/(default)/documents', {method:'DELETE'});
  await db.doc('users/owner').set({name:'Teste', email:'owner@example.test', level:1, xp:0, nextLevelXp:100});
});
async function authorize(goalId, overrides = {}) {
  const goal = (await db.doc(`users/owner/goals/${goalId}`).get()).data();
  await db.doc(`users/owner/goalRewardAuthorizations/${goalId}`).set({
    version:1, uid:'owner', goalId, conditionId:'verified-condition-1', targetAmount:100, verifiedAmount:100,
    goalCreatedAt:goal.createdAt ?? Timestamp.fromMillis(0), verifiedAt:Timestamp.now(),
    expiresAt:Timestamp.fromMillis(Date.now()+3600000), ...overrides,
  });
}
test('e69a: criar metas concluídas com requests novos nunca fabrica elegibilidade', async () => {
  for (const requestId of ['new-request-1','new-request-2','new-request-1']) {
    assert.equal((await call('createGoal', {...goalData(requestId,100), rewardApproved:true, xpReward:999})).xpEarned, 0);
  }
  assert.equal((await db.doc('users/owner').get()).get('xp'),0);
});
test('e69a: primeira condição privada válida concede200 uma vez, concorrência e retry não duplicam', async () => {
  const {goalId} = await call('createGoal',goalData('valid-request-1',90));
  await authorize(goalId);
  const results = await Promise.all([1,2].map(() => call('updateGoalProgress',{goalId,amountToAdd:10})));
  assert.deepEqual(results.map(r=>r.xpEarned).sort((a,b)=>a-b),[0,200]);
  assert.equal((await call('updateGoalProgress',{goalId,amountToAdd:10,requestId:'another-request'})).xpEarned,0);
  assert.equal((await db.collection('users/owner/goalRewardClaims').get()).size,1);
  const owner = (await db.doc('users/owner').get()).data();
  assert.deepEqual([owner.level,owner.xp],[2,100]);
});
test('e69a: condição consumida não pode ser reutilizada em outra meta com outro requestId', async () => {
  for (const requestId of ['eligible-request-1','eligible-request-2']) {
    const {goalId} = await call('createGoal',goalData(requestId,90));
    await authorize(goalId);
    const result = await call('updateGoalProgress',{goalId,amountToAdd:10});
    assert.equal(result.xpEarned,requestId.endsWith('1')?200:0);
  }
  assert.equal((await db.collection('users/owner/goalRewardClaims').get()).size,1);
});
test('e69a: duas metas concorrentes para a mesma condição concedem somente200XP no total', async () => {
  const goals = await Promise.all(['parallel-one','parallel-two'].map(requestId=>call('createGoal',goalData(requestId,90))));
  for (const {goalId} of goals) await authorize(goalId);
  const rewards=await Promise.all(goals.map(({goalId})=>call('updateGoalProgress',{goalId,amountToAdd:10})));
  assert.deepEqual(rewards.map(r=>r.xpEarned).sort((a,b)=>a-b),[0,200]);
  assert.equal((await db.collection('users/owner/goalRewardClaims').get()).size,1);
});
test('e69a: falha antes do commit não deixa meta, recibo ouXP parcialmente gravados', async t => {
  const {goalId}=await call('createGoal',goalData('atomic-request',90));
  await authorize(goalId);
  const goalBefore=await db.doc(`users/owner/goals/${goalId}`).get();
  const original=Transaction.prototype.update;
  t.mock.method(Transaction.prototype,'update',function(ref,...args){
    if(ref.path==='users/owner')throw Error('synthetic precommit failure');
    return original.call(this,ref,...args);
  });
  await assert.rejects(call('updateGoalProgress',{goalId,amountToAdd:10}),e=>e.code==='internal');
  t.mock.restoreAll();
  assert.deepEqual((await goalBefore.ref.get()).data(),goalBefore.data());
  assert.equal((await db.doc('users/owner').get()).get('xp'),0);
  assert.equal((await db.collection('users/owner/goalRewardClaims').get()).size,0);
  assert.equal((await call('updateGoalProgress',{goalId,amountToAdd:10})).xpEarned,200);
});
test('e69a: sem autorização, payload manipulado não concede200 e mantém gerenciamento', async () => {
  const {goalId} = await call('createGoal',goalData('unverified-request',90));
  assert.equal((await call('updateGoalProgress',{goalId,amountToAdd:10,verifiedAmount:100,conditionId:'spoof',rewardApproved:true})).xpEarned,0);
  assert.equal((await db.doc(`users/owner/goals/${goalId}`).get()).get('currentAmount'),100);
});
test('e69a: autorização inválida, expirada, alheia ou insuficiente nunca concedeXP', async () => {
  for (const [i, override] of [
    {uid:'other'}, {goalId:'other'}, {verifiedAmount:99}, {targetAmount:50},
    {expiresAt:Timestamp.fromMillis(0)}, {verifiedAt:Timestamp.fromMillis(Date.now()+3600000)},
    {goalCreatedAt:Timestamp.fromMillis(0)}, {conditionId:'../spoof'}, {version:2},
  ].entries()) {
    const {goalId} = await call('createGoal',goalData(`invalid-request-${i}`,90));
    await authorize(goalId,override);
    assert.equal((await call('updateGoalProgress',{goalId,amountToAdd:10})).xpEarned,0);
  }
});
test('e69a: meta legada ou já concluída não ganhaXP retroativo', async () => {
  await db.doc('users/owner/goals/old').set({title:'Histórico',targetAmount:100,currentAmount:90,deadline:'CURTO'});
  await authorize('old');
  assert.equal((await call('updateGoalProgress',{goalId:'old',amountToAdd:10})).xpEarned,0);
  const {goalId} = await call('createGoal',goalData('already-completed',100));
  await authorize(goalId);
  assert.equal((await call('updateGoalProgress',{goalId,amountToAdd:10})).xpEarned,0);
});
test('e69a: guard individual não herda open global nem modo de outra callable', async () => {
  process.env.BACKEND_ACCESS_MODE='open';
  process.env.BACKEND_CALLABLE_MODES=JSON.stringify({openDailyMission:'open',createGoal:'disabled',deleteAccount:'pilot'});
  assert.deepEqual(await call('openDailyMission',{deviceTimeMillis:0}),{available:false});
  await assert.rejects(call('createGoal',goalData('forbidden-request')),e=>e.code==='failed-precondition');
  await assert.rejects(call('deleteAccount',{},'other'),e=>e.code==='permission-denied');
  await assert.rejects(call('openLesson',{lessonId:'none'}),e=>e.code==='failed-precondition');
});
test('e69a: nove callables negam payload sem Auth mesmo em open', async () => {
  for (const name of names) await assert.rejects(api[name].run({data:{uid:'owner',userId:'owner'}}),e=>e.code==='unauthenticated');
});
test('e69a: contagem autoritativa concede conquista e eventos duplicados não reescrevem desbloqueio', async () => {
  const {syncCountAchievements} = await import('../lib/achievementAuthority.js');
  await db.doc('achievements/transactions-2').set({requirementType:'TRANSACTION_COUNT',requirementValue:2,category:'FINANCAS'});
  await db.doc('users/owner/transactions/one').set({title:'Uma'});
  await syncCountAchievements(db,'owner','TRANSACTION_COUNT');
  const ref=db.doc('users/owner/achievementProgress/transactions-2');
  assert.equal((await ref.get()).get('isUnlocked'),false);
  await db.doc('users/owner/transactions/two').set({title:'Duas'});
  await Promise.all([1,2].map(()=>syncCountAchievements(db,'owner','TRANSACTION_COUNT')));
  const unlocked=await ref.get();
  assert.equal(unlocked.get('isUnlocked'),true);
  assert.equal(unlocked.get('currentProgress'),2);
  await syncCountAchievements(db,'owner','TRANSACTION_COUNT');
  assert.equal((await ref.get()).updateTime.toMillis(),unlocked.updateTime.toMillis());
  await db.doc('users/owner/transactions/two').delete();
  await syncCountAchievements(db,'owner','TRANSACTION_COUNT');
  assert.equal((await ref.get()).get('isUnlocked'),true);
  assert.equal((await ref.get()).get('unlockedAt').toMillis(),unlocked.get('unlockedAt').toMillis());
  assert.equal((await db.doc('users/owner').get()).get('xp'),0);
  assert.equal((await db.doc('users/other/achievementProgress/transactions-2').get()).exists,false);
});
test('e69a: contagem de metas e histórico legado preservados sem promover flags de lição', async () => {
  const {syncCountAchievements} = await import('../lib/achievementAuthority.js');
  await db.doc('achievements/goals-1').set({requirementType:'GOAL_COUNT',requirementValue:1,category:'FINANCAS'});
  await db.doc('achievementProgress/historical').set({userId:'owner',achievementId:'goals-1',isUnlocked:true,currentProgress:1});
  const legacy=await db.doc('achievementProgress/historical').get();
  await call('createGoal',goalData('achievement-request'));
  await syncCountAchievements(db,'owner','GOAL_COUNT');
  assert.equal((await db.doc('users/owner/achievementProgress/goals-1').get()).get('isUnlocked'),true);
  assert.deepEqual((await db.doc('achievementProgress/historical').get()).data(),legacy.data());
  await db.doc('achievements/lessons-1').set({requirementType:'LESSON_COUNT',requirementValue:1,category:'EDUCACAO'});
  await db.doc('users/owner/lessonProgress/fake').set({lessonId:'fake',isCompleted:true,completionValidationVersion:3});
  await db.doc('learningCatalogControl/current').set({protocolVersion:1,generation:1});
  await db.doc('lessons/fake').set({title:'Teste',xpReward:50,order:0});
  await db.doc('lessons/fake/blocks/activity').set({type:'ACTIVITY',activityType:'MULTIPLE_CHOICE',alternatives:{a:'A',b:'B'},correctAnswerIds:['a'],feedback:'Teste'});
  const {proofActivityFromBlock, signLessonActivityProof}=await import('../lib/lessonActivityProofs.js');
  const activity=proofActivityFromBlock((await db.doc('lessons/fake/blocks/activity').get()).data(),['a']);
  const keyring={activeKeyId:'test',keys:new Map([['test',Buffer.alloc(32,7)]])};
  const options={keyring,activities:()=>[{id:'activity',activity}]};
  await syncCountAchievements(db,'owner','LESSON_COUNT',options);
  assert.equal((await db.doc('users/owner/achievementProgress/lessons-1').get()).get('isUnlocked'),false);
  const ref=db.doc('users/owner/lessonProgress/fake');
  const token=signLessonActivityProof({uid:'other',lessonId:'fake',activityId:'activity'},activity,keyring);
  await ref.update({activityValidationVersion:3,completedActivityIds:['activity'],validatedActivityIds:['activity'],activityValidationProofs:{activity:[token]}});
  await syncCountAchievements(db,'owner','LESSON_COUNT',options);
  assert.equal((await db.doc('users/owner/achievementProgress/lessons-1').get()).get('isUnlocked'),false);
  await ref.update({activityValidationProofs:{activity:[signLessonActivityProof({uid:'owner',lessonId:'fake',activityId:'activity'},activity,keyring)]}});
  await syncCountAchievements(db,'owner','LESSON_COUNT',options);
  assert.equal((await db.doc('users/owner/achievementProgress/lessons-1').get()).get('isUnlocked'),true);
});
test('e69a: exports de trigger respeitam contenção/piloto e não recriam usuário excluído', async () => {
  await db.doc('achievements/trans-1').set({requirementType:'TRANSACTION_COUNT',requirementValue:1,category:'FINANCAS'});
  await db.doc('users/owner/transactions/one').set({title:'Uma'});
  for (const mode of ['disabled','pilot']) {
    process.env.ACHIEVEMENT_SYNC_MODE=mode;process.env.BACKEND_PILOT_UID='other';
    await api.syncTransactionAchievements.run({params:{uid:'owner',documentId:'one'}});
    assert.equal((await db.doc('users/owner/achievementProgress/trans-1').get()).exists,false);
  }
  process.env.ACHIEVEMENT_SYNC_MODE='open';
  await api.syncTransactionAchievements.run({params:{uid:'owner',documentId:'one'}});
  assert.equal((await db.doc('users/owner/achievementProgress/trans-1').get()).get('isUnlocked'),true);
  await db.doc('users/owner').delete();
  await api.syncGoalAchievements.run({params:{uid:'owner',documentId:'missing'}});
  await api.syncLessonAchievements.run({params:{uid:'owner',documentId:'missing'}});
  assert.equal((await db.doc('users/owner').get()).exists,false);
});
test('e69a: flag v3 forjada em outra lição não concede conquista de módulo', async () => {
  await db.doc('learningCatalogControl/current').set({protocolVersion:1,generation:1});
  await db.doc('modules/mod').set({title:'Módulo',order:0});
  for(const [i,id] of ['first','second'].entries()) {
    await db.doc(`lessons/${id}`).set({title:id,moduleId:'mod',order:i,xpReward:50});
    await db.doc(`lessons/${id}/blocks/a`).set({type:'ACTIVITY',order:0,activityType:'MULTIPLE_CHOICE',alternatives:{a:'A',b:'B'},correctAnswerIds:['a'],feedback:'Teste'});
  }
  await db.doc('achievements/module').set({requirementType:'MODULE_COMPLETION',referenceId:'mod',requirementValue:1,category:'EDUCACAO'});
  await db.doc('users/owner/lessonProgress/second').set({lessonId:'second',isCompleted:true,completionValidationVersion:3});
  await call('openLesson',{lessonId:'first'});
  await call('submitLessonActivity',{lessonId:'first',blockId:'a',selectedAnswerIds:['a']});
  assert.equal((await call('completeLesson',{lessonId:'first'})).xpEarned,50);
  assert.equal((await db.doc('users/owner/achievementProgress/module').get()).exists,false);
});
if(process.env.FUNCTIONS_EMULATOR_PORT==='15001') test('e69a: três triggers concedem conquistas por transporte Firestore real do emulador', async () => {
  for(const [id,kind,category] of [
    ['transport-t','TRANSACTION_COUNT','FINANCAS'],['transport-g','GOAL_COUNT','FINANCAS'],['transport-l','LESSON_COUNT','EDUCACAO'],
  ]) await db.doc(`achievements/${id}`).set({requirementType:kind,requirementValue:1,category});
  await db.doc('users/owner/transactions/transport').set({title:'Teste',amount:1});
  await call('createGoal',goalData('transport-goal-1'));
  await db.doc('learningCatalogControl/current').set({protocolVersion:1,generation:1});
  await db.doc('lessons/transport').set({title:'Teste',order:0,xpReward:50});
  await db.doc('lessons/transport/blocks/a').set({type:'ACTIVITY',order:0,activityType:'MULTIPLE_CHOICE',alternatives:{a:'A',b:'B'},correctAnswerIds:['a'],feedback:'Teste'});
  await call('openLesson',{lessonId:'transport'});
  await call('submitLessonActivity',{lessonId:'transport',blockId:'a',selectedAnswerIds:['a']});
  await call('completeLesson',{lessonId:'transport'});
  const limit=Date.now()+30000;
  let done=false;
  while(Date.now()<limit) {
    const states=await Promise.all(['transport-t','transport-g','transport-l'].map(id=>db.doc(`users/owner/achievementProgress/${id}`).get()));
    if(states.every(s=>s.get('isUnlocked')===true)){done=true;break;}
    await new Promise(resolve=>setTimeout(resolve,150));
  }
  assert.ok(done,'Actual Firestore delivery did not produce all three canonical achievements');
  assert.equal((await db.doc('users/owner').get()).get('xp'),50);
});
