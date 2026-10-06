import assert from 'node:assert/strict';
import {readFileSync, writeFileSync} from 'node:fs';
import {dirname, isAbsolute, relative, resolve} from 'node:path';
import {fileURLToPath} from 'node:url';

const root = resolve(dirname(fileURLToPath(import.meta.url)), '..');
const drafts = [
  ['orcamento', 'orcamento_pessoal_2iT6j1pA0vvq62oX7jO7'],
  ['reserva', 'reserva_emergencia_gMmIv1XNtMhIrhBzDFKc'],
  ['investimentos', 'introducao_investimentos_Eo6rq9c4HDAtZRR6TAjp'],
];
const expected = [
  ['2iT6j1pA0vvq62oX7jO7', 'organizacao_financeira', 0, 50, 8, 11],
  ['gMmIv1XNtMhIrhBzDFKc', 'organizacao_financeira', 1, 100, 9, 12],
  ['Eo6rq9c4HDAtZRR6TAjp', 'investimentos', 0, 150, 10, 11],
];
const types = new Set(['FINANCIAL_SCENARIO', 'CLASSIFICATION', 'MULTIPLE_CHOICE']);

function select(source, keys, path) {
  const fields = {};
  for (const key of keys) {
    assert.ok(Object.hasOwn(source, key), `${path}.${key} missing`);
    fields[key] = source[key];
  }
  return fields;
}

function unique(entries, kind) {
  assert.equal(new Set(entries.map(({path}) => path)).size, entries.length, `${kind} path duplicado`);
}

export function buildCatalogManifest(draftsDir, overrides = {}) {
  const modules = [], lessons = [], blocks = [], legacy = [];
  for (const [key, stem] of drafts) {
    const draft = overrides[key] ?? JSON.parse(readFileSync(resolve(draftsDir, `${stem}.json`), 'utf8'));
    const md = readFileSync(resolve(draftsDir, `${stem}.md`), 'utf8');
    assert.match(draft.status, /APROVAD/i);
    if (draft.module) {
      const {path} = draft.module;
      assert.match(path, /^modules\/[a-z_]+$/);
      const fields = select(draft.module.fields, ['title', 'description', 'order'], path);
      assert.ok(md.includes(path) && md.includes(fields.title) && md.includes(fields.description));
      modules.push({path, fields});
    }
    const {path} = draft.lesson;
    assert.match(path, /^lessons\/[A-Za-z0-9]+$/);
    const fields = select(draft.lesson.fields,
      ['moduleId', 'title', 'description', 'duration', 'xpReward', 'order'], path);
    assert.ok(md.includes(path) && md.includes(fields.title) && md.includes(fields.description));
    lessons.push({path, fields});
    legacy.push({path: draft.legacyRead.path, fields: draft.legacyRead.fields});
    for (const block of draft.blocks) {
      assert.ok(block.path.startsWith(`${path}/blocks/`));
      const activity = block.fields.type === 'ACTIVITY';
      const keys = ['type', 'order', 'title', 'content', ...(activity ?
        ['activityType', 'alternatives', 'correctAnswerIds', 'feedback'] : [])];
      const value = select(block.fields, keys, block.path);
      assert.ok(md.includes(`ID do block: ${block.path.split('/').at(-1)}`)
        && md.includes(value.title) && md.includes(value.content),
      `${block.path} differs from Markdown`);
      if (activity) {
        assert.ok(types.has(value.activityType));
        assert.deepEqual(Object.keys(value.alternatives), ['a', 'b', 'c', 'd']);
        assert.equal(new Set(Object.values(value.alternatives)).size, 4);
        assert.deepEqual(value.correctAnswerIds.length, 1);
        assert.ok(Object.hasOwn(value.alternatives, value.correctAnswerIds[0]));
        assert.ok(value.feedback.trim());
        for (const [id, label] of Object.entries(value.alternatives)) {
          assert.ok(label.trim() && md.includes(`- ${id}: ${label}`));
        }
        assert.ok(md.includes(`Resposta correta: ${value.correctAnswerIds[0]}`)
          && md.includes(`Feedback: ${value.feedback}`));
      }
      blocks.push({path: block.path, fields: value});
    }
  }
  unique(modules, 'Module'); unique(lessons, 'Lesson'); unique(blocks, 'Block');
  modules.sort((a, b) => a.fields.order - b.fields.order || a.path.localeCompare(b.path));
  const moduleOrder = new Map(modules.map(({path, fields}) => [path.split('/')[1], fields.order]));
  lessons.sort((a, b) => moduleOrder.get(a.fields.moduleId) - moduleOrder.get(b.fields.moduleId)
    || a.fields.order - b.fields.order || a.path.localeCompare(b.path));
  const lessonOrder = new Map(lessons.map(({path}, index) => [path.split('/')[1], index]));
  blocks.sort((a, b) => lessonOrder.get(a.path.split('/')[1]) - lessonOrder.get(b.path.split('/')[1])
    || a.fields.order - b.fields.order || a.path.localeCompare(b.path));
  assert.deepEqual(modules.map(({path, fields}) => [path, fields.order]), [
    ['modules/organizacao_financeira', 0], ['modules/investimentos', 1],
  ]);
  assert.equal(lessons.length, 3); assert.equal(blocks.length, 34);
  assert.equal(blocks.filter(({fields}) => fields.type === 'ACTIVITY').length, 9);
  for (const [id, moduleId, order, xp, duration, count] of expected) {
    const lesson = lessons.find(({path}) => path === `lessons/${id}`);
    assert.ok(lesson);
    assert.deepEqual([lesson.fields.moduleId, lesson.fields.order, lesson.fields.xpReward,
      lesson.fields.duration, blocks.filter(({path}) => path.startsWith(`lessons/${id}/`)).length],
    [moduleId, order, xp, duration, count]);
  }
  for (const module of modules) {
    const id = module.path.split('/')[1];
    const orders = lessons.filter(({fields}) => fields.moduleId === id).map(({fields}) => fields.order);
    assert.equal(new Set(orders).size, orders.length, `${id} lesson order conflict`);
  }
  for (const lesson of lessons) {
    const orders = blocks.filter(({path}) => path.startsWith(`${lesson.path}/`)).map(({fields}) => fields.order);
    assert.equal(new Set(orders).size, orders.length, `${lesson.path} block order conflict`);
    assert.deepEqual(orders, orders.slice().sort((a, b) => a - b));
  }
  return {manifest: {modules, lessons, blocks}, legacy};
}

export function assertDemoEmulatorTarget(projectId, host) {
  assert.equal(projectId, 'demo-granaxp', 'Only demo-granaxp is permitted');
  assert.equal(host, '127.0.0.1:8080', 'Only 127.0.0.1:8080 is permitted');
}

function outsideRepository(path) {
  assert.ok(isAbsolute(path), 'Output path must be absolute');
  const child = relative(root, path);
  assert.ok(child === '..' || child.startsWith('..\\') || child.startsWith('../'),
    'Catalog artifacts must stay outside the repository');
}

if (process.argv[1] && resolve(process.argv[1]) === fileURLToPath(import.meta.url)) {
  const [command, draftsDir, manifestPath, legacyPath] = process.argv.slice(2);
  assert.ok(['export', 'verify'].includes(command) && draftsDir && manifestPath,
    'Usage: catalog-10-3.mjs export|verify DRAFTS_DIR MANIFEST_PATH [LEGACY_PATH]');
  const {manifest, legacy} = buildCatalogManifest(draftsDir);
  if (command === 'export') {
    assert.ok(legacyPath, 'Legacy output path required');
    outsideRepository(manifestPath); outsideRepository(legacyPath);
    writeFileSync(manifestPath, JSON.stringify(manifest, null, 2) + '\n', {flag: 'wx'});
    writeFileSync(legacyPath, JSON.stringify(legacy, null, 2) + '\n', {flag: 'wx'});
  } else {
    assert.deepEqual(JSON.parse(readFileSync(manifestPath, 'utf8')), manifest);
  }
  console.log(`Catalog ${command}: 2 modules, 3 lessons, 34 blocks, 9 activities.`);
}
