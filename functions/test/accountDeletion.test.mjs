import assert from "node:assert/strict";
import {test} from "node:test";
import {deleteAccountForUid} from "../lib/accountDeletion.js";

test("falha individual de BulkWriter impede excluir o Authentication", async () => {
  let authDeleted = false;
  let recursiveDeleted = false;
  const failingDocument = {ref: {path: "achievementProgress/failed"}};
  const database = {
    collection: () => ({where: () => ({get: async () => ({docs: [failingDocument]})})}),
    bulkWriter: () => ({
      delete: () => Promise.reject(new Error("falha individual")),
      close: async () => undefined,
    }),
    doc: (path) => ({path}),
    recursiveDelete: async () => { recursiveDeleted = true; },
  };
  const identity = {deleteUser: async () => { authDeleted = true; }};

  await assert.rejects(deleteAccountForUid("owner", database, identity), /falha individual/);
  assert.equal(recursiveDeleted, false);
  assert.equal(authDeleted, false);
});

test("falha na exclusão recursiva impede excluir o Authentication", async () => {
  let authDeleted = false;
  const database = {
    collection: () => ({where: () => ({get: async () => ({docs: []})})}),
    bulkWriter: () => ({delete: () => Promise.resolve(), close: async () => undefined}),
    doc: (path) => ({path}),
    recursiveDelete: async () => { throw new Error("falha recursiva"); },
  };
  const identity = {deleteUser: async () => { authDeleted = true; }};

  await assert.rejects(deleteAccountForUid("owner", database, identity), /falha recursiva/);
  assert.equal(authDeleted, false);
});
