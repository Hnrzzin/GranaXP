package com.hnrzzin.granaxp.repositories

import com.google.firebase.Timestamp
import com.google.firebase.firestore.FieldValue
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



    // Novos progressos usam lessonId como ID. Registros legados continuam no
    // documento original até uma migração de dados ser explicitamente autorizada.
    suspend fun startOrTouchLessonProgress(
        lessonId: String,
        existingProgress: LessonProgressModel?,
    ): String {
        val now = Timestamp.now()
        val documentId = progressDocumentId(lessonId, existingProgress)
        val document = progressCollection.document(documentId)

        if (existingProgress == null) {
            document.set(newLessonProgressData(lessonId, now)).await()
        } else {
            document.update("lastAccessed", now).await()
        }

        return documentId
    }

    suspend fun completeLessonProgress(
        lessonId: String,
        existingProgress: LessonProgressModel?,
    ): String {
        val now = Timestamp.now()
        val documentId = progressDocumentId(lessonId, existingProgress)
        val document = progressCollection.document(documentId)

        if (existingProgress == null) {
            document.set(
                newLessonProgressData(
                    lessonId = lessonId,
                    now = now,
                    isCompleted = true,
                ),
            ).await()
        } else {
            document.update(lessonCompletionUpdates(existingProgress, now)).await()
        }

        return documentId
    }

    suspend fun addCompletedActivity(
        progressDocumentId: String,
        blockId: String,
    ) {
        require(progressDocumentId.isNotBlank()) { "O progresso da lição não possui ID." }
        require(blockId.isNotBlank()) { "A atividade não possui ID." }

        progressCollection.document(progressDocumentId)
            .update("completedActivityIds", FieldValue.arrayUnion(blockId))
            .await()
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
    // Visão lógica para a trilha. Durante uma migração parcial, combina o
    // documento legado e o determinístico sem perder conclusão ou atividades.
    suspend fun getLessonProgress(): List<LessonProgressModel> {
        return consolidateLessonProgressDocuments(getLessonProgressDocuments())
    }

    // Visão física usada pela exclusão de conta, que precisa remover todos os
    // documentos, inclusive duplicatas temporárias de uma futura migração.
    suspend fun getLessonProgressDocuments(): List<LessonProgressModel> {
        val result = progressCollection.get().await()
        return result.mapNotNull { it.toObject(LessonProgressModel::class.java) }
    }
}
