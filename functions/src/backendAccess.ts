import {defineString} from "firebase-functions/params";
import {HttpsError, type CallableRequest} from "firebase-functions/v2/https";

export const callableNames = ["openLesson", "submitLessonActivity", "completeLesson", "createGoal",
  "updateGoalProgress", "updateGoalDetails", "deleteAccount", "openDailyMission", "submitDailyMissionAnswer"] as const;
export type CallableName = typeof callableNames[number];
const callableModes = defineString("BACKEND_CALLABLE_MODES", {default: "{}"});
const pilotUid = defineString("BACKEND_PILOT_UID", {default: ""});
const achievementSyncMode = defineString("ACHIEVEMENT_SYNC_MODE", {default: "disabled"});

function admitted(mode: unknown, uid: string): boolean {
  if (mode === "open") return true;
  if (mode !== "pilot") return false;
  const pilot = pilotUid.value();
  return typeof pilot === "string" && pilot.length > 0 && pilot.length <= 128 && pilot.trim() === pilot && uid === pilot;
}

export function requireCallableUid(name: CallableName, request: CallableRequest<unknown>): string {
  const uid = request.auth?.uid;
  if (!uid) throw new HttpsError("unauthenticated", "É necessário estar autenticado.");
  let modes: unknown;
  try { modes = JSON.parse(callableModes.value()); } catch { /* fail closed without logging configuration */ }
  if (!modes || typeof modes !== "object" || Array.isArray(modes) || Object.entries(modes).some(([key, mode]) =>
    !callableNames.includes(key as CallableName) || !["disabled", "pilot", "open"].includes(mode))) {
    throw new HttpsError("failed-precondition", "Acesso ao backend indisponível.");
  }
  const mode = Object.hasOwn(modes, name) ? (modes as Record<string, unknown>)[name] : "disabled";
  if (admitted(mode, uid)) return uid;
  if (mode === "pilot") {
    const pilot = pilotUid.value();
    if (typeof pilot === "string" && pilot.length > 0 && pilot.length <= 128 && pilot.trim() === pilot) {
      throw new HttpsError("permission-denied", "Acesso ao backend não autorizado.");
    }
  }
  throw new HttpsError("failed-precondition", "Acesso ao backend indisponível.");
}

// Firestore events are trusted transport, but their availability is independent of the nine callables.
export function maySyncAchievements(uid: string): boolean {
  return admitted(achievementSyncMode.value(), uid);
}
