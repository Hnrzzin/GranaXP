import assert from 'node:assert/strict';
import {createHash} from 'node:crypto';
import {readFileSync} from 'node:fs';

const approval=JSON.parse(readFileSync(new URL('./educational-catalog-approved.json',import.meta.url),'utf8'));
export const CONTROL='learningCatalogControl/current';
const object=x=>x!==null && typeof x==='object' && !Array.isArray(x) && Object.getPrototypeOf(x)===Object.prototype;
export function canonical(x) {
  if(Array.isArray(x)) return `[${x.map(canonical).join(',')}]`;
  if(object(x)) return `{${Object.keys(x).sort().map(k=>`${JSON.stringify(k)}:${canonical(x[k])}`).join(',')}}`;
  assert.ok(x===null || typeof x==='string' || typeof x==='boolean' || (typeof x==='number'&&Number.isFinite(x)),'Non-JSON value');
  return JSON.stringify(x);
}
export const hash=x=>createHash('sha256').update(canonical(x)).digest('hex');
function keys(x,required,optional=[]) {
  assert.ok(object(x),'Expected object');
  assert.ok(required.every(k=>Object.hasOwn(x,k)) && Object.keys(x).every(k=>[...required,...optional].includes(k)),'Unexpected/missing fields');
}
const text=x=>assert.ok(typeof x==='string'&&x.trim().length>0,'Expected nonempty text');
const integer=(x,min=0)=>assert.ok(Number.isSafeInteger(x)&&x>=min,'Invalid integer');
function activity(f) {
  assert.ok(['MULTIPLE_CHOICE','TRUE_FALSE','CLASSIFICATION','FINANCIAL_SCENARIO','CHART_ANALYSIS'].includes(f.activityType),'Activity type');
  assert.ok(object(f.alternatives)&&Object.keys(f.alternatives).length>=2,'Alternatives');
  for(const [id,label] of Object.entries(f.alternatives)){assert.match(id,/^[A-Za-z0-9_-]+$/);text(label);}
  assert.ok(Array.isArray(f.correctAnswerIds)&&f.correctAnswerIds.length>0&&new Set(f.correctAnswerIds).size===f.correctAnswerIds.length,'Answer set');
  assert.ok(f.correctAnswerIds.every(id=>typeof id==='string'&&Object.hasOwn(f.alternatives,id)),'Unknown answer');
  text(f.feedback);
  assert.ok(f.chartImageUrl===undefined||f.chartImageUrl===null||f.chartImageUrl==='','Unapproved external media');
}
export function validateBundle(b) {
  keys(b,['version','learning','daily','legacy']);assert.equal(b.version,1);
  for(const scope of ['learning','daily']) {
    assert.ok(Array.isArray(b[scope]));
    assert.equal(b[scope].length,approval[scope].length,'Resource count');
    const entries=new Map();
    for(const entry of b[scope]) {
      keys(entry,['path','fields']);assert.ok(!entries.has(entry.path),'Duplicate path');entries.set(entry.path,entry.fields);
      const f=entry.fields;
      if(entry.path.startsWith('modules/')) {keys(f,['title','description','order']);text(f.title);text(f.description);integer(f.order);}
      else if(/^lessons\/[^/]+$/.test(entry.path)) {
        keys(f,['moduleId','title','description','order','duration','xpReward']);text(f.title);text(f.description);text(f.moduleId);
        for(const k of ['order','duration','xpReward'])integer(f[k],k==='duration'?1:0);
      } else if(entry.path.includes('/blocks/')) {
        const active=f.type==='ACTIVITY';
        keys(f,['type','order','title','content',...(active?['activityType','alternatives','correctAnswerIds','feedback']:[])]);
        assert.ok(['OBJETIVO','EXPLICAÇÃO','EXEMPLO','ACTIVITY','APLICAÇÃO','RESUMO','CONTEXTO'].includes(f.type),'Block type');integer(f.order);text(f.title);text(f.content);if(active)activity(f);
      } else if(entry.path.startsWith('dailyMissions/')) {
        keys(f,['id','title','description','order','activityType','alternatives','correctAnswerIds','feedback','chartImageUrl']);
        assert.equal(entry.path,`dailyMissions/${f.id}`);text(f.title);text(f.description);integer(f.order);activity(f);
      } else assert.fail('Resource outside approved catalog');
    }
    for(const a of approval[scope]) {assert.ok(entries.has(a.path),'Missing approved resource');assert.equal(hash(entries.get(a.path)),a.sha256,'Content differs from approval');}
  }
  assert.equal(hash(b.legacy),approval.legacySha256,'Legacy baseline differs');
  for(const l of b.learning.filter(x=>/^lessons\/[^/]+$/.test(x.path)))
    assert.ok(b.learning.some(x=>x.path===`modules/${l.fields.moduleId}`),'Missing module');
  for(const block of b.learning.filter(x=>x.path.includes('/blocks/')))
    assert.ok(b.learning.some(x=>x.path===block.path.split('/').slice(0,2).join('/')),'Missing lesson');
  assert.equal(hash(b),approval.bundleSha256,'Bundle differs from approval');
  return b;
}
export function validateTarget(target) {
  keys(target,['project','environment']);
  assert.ok((target.environment==='emulator'&&target.project==='demo-granaxp')||
    (target.environment==='production'&&target.project==='granaxp-d7927'),'Explicit approved project/environment required');
}
export function scopePaths(scope) {
  assert.ok(['learning','daily'].includes(scope),'Explicit learning or daily scope required');
  return [...approval[scope].map(x=>x.path),...(scope==='learning'?[CONTROL]:[])].sort();
}
export function createPlan(bundle,snapshot,target,scope) {
  validateBundle(bundle);validateTarget(target);const allowed=new Set(scopePaths(scope));
  keys(snapshot,['documents']);assert.ok(Array.isArray(snapshot.documents));
  const allAllowed=new Set([...scopePaths('learning'),...scopePaths('daily')]);
  const before=new Map();
  for(const d of snapshot.documents) {
    keys(d,['path','fields'],['updateTime']);assert.ok(allAllowed.has(d.path),'Unexpected document');
    assert.ok(!before.has(d.path),'Duplicate snapshot path');assert.ok(object(d.fields));
    if(d.updateTime!==undefined)text(d.updateTime);
    before.set(d.path,d);
  }
  const operations=[];
  for(const {path,fields} of bundle[scope]) {
    const previous=before.get(path)?.fields;
    if(!previous) operations.push({operation:'create',path,fields});
    else if(hash(previous)===hash(fields))continue;
    else if(/^lessons\/[^/]+$/.test(path)) {
      const legacy=bundle.legacy.find(x=>x.path===path)?.fields;
      assert.ok(legacy,'Missing legacy baseline');
      if(hash(previous)===hash({...legacy,...fields}))continue;
      assert.equal(hash(previous),hash(legacy),`Unexpected existing lesson: ${path}`);
      operations.push({operation:'update',path,fields});
    } else assert.fail(`Unexpected existing catalog: ${path}`);
  }
  if(scope==='learning') {
    const control=before.get(CONTROL)?.fields;
    if(control){keys(control,['protocolVersion','generation']);assert.equal(control.protocolVersion,1);integer(control.generation,1);}
    if(operations.length||!control) {
      const generation=control?control.generation+1:2;integer(generation,1);
      operations.push({operation:control?'update':'create',path:CONTROL,fields:{protocolVersion:1,generation}});
    }
  }
  const plan={version:1,target:structuredClone(target),scope,bundleSha256:approval.bundleSha256,
    before:{documents:snapshot.documents.filter(d=>allowed.has(d.path)).sort((a,b)=>a.path.localeCompare(b.path))},
    resources:[...allowed].sort(),operations};
  return {...plan,digest:hash(plan)};
}
export const confirmation=plan=>`${plan.target.project}/${plan.target.environment}/${plan.scope}/${plan.digest}`;
export function preview(plan) {
  return {version:plan.version,target:plan.target,scope:plan.scope,digest:plan.digest,confirmation:confirmation(plan),
    resources:plan.resources,operations:plan.operations.map(({operation,path,fields})=>({operation,path,fieldNames:Object.keys(fields).filter(k=>!['correctAnswerIds','feedback'].includes(k)).sort(),sha256:hash(fields)})),
    productionExecution:'BLOCKED: E6.6 permits local rehearsal only'};
}
