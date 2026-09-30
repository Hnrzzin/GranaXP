package com.hnrzzin.granaxp.repositories

import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.functions.FirebaseFunctions
import com.hnrzzin.granaxp.model.LessonModel
import com.hnrzzin.granaxp.model.LessonProgressModel
import kotlinx.coroutines.tasks.await

class LessonRepository(private val userId: String) {
    private val db = FirebaseFirestore.getInstance()
    private val lessonsCollection = db.collection("lessons")
    private val progressCollection = db.collection("users").document(userId).collection("lessonProgress")
    private val functions: FirebaseFunctions = FirebaseFunctionsProvider.instance

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


    suspend fun openLesson(lessonId: String): LessonOpenResult {
        require(lessonId.isNotBlank()) { "A lição não possui ID." }
        val result = functions.getHttpsCallable("openLesson")
            .call(mapOf("lessonId" to lessonId))
            .await()
        return parseLessonOpenResult(result.data)
    }

    suspend fun completeLesson(lessonId: String): LessonCompletionResult {
        require(lessonId.isNotBlank()) { "A lição não possui ID." }
        val result = functions.getHttpsCallable("completeLesson")
            .call(mapOf("lessonId" to lessonId))
            .await()
        return parseLessonCompletionResult(result.data)
    }

    suspend fun submitLessonActivity(
        lessonId: String,
        blockId: String,
        selectedAnswerIds: Set<String>,
    ): ActivitySubmissionResult {
        require(lessonId.isNotBlank()) { "A lição não possui ID." }
        require(blockId.isNotBlank()) { "A atividade não possui ID." }
        require(selectedAnswerIds.isNotEmpty()) { "Selecione uma alternativa." }
        val result = functions.getHttpsCallable("submitLessonActivity")
            .call(mapOf(
                "lessonId" to lessonId,
                "blockId" to blockId,
                "selectedAnswerIds" to selectedAnswerIds.sorted(),
            ))
            .await()
        return parseActivitySubmissionResult(result.data)
    }

    // Visão lógica para a trilha. Durante uma migração parcial, combina o
    // documento legado e o determinístico sem perder conclusão ou atividades.
    suspend fun getLessonProgress(): List<LessonProgressModel> {
        return consolidateLessonProgressDocuments(getLessonProgressDocuments())
    }

    private suspend fun getLessonProgressDocuments(): List<LessonProgressModel> {
        val result = progressCollection.get().await()
        return result.mapNotNull { it.toObject(LessonProgressModel::class.java) }
    }
}
