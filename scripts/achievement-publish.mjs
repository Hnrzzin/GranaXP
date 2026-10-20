// Isolated five-create publisher. H, I and the learning/daily publisher remain unchanged.
import {readFileSync,realpathSync,existsSync,openSync,writeFileSync,fsyncSync,closeSync} from 'node:fs';
import {createRequire} from 'node:module';
import {dirname,isAbsolute,relative,resolve} from 'node:path';
import {fileURLToPath,pathToFileURL} from 'node:url';
import {parseArgs} from 'node:util';
import {hash} from './educational-publication.mjs';
import {validateAchievementDefinitions,HISTORICAL_ACHIEVEMENTS} from './achievement-publication.mjs';
import {decodeFields} from './catalog-readonly.mjs';

export const ACHIEVEMENT_PROPOSAL_DIGEST='14f9e0d048437253ed600f3823bcc4fade58799fa707d511a98d2bc70c0613e5';
const target=Object.freeze({project:'granaxp-d7927',database:'(default)',projectNumber:'136652496724',databaseUid:'81c02a82-fb39-40a3-b0b8-82439c8ba232'});
const resource='projects/granaxp-d7927/databases/(default)',base=`https://firestore.googleapis.com/v1/${resource}`;
const crm='https://cloudresourcemanager.googleapis.com/v1/projects/granaxp-d7927';
const requireFunctions=createRequire(new URL('../functions/package.json',import.meta.url));
const root=realpathSync(fileURLToPath(new URL('..',import.meta.url)));
class PublicationError extends Error {constructor(code,observed){super(code);this.code=code;if(observed)this.observed=observed;}}
const check=(ok,code)=>{if(!ok)throw new PublicationError(code);};
const same=(a,b)=>hash(a)===hash(b);
// Reject getters and non-JSON input before reading proposal properties, including async caller mutation.
function copy(value,seen=new Set()){
 if(value===null||['string','boolean'].includes(typeof value))return value;
 if(typeof value==='number'){check(Number.isFinite(value)&&(!Number.isInteger(value)||Number.isSafeInteger(value)),'JSON_INVALID');return value;}
 check(value&&typeof value==='object'&&!seen.has(value),'JSON_INVALID');seen.add(value);
 const array=Array.isArray(value),keys=Reflect.ownKeys(value);
 check(Object.getPrototypeOf(value)===(array?Array.prototype:Object.prototype),'JSON_INVALID');
 if(array)check(keys.length===value.length+1,'JSON_INVALID');
 const entries=[];
 for(const key of keys){if(array&&key==='length')continue;const d=Object.getOwnPropertyDescriptor(value,key);
  check(typeof key==='string'&&d.enumerable&&Object.hasOwn(d,'value')&&(!array||/^(0|[1-9]\d*)$/.test(key)),'JSON_INVALID');entries.push([key,copy(d.value,seen)]);}
 seen.delete(value);return array?entries.map(([,v])=>v):Object.fromEntries(entries);
}
const sorted=docs=>[...docs].sort((a,b)=>a.path.localeCompare(b.path));
const content=docs=>sorted(docs).map(({path,fields})=>({path,fields}));
export function validateProposal(input){
 try{
  const p=copy(input),{digest,...body}=p,m=validateAchievementDefinitions();
  check(digest===ACHIEVEMENT_PROPOSAL_DIGEST&&hash(body)===digest&&p.version===1&&
   p.kind==='SPARK_ACHIEVEMENT_PUBLICATION_PROPOSAL'&&same(p.target,target)&&p.manifestDigest===m.digest&&
   p.editorialApproval.status==='APPROVED_BY_OWNER'&&p.editorialApproval.executionAuthorized===false,'PROPOSAL_INVALID');
  const operations=sorted(m.definitions.map(d=>({operation:'create',path:`achievements/${d.id}`,fields:d.fields})));
  check(same(p.operations,operations)&&p.operations.length===5,'PROPOSAL_INVALID');
  check(same(p.before.documents.map(d=>d.path).sort(),Object.keys(HISTORICAL_ACHIEVEMENTS).map(id=>`achievements/${id}`).sort()),'PROPOSAL_INVALID');
  check(p.dependencies.documents.length===5&&p.dependencies.documents.every(d=>/^(modules|lessons)\/[^/]+$/.test(d.path)),'PROPOSAL_INVALID');
  for(const d of [...p.before.documents,...p.dependencies.documents])check(/^\d+\.\d{9}$/.test(d.updateTime),'PROPOSAL_INVALID');
  check(p.expectedAfterContentHash===hash(content([...p.before.documents,...p.operations])),'PROPOSAL_INVALID');return p;
 }catch{throw new PublicationError('PROPOSAL_INVALID');}
}
function encode(fields){
 const value=x=>x===null?{nullValue:null}:Array.isArray(x)?{arrayValue:{values:x.map(value)}}:typeof x==='object'?{mapValue:{fields:encode(x)}}:
  typeof x==='string'?{stringValue:x}:typeof x==='boolean'?{booleanValue:x}:Number.isInteger(x)?{integerValue:String(x)}:{doubleValue:x};
 return Object.fromEntries(Object.entries(fields).map(([k,v])=>[k,value(v)]));
}
export function buildWrites(input){return validateProposal(input).operations.map(op=>({
 update:{name:`${resource}/documents/${op.path}`,fields:encode(op.fields)},currentDocument:{exists:false}}));}
export function publicationConfirmation(){return `PUBLISH_ACHIEVEMENTS/${target.project}/${target.database}/${ACHIEVEMENT_PROPOSAL_DIGEST}`;}
function environment(){
 for(const key of ['FIRESTORE_EMULATOR_HOST','FIREBASE_AUTH_EMULATOR_HOST','GOOGLE_APPLICATION_CREDENTIALS','GOOGLE_OAUTH_ACCESS_TOKEN','FIREBASE_TOKEN',
  'CLOUDSDK_AUTH_ACCESS_TOKEN','CLOUDSDK_AUTH_CREDENTIAL_FILE_OVERRIDE','GOOGLE_CLOUD_QUOTA_PROJECT','GOOGLE_CLOUD_UNIVERSE_DOMAIN','GCE_METADATA_HOST','GCE_METADATA_IP',
  'FIREBASE_CONFIG','HTTP_PROXY','HTTPS_PROXY','ALL_PROXY','http_proxy','https_proxy','all_proxy'])check(!process.env[key],'ENVIRONMENT_INVALID');
 for(const key of ['GCLOUD_PROJECT','GOOGLE_CLOUD_PROJECT'])check(!process.env[key]||process.env[key]===target.project,'ENVIRONMENT_INVALID');
}
function context(options,p,write){
 environment();for(const[k,v]of Object.entries(target))check(options[k]===v,'TARGET_INVALID');
 check(options.principal===p.principal,'SDK_IDENTITY_INVALID');
 check(typeof options.credentialFile==='string'&&isAbsolute(options.credentialFile),'CREDENTIAL_FILE_INVALID');
 if(write)check(options.confirm===publicationConfirmation()&&options.adminWindow==='exclusive','FINAL_AUTHORIZATION_REQUIRED');
}
async function authenticate(credentials,expected,adapter){
 try{
  const c=copy(credentials);check(c.type==='authorized_user'&&Object.keys(c).every(k=>['type','client_id','client_secret','refresh_token','quota_project_id','universe_domain'].includes(k)),'SDK_IDENTITY_INVALID');
  for(const k of ['client_id','client_secret','refresh_token'])check(typeof c[k]==='string'&&c[k],'SDK_IDENTITY_INVALID');
  check((!c.quota_project_id||c.quota_project_id===target.project)&&(!c.universe_domain||c.universe_domain==='googleapis.com'),'SDK_IDENTITY_INVALID');
  const {GoogleAuth}=requireFunctions('google-auth-library'),client=new GoogleAuth({projectId:target.project}).fromJSON(c);
  if(adapter)client.transporter.defaults.adapter=adapter;
  const send=client.transporter.request.bind(client.transporter);
  client.transporter.request=opts=>{environment();check(opts.method==='POST'&&['https://oauth2.googleapis.com/token','https://oauth2.googleapis.com/tokeninfo'].includes(String(opts.url)),'SDK_IDENTITY_INVALID');
   return send({...opts,maxRedirects:0,retry:false,retryConfig:{retry:0},timeout:15000});};
  const {token}=await client.getAccessToken();check(typeof token==='string'&&token,'SDK_IDENTITY_INVALID');
  const info=await client.getTokenInfo(token);
  check(info.email===expected&&[true,'true'].includes(info.email_verified)&&info.expiry_date>Date.now()+60000&&info.scopes?.includes('https://www.googleapis.com/auth/cloud-platform'),'SDK_IDENTITY_INVALID');
  return {token,expiresAt:info.expiry_date};
 }catch{throw new PublicationError('SDK_IDENTITY_INVALID');}
}
function version(time){
 check(typeof time==='string','VERSION_INVALID');const m=/^(\d{4}-\d\d-\d\dT\d\d:\d\d:\d\d)(?:\.(\d{3}|\d{6}|\d{9}))?Z$/.exec(time);
 check(m&&Number.isFinite(Date.parse(time)),'VERSION_INVALID');return `${Date.parse(m[1]+'Z')/1000}.${(m[2]??'').padEnd(9,'0')}`;
}
async function session(options,p,write,checkCreate,deps){
 context(options,p,write);
 const auth=await authenticate((deps.loadCredentials??(file=>JSON.parse(readFileSync(file,'utf8').replace(/^\uFEFF/,''))))(options.credentialFile),options.principal,deps.oauthAdapter);
 const fetchImpl=deps.fetchImpl??globalThis.fetch,txs=new Map();let commitSent=false;
 const permissions=['datastore.databases.getMetadata','datastore.databases.get','datastore.entities.get','datastore.entities.list','resourcemanager.projects.get',...(checkCreate?['datastore.entities.create']:[])];
 const event=data=>{try{deps.onEvent?.({...data,at:new Date().toISOString()});}catch{throw new PublicationError('JOURNAL_FAILED');}};
 async function request(method,url,body){
  context(options,p,write);check(auth.expiresAt>Date.now()+10000,'SDK_IDENTITY_EXPIRED');
  const u=new URL(url);check(u.href===url&&!u.hash&&!u.username&&!u.password,'REQUEST_FORBIDDEN');let tx;
  if(method==='GET'&&[crm,base].includes(url))check(body===undefined,'REQUEST_FORBIDDEN');
  else if(method==='POST'&&url===`${crm}:testIamPermissions`)check(same(body,{permissions}),'REQUEST_FORBIDDEN');
  else if(method==='POST'&&url===`${base}/documents:beginTransaction`)check(same(body,{options:{readOnly:{}}})||write&&same(body,{options:{readWrite:{}}}),'REQUEST_FORBIDDEN');
  else if(method==='POST'&&url===`${base}/documents:rollback`){tx=txs.get(body?.transaction);check(tx&&!tx.sent&&same(Object.keys(body),['transaction']),'REQUEST_FORBIDDEN');}
  else if(method==='POST'&&url===`${base}/documents:commit`){
   tx=txs.get(body?.transaction);check(write&&tx&&!tx.readOnly&&tx.checked&&!tx.sent&&same(Object.keys(body).sort(),['transaction','writes'])&&same(body.writes,buildWrites(p)),'WRITE_FORBIDDEN');
   check(Date.now()-tx.started<120000,'TRANSACTION_TIMEOUT');
  }else{
   check(method==='GET'&&body===undefined&&url.startsWith(`${base}/documents/`),'REQUEST_FORBIDDEN');
   const path=url.slice(`${base}/documents/`.length).split('?')[0],q=u.searchParams,keys=[...q.keys()];tx=txs.get(q.get('transaction'));
   check(tx&&['achievements','modules','lessons'].includes(path)&&new Set(keys).size===keys.length&&
    keys.every(k=>['transaction','pageSize','showMissing','pageToken'].includes(k))&&q.get('pageSize')==='100'&&q.get('showMissing')==='true','REQUEST_FORBIDDEN');
  }
  if(url===`${base}/documents:commit`){tx.sent=true;commitSent=true;}
  const response=await fetchImpl(url,{method,redirect:'error',signal:AbortSignal.timeout(15000),headers:{Authorization:`Bearer ${auth.token}`,...(body?{'Content-Type':'application/json'}:{})},...(body?{body:JSON.stringify(body)}:{})});
  check(response.ok&&response.status===200,'REMOTE_REQUEST_FAILED');const result=await response.json();check(result&&typeof result==='object'&&!Array.isArray(result),'RESPONSE_INVALID');return result;
 }
 const project=await request('GET',crm);check(project.projectId===target.project&&project.projectNumber===target.projectNumber&&project.lifecycleState==='ACTIVE','PROJECT_MISMATCH');
 const db=await request('GET',base);check(db.name===resource&&db.uid===target.databaseUid&&db.type==='FIRESTORE_NATIVE'&&db.databaseEdition==='STANDARD'&&db.locationId==='southamerica-east1','DATABASE_MISMATCH');
 const iam=await request('POST',`${crm}:testIamPermissions`,{permissions});check(Array.isArray(iam.permissions)&&permissions.every(k=>iam.permissions.includes(k)),'PERMISSIONS_MISSING');
 event({stage:'IDENTITY_TARGET_IAM_VERIFIED',principal:options.principal,permissions});
 async function begin(readOnly){const data=await request('POST',`${base}/documents:beginTransaction`,{options:{[readOnly?'readOnly':'readWrite']:{}}});const id=data.transaction;
  check(typeof id==='string'&&/^[A-Za-z0-9+/]+={0,2}$/.test(id)&&id.length<=4096&&!txs.has(id),'TRANSACTION_INVALID');const tx={id,readOnly,sent:false,checked:false,started:Date.now()};txs.set(id,tx);return tx;}
 async function rollback(tx){await request('POST',`${base}/documents:rollback`,{transaction:tx.id});txs.delete(tx.id);}
 async function read(tx){
  const documents=[],seen=new Set(),allowed=new Set([...p.before.documents,...p.operations,...p.dependencies.documents].map(d=>d.path));
  for(const collection of ['achievements','modules','lessons']){let pageToken;const tokens=new Set();let pages=0;
   do{check(++pages<=100,'INVENTORY_LIMIT');const q=new URLSearchParams({transaction:tx.id,pageSize:'100',showMissing:'true'});if(pageToken)q.set('pageToken',pageToken);
    const data=await request('GET',`${base}/documents/${collection}?${q}`);check(!Object.hasOwn(data,'documents')||Array.isArray(data.documents),'RESPONSE_INVALID');
    for(const d of data.documents??[]){check(d&&typeof d.name==='string'&&d.name.startsWith(`${resource}/documents/`),'DOCUMENT_INVALID');const path=d.name.slice(`${resource}/documents/`.length);
     check(allowed.has(path)&&path.split('/')[0]===collection&&!seen.has(path),'UNEXPECTED_DOCUMENT');seen.add(path);check(Object.hasOwn(d,'updateTime'),'ORPHAN_DOCUMENT');
     documents.push({path,fields:decodeFields(d.fields??{}),updateTime:version(d.updateTime)});}
    pageToken=data.nextPageToken;check(pageToken===undefined||typeof pageToken==='string','RESPONSE_INVALID');if(pageToken){check(!tokens.has(pageToken),'PAGE_TOKEN_REPEATED');tokens.add(pageToken);}
   }while(pageToken);
  }
  return {achievements:{documents:sorted(documents.filter(d=>d.path.startsWith('achievements/')))},dependencies:{documents:sorted(documents.filter(d=>!d.path.startsWith('achievements/')))}};
 }
 async function snapshot(){const tx=await begin(true);try{return await read(tx);}finally{await rollback(tx);}}
 function classify(current){
  if(!same(current.dependencies,p.dependencies))return {status:'DIVERGED'};
  const historical=current.achievements.documents.filter(d=>Object.hasOwn(HISTORICAL_ACHIEVEMENTS,d.path.split('/')[1]));
  if(!same(historical,p.before.documents))return {status:'DIVERGED'};
  return {status:same(current.achievements,p.before)?'UNCHANGED':hash(content(current.achievements.documents))===p.expectedAfterContentHash?'APPLIED':'DIVERGED'};
 }
 async function observed(){try{return classify(await snapshot());}catch{return {status:'UNKNOWN'};}}
 async function apply(){
  const tx=await begin(false);let failed=false;
  try{
   check(classify(await read(tx)).status==='UNCHANGED','PRECONDITIONS_CHANGED');tx.checked=true;
   event({stage:'ACHIEVEMENTS_COMMIT_SENDING',digest:p.digest,writes:5});let result;
   try{result=await request('POST',`${base}/documents:commit`,{transaction:tx.id,writes:buildWrites(p)});version(result.commitTime);
    check(Array.isArray(result.writeResults)&&result.writeResults.length===5,'COMMIT_RESPONSE_INVALID');for(const r of result.writeResults)version(r.updateTime);
   }catch{throw new PublicationError('COMMIT_UNCERTAIN',await observed());}
   event({stage:'ACHIEVEMENTS_COMMIT_ACKNOWLEDGED',writes:5});return new Map(p.operations.map((op,i)=>[op.path,version(result.writeResults[i].updateTime)]));
  }catch(e){failed=true;throw e;}finally{
   if(tx.sent)txs.delete(tx.id);else try{await rollback(tx);}catch{if(!failed)throw new PublicationError('ROLLBACK_UNCONFIRMED');event({stage:'ROLLBACK_UNCONFIRMED'});}
  }
 }
 return {snapshot,classify,observed,apply,event,hasSent:()=>commitSent,permissions};
}
function checked(input,rawOptions,write){const p=validateProposal(input),options=copy(rawOptions);context(options,p,write);return {p,options};}
export async function preflightAchievements(input,rawOptions,deps={}){
 try{const {p,options}=checked(input,rawOptions,false),s=await session(options,p,false,true,deps),state=s.classify(await s.snapshot());check(state.status==='UNCHANGED','PRECONDITIONS_CHANGED');return {status:'READY',writes:0,proposedWrites:5,digest:p.digest,principal:p.principal,target,permissions:s.permissions};}
 catch(e){throw e instanceof PublicationError?e:new PublicationError('PREFLIGHT_FAILED');}
}
export async function reconcileAchievements(input,rawOptions,deps={}){
 try{const {p,options}=checked(input,rawOptions,false),s=await session(options,p,false,false,deps);return s.classify(await s.snapshot());}
 catch(e){throw e instanceof PublicationError?e:new PublicationError('RECONCILIATION_FAILED');}
}
export async function publishAchievements(input,rawOptions,deps={}){
 let s;
 try{const {p,options}=checked(input,rawOptions,true);check(typeof deps.onEvent==='function','JOURNAL_REQUIRED');s=await session(options,p,true,true,deps);const status=s.classify(await s.snapshot()).status;
  if(status==='APPLIED'){s.event({stage:'ACHIEVEMENTS_ALREADY_APPLIED',writes:0});return {status:'ALREADY_APPLIED',writes:0};}check(status==='UNCHANGED','PRECONDITIONS_CHANGED');
  const versions=await s.apply(),current=await s.snapshot();
  check(s.classify(current).status==='APPLIED'&&current.achievements.documents.every(d=>!versions.has(d.path)||versions.get(d.path)===d.updateTime),'RECONCILIATION_DIVERGED');
  s.event({stage:'ACHIEVEMENTS_RECONCILED',writes:5});return {status:'PUBLISHED_VERIFIED',writes:5,digest:p.digest};
 }catch(e){if(s?.hasSent()&&!e?.observed)throw new PublicationError(e instanceof PublicationError?e.code:'PUBLICATION_FAILED',await s.observed());throw e instanceof PublicationError?e:new PublicationError('PUBLICATION_FAILED');}
}
function auditFile(path){check(typeof path==='string'&&isAbsolute(path),'AUDIT_INVALID');const resolved=resolve(realpathSync(dirname(path)),path.split(/[\\/]/).at(-1)),rel=relative(root,resolved);
 check((rel==='..'||rel.startsWith('../')||rel.startsWith('..\\'))&&!existsSync(resolved),'AUDIT_INVALID');return openSync(resolved,'wx',0o600);}
async function cli(){
 const names=['proposal','project','database','project-number','database-uid','principal','credential-file','confirm','admin-window','audit'];
 const {values:v,positionals:commands}=parseArgs({allowPositionals:true,options:Object.fromEntries(names.map(n=>[n,{type:'string'}]))});
 check(commands.length===1&&['verify','preflight','publish','reconcile'].includes(commands[0]),'COMMAND_FORBIDDEN');const command=commands[0];
 if(command==='publish')check(v.confirm===publicationConfirmation()&&v['admin-window']==='exclusive','FINAL_AUTHORIZATION_REQUIRED');
 const p=validateProposal(JSON.parse(readFileSync(v.proposal,'utf8').replace(/^\uFEFF/,'')));
 if(command==='verify'){console.log(JSON.stringify({valid:true,digest:p.digest,writes:5,productionExecuted:false}));return;}
 const options={project:v.project,database:v.database,projectNumber:v['project-number'],databaseUid:v['database-uid'],principal:v.principal,credentialFile:v['credential-file'],
  ...(command==='publish'?{confirm:v.confirm,adminWindow:v['admin-window']}:{})};context(options,p,command==='publish');
 const fd=auditFile(v.audit),record=e=>{writeFileSync(fd,Buffer.from(JSON.stringify(e)+'\n','utf8'));fsyncSync(fd);};
 try{record({stage:'COMMAND_STARTED',command,digest:p.digest,target});const fn=command==='publish'?publishAchievements:command==='preflight'?preflightAchievements:reconcileAchievements;
  const result=await fn(p,options,{onEvent:record});
  // Publication already fsyncs its terminal event while commit context can still reconcile failures.
  if(command!=='publish')record({stage:'COMMAND_RESULT',result});console.log(JSON.stringify(result));
 }catch(e){try{record({stage:'COMMAND_FAILED',code:e instanceof PublicationError?e.code:'PUBLICATION_FAILED',...(e.observed?{observed:e.observed}:{})});}catch{}throw e;}finally{closeSync(fd);}
}
if(process.argv[1]&&import.meta.url===pathToFileURL(resolve(process.argv[1])).href)cli().catch(e=>{console.error(JSON.stringify({error:e instanceof PublicationError?e.code:'PUBLICATION_FAILED',...(e instanceof PublicationError&&e.observed?{observed:e.observed}:{})}));process.exitCode=1;});
