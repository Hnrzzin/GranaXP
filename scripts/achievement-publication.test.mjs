import assert from 'node:assert/strict';
import {readFileSync} from 'node:fs';
import {test} from 'node:test';
import {hash} from './educational-publication.mjs';

const m = await import('./achievement-publication.mjs').catch(e => {
  if (e.code !== 'ERR_MODULE_NOT_FOUND') throw e;
  return null;
});
test('achievement planner and candidate manifest exist without production approval', () => {
  assert.equal(typeof m?.createAchievementPlan, 'function');
  const manifest = m.validateAchievementDefinitions();
  assert.equal(manifest.editorialApproval.status, 'PENDING_OWNER_REVIEW');
  assert.equal(manifest.editorialApproval.productionApproved, false);
  assert.equal(manifest.definitions.length, 5);
  const {digest, ...content} = manifest;
  assert.equal(digest, hash(content));
});

const clone = value => structuredClone(value);
const bundle = JSON.parse(readFileSync(process.env.GRANAXP_APPROVED_BUNDLE, 'utf8'));
const modules = {documents: bundle.learning.filter(x => x.path.startsWith('modules/'))};
export function legacyAchievements() {
  return {documents: [
    ['Yf1Gf6ez5AkPUUECOePP', 'LESSON_COUNT', 3],
    ['aKJgBEffC6cTvrCaXjBY', 'GOAL_COUNT', 1],
    ['iQo20q4jxFnAtkJBn8F4', 'TRANSACTION_COUNT', 1],
  ].map(([id, requirementType, requirementValue]) => ({
    path: `achievements/${id}`, updateTime: '1.000000001',
    fields: {title: 'Synthetic historical definition', description: 'Local preservation witness',
      icon: 'EmojiEvents', requirementType, requirementValue, category: 'FINANCAS'},
  }))};
}
test('five create-only definitions preserve three historical records and become no-op', {skip: !m}, () => {
  const before = legacyAchievements(), saved = clone(before);
  const p = m.createAchievementPlan(m.validateAchievementDefinitions(), before, modules);
  assert.equal(p.operations.length, 5);
  assert.ok(p.operations.every(o => o.operation === 'create'));
  assert.deepEqual(before, saved);
  const after = clone(before);
  after.documents.push(...p.operations.map(o => ({path: o.path, fields: o.fields, updateTime: '2.000000001'})));
  assert.equal(m.createAchievementPlan(m.validateAchievementDefinitions(), after, modules).operations.length, 0);
  assert.equal(after.documents.length, 8);
});
test('manifest cannot self-approve or change payload, criteria, identity, extras or digest', {skip: !m}, () => {
  for (const change of [
    x => { x.editorialApproval.productionApproved = true; },
    x => { x.definitions[0].fields.title = 'Unreviewed'; },
    x => { x.definitions[0].id = '../users/victim'; },
    x => { x.definitions[0].fields.requirementValue = 999; },
    x => { x.definitions[0].fields.xp = 200; },
    x => { x.definitions.push(clone(x.definitions[0])); },
    x => { x.digest = ''; },
  ]) {
    const value = clone(m.validateAchievementDefinitions()); change(value);
    assert.throws(() => m.validateAchievementDefinitions(value), /^Error: ACHIEVEMENT_INVALID$/);
  }
});
test('unknown documents, missing legacy, changed existing definitions and missing references abort', {skip: !m}, () => {
  const manifest = m.validateAchievementDefinitions();
  for (const change of [
    s => s.documents.push({path: 'users/victim', fields: {xp: 999}}),
    s => s.documents.pop(),
    s => { s.documents[0].fields.extra = true; },
    s => s.documents.push({path: `achievements/${manifest.definitions[0].id}`, fields: {title: 'Conflict'}}),
  ]) {
    const value = legacyAchievements(); change(value);
    assert.throws(() => m.createAchievementPlan(manifest, value, modules), /^Error: ACHIEVEMENT_INVALID$/);
  }
  assert.throws(() => m.createAchievementPlan(manifest, legacyAchievements(), {documents: []}));
});
