import {Timestamp, type DocumentData, type Firestore, type Transaction, type DocumentReference} from "firebase-admin/firestore";

/** Only a trusted administrative issuer may create authorizations. No client declaration produces one. */
export async function goalRewardClaim(
  transaction: Transaction, db: Firestore, uid: string, goalId: string, goal: DocumentData, newAmount: number,
): Promise<DocumentReference | undefined> {
  if (goal.rewardPolicyVersion !== 1 || !(goal.createdAt instanceof Timestamp) || goal.completedAt != null ||
      !(goal.targetAmount > 0) || goal.currentAmount >= goal.targetAmount || newAmount < goal.targetAmount) return;
  const authorization = (await transaction.get(db.doc(`users/${uid}/goalRewardAuthorizations/${goalId}`))).data();
  if (!authorization || authorization.version !== 1 || authorization.uid !== uid || authorization.goalId !== goalId ||
      typeof authorization.conditionId !== "string" || !/^[A-Za-z0-9_-]{8,128}$/.test(authorization.conditionId) ||
      authorization.targetAmount !== goal.targetAmount || typeof authorization.verifiedAmount !== "number" ||
      !Number.isFinite(authorization.verifiedAmount) || authorization.verifiedAmount < goal.targetAmount ||
      !(authorization.goalCreatedAt instanceof Timestamp) || !authorization.goalCreatedAt.isEqual(goal.createdAt) ||
      !(authorization.verifiedAt instanceof Timestamp) || authorization.verifiedAt.toMillis() < goal.createdAt.toMillis() ||
      authorization.verifiedAt.toMillis() > Date.now() || !(authorization.expiresAt instanceof Timestamp) ||
      authorization.expiresAt.toMillis() <= Date.now()) return;
  const claim = db.doc(`users/${uid}/goalRewardClaims/${authorization.conditionId}`);
  return (await transaction.get(claim)).exists ? undefined : claim;
}
