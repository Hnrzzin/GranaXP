package com.hnrzzin.granaxp.repositories

import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.Source
import com.hnrzzin.granaxp.model.ActivityType
import com.hnrzzin.granaxp.model.LessonBlock
import com.hnrzzin.granaxp.model.LessonSelectionMode
import com.hnrzzin.granaxp.model.LessonModel
import com.hnrzzin.granaxp.model.LessonProgressModel
import kotlinx.coroutines.tasks.await

class LessonRepository(private val userId: String) {
    private val db = FirebaseFirestore.getInstance()
    private val lessonsCollection = db.collection("lessons")
    private val progressCollection = db.collection("users").document(userId).collection("lessonProgress")

    suspend fun getLessons(): List<LessonModel> {
        // Only a server response can confirm that the catalog is really empty.
        val result = lessonsCollection.orderBy("order").get(Source.SERVER).await()
        return result.mapNotNull { it.toObject(LessonModel::class.java) }
            .sortedWith(compareBy<LessonModel> { it.order }.thenBy { it.id })
    }

    suspend fun openLesson(lessonId: String): LessonOpenResult {
        require(lessonId.isNotBlank()) { "A lição não possui ID." }
        val lesson = lessonsCollection.document(lessonId)
        check(lesson.get(Source.SERVER).await().exists()) { "Lição não encontrada." }
        val blocks = lesson.collection("blocks").orderBy("order")
            .get(Source.SERVER).await().documents.map { document ->
                val block = document.toObject(LessonBlock::class.java)
                    ?: error("Bloco inválido: ${document.id}")
                block.copy(
                    id = document.id,
                    activityType = document.getString("activityType")?.let(ActivityType::valueOf),
                    selectionMode = if (block.type == "ACTIVITY") {
                        if (block.correctAnswerIds.size > 1) LessonSelectionMode.MULTIPLE
                        else LessonSelectionMode.SINGLE
                    } else null,
                )
            }
        val progressRef = progressCollection.document(lessonId)
        db.runTransaction { transaction ->
            if (!transaction.get(progressRef).exists()) {
                transaction.set(progressRef, mapOf(
                    "lessonId" to lessonId, "isCompleted" to false,
                    "completedActivityIds" to emptyList<String>(),
                    "lastAccessed" to FieldValue.serverTimestamp(), "completedAt" to null,
                ))
            }
        }.await()
        val progress = getLessonProgress().find { it.lessonId == lessonId }
        return LessonOpenResult(blocks, progress?.completedActivityIds.orEmpty().toSet())
    }

    suspend fun completeLesson(lessonId: String): LessonCompletionResult {
        require(lessonId.isNotBlank()) { "A lição não possui ID." }
        val lesson = lessonsCollection.document(lessonId).get(Source.SERVER).await()
        check(lesson.exists()) { "Lição não encontrada." }
        val required = lessonsCollection.document(lessonId).collection("blocks")
            .get(Source.SERVER).await().documents
            .filter { it.getString("type") == "ACTIVITY" }.map { it.id }.toSet()
        check(required.isNotEmpty()) { "Atividades da lição indisponíveis." }
        if (getLessonProgress().any { it.lessonId == lessonId && it.isCompleted }) {
            return LessonCompletionResult(0, false, true)
        }
        val progressRef = progressCollection.document(lessonId)
        val firstCompletion = db.runTransaction { transaction ->
            val progress = transaction.get(progressRef)
            check(progress.exists()) { "Abra a lição antes de concluí-la." }
            if (progress.getBoolean("isCompleted") == true) return@runTransaction false
            val completed = (progress.get("completedActivityIds") as? List<*>)
                ?.filterIsInstance<String>()?.toSet().orEmpty()
            check(completed.containsAll(required)) { "Conclua todas as atividades." }
            transaction.update(progressRef, mapOf(
                "isCompleted" to true, "completedAt" to FieldValue.serverTimestamp(),
            ))
            true
        }.await()
        return LessonCompletionResult(if (firstCompletion) lesson.getLong("xpReward")?.toInt() ?: 0 else 0,
            false, !firstCompletion)
    }

    suspend fun submitLessonActivity(
        lessonId: String,
        blockId: String,
        selectedAnswerIds: Set<String>,
    ): ActivitySubmissionResult {
        require(lessonId.isNotBlank()) { "A lição não possui ID." }
        require(blockId.isNotBlank()) { "A atividade não possui ID." }
        require(selectedAnswerIds.isNotEmpty()) { "Selecione uma alternativa." }
        val block = lessonsCollection.document(lessonId).collection("blocks")
            .document(blockId).get(Source.SERVER).await()
        check(block.exists() && block.getString("type") == "ACTIVITY") { "Atividade não encontrada." }
        val alternatives = (block.get("alternatives") as? Map<*, *>)
            ?.keys?.filterIsInstance<String>()?.toSet().orEmpty()
        val answers = (block.get("correctAnswerIds") as? List<*>)
            ?.filterIsInstance<String>()?.toSet().orEmpty()
        if (!isCorrectEducationalAnswer(selectedAnswerIds, answers, alternatives)) {
            return ActivitySubmissionResult(false, block.getString("feedback") ?: "Tente novamente.", emptySet())
        }
        val progressRef = progressCollection.document(lessonId)
        val completed = db.runTransaction { transaction ->
            val progress = transaction.get(progressRef)
            check(progress.exists()) { "Abra a lição antes de responder." }
            val previous = (progress.get("completedActivityIds") as? List<*>)
                ?.filterIsInstance<String>().orEmpty()
            if (blockId !in previous && progress.getBoolean("isCompleted") != true) {
                transaction.update(progressRef, mapOf(
                    "completedActivityIds" to (previous + blockId),
                    "lastActivityId" to blockId,
                    "lastAccessed" to FieldValue.serverTimestamp(),
                ))
            }
            (previous + blockId).toSet()
        }.await()
        return ActivitySubmissionResult(true, null, completed)
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
