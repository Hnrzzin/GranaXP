package com.hnrzzin.granaxp.repositories

import com.google.firebase.Timestamp
import com.google.firebase.firestore.FirebaseFirestore
import com.hnrzzin.granaxp.model.AchievementModel
import com.hnrzzin.granaxp.model.AchievementProgressModel
import kotlinx.coroutines.tasks.await

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
        return try {
            db.collection("achievements").get().await().toObjects(AchievementModel::class.java)
        } catch (e: Exception) {
            println("Error fetching achievements: ${e.message}")
            emptyList()
        }
    }

    suspend fun getAchievementProgress(userId: String): List<AchievementProgressModel> {
        return try {
            db.collection("achievementProgress")
                .whereEqualTo("userId", userId)
                .get()
                .await()
                .toObjects(AchievementProgressModel::class.java)
        } catch (e: Exception) {
            println("Error fetching achievement progress: ${e.message}")
            emptyList()
        }
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
