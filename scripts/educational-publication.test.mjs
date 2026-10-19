import assert from 'node:assert/strict';
import {readFileSync,writeFileSync,mkdtempSync,existsSync} from 'node:fs';
import {dirname,join} from 'node:path';
import {spawnSync} from 'node:child_process';
import {test} from 'node:test';
const m = await import('./educational-publication.mjs').catch(e => {
  if (e.code !== 'ERR_MODULE_NOT_FOUND') throw e;
  return null;
});
const bundle = JSON.parse(readFileSync(process.env.GRANAXP_APPROVED_BUNDLE, 'utf8'));
const clone = x => structuredClone(x);
const target = {project:'demo-granaxp', environment:'emulator'};
const snapshot = () => ({documents: bundle.legacy.map(x => ({path:x.path, fields:clone(x.fields)}))});
test('publication planner and schema validator exist', () => {
  assert.equal(typeof m?.createPlan, 'function');
  assert.equal(typeof m?.validateBundle, 'function');
});
test('approved complete catalog, metadata preservation, atomic control and scopes', {skip:!m}, () => {
  m.validateBundle(bundle);
  const p = m.createPlan(bundle, snapshot(), target, 'learning');
  assert.equal(p.operations.length, 40); // 39 catalog resources + control
  assert.equal(p.operations.filter(x=>x.operation==='update').length, 3);
  assert.equal(p.operations.find(x=>x.path==='learningCatalogControl/current').fields.generation, 2);
  assert.equal(p.operations.find(x=>x.path.includes('2iT6')).fields.category, undefined);
  const daily=m.createPlan(bundle,snapshot(),target,'daily');
  assert.equal(daily.operations.length,7);
  assert.ok(daily.operations.every(x=>x.path.startsWith('dailyMissions/')));
  assert.ok(!JSON.stringify(m.preview(p)).includes('correctAnswerIds'));
});
test('second publication is a no-op and leaves generation unchanged', {skip:!m}, () => {
  const s=snapshot(), first=m.createPlan(bundle,s,target,'learning');
  for(const op of first.operations){
    const previous=s.documents.find(d=>d.path===op.path);
    if(previous) Object.assign(previous.fields,op.fields); else s.documents.push({path:op.path,fields:clone(op.fields)});
  }
  assert.equal(m.createPlan(bundle,s,target,'learning').operations.length,0);
});
test('schema and exact approval reject changed content, paths, answers and public answer leaks', {skip:!m}, () => {
  for(const mutate of [
    b=>b.learning[0].fields.correctAnswerIds=['a'],
    b=>b.learning[2].fields.moduleId='missing',
    b=>b.learning[2].fields.xpReward=999,
    b=>b.learning[5].path='users/anyone',
    b=>b.learning.push(clone(b.learning[0])),
    b=>b.daily[0].fields.correctAnswerIds=['missing'],
    b=>b.daily[0].fields.order=2,
    b=>b.daily[0].fields.chartImageUrl='https://example.com/image.png',
    b=>b.extra={users:[]},
  ]) { const b=clone(bundle);mutate(b);assert.throws(()=>m.validateBundle(b)); }
});
test('unexpected state, extra documents and invalid control abort instead of overwrite', {skip:!m}, () => {
  for(const mutate of [
    s=>s.documents[0].fields.duration=999,
    s=>s.documents.push({path:'modules/unexpected',fields:{}}),
    s=>s.documents.push({path:'learningCatalogControl/current',fields:{protocolVersion:1,generation:0}}),
    s=>s.documents.push({path:'users/anyone',fields:{xp:100}}),
    s=>s.documents.push(clone(s.documents[0])),
  ]) {const s=snapshot();mutate(s);assert.throws(()=>m.createPlan(bundle,s,target,'learning'));}
});
test('production preview is explicit and unknown project/environment/scope is refused', {skip:!m}, () => {
  const p=m.createPlan(bundle,snapshot(),{project:'granaxp-d7927',environment:'production'},'learning');
  assert.equal(p.target.project,'granaxp-d7927');
  for(const t of [{project:'granaxp-d7927',environment:'emulator'},{project:'other',environment:'production'},{}])
    assert.throws(()=>m.createPlan(bundle,snapshot(),t,'learning'));
  assert.throws(()=>m.createPlan(bundle,snapshot(),target,'all'));
});
test('confirmation binds target, scope, exact resources and before/after state', {skip:!m}, () => {
  const p=m.createPlan(bundle,snapshot(),target,'learning');
  assert.equal(m.confirmation(p),`demo-granaxp/emulator/learning/${p.digest}`);
  const s=snapshot();s.documents[0].updateTime='different';
  assert.notEqual(m.createPlan(bundle,s,target,'learning').digest,p.digest);
  assert.notEqual(m.createPlan(bundle,snapshot(),target,'daily').digest,p.digest);
});
test('CLI offline preview, private output protection, no overwrite and production refusal', {skip:!m},()=>{
  const folder=mkdtempSync(join(dirname(process.env.GRANAXP_APPROVED_BUNDLE),'cli-'));
  const s=join(folder,'snapshot.json'),p=join(folder,'plan.private.json');
  writeFileSync(s,JSON.stringify(snapshot()));
  const args=['scripts/educational-publication-cli.mjs','preview','--bundle',process.env.GRANAXP_APPROVED_BUNDLE,
    '--snapshot',s,'--project','granaxp-d7927','--environment','production','--scope','learning','--out',p];
  const run=extra=>spawnSync(process.execPath,extra,{encoding:'utf8',env:{...process.env,FIRESTORE_EMULATOR_HOST:'forbidden-host:1'}});
  const preview=run(args);assert.equal(preview.status,0);
  assert.equal(JSON.parse(preview.stdout).operations.length,40);
  assert.ok(!preview.stdout.includes('correctAnswerIds'));
  const prior=readFileSync(p,'utf8');assert.notEqual(run(args).status,0);assert.equal(readFileSync(p,'utf8'),prior);
  const inside='scripts/e66-forbidden.private.json';assert.equal(existsSync(inside),false);
  const blocked=run([...args.slice(0,-1),join(process.cwd(),inside)]);assert.notEqual(blocked.status,0);assert.equal(existsSync(inside),false);
  const plan=JSON.parse(prior);
  const applied=run(['scripts/educational-publication-cli.mjs','apply-emulator','--bundle',process.env.GRANAXP_APPROVED_BUNDLE,
    '--plan',p,'--project','granaxp-d7927','--environment','production','--scope','learning','--host','127.0.0.1:18080',
    '--confirm',m.confirmation(plan),'--resources-hash',m.hash(plan.resources)]);
  assert.notEqual(applied.status,0);assert.ok(applied.stderr.includes('Production execution is blocked'));
  assert.ok(!applied.stderr.includes('correctAnswerIds'));
});
