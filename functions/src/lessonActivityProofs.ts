import {createHmac, timingSafeEqual} from "node:crypto";
import type {DocumentData} from "firebase-admin/firestore";
import {HttpsError} from "firebase-functions/v2/https";

export type ProofContext = {uid: string; lessonId: string; activityId: string};
export type ProofActivity = {
  activityType: string; title: string; content: string;
  alternatives: Record<string, string>; correctAnswerIds: string[]; media: null;
};
export type ProofKeyring = {activeKeyId: string; keys: ReadonlyMap<string, Buffer>};
export type ActivityValidationProofs = Record<string, string[]>;
const keyIdPattern = /^[A-Za-z0-9_-]{1,32}$/;
const tokenPattern = /^hmac-sha256:3:([A-Za-z0-9_-]{1,32}):([0-9a-f]{64})$/;
export const stableCompare = (left: string, right: string): number => left < right ? -1 : left > right ? 1 : 0;
const objectRecord = (value: unknown): value is Record<string, unknown> =>
  typeof value === "object" && value !== null && !Array.isArray(value) &&
  (Object.getPrototypeOf(value) === Object.prototype || Object.getPrototypeOf(value) === null);

export function parseLessonActivityProofKeyring(raw: string | undefined): ProofKeyring {
  // Never include the raw secret or a JSON parser exception in errors/logs.
  const unavailable = () => new HttpsError("failed-precondition", "Validação de atividades indisponível.");
  let data: unknown;
  try { data = raw ? JSON.parse(raw) : null; } catch { throw unavailable(); }
  if (!objectRecord(data) || Object.keys(data).length !== 2 ||
      typeof data.activeKeyId !== "string" || !keyIdPattern.test(data.activeKeyId) || !objectRecord(data.keys)) {
    throw unavailable();
  }
  const keys = new Map<string, Buffer>();
  for (const [id, encoded] of Object.entries(data.keys)) {
    if (!keyIdPattern.test(id) || typeof encoded !== "string") throw unavailable();
    const bytes = Buffer.from(encoded, "base64");
    if (bytes.length !== 32 || bytes.toString("base64") !== encoded) throw unavailable();
    keys.set(id, bytes);
  }
  if (!keys.has(data.activeKeyId)) throw unavailable();
  return {activeKeyId: data.activeKeyId, keys};
}

/** Called only after the shared structural activity validator. Media is not yet supported. */
export function proofActivityFromBlock(block: DocumentData, answers: string[]): ProofActivity {
  const text = (field: string): string => {
    if (block[field] === undefined || block[field] === null) return "";
    if (typeof block[field] !== "string") throw new HttpsError("failed-precondition", "Atividade do catálogo inválida.");
    return block[field];
  };
  if (block.chartImageUrl !== undefined && block.chartImageUrl !== null && block.chartImageUrl !== "") {
    throw new HttpsError("failed-precondition", "Mídia da atividade sem contrato de integridade.");
  }
  return {activityType: block.activityType, title: text("title"), content: text("content"),
    alternatives: block.alternatives, correctAnswerIds: answers, media: null};
}

export function canonicalActivityProofPayload(context: ProofContext, activity: ProofActivity, keyId: string): Buffer {
  const pairs = Object.keys(activity.alternatives).sort(stableCompare).map((id) => [id, activity.alternatives[id]]);
  return Buffer.from(JSON.stringify([
    "granaxp/lesson-activity-proof", 3, keyId, context.uid, context.lessonId, context.activityId,
    "ACTIVITY", activity.activityType, activity.title, activity.content, pairs,
    [...activity.correctAnswerIds].sort(stableCompare), activity.media,
  ]), "utf8");
}

export function signLessonActivityProof(context: ProofContext, activity: ProofActivity, keyring: ProofKeyring): string {
  const key = keyring.keys.get(keyring.activeKeyId);
  if (!key) throw new HttpsError("failed-precondition", "Validação de atividades indisponível.");
  const digest = createHmac("sha256", key).update(canonicalActivityProofPayload(context, activity, keyring.activeKeyId)).digest("hex");
  return `hmac-sha256:3:${keyring.activeKeyId}:${digest}`;
}

export function matchesLessonActivityProof(
  token: string, context: ProofContext, activity: ProofActivity, keyring: ProofKeyring,
): boolean {
  const parsed = tokenPattern.exec(token);
  if (!parsed) return false;
  const key = keyring.keys.get(parsed[1]);
  if (!key) return false; // A retired/unknown key is not a proof, but can be reanswered.
  const expected = createHmac("sha256", key).update(canonicalActivityProofPayload(context, activity, parsed[1])).digest();
  return timingSafeEqual(expected, Buffer.from(parsed[2], "hex"));
}

export function progressIdList(data: DocumentData, field: string, required = false): string[] {
  const value = data[field];
  if (value === undefined && !required) return [];
  if (!Array.isArray(value) || !value.every((id: unknown) => typeof id === "string" && id.length > 0) ||
      new Set(value).size !== value.length) {
    throw new HttpsError("failed-precondition", "Lista de atividades do progresso inválida.");
  }
  return value;
}

export function parseActivityValidationProofs(data: DocumentData): ActivityValidationProofs {
  const v3 = data.activityValidationVersion === 3;
  const historical = progressIdList(data, "completedActivityIds", v3);
  const validated = progressIdList(data, "validatedActivityIds", v3);
  if (!v3 && data.activityValidationProofs === undefined) return {};
  const invalid = () => new HttpsError("failed-precondition", "Provas de atividades do progresso inválidas.");
  if (!v3 || !objectRecord(data.activityValidationProofs)) throw invalid();
  const entries = Object.entries(data.activityValidationProofs).map(([id, tokens]) => {
    if (!id || !historical.includes(id) || !validated.includes(id) || !Array.isArray(tokens) || tokens.length === 0 ||
        !tokens.every((token: unknown) => typeof token === "string" && tokenPattern.test(token)) ||
        new Set(tokens).size !== tokens.length) throw invalid();
    return [id, [...tokens].sort(stableCompare)] as [string, string[]];
  });
  return Object.fromEntries(entries.sort(([left], [right]) => stableCompare(left, right)));
}

export function collectValidActivityIds(
  progressData: readonly DocumentData[], activities: readonly {id: string; activity: ProofActivity}[],
  context: {uid: string; lessonId: string}, keyring: ProofKeyring,
): string[] {
  const documents = progressData.map(parseActivityValidationProofs);
  return activities.filter(({id, activity}) => documents.some((proofs) =>
    Object.hasOwn(proofs, id) && proofs[id].some((token) =>
      matchesLessonActivityProof(token, {...context, activityId: id}, activity, keyring)),
  )).map(({id}) => id).sort(stableCompare);
}
