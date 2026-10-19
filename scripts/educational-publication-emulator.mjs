// E6.6 rehearsal adapter. Production execution is intentionally unavailable.
import assert from 'node:assert/strict';
import {createRequire} from 'node:module';
import {CONTROL,confirmation,createPlan,hash,scopePaths} from './educational-publication.mjs';
const requireFunctions=createRequire(new URL('../functions/package.json',import.meta.url));
export function assertEmulatorHost(host) {
  assert.ok(['127.0.0.1:18080','127.0.0.1:28080'].includes(host),
    'Only dedicated demo loopback ports 18080 and 28080 are permitted');
  assert.equal(process.env.FIRESTORE_EMULATOR_HOST,host,'Explicit emulator environment required');
  for(const name of ['GOOGLE_APPLICATION_CREDENTIALS','GOOGLE_OAUTH_ACCESS_TOKEN','FIREBASE_TOKEN','CLOUDSDK_AUTH_ACCESS_TOKEN'])
    assert.ok(!process.env[name],`Credential environment forbidden: ${name}`);
  for(const name of ['GCLOUD_PROJECT','GOOGLE_CLOUD_PROJECT'])
    assert.ok(!process.env[name]||process.env[name]==='demo-granaxp',`Unexpected project environment: ${name}`);
}
export async function connectEmulator(host) {
  assertEmulatorHost(host);
  const {Firestore}=requireFunctions('@google-cloud/firestore');
  return new Firestore({projectId:'demo-granaxp',host,ssl:false});
}
export async function readScope(db,reader,scope) {
  scopePaths(scope);
  if(scope==='learning') {
    // listDocuments includes missing parents with subcollections; a normal query does not.
    // This metadata scan is limited to root lessons and never reads user collections.
    const lessonPaths=new Set(scopePaths(scope).filter(p=>/^lessons\/[^/]+$/.test(p)));
    for(const ref of await db.collection('lessons').listDocuments())
      assert.ok(lessonPaths.has(ref.path),'Unexpected lesson parent, including orphan blocks');
  }
  const refs=scope==='daily'?[db.collection('dailyMissions')]:[
    db.collection('modules'),db.collection('lessons'),
    ...scopePaths('learning').filter(p=>/^lessons\/[^/]+$/.test(p)).map(p=>db.collection(`${p}/blocks`)),db.doc(CONTROL)];
  const results=await Promise.all(refs.map(ref=>reader===db?ref.get():reader.get(ref)));
  return {documents:results.flatMap(s=>s.docs??(s.exists?[s]:[])).map(d=>({path:d.ref.path,fields:d.data(),
    updateTime:`${d.updateTime.seconds}.${String(d.updateTime.nanoseconds).padStart(9,'0')}`})).sort((a,b)=>a.path.localeCompare(b.path))};
}
export async function applyEmulatorPlan(bundle,plan,options) {
  assert.equal(plan.target?.environment,'emulator','Production execution is blocked in E6.6');
  assert.equal(plan.target?.project,'demo-granaxp','Production project forbidden');
  assert.equal(options.confirm,confirmation(plan),'Exact target/scope/plan confirmation required');
  assert.equal(options.resourcesHash,hash(plan.resources),'Exact resource-list confirmation required');
  assert.ok(hash(plan)===hash(createPlan(bundle,plan.before,plan.target,plan.scope)),'Plan modified');
  assertEmulatorHost(options.host);
  const db=await connectEmulator(options.host);
  try {
    return await db.runTransaction(async transaction=>{
      const current=await readScope(db,transaction,plan.scope);
      assert.ok(hash(current)===hash(plan.before),'Plan stale or changed; preview again');
      const fresh=createPlan(bundle,current,plan.target,plan.scope);
      assert.equal(fresh.digest,plan.digest,'Plan stale or changed');
      for(const operation of fresh.operations) {
        const ref=db.doc(operation.path);
        if(operation.operation==='create')transaction.create(ref,operation.fields);
        else transaction.update(ref,operation.fields);
      }
      return {writes:fresh.operations.length,digest:fresh.digest};
    });
  } finally {await db.terminate();}
}
