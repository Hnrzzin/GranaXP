import assert from 'node:assert/strict';
import {readFileSync,writeFileSync,realpathSync} from 'node:fs';
import {dirname,isAbsolute,relative,resolve} from 'node:path';
import {parseArgs} from 'node:util';
import {fileURLToPath} from 'node:url';
import {buildCatalogManifest} from './catalog-10-3.mjs';
import {createPlan,hash,preview,validateBundle} from './educational-publication.mjs';
const root=realpathSync(fileURLToPath(new URL('..',import.meta.url)));
function read(path){assert.ok(path,'Input path required');return JSON.parse(readFileSync(path,'utf8').replace(/^\uFEFF/,''));}
function output(path,value){
  assert.ok(path&&isAbsolute(path),'Absolute external output path required');
  const rel=relative(root,resolve(realpathSync(dirname(path)),path.split(/[\\/]/).at(-1)));
  assert.ok(rel==='..'||rel.startsWith('../')||rel.startsWith('..\\'),'Private artifacts must stay outside repository');
  writeFileSync(path,JSON.stringify(value,null,2)+'\n',{flag:'wx',mode:0o600});
}
const optionNames=['bundle','snapshot','project','environment','scope','out','plan','confirm','resources-hash','host','drafts','missions'];
const {values,positionals}=parseArgs({allowPositionals:true,options:Object.fromEntries(optionNames.map(k=>[k,{type:'string'}]))});
assert.equal(positionals.length,1,'Command required');
const command=positionals[0];
if(command==='prepare') {
  const {manifest,legacy}=buildCatalogManifest(values.drafts);
  const b=validateBundle({version:1,learning:[...manifest.modules,...manifest.lessons,...manifest.blocks],daily:read(values.missions).dailyMissions,legacy});
  output(values.out,b);console.log(JSON.stringify({prepared:true,bundleSha256:hash(b),learning:39,daily:7}));
} else if(command==='preview') {
  const p=createPlan(read(values.bundle),read(values.snapshot),{project:values.project,environment:values.environment},values.scope);
  output(values.out,p);console.log(JSON.stringify({...preview(p),resourcesHash:hash(p.resources)},null,2));
} else if(command==='snapshot-emulator') {
  assert.equal(values.project,'demo-granaxp');assert.equal(values.environment,'emulator');
  const {connectEmulator,readScope}=await import('./educational-publication-emulator.mjs');
  const db=await connectEmulator(values.host);
  try {output(values.out,await readScope(db,db,values.scope));}finally{await db.terminate();}
  console.log('Private emulator catalog snapshot saved; no writes.');
} else if(command==='apply-emulator') {
  const {applyEmulatorPlan}=await import('./educational-publication-emulator.mjs');
  const p=read(values.plan);
  assert.equal(values.project,p.target.project);assert.equal(values.environment,p.target.environment);assert.equal(values.scope,p.scope);
  console.log(JSON.stringify(await applyEmulatorPlan(read(values.bundle),p,{host:values.host,confirm:values.confirm,resourcesHash:values['resources-hash']})));
} else assert.fail('Only prepare, preview, snapshot-emulator and apply-emulator exist; production publication is blocked');
