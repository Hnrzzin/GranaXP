package com.hnrzzin.granaxp.repositories

import com.google.firebase.firestore.FirebaseFirestore
import com.hnrzzin.granaxp.model.LessonModel
import com.hnrzzin.granaxp.model.LessonProgressModel
import kotlinx.coroutines.tasks.await

class LessonRepository(private val userID: String) {
    private val db = FirebaseFirestore.getInstance()
    private val lessonsCollection = db.collection("Lessons")
    private val lessonProgress = db.collection("Users").document(userID).collection("LessonProgress")

    suspend fun getLessons(): List<LessonModel> {
        return try {
            val result = lessonsCollection.get().await()
            result.mapNotNull { it.toObject(LessonModel::class.java) }
        } catch (e: Exception) {
            println("Falha ao buscar lições: $e")
            emptyList()
        }
    }

    suspend fun getLessonProgress(): List<LessonProgressModel> {
        return try {
            val result = lessonProgress
                .get().await()
            result.mapNotNull { it.toObject(LessonProgressModel::class.java) }
        } catch (e: Exception) {
            println("Falha ao buscar progresso: $e")
            emptyList()
        }
    }

    suspend fun updateLessonProgress(progressId: String, isComplete: Boolean) {
        val updates = mapOf("lessonComplete" to isComplete)
        try {
            lessonProgress.document(progressId)
                .update(updates).await()
            println("Progresso atualizado!")
        } catch (e: Exception) {
            println("Falha ao atualizar progresso: $e")
        }
    }
}