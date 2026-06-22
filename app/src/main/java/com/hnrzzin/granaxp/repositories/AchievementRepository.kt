package com.hnrzzin.granaxp.repositories

import com.google.firebase.firestore.FirebaseFirestore
import com.hnrzzin.granaxp.model.AchievementModel
import com.hnrzzin.granaxp.model.AchievementProgressModel
import kotlinx.coroutines.tasks.await

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

    suspend fun createAchievementProgress(
        userId: String,
        achievementId: String,
        currentProgress: Int,
        isUnlocked: Boolean
    ): Boolean {
        return try {
            val progress = AchievementProgressModel(
                userId = userId,
                achievementId = achievementId,
                currentProgress = currentProgress,
                isUnlocked = isUnlocked
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
}