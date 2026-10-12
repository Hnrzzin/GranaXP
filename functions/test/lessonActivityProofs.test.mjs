import assert from "node:assert/strict";
import {createHmac, randomBytes} from "node:crypto";
import {test} from "node:test";
import {
  parseLessonActivityProofKeyring, canonicalActivityProofPayload, signLessonActivityProof,
  matchesLessonActivityProof, parseActivityValidationProofs, collectValidActivityIds,
} from "../lib/lessonActivityProofs.js";

const context = {uid: "u", lessonId: "lesson", activityId: "activity"};
const activity = {activityType: "MULTIPLE_CHOICE", title: "Pergunta", content: "Conteúdo",
  alternatives: {b: "B", a: "A"}, correctAnswerIds: ["a"], media: null};
const key1 = randomBytes(32).toString("base64");
const key2 = randomBytes(32).toString("base64");
const rawRing = (activeKeyId = "k1", keys = {k1: key1, k2: key2}) => JSON.stringify({activeKeyId, keys});
const ring = () => parseLessonActivityProofKeyring(rawRing());
const document = (tokens, overrides = {}) => ({activityValidationVersion: 3,
  completedActivityIds: ["activity"], validatedActivityIds: ["activity"],
  activityValidationProofs: {activity: tokens}, ...overrides});
const rejected = (operation) => assert.throws(operation,
  (error) => error.code === "failed-precondition");

test("canonicaliza somente os campos de correção em ordem estável e confere HMAC independente", () => {
  const payload = '["granaxp/lesson-activity-proof",3,"k1","u","lesson","activity","ACTIVITY","MULTIPLE_CHOICE","Pergunta","Conteúdo",[["a","A"],["b","B"]],["a"],null]';
  assert.equal(canonicalActivityProofPayload(context, activity, "k1").toString("utf8"), payload);
  const expected = "hmac-sha256:3:k1:" + createHmac("sha256", Buffer.from(key1, "base64")).update(payload).digest("hex");
  assert.ok(signLessonActivityProof(context, activity, ring()) === expected, "HMAC difere do vetor independente");
});

test("reordenação de alternativas e respostas não invalida prova; strings não são normalizadas", () => {
  const multi = {...activity, correctAnswerIds: ["b", "a"]};
  const token = signLessonActivityProof(context, multi, ring());
  assert.ok(matchesLessonActivityProof(token, context,
    {...multi, alternatives: {a: "A", b: "B"}, correctAnswerIds: ["a", "b"]}, ring()));
  assert.equal(matchesLessonActivityProof(token, context, {...multi, content: "Conteúdo"}, ring()), false);
});

for (const [name, change] of [
  ["gabarito", {correctAnswerIds: ["b"]}], ["tipo", {activityType: "TRUE_FALSE"}],
  ["título", {title: "Outra pergunta"}], ["enunciado", {content: "Outro cenário"}],
  ["alternativa", {alternatives: {a: "Outro significado", b: "B"}}],
]) {
  test(`mudança de ${name} invalida apenas a prova correspondente`, () => {
    const token = signLessonActivityProof(context, activity, ring());
    assert.equal(matchesLessonActivityProof(token, context, {...activity, ...change}, ring()), false);
  });
}

test("ordem, feedback e geração administrativa não fazem parte da prova", () => {
  const token = signLessonActivityProof(context, activity, ring());
  assert.ok(matchesLessonActivityProof(token, context,
    {...activity, order: 7, feedback: "Outro feedback", generation: 9}, ring()));
});

test("não aceita token copiado entre usuários, lições ou atividades, nem MAC alterado", () => {
  const token = signLessonActivityProof(context, activity, ring());
  for (const name of ["uid", "lessonId", "activityId"]) {
    assert.equal(matchesLessonActivityProof(token, {...context, [name]: "other"}, activity, ring()), false);
  }
  const tampered = token.slice(0, -1) + (token.endsWith("0") ? "1" : "0");
  assert.equal(matchesLessonActivityProof(tampered, context, activity, ring()), false);
  assert.equal(matchesLessonActivityProof(token.replace(":k1:", ":unknown:"), context, activity, ring()), false);
  assert.equal(matchesLessonActivityProof("invalid", context, activity, ring()), false);
});

test("rotação mantém provas antigas enquanto chave antiga é aceita", () => {
  const token = signLessonActivityProof(context, activity, ring());
  const rotated = parseLessonActivityProofKeyring(rawRing("k2"));
  assert.ok(matchesLessonActivityProof(token, context, activity, rotated));
  assert.ok(signLessonActivityProof(context, activity, rotated).startsWith("hmac-sha256:3:k2:"));
  assert.equal(matchesLessonActivityProof(token, context, activity,
    parseLessonActivityProofKeyring(rawRing("k2", {k2: key2}))), false);
});

for (const raw of [undefined, "", "{", "{}", rawRing("missing"), rawRing("k1", {k1: "bad"}),
  rawRing("k1", {k1: randomBytes(31).toString("base64")}),
  JSON.stringify({activeKeyId: "k1", keys: {k1: key1}, extra: true})]) {
  test("configuração ausente ou inválida falha fechado sem revelar valor", () => {
    rejected(() => parseLessonActivityProofKeyring(raw));
    try { parseLessonActivityProofKeyring(raw); } catch (error) {
      assert.ok(!error.message.includes(key1), "erro revelou material de chave");
    }
  });
}

test("v1/v2/ausência de versão não são promovidas a prova v3", () => {
  for (const version of [undefined, 1, 2, "3", 4]) {
    assert.deepEqual(parseActivityValidationProofs({completedActivityIds: ["activity"],
      validatedActivityIds: ["activity"], ...(version === undefined ? {} : {activityValidationVersion: version})}), {});
  }
});

test("recusa mapas, listas e versões inconsistentes em vez de fabricar prova", () => {
  const token = signLessonActivityProof(context, activity, ring());
  for (const data of [
    document([token], {activityValidationVersion: 2}),
    document([token], {activityValidationProofs: undefined}),
    document([token], {activityValidationProofs: []}), document([token, token]), document([]),
    document(["bad"]), document([token], {validatedActivityIds: []}),
    document([token], {completedActivityIds: ["activity", "activity"]}),
    document([token], {completedActivityIds: [7]}),
  ]) rejected(() => parseActivityValidationProofs(data));
});

test("duplicatas preservam prova A e B e restauram elegibilidade A sem unir histórico como prova", () => {
  const a = signLessonActivityProof(context, activity, ring());
  const b = signLessonActivityProof(context, {...activity, correctAnswerIds: ["b"]}, ring());
  const docs = [document([a]), document([b]), {completedActivityIds: ["ghost"], activityValidationVersion: 2}];
  const current = [{id: "activity", activity}];
  assert.deepEqual(collectValidActivityIds(docs, current, context, ring()), ["activity"]);
  assert.deepEqual(collectValidActivityIds([document([a])],
    [{id: "activity", activity: {...activity, correctAnswerIds: ["b"]}}], context, ring()), []);
  assert.deepEqual(collectValidActivityIds([document([a, b])], current, context, ring()), ["activity"]);
  assert.deepEqual(collectValidActivityIds([{...document([a]), activityValidationVersion: 2,
    activityValidationProofs: undefined}], current, context, ring()), []);
});
