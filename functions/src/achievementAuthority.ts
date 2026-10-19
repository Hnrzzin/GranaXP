import {FieldValue, type Firestore, type QueryDocumentSnapshot} from "firebase-admin/firestore";
import {collectValidActivityIds, type ProofActivity, type ProofKeyring} from "./lessonActivityProofs";
import {readLearningCatalogControl} from "./learningCatalogPublication";

type CountType = "TRANSACTION_COUNT" | "GOAL_COUNT" | "LESSON_COUNT";
type LessonProofReader = {keyring: ProofKeyring; activities: (blocks: QueryDocumentSnapshot[]) => {id: string; activity: ProofActivity}[]};

/** Re-read authoritative state: event order/payload and client progress are never the authority. */
export async function syncCountAchievements(db: Firestore, uid: string, kind: CountType, proofs?: LessonProofReader): Promise<void> {
  await db.runTransaction(async (transaction) => {
    const user = db.doc(`users/${uid}`);
    const [profile, definitions] = await Promise.all([
      transaction.get(user), transaction.get(db.collection("achievements").where("requirementType", "==", kind)),
    ]);
    if (!profile.exists || definitions.empty) return; // Never recreate deleted accounts from delayed events.
    let count = 0;
    if (kind !== "LESSON_COUNT") {
      count = (await transaction.get(user.collection(kind === "GOAL_COUNT" ? "goals" : "transactions"))).size;
    } else if (proofs) {
      // No catalog means no new validated lesson count; historical display remains untouched.
      const control = await transaction.get(db.doc("learningCatalogControl/current"));
      if (control.exists) {
        await readLearningCatalogControl(transaction, db);
        const [lessons, progress] = await Promise.all([
          transaction.get(db.collection("lessons")), transaction.get(user.collection("lessonProgress")),
        ]);
        for (const lesson of lessons.docs) {
          const completed = progress.docs.filter((record) => record.get("lessonId") === lesson.id &&
            record.get("completionValidationVersion") === 3 && (record.get("isCompleted") === true || record.get("completed") === true));
          if (!completed.length) continue;
          const blocks = await transaction.get(lesson.ref.collection("blocks"));
          try {
            const activities = proofs.activities(blocks.docs);
            if (activities.length && collectValidActivityIds(completed.map((record) => record.data()), activities,
              {uid, lessonId: lesson.id}, proofs.keyring).length === activities.length) count++;
          } catch { /* Invalid or historical proofs never grant a new achievement. */ }
        }
      }
    }
    const valid = definitions.docs.filter((definition) => {
      const threshold = definition.get("requirementValue");
      return Number.isInteger(threshold) && threshold > 0 && threshold <= 1_000_000_000 &&
        (kind === "GOAL_COUNT" || definition.get("category") === (kind === "LESSON_COUNT" ? "EDUCACAO" : "FINANCAS"));
    });
    const previous = await Promise.all(valid.map((definition) =>
      transaction.get(user.collection("achievementProgress").doc(definition.id))));
    for (const [i, definition] of valid.entries()) {
      const old = previous[i];
      const unlocked = old.get("isUnlocked") === true || count >= definition.get("requirementValue");
      const currentProgress = Math.max(count, old.get("isUnlocked") === true ? old.get("currentProgress") ?? 0 : 0);
      if (old.exists && old.get("isUnlocked") === unlocked && old.get("currentProgress") === currentProgress) continue;
      const data = {achievementId: definition.id, currentProgress, isUnlocked: unlocked,
        ...(unlocked ? {unlockedAt: old.get("unlockedAt") ?? FieldValue.serverTimestamp()} : {})};
      if (old.exists) transaction.update(old.ref, data); else transaction.create(old.ref, data);
    }
  });
}
