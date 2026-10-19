import assert from 'node:assert/strict';
import {readFileSync} from 'node:fs';
import {hash, scopePaths} from './educational-publication.mjs';

const candidate = JSON.parse(readFileSync(new URL('./achievement-catalog-approved.json', import.meta.url), 'utf8'));
const approval = JSON.parse(readFileSync(new URL('./educational-catalog-approved.json', import.meta.url), 'utf8'));
export const ACHIEVEMENT_MANIFEST_DIGEST = '8bb55c5ebc0824729096a6dea059d81b0235bd51746670adff3ecbe716efaca8';
export const HISTORICAL_ACHIEVEMENTS = Object.freeze({
  Yf1Gf6ez5AkPUUECOePP: ['LESSON_COUNT', 3],
  aKJgBEffC6cTvrCaXjBY: ['GOAL_COUNT', 1],
  iQo20q4jxFnAtkJBn8F4: ['TRANSACTION_COUNT', 1],
});
const criteria = {
  conquista_investimentos: ['MODULE_COMPLETION', 1, 'investimentos'],
  conquista_organizacao_financeira: ['MODULE_COMPLETION', 1, 'organizacao_financeira'],
  streak_7: ['STREAK', 7, null], streak_15: ['STREAK', 15, null], streak_30: ['STREAK', 30, null],
};
const required = ['title', 'description', 'icon', 'requirementType', 'requirementValue', 'category'];
function fields(value, historical = false) {
  assert.ok(value && Object.getPrototypeOf(value) === Object.prototype);
  assert.ok(required.every(k => Object.hasOwn(value, k)));
  assert.ok(Object.keys(value).every(k => [...required, 'referenceId'].includes(k)));
  for (const key of ['title', 'description', 'icon'])
    assert.ok(typeof value[key] === 'string' && value[key].trim() && value[key].length <= 2000);
  assert.ok(['FINANCAS', 'EDUCACAO'].includes(value.category));
  assert.ok(Number.isSafeInteger(value.requirementValue) && value.requirementValue > 0);
  if (!historical) assert.ok(Object.hasOwn(value, 'referenceId'));
}
export function validateAchievementDefinitions(manifest = candidate) {
  try {
    const {digest, ...content} = manifest;
    assert.equal(digest, ACHIEVEMENT_MANIFEST_DIGEST);
    assert.equal(hash(content), digest);
    assert.equal(manifest.editorialApproval.status, 'PENDING_OWNER_REVIEW');
    assert.equal(manifest.editorialApproval.productionApproved, false);
    assert.equal(manifest.definitions.length, 5);
    const seen = new Set();
    for (const d of manifest.definitions) {
      assert.ok(Object.hasOwn(criteria, d.id) && !seen.has(d.id)); seen.add(d.id);
      assert.deepEqual(Object.keys(d).sort(), ['fields', 'id']);
      fields(d.fields);
      assert.deepEqual([d.fields.requirementType, d.fields.requirementValue, d.fields.referenceId], criteria[d.id]);
      assert.equal(d.fields.icon, 'EmojiEvents'); assert.equal(d.fields.category, 'EDUCACAO');
    }
    return structuredClone(manifest);
  } catch { throw new Error('ACHIEVEMENT_INVALID'); }
}
export function achievementPaths() {
  return [...Object.keys(criteria), ...Object.keys(HISTORICAL_ACHIEVEMENTS)]
    .map(id => `achievements/${id}`).sort();
}
export function createAchievementPlan(manifest, snapshot, dependencies) {
  try {
    const m = validateAchievementDefinitions(manifest), allowed = new Set(achievementPaths());
    assert.deepEqual(Object.keys(snapshot), ['documents']);
    assert.ok(Array.isArray(snapshot.documents));
    const before = new Map();
    for (const d of snapshot.documents) {
      assert.ok(allowed.has(d.path) && !before.has(d.path));
      assert.deepEqual(Object.keys(d).sort(), ['fields', 'path', 'updateTime']);
      assert.match(d.updateTime, /^\d+\.\d{9}$/);
      fields(d.fields, true); hash(d.fields); before.set(d.path, d);
    }
    for (const [id, [type, value]] of Object.entries(HISTORICAL_ACHIEVEMENTS)) {
      const d = before.get(`achievements/${id}`);
      assert.ok(d); assert.equal(d.fields.requirementType, type); assert.equal(d.fields.requirementValue, value);
    }
    const modules = new Map();
    for (const d of dependencies.documents) {
      assert.ok(scopePaths('learning').includes(d.path));
      if (d.path.startsWith('modules/')) {
        assert.ok(!modules.has(d.path)); modules.set(d.path, d.fields);
        assert.equal(hash(d.fields), approval.learning.find(x => x.path === d.path).sha256);
      }
    }
    assert.equal(modules.size, 2);
    const operations = [];
    for (const d of m.definitions) {
      const path = `achievements/${d.id}`, old = before.get(path);
      if (old) assert.equal(hash(old.fields), hash(d.fields));
      else operations.push({operation: 'create', path, fields: d.fields});
    }
    // A partial previous catalog needs a new audit, not silent completion.
    assert.ok(operations.length === 0 || operations.length === 5);
    return {version: 1, scope: 'achievements', resources: achievementPaths(),
      before: structuredClone(snapshot), operations, manifestDigest: m.digest,
      editorialStatus: m.editorialApproval.status};
  } catch { throw new Error('ACHIEVEMENT_INVALID'); }
}
