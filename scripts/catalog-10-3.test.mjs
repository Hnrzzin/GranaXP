import assert from 'node:assert/strict';
import {readFileSync} from 'node:fs';
import {dirname, resolve} from 'node:path';
import {fileURLToPath} from 'node:url';
import {test} from 'node:test';
import {buildCatalogManifest, assertDemoEmulatorTarget} from './catalog-10-3.mjs';

const root = resolve(dirname(fileURLToPath(import.meta.url)), '..');
const drafts = process.env.GRANAXP_DRAFTS_DIR ?? resolve(root, '..', '..', '..', '..',
  'IA', 'contexto', 'GranaXP_Bloco_10_3_Rascunhos');

test('extracts only catalog document payloads from the approved drafts', () => {
  const {manifest, legacy} = buildCatalogManifest(drafts);
  assert.equal(manifest.modules.length, 2);
  assert.equal(manifest.lessons.length, 3);
  assert.equal(manifest.blocks.length, 34);
  assert.equal(manifest.blocks.filter(({fields}) => fields.type === 'ACTIVITY').length, 9);
  assert.deepEqual(manifest.modules.map(({path}) => path),
    ['modules/organizacao_financeira', 'modules/investimentos']);
  assert.deepEqual(manifest.lessons.map(({path, fields}) => [path, fields.moduleId, fields.order, fields.xpReward]), [
    ['lessons/2iT6j1pA0vvq62oX7jO7', 'organizacao_financeira', 0, 50],
    ['lessons/gMmIv1XNtMhIrhBzDFKc', 'organizacao_financeira', 1, 100],
    ['lessons/Eo6rq9c4HDAtZRR6TAjp', 'investimentos', 0, 150],
  ]);
  for (const entry of [...manifest.modules, ...manifest.lessons, ...manifest.blocks]) {
    assert.equal('id' in entry.fields, false);
    assert.equal('idModule' in entry.fields, false);
    assert.equal('selectionMode' in entry.fields, false);
    assert.equal('legacyRead' in entry.fields, false);
  }
  assert.equal(legacy.length, 3);
  assert.equal(legacy[2].fields.order, 2);
  assert.equal(legacy[2].fields.duration, 5);
});

test('manifest extraction rejects a duplicated block path', () => {
  const source = JSON.parse(readFileSync(resolve(drafts,
    'orcamento_pessoal_2iT6j1pA0vvq62oX7jO7.json'), 'utf8'));
  source.blocks[1].path = source.blocks[0].path;
  assert.throws(() => buildCatalogManifest(drafts, {orcamento: source}), /duplicad/i);
});

test('local seed rejects a production target before attempting a request', () => {
  assert.throws(() => assertDemoEmulatorTarget('granaxp-d7927', '127.0.0.1:8080'), /demo-granaxp/);
  assert.throws(() => assertDemoEmulatorTarget('demo-granaxp', 'firestore.googleapis.com'), /127\.0\.0\.1:8080/);
  assert.equal(assertDemoEmulatorTarget('demo-granaxp', '127.0.0.1:8080'), undefined);
});
