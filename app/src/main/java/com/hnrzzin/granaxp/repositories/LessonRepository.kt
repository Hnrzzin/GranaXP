package com.hnrzzin.granaxp.repositories

import com.google.firebase.firestore.FirebaseFirestore
import com.hnrzzin.granaxp.model.LessonModel
import com.hnrzzin.granaxp.model.LessonProgressModel
import kotlinx.coroutines.tasks.await

class LessonRepository(private val userId: String) {
    private val db = FirebaseFirestore.getInstance()
    private val lessonsCollection = db.collection("lessons")
    private val progressCollection = db.collection("users").document(userId).collection("lessonProgress")

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
            val result = progressCollection.get().await()
            result.mapNotNull { it.toObject(LessonProgressModel::class.java) }
        } catch (e: Exception) {
            println("Falha ao buscar progresso: $e")
            emptyList()
        }
    }

    suspend fun updateLessonProgress(progressId: String, isCompleted: Boolean) {
        if (progressId.isEmpty()) return
        val updates = mapOf("isCompleted" to isCompleted)
        try {
            progressCollection.document(progressId).update(updates).await()
        } catch (e: Exception) {
            println("Falha ao atualizar progresso: $e")
        }
    }

    suspend fun createLessonProgress(lessonId: String) {
        val progress = LessonProgressModel(
            userId = userId,
            lessonId = lessonId
        )
        try {
            progressCollection.add(progress).await()
        } catch (e: Exception) {
            println("Falha ao criar progresso: $e")
        }
    }
}