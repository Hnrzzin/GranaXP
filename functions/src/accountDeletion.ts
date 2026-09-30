import type {Auth} from "firebase-admin/auth";
import type {Firestore} from "firebase-admin/firestore";

/** Keep Authentication until every known private-data deletion has succeeded. */
export async function deleteAccountForUid(uid: string, database: Firestore, identity: Auth): Promise<void> {
  const legacyProgress = await database.collection("achievementProgress").where("userId", "==", uid).get();
  const writer = database.bulkWriter();
  // Attach rejection handlers immediately: close() itself never rejects for individual writes.
  const outcomes = legacyProgress.docs.map((document) =>
    writer.delete(document.ref).then(() => null, (error: unknown) => error),
  );
  await writer.close();
  const failures = (await Promise.all(outcomes)).filter((outcome) => outcome !== null);
  if (failures.length > 0) {
    throw failures[0] instanceof Error ? failures[0] : new Error("Falha ao excluir progresso legado.");
  }
  await database.recursiveDelete(database.doc(`users/${uid}`));
  await identity.deleteUser(uid);
}
