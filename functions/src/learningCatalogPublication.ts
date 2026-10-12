import type {DocumentData, Firestore, Transaction} from "firebase-admin/firestore";
import {HttpsError} from "firebase-functions/v2/https";

export const LEARNING_CATALOG_CONTROL_PATH = "learningCatalogControl/current";
export type LearningCatalogMutation = {
  operation: "create" | "set" | "update" | "delete"; path: string; data?: DocumentData;
};
const generationValid = (value: unknown): value is number => Number.isSafeInteger(value) && Number(value) > 0;

export async function readLearningCatalogControl(transaction: Transaction, database: Firestore): Promise<number> {
  const snapshot = await transaction.get(database.doc(LEARNING_CATALOG_CONTROL_PATH));
  const data = snapshot.data();
  if (data?.protocolVersion !== 1 || !generationValid(data?.generation)) {
    throw new HttpsError("failed-precondition", "Controle do catálogo indisponível.");
  }
  return data.generation;
}

/** Administrative helper only: no callable/exported HTTP endpoint and no automatic bootstrap. */
export async function publishLearningCatalogMutation(
  database: Firestore, expectedGeneration: number, mutations: readonly LearningCatalogMutation[],
): Promise<number> {
  const invalid = () => new HttpsError("invalid-argument", "Publicação de catálogo inválida.");
  if (!generationValid(expectedGeneration) || !Array.isArray(mutations) || mutations.length === 0) throw invalid();
  const paths = new Set<string>();
  for (const mutation of mutations) {
    if (!mutation || !["create", "set", "update", "delete"].includes(mutation.operation) ||
        typeof mutation.path !== "string" ||
        !/^(modules\/[^/]+|lessons\/[^/]+(?:\/blocks\/[^/]+)?|achievements\/[^/]+)$/.test(mutation.path) ||
        paths.has(mutation.path) || (mutation.operation !== "delete" &&
        (typeof mutation.data !== "object" || mutation.data === null || Array.isArray(mutation.data)))) throw invalid();
    paths.add(mutation.path);
  }
  return await database.runTransaction(async (transaction) => {
    const generation = await readLearningCatalogControl(transaction, database);
    if (generation !== expectedGeneration || !Number.isSafeInteger(generation + 1)) {
      throw new HttpsError("failed-precondition", "Geração do catálogo obsoleta.");
    }
    const references = mutations.map(({path}) => database.doc(path));
    const previous = await transaction.getAll(...references);
    for (let index = 0; index < mutations.length; index++) {
      const mutation = mutations[index];
      if (!mutation.path.startsWith("achievements/")) continue;
      const oldType = previous[index].data()?.requirementType;
      const newType = mutation.operation === "delete" ? oldType :
        mutation.operation === "update" && !Object.hasOwn(mutation.data!, "requirementType") ? oldType :
          mutation.data!.requirementType;
      if (newType !== "MODULE_COMPLETION" || (previous[index].exists && oldType !== "MODULE_COMPLETION")) throw invalid();
    }
    for (let index = 0; index < mutations.length; index++) {
      const mutation = mutations[index];
      const reference = references[index];
      switch (mutation.operation) {
      case "create": transaction.create(reference, mutation.data!); break;
      case "set": transaction.set(reference, mutation.data!); break;
      case "update": transaction.update(reference, mutation.data!); break;
      case "delete": transaction.delete(reference); break;
      }
    }
    transaction.update(database.doc(LEARNING_CATALOG_CONTROL_PATH), {generation: generation + 1});
    return generation + 1;
  });
}
