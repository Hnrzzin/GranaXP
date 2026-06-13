package com.hnrzzin.granaxp.repositories

import com.google.firebase.firestore.FirebaseFirestore
import com.hnrzzin.granaxp.model.AchievementModel
import com.hnrzzin.granaxp.model.AchievementProgressModel
import kotlinx.coroutines.tasks.await

class AchievementRepository(val userID: String) {
    private val db = FirebaseFirestore.getInstance()
    private val achievementsCollection = db.collection("Achievements")
    private val achievementProgress = db.collection("Users").document(userID).collection("AchievementProgress")

    suspend fun getAchievements(): List<AchievementModel> {
        return try {
            val result = achievementsCollection.get().await()
            result.mapNotNull { it.toObject(AchievementModel::class.java) }
        } catch (e: Exception) {
            println("Falha ao buscar conquistas: $e")
            emptyList()
        }
    }

    suspend fun getAchievementProgress(): List<AchievementProgressModel> {
        return try {
            val result = achievementProgress
                .get().await()
            result.mapNotNull { it.toObject(AchievementProgressModel::class.java) }
        } catch (e: Exception) {
            println("Falha ao buscar progresso: $e")
            emptyList()
        }
    }

    suspend fun updateAchievementProgress(progressId: String, isComplete: Boolean) {
        val updates = mapOf("achievementComplete" to isComplete)
        try {
            achievementProgress.document(progressId)
                .update(updates).await()
            println("Conquista atualizada!")
        } catch (e: Exception) {
            println("Falha ao atualizar conquista: $e")
        }
    }
}

