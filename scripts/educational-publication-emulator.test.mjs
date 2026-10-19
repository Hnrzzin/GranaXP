import assert from 'node:assert/strict';
import {readFileSync} from 'node:fs';
import {test} from 'node:test';
import {createPlan,confirmation,hash} from './educational-publication.mjs';
const m=await import('./educational-publication-emulator.mjs').catch(e=>{if(e.code!=='ERR_MODULE_NOT_FOUND')throw e;return null;});
const bundle=JSON.parse(readFileSync(process.env.GRANAXP_APPROVED_BUNDLE,'utf8'));
const plan=createPlan(bundle,{documents:[]},{project:'demo-granaxp',environment:'emulator'},'learning');
test('isolated transaction executor exists',()=>assert.equal(typeof m?.applyEmulatorPlan,'function'));
test('production, redirects, credentials and missing resource confirmation fail before SDK initialization',{skip:!m},async()=>{
  const good={host:'127.0.0.1:18080',confirm:confirmation(plan),resourcesHash:hash(plan.resources)};
  for(const [p,o] of [[{...plan,target:{project:'granaxp-d7927',environment:'production'}},good],
    [plan,{...good,host:'localhost:18080'}],[plan,{...good,host:'firestore.googleapis.com'}],
    [plan,{...good,confirm:'yes'}],[plan,{...good,resourcesHash:'wrong'}]])
    await assert.rejects(m.applyEmulatorPlan(bundle,p,o));
  const changed=structuredClone(plan);changed.operations[0].fields.title='tampered';
  await assert.rejects(m.applyEmulatorPlan(bundle,changed,good),/Plan modified/);
  const old=process.env.GOOGLE_APPLICATION_CREDENTIALS;
  process.env.GOOGLE_APPLICATION_CREDENTIALS='forbidden-credential.json';
  const oldHost=process.env.FIRESTORE_EMULATOR_HOST;process.env.FIRESTORE_EMULATOR_HOST='127.0.0.1:18080';
  try {assert.throws(()=>m.assertEmulatorHost('127.0.0.1:18080'),/Credential environment forbidden/);}
  finally {if(old===undefined)delete process.env.GOOGLE_APPLICATION_CREDENTIALS;else process.env.GOOGLE_APPLICATION_CREDENTIALS=old;
    if(oldHost===undefined)delete process.env.FIRESTORE_EMULATOR_HOST;else process.env.FIRESTORE_EMULATOR_HOST=oldHost;}
});
test('tampered-plan errors never serialize private catalogs',{skip:!m},async()=>{
  const changed=structuredClone(plan);changed.operations[0].fields.title='tampered';
  const error=await m.applyEmulatorPlan(bundle,changed,{host:'127.0.0.1:18080',confirm:confirmation(changed),resourcesHash:hash(changed.resources)}).catch(e=>e);
  assert.ok(error instanceof Error);
  assert.ok(!JSON.stringify(error).includes('correctAnswerIds'),'Private answer catalog leaked in error metadata');
});
test('Spark Rules expose educational answers but forbid catalog writes and XP forgery',
  {skip:!m||!process.env.GRANAXP_PUBLICATION_INTEGRATION},async()=>{
    const {initializeTestEnvironment,assertFails,assertSucceeds}=await import('@firebase/rules-unit-testing');
    const {doc,getDoc,setDoc,updateDoc}=await import('firebase/firestore');
    const adminDb=await m.connectEmulator(process.env.FIRESTORE_EMULATOR_HOST);
    await adminDb.doc('users/e66-rules-owner').create({name:'Local fixture',email:'fixture@example.test',level:1,xp:0,nextLevelXp:100});
    await adminDb.terminate();
    const env=await initializeTestEnvironment({projectId:'demo-granaxp',firestore:{host:'127.0.0.1',
      port:Number(process.env.FIRESTORE_EMULATOR_HOST.split(':')[1]),
      rules:readFileSync(new URL('../firestore.rules',import.meta.url),'utf8')}});
    try {
      const owner=env.authenticatedContext('e66-rules-owner').firestore();
      const other=env.authenticatedContext('e66-other').firestore();
      const anon=env.unauthenticatedContext().firestore();
      await assertSucceeds(getDoc(doc(owner,'modules/organizacao_financeira')));
      await assertSucceeds(getDoc(doc(owner,bundle.legacy[0].path)));
      await assertFails(getDoc(doc(anon,'modules/organizacao_financeira')));
      const blockPath=bundle.learning.find(x=>x.path.includes('/blocks/')&&x.fields.type==='ACTIVITY').path;
      await assertSucceeds(getDoc(doc(owner,blockPath)));
      await assertSucceeds(getDoc(doc(owner,bundle.daily[0].path)));
      await assertSucceeds(getDoc(doc(other,blockPath)));
      await assertFails(updateDoc(doc(owner,blockPath),{correctAnswerIds:['forged']}));
      await assertFails(updateDoc(doc(owner,bundle.daily[0].path),{correctAnswerIds:['forged']}));
      await assertSucceeds(updateDoc(doc(owner,'users/e66-rules-owner'),{name:'Allowed local edit'}));
      await assertFails(getDoc(doc(other,'users/e66-rules-owner')));
      await assertFails(updateDoc(doc(owner,'users/e66-rules-owner'),{xp:9999}));
      assert.equal((await getDoc(doc(owner,'users/e66-rules-owner'))).get('xp'),0);
      for(const p of ['lessonProgress/test','dailyMissionProgress/2026-10-14','learningStats/streak','achievementProgress/fake'])
        await assertFails(setDoc(doc(owner,`users/e66-rules-owner/${p}`),{isCompleted:true,xp:9999}));
    }finally{await env.cleanup();}
});
test('atomic publication, idempotence, stale-plan rejection, daily separation and untouched user sentinel',
  {skip:!m||!process.env.GRANAXP_PUBLICATION_INTEGRATION},async()=>{
    const host=process.env.FIRESTORE_EMULATOR_HOST;
    assert.ok(['127.0.0.1:18080','127.0.0.1:28080'].includes(host));
    const db=await m.connectEmulator(host);
    try {
      // This fresh emulator is exclusive to E6.6. Never clear a shared Suite.
      await db.doc('users/e66-sentinel').create({xp:123,level:2,isCompleted:true});
      const sentinel=await db.doc('users/e66-sentinel').get();
      for(const x of bundle.legacy)await db.doc(x.path).create(x.fields);
      const initial=await m.readScope(db,db,'learning');
      const p=createPlan(bundle,initial,{project:'demo-granaxp',environment:'emulator'},'learning');
      const opts={host,confirm:confirmation(p),resourcesHash:hash(p.resources)};
      assert.equal((await m.applyEmulatorPlan(bundle,p,opts)).writes,40);
      assert.equal((await db.doc('learningCatalogControl/current').get()).get('generation'),2);
      assert.equal((await db.doc(bundle.legacy[0].path).get()).get('category'),bundle.legacy[0].fields.category);
      const again=createPlan(bundle,await m.readScope(db,db,'learning'),p.target,'learning');
      assert.equal((await m.applyEmulatorPlan(bundle,again,{host,confirm:confirmation(again),resourcesHash:hash(again.resources)})).writes,0);
      const staleError=await m.applyEmulatorPlan(bundle,p,opts).catch(e=>e);
      assert.match(staleError.message,/stale|changed/i);
      assert.ok(!JSON.stringify(staleError).includes('correctAnswerIds'),'Private catalog leaked on stale plan');
      const daily=createPlan(bundle,await m.readScope(db,db,'daily'),p.target,'daily');
      assert.equal((await m.applyEmulatorPlan(bundle,daily,{host,confirm:confirmation(daily),resourcesHash:hash(daily.resources)})).writes,7);
      assert.equal((await db.doc('learningCatalogControl/current').get()).get('generation'),2);
      const after=await db.doc('users/e66-sentinel').get();
      assert.equal(after.updateTime.isEqual(sentinel.updateTime),true);
      assert.deepEqual(after.data(),sentinel.data());
      const raced=createPlan(bundle,await m.readScope(db,db,'daily'),p.target,'daily');
      await db.doc('dailyMissions/unexpected').create({order:99});
      await assert.rejects(m.applyEmulatorPlan(bundle,raced,{host,confirm:confirmation(raced),resourcesHash:hash(raced.resources)}));
      assert.equal((await db.collection('dailyMissions').get()).size,8);
    } finally {await db.terminate();}
});
test('orphan blocks under an unexpected lesson parent stop managed-catalog inventory',
  {skip:!m||!process.env.GRANAXP_PUBLICATION_INTEGRATION},async()=>{
    const db=await m.connectEmulator(process.env.FIRESTORE_EMULATOR_HOST);
    try {
      await db.doc('lessons/unexpected-orphan/blocks/extra').create({type:'ACTIVITY'});
      await assert.rejects(m.readScope(db,db,'learning'),/Unexpected lesson parent/);
    }finally{await db.terminate();}
});
