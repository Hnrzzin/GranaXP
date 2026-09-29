package com.hnrzzin.granaxp.repositories

import com.google.firebase.firestore.FirebaseFirestore
import com.hnrzzin.granaxp.model.LessonBlock
import com.hnrzzin.granaxp.model.LessonModel
import com.hnrzzin.granaxp.model.LessonProgressModel
import kotlinx.coroutines.tasks.await

class LessonRepository(private val userId: String) {
    private val db = FirebaseFirestore.getInstance()
    private val lessonsCollection = db.collection("lessons")
    private val progressCollection = db.collection("users").document(userId).collection("lessonProgress")

    suspend fun getLessons(): List<LessonModel> {
        return try {
            val result = lessonsCollection.orderBy("order").get().await()
            result.mapNotNull { it.toObject(LessonModel::class.java) }
                .sortedWith(compareBy<LessonModel> { it.order }.thenBy { it.id })
        } catch (e: Exception) {
            println("Falha ao buscar lições: $e")
            emptyList()
        }
    }

    suspend fun getLessonsByModule(moduleId: String): List<LessonModel> {
        return lessonsCollection.whereEqualTo("moduleId", moduleId).get().await()
            .mapNotNull { it.toObject(LessonModel::class.java) }
            .sortedWith(compareBy<LessonModel> { it.order }.thenBy { it.id })
    }


    // esta no repo do Lesson pq ela depende de lesson dai faz mais sentido colocar aqui mesmo ao inves de criar um arquivo separado
    suspend fun getLessonBlocks(lessonId: String): List<LessonBlock> {
        return lessonsCollection.document(lessonId).collection("blocks")
            .orderBy("order")
            .get()
            .await()
            .map { document ->
                document.toObject(LessonBlock::class.java).copy(id = document.id)
            }
            .sortedWith(compareBy<LessonBlock> { it.order }.thenBy { it.id })
    }



    // operações relacionadas ao progresso do usuario
    suspend fun updateLessonProgress(progressId: String, isCompleted: Boolean) {
        if (progressId.isEmpty()) return
        val updates = mapOf("isCompleted" to isCompleted)
        try {
            progressCollection.document(progressId).update(updates).await()
        } catch (e: Exception) {
            println("Falha ao atualizar progresso: $e")
        }
    }

    suspend fun createLessonProgress(lessonId: String): String? {
        val progress = LessonProgressModel(userId = userId, lessonId = lessonId)
        return try {
            val ref = progressCollection.add(progress).await()
            ref.id
        } catch (e: Exception) {
            println("Falha ao criar progresso: $e")
            null
        }
    }

    suspend fun deleteLessonProgress(progressId: String): Boolean {
        return try {
            progressCollection.document(progressId).delete().await()
            true
        } catch (e: Exception) {
            println("Error deleting lesson progress: ${e.message}")
            false
        }
    }
    // Retorna TODO o progresso de lições do usuário (para a trilha completa)
    suspend fun getAllLessonProgress(): List<LessonProgressModel> {
        return try {
            val result = progressCollection.get().await()
            result.mapNotNull { it.toObject(LessonProgressModel::class.java) }
        } catch (e: Exception) {
            println("Falha ao buscar progresso de lições: $e")
            emptyList()
        }
    }
}
