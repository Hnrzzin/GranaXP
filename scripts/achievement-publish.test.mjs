import assert from 'node:assert/strict';
import {test} from 'node:test';
import {readFileSync,mkdtempSync,writeFileSync} from 'node:fs';
import {tmpdir} from 'node:os';
import {join} from 'node:path';
import {spawnSync} from 'node:child_process';
import net from 'node:net';
import tls from 'node:tls';
import {hash} from './educational-publication.mjs';
let attempts=0;
const deny=()=>{attempts++;throw Error('NETWORK_FORBIDDEN');};
net.Socket.prototype.connect=deny;tls.connect=deny;globalThis.fetch=deny;
for(const k of Object.keys(process.env))if(/PROXY|EMULATOR|GOOGLE_|GCLOUD|GCE_|FIREBASE_|CLOUDSDK_/i.test(k))delete process.env[k];
const m=await import('./achievement-publish.mjs').catch(e=>{if(e.code!=='ERR_MODULE_NOT_FOUND')throw e;return null;});
const proposal=()=>JSON.parse(readFileSync(process.env.GRANAXP_ACHIEVEMENT_PROPOSAL,'utf8'));
const clone=x=>structuredClone(x);
const p0=proposal(),target=p0.target,principal=p0.principal;
const options=()=>({...target,principal,credentialFile:join(tmpdir(),'synthetic-never-read.json'),adminWindow:'exclusive',confirm:`PUBLISH_ACHIEVEMENTS/granaxp-d7927/(default)/${p0.digest}`});
const resource='projects/granaxp-d7927/databases/(default)',base=`https://firestore.googleapis.com/v1/${resource}`,crm='https://cloudresourcemanager.googleapis.com/v1/projects/granaxp-d7927';
const version=t=>{const[s,n]=t.split('.');return new Date(Number(s)*1000).toISOString().slice(0,19)+'.'+n+'Z';};
function encode(fields){return Object.fromEntries(Object.entries(fields).map(([k,v])=>[k,v===null?{nullValue:null}:typeof v==='string'?{stringValue:v}:Number.isInteger(v)?{integerValue:String(v)}:typeof v==='boolean'?{booleanValue:v}:{doubleValue:v}]));}
const credentials={type:'authorized_user',client_id:'SYNTHETIC.apps.googleusercontent.com',client_secret:'SYNTHETIC',refresh_token:'SYNTHETIC'};
function fake({identity=principal,permissions,beforeCommit,afterCommit,fail,badMetadata,tokenInfo={},pageSize=100,pageTokenBad=false}={}){
 const p=proposal(),state=new Map([...p.before.documents,...p.dependencies.documents].map(d=>[d.path,clone(d)]));
 state.set('users/PRIVATE_SENTINEL',{path:'users/PRIVATE_SENTINEL',fields:{xp:77},updateTime:'1.000000001'});
 const transactions=new Map(),calls=[],commits=[],events=[];let serial=0;
 const reply=(data,status=200)=>({ok:status===200,status,json:async()=>clone(data)});
 const doc=d=>({name:`${resource}/documents/${d.path}`,fields:encode(d.fields),updateTime:version(d.updateTime)});
 const deps={loadCredentials:()=>clone(credentials),onEvent:e=>events.push(clone(e)),oauthAdapter:async opts=>{
  assert.equal(opts.retry,false);assert.equal(opts.maxRedirects,0);assert.equal(opts.method,'POST');
  assert.ok(['https://oauth2.googleapis.com/token','https://oauth2.googleapis.com/tokeninfo'].includes(String(opts.url)));
  const data=String(opts.url).endsWith('/token')?{access_token:'SYNTHETIC_ACCESS',expires_in:3600,token_type:'Bearer'}:{email:identity,email_verified:true,expires_in:3600,scope:'https://www.googleapis.com/auth/cloud-platform',...tokenInfo};
  return {data,status:200,headers:{},config:opts};
 },fetchImpl:async(url,init)=>{
  assert.equal(init.redirect,'error');assert.equal(init.headers.Authorization,'Bearer SYNTHETIC_ACCESS');
  const body=init.body?JSON.parse(init.body):undefined,u=new URL(url);calls.push({url,method:init.method,body});
  const intercepted=fail?.({url,body,state,calls,commits,reply});if(intercepted)return intercepted;
  if(url===crm)return reply(badMetadata==='project'?{}:{projectId:target.project,projectNumber:target.projectNumber,lifecycleState:'ACTIVE'});
  if(url===base)return reply(badMetadata==='database'?{}:{name:resource,uid:target.databaseUid,type:'FIRESTORE_NATIVE',databaseEdition:'STANDARD',locationId:'southamerica-east1'});
  if(url===`${crm}:testIamPermissions`)return reply({permissions:permissions??body.permissions});
  if(url===`${base}/documents:beginTransaction`){const id=Buffer.from('tx-'+(++serial)).toString('base64');transactions.set(id,{snapshot:clone(state),readOnly:!!body.options.readOnly,reads:new Set()});return reply({transaction:id});}
  if(url===`${base}/documents:rollback`){assert.ok(transactions.has(body.transaction));transactions.delete(body.transaction);return reply({});}
  if(url===`${base}/documents:commit`){
   const tx=transactions.get(body.transaction);assert.ok(tx&&!tx.readOnly);assert.equal(body.writes.length,5);
   beforeCommit?.({state,body,tx});
   if([...tx.reads].some(path=>hash(state.get(path)??null)!==hash(tx.snapshot.get(path)??null)))return reply({},409);
   if(body.writes.some(w=>state.has(w.update.name.slice(`${resource}/documents/`.length))))return reply({},409);
   const result={commitTime:'2026-10-19T20:00:00.000000001Z',writeResults:body.writes.map(()=>({updateTime:'2026-10-19T20:00:00.000000001Z'}))};
   for(const w of body.writes){assert.deepEqual(w.currentDocument,{exists:false});assert.deepEqual(Object.keys(w).sort(),['currentDocument','update']);const path=w.update.name.slice(`${resource}/documents/`.length);const expected=p.operations.find(o=>o.path===path);assert.ok(expected);assert.deepEqual(w.update.fields,encode(expected.fields));state.set(path,{path,fields:clone(expected.fields),updateTime:'1792440000.000000001'});}
   commits.push(clone(body));transactions.delete(body.transaction);return afterCommit?.({state,result,reply})??reply(result);
  }
  assert.equal(init.method,'GET');const collection=u.pathname.split('/').at(-1);assert.ok(['achievements','modules','lessons'].includes(collection));
  const tx=transactions.get(u.searchParams.get('transaction'));assert.ok(tx);
  assert.equal(u.searchParams.get('showMissing'),'true');assert.equal(u.searchParams.get('pageSize'),'100');
  // Include absent allowed IDs in the synthetic conflict model; native transaction semantics remain a service guarantee.
  for(const path of [...p.before.documents,...p.dependencies.documents,...p.operations].map(d=>d.path))if(path.startsWith(collection+'/'))tx.reads.add(path);
  const docs=[...tx.snapshot.values()].filter(d=>d.path.split('/')[0]===collection).sort((a,b)=>a.path.localeCompare(b.path));
  const start=Number(u.searchParams.get('pageToken')??0),slice=docs.slice(start,start+pageSize),next=start+slice.length<docs.length?String(start+slice.length):undefined;
  return reply({documents:slice.map(doc),...(next?{nextPageToken:pageTokenBad?'1':next}:{})});
 }};
 return {state,calls,commits,events,deps};
}
const publish=f=>m.publishAchievements(proposal(),options(),f.deps);
test('executor exists and validates the exact reviewed proposal',()=>{assert.ok(m,'isolated executor missing');assert.equal(m.validateProposal(proposal()).digest,p0.digest);assert.equal(m.buildWrites(proposal()).length,5);});
test('one atomic commit creates only five candidates and preserves historical, dependencies and private sentinel',async()=>{assert.ok(m);const f=fake(),before=clone(f.state);const result=await publish(f);assert.equal(result.status,'PUBLISHED_VERIFIED');assert.equal(result.writes,5);assert.equal(f.commits.length,1);for(const[path,d]of before)assert.deepEqual(f.state.get(path),d);assert.ok(f.calls.every(c=>!c.url.includes('/users/')));assert.ok(f.events.some(e=>e.stage==='ACHIEVEMENTS_RECONCILED'));});
test('read-only preflight verifies create permission without commit or readWrite transaction',async()=>{assert.ok(m);const f=fake();assert.equal((await m.preflightAchievements(proposal(),options(),f.deps)).status,'READY');assert.equal(f.commits.length,0);assert.ok(f.calls.every(c=>!c.body?.options?.readWrite));assert.ok(f.calls.find(c=>c.url.endsWith(':testIamPermissions')).body.permissions.includes('datastore.entities.create'));});
test('reconciliation and already-applied publication never repeat writes',async()=>{assert.ok(m);const f=fake();await publish(f);assert.equal((await m.reconcileAchievements(proposal(),options(),f.deps)).status,'APPLIED');assert.equal((await publish(f)).status,'ALREADY_APPLIED');assert.equal(f.commits.length,1);});
test('publication journals a terminal result inside its protected commit context',async()=>{assert.ok(m);const f=fake();await publish(f);assert.equal(f.events.at(-1).stage,'ACHIEVEMENTS_RECONCILED');await publish(f);assert.equal(f.events.at(-1).stage,'ACHIEVEMENTS_ALREADY_APPLIED');});
test('adulterated fields, operations, paths, metadata, digest and recomputed digest fail before authentication',async()=>{assert.ok(m);const corrupt=proposal();corrupt.digest='0'.repeat(64);assert.throws(()=>m.validateProposal(corrupt));for(const mutate of [p=>p.operations[0].fields.title='ADULTERADO',p=>p.operations.push(p.operations[0]),p=>p.operations[0].path='users/private',p=>p.operations[0].operation='update',p=>p.before.documents[0].fields.icon='changed',p=>p.dependencies.documents[0].updateTime='1.000000001',p=>p.target.database='other',p=>p.editorialApproval.executionAuthorized=true]){const p=proposal();mutate(p);for(const recalc of [false,true]){if(recalc){const{digest,...body}=p;p.digest=hash(body);}let loaded=false;await assert.rejects(m.publishAchievements(p,options(),{loadCredentials:()=>{loaded=true;throw Error();}}));assert.equal(loaded,false);}}});
test('accessor inputs rejected before evaluating getter',()=>{assert.ok(m);let read=false;const p=proposal();Object.defineProperty(p,'operations',{enumerable:true,get(){read=true;return p0.operations;}});assert.throws(()=>m.validateProposal(p));assert.equal(read,false);});
test('target, identity, confirmation and window are mandatory',async()=>{assert.ok(m);for(const patch of [{project:'other'},{database:'other'},{projectNumber:'0'},{databaseUid:'wrong'},{principal:'other@example.test'},{confirm:'wrong'},{adminWindow:'shared'},{credentialFile:'relative.json'}]){const f=fake();await assert.rejects(m.publishAchievements(proposal(),{...options(),...patch},f.deps));assert.equal(f.calls.length,0);}});
test('OAuth identity, verification, scope and expiry fail closed',async()=>{assert.ok(m);for(const cfg of [{identity:'wrong@example.test'},{tokenInfo:{email_verified:false}},{tokenInfo:{scope:'openid'}},{tokenInfo:{expires_in:1}}]){const f=fake(cfg);await assert.rejects(publish(f));assert.equal(f.calls.length,0);}});
test('project, database metadata and missing permissions stop without commit',async()=>{assert.ok(m);for(const cfg of [{badMetadata:'project'},{badMetadata:'database'},{permissions:[]}]){const f=fake(cfg);await assert.rejects(publish(f));assert.equal(f.commits.length,0);}});
test('proxy and ADC overrides are rejected before requests',async()=>{assert.ok(m);for(const key of ['HTTPS_PROXY','GOOGLE_APPLICATION_CREDENTIALS','FIRESTORE_EMULATOR_HOST','GCE_METADATA_HOST']){process.env[key]='forbidden';try{const f=fake();await assert.rejects(publish(f));assert.equal(f.calls.length,0);}finally{delete process.env[key];}}});
test('changed history or learning dependency versions stop before commit',async()=>{assert.ok(m);for(const path of [p0.before.documents[0].path,p0.dependencies.documents[0].path]){const f=fake();f.state.get(path).updateTime='1.000000001';await assert.rejects(publish(f),{code:'PRECONDITIONS_CHANGED'});assert.equal(f.commits.length,0);}});
test('partial, conflicting and extra catalogs are never completed silently',async()=>{assert.ok(m);for(const kind of ['partial','conflict','extra']){const f=fake();if(kind==='extra')f.state.set('achievements/EXTRA',{path:'achievements/EXTRA',fields:{title:'unknown'},updateTime:'1.000000001'});else{const op=p0.operations[0];f.state.set(op.path,{path:op.path,fields:kind==='partial'?clone(op.fields):{...op.fields,title:'wrong'},updateTime:'1.000000001'});}await assert.rejects(publish(f));assert.equal(f.commits.length,0);}});
test('concurrent candidate creation, historical change or dependency edit aborts atomically',async()=>{assert.ok(m);for(const path of [p0.operations[0].path,p0.before.documents[0].path,p0.dependencies.documents[0].path]){const f=fake({beforeCommit:({state})=>{if(state.has(path))state.get(path).updateTime='2.000000001';else state.set(path,{path,fields:p0.operations[0].fields,updateTime:'2.000000001'});}});await assert.rejects(publish(f),e=>e.code==='COMMIT_UNCERTAIN'&&e.observed?.status==='DIVERGED');assert.equal(f.commits.length,0);assert.equal(f.calls.filter(c=>c.url.endsWith(':commit')).length,1);}});
test('lost or malformed commit response reconciles by reading and never retries',async()=>{assert.ok(m);for(const afterCommit of [()=>{throw Error('lost');},({reply})=>reply({}),({reply,result})=>reply({...result,writeResults:[]}),({reply,result})=>reply({...result,writeResults:result.writeResults.map(()=>({updateTime:'invalid'}))})]){const f=fake({afterCommit});await assert.rejects(publish(f),e=>e.code==='COMMIT_UNCERTAIN'&&e.observed?.status==='APPLIED');assert.equal(f.commits.length,1);assert.equal(f.calls.filter(c=>c.url.endsWith(':commit')).length,1);}});
test('failed reconciliation returns unknown after uncertain commit',async()=>{assert.ok(m);const f=fake({afterCommit:()=>{throw Error('lost');},fail:({url,commits,reply})=>commits.length&&url.endsWith(':beginTransaction')?reply({},503):null});await assert.rejects(publish(f),e=>e.code==='COMMIT_UNCERTAIN'&&e.observed?.status==='UNKNOWN');assert.equal(f.commits.length,1);});
test('postcommit mutation or mismatched versions fails final reconciliation',async()=>{assert.ok(m);for(const path of [p0.operations[0].path,p0.before.documents[0].path,p0.dependencies.documents[0].path]){const f=fake({afterCommit:({state})=>{state.get(path).updateTime='3.000000001';}});await assert.rejects(publish(f),{code:'RECONCILIATION_DIVERGED'});assert.equal(f.commits.length,1);}});
test('journal failure before commit prevents writes; after commit returns observed status',async()=>{assert.ok(m);for(const stage of ['ACHIEVEMENTS_COMMIT_SENDING','ACHIEVEMENTS_COMMIT_ACKNOWLEDGED','ACHIEVEMENTS_RECONCILED']){const f=fake();f.deps.onEvent=e=>{if(e.stage===stage)throw Error('disk');};await assert.rejects(publish(f),e=>e.code==='JOURNAL_FAILED'&&(stage==='ACHIEVEMENTS_COMMIT_SENDING'||e.observed?.status==='APPLIED'));assert.equal(f.commits.length,stage==='ACHIEVEMENTS_COMMIT_SENDING'?0:1);}});
test('missing or invalid journal sink is rejected before authentication',async()=>{assert.ok(m);for(const sink of [undefined,null,'not-a-function']){const f=fake();f.deps.onEvent=sink;let loaded=false;f.deps.loadCredentials=()=>{loaded=true;return credentials;};await assert.rejects(publish(f),{code:'JOURNAL_REQUIRED'});assert.equal(loaded,false);assert.equal(f.commits.length,0);}});
test('acknowledged commit followed by failed verification includes read-only observed status',async()=>{assert.ok(m);let failed=false;const f=fake({fail:({url,commits,reply})=>{if(commits.length&&url.endsWith(':beginTransaction')&&!failed){failed=true;return reply({},503);}return null;}});await assert.rejects(publish(f),e=>e.code==='REMOTE_REQUEST_FAILED'&&e.observed?.status==='APPLIED');assert.equal(f.commits.length,1);assert.equal(f.calls.filter(c=>c.url.endsWith(':commit')).length,1);});
test('pagination is complete and repeated tokens are refused',async()=>{assert.ok(m);const f=fake({pageSize:1});assert.equal((await publish(f)).writes,5);const bad=fake({pageSize:1,pageTokenBad:true});await assert.rejects(publish(bad));assert.equal(bad.commits.length,0);});
test('invalid, orphan and duplicate server documents stop before commit',async()=>{assert.ok(m);for(const type of ['orphan','duplicate','unsupported']){const f=fake({fail:({url,reply})=>{if(!url.includes('/documents/achievements?'))return null;const name=`${resource}/documents/${p0.before.documents[0].path}`;const doc={name,fields:encode(p0.before.documents[0].fields),updateTime:'2026-10-19T00:00:00Z'};return reply({documents:type==='orphan'?[{name}]:type==='duplicate'?[doc,doc]:[{...doc,fields:{secret:{bytesValue:'AA=='}}}]});}});await assert.rejects(publish(f));assert.equal(f.commits.length,0);}});
test('rollback failure stops without commit',async()=>{assert.ok(m);const f=fake({fail:({url,reply})=>url.endsWith(':rollback')?reply({},503):null});await assert.rejects(publish(f));assert.equal(f.commits.length,0);});
test('CLI verify is offline and publish without final authorization cannot load credentials',()=>{assert.ok(m);const tmp=mkdtempSync(join(tmpdir(),'granaxp-achievements-')),guard=join(tmp,'deny.cjs');writeFileSync(guard,"require('net').Socket.prototype.connect=()=>{throw Error('NETWORK_FORBIDDEN')};globalThis.fetch=()=>{throw Error('NETWORK_FORBIDDEN')};");const args=['--require',guard,'scripts/achievement-publish.mjs'];const run=tail=>spawnSync(process.execPath,[...args,...tail],{encoding:'utf8',env:{...process.env},windowsHide:true});const v=run(['verify','--proposal',process.env.GRANAXP_ACHIEVEMENT_PROPOSAL]);assert.equal(v.status,0,v.stderr);assert.equal(JSON.parse(v.stdout).writes,5);const bad=run(['publish','--proposal',process.env.GRANAXP_ACHIEVEMENT_PROPOSAL]);assert.equal(bad.status,1);assert.match(bad.stderr,/FINAL_AUTHORIZATION_REQUIRED/);});
test('all tests used zero external sockets or fetches',()=>assert.equal(attempts,0));
