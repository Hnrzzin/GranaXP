package com.hnrzzin.granaxp.repositories

import com.google.firebase.Timestamp
import com.google.firebase.firestore.FirebaseFirestore
import com.hnrzzin.granaxp.model.AchievementModel
import com.hnrzzin.granaxp.model.AchievementProgressModel
import kotlinx.coroutines.tasks.await

private fun validStreakDefinition(data: Map<String, Any?>): Boolean {
    val requirement = data["requirementValue"] as? Number ?: return false
    val threshold = requirement.toInt()
    return requirement.toDouble() == threshold.toDouble() && threshold in setOf(7, 15, 30) &&
        data["category"] in setOf("EDUCACAO", "FINANCAS") &&
        listOf("title", "description", "icon").all { data[it] is String } &&
        (data["referenceId"] == null || data["referenceId"] is String)
}

internal fun <T> mapAchievementDocuments(
    documents: List<T>,
    fields: (T) -> Map<String, Any?>,
    decode: (T) -> AchievementModel?,
): List<AchievementModel> = documents.mapNotNull { document ->
    try {
        val data = fields(document)
        if (data["requirementType"] == "STREAK" && !validStreakDefinition(data)) null
        else decode(document)
    } catch (_: Exception) {
        null
    }
}

internal suspend fun <T> readAchievementCatalog(
    fetch: suspend () -> List<T>,
    fields: (T) -> Map<String, Any?>,
    decode: (T) -> AchievementModel?,
): List<AchievementModel> = mapAchievementDocuments(fetch(), fields, decode)

internal fun legacyAchievementProgressData(
    userId: String,
    achievementId: String,
    currentProgress: Int,
    isUnlocked: Boolean,
    lastUpdated: Timestamp,
): Map<String, Any> = mapOf(
    "userId" to userId,
    "achievementId" to achievementId,
    "currentProgress" to currentProgress,
    "isUnlocked" to isUnlocked,
    "lastUpdated" to lastUpdated,
)

class AchievementRepository {
    private val db = FirebaseFirestore.getInstance()

    suspend fun getAchievements(): List<AchievementModel> {
        return readAchievementCatalog(
            fetch = { db.collection("achievements").get().await().documents },
            fields = { it.data.orEmpty() },
            decode = { it.toObject(AchievementModel::class.java) },
        )
    }

    suspend fun getAchievementProgress(userId: String): List<AchievementProgressModel> {
        return db.collection("achievementProgress")
            .whereEqualTo("userId", userId)
            .get()
            .await()
            .toObjects(AchievementProgressModel::class.java)
    }

    suspend fun getUserAchievementProgress(userId: String): List<AchievementProgressModel> {
        return db.collection("users").document(userId).collection("achievementProgress")
            .get().await()
            .map { document ->
                document.toObject(AchievementProgressModel::class.java).also {
                    check(it.achievementId == document.id) {
                        "Progresso de conquista com ID inconsistente."
                    }
                }
            }
    }

    suspend fun createAchievementProgress(
        userId: String,
        achievementId: String,
        currentProgress: Int,
        isUnlocked: Boolean
    ): Boolean {
        return try {
            val progress = legacyAchievementProgressData(
                userId, achievementId, currentProgress, isUnlocked, Timestamp.now(),
            )
            db.collection("achievementProgress").add(progress).await()
            true
        } catch (e: Exception) {
            println("Error creating achievement progress: ${e.message}")
            false
        }
    }

    suspend fun updateAchievementProgress(
        progressId: String,
        currentProgress: Int,
        isUnlocked: Boolean
    ): Boolean {
        return try {
            db.collection("achievementProgress").document(progressId)
                .update("currentProgress", currentProgress, "isUnlocked", isUnlocked)
                .await()
            true
        } catch (e: Exception) {
            println("Error updating achievement progress: ${e.message}")
            false
        }
    }

    suspend fun unlockAchievement(progressId: String): Boolean {
        return try {
            db.collection("achievementProgress").document(progressId)
                .update("isUnlocked", true)
                .await()
            true
        } catch (e: Exception) {
            println("Error unlocking achievement: ${e.message}")
            false
        }
    }
    suspend fun deleteAchievementProgress(progressId: String): Boolean {
        return try {
            db.collection("achievementProgress").document(progressId).delete().await()
            true
        } catch (e: Exception) {
            println("Error deleting achievement progress: ${e.message}")
            false
        }
    }
}
