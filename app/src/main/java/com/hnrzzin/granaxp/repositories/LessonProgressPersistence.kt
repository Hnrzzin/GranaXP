package com.hnrzzin.granaxp.repositories

import com.google.firebase.Timestamp
import com.hnrzzin.granaxp.model.LessonProgressModel

internal fun newLessonProgressData(
    lessonId: String,
    now: Timestamp,
    isCompleted: Boolean = false,
): Map<String, Any?> = mapOf(
    "lessonId" to lessonId,
    "isCompleted" to isCompleted,
    "completedActivityIds" to emptyList<String>(),
    "lastAccessed" to now,
    "completedAt" to now.takeIf { isCompleted },
)

internal fun lessonCompletionUpdates(
    progress: LessonProgressModel?,
    now: Timestamp,
): Map<String, Any> = buildMap {
    put("isCompleted", true)
    if (progress?.completedAt == null) {
        put("completedAt", now)
    }
}

internal fun progressDocumentId(
    lessonId: String,
    progress: LessonProgressModel?,
): String = progress?.id?.takeIf { it.isNotBlank() } ?: lessonId

@Suppress("DEPRECATION")
internal fun consolidateLessonProgressDocuments(
    documents: List<LessonProgressModel>,
): List<LessonProgressModel> = documents
    .groupBy { it.lessonId }
    .map { (lessonId, duplicates) ->
        val canonical = duplicates.find { it.id == lessonId } ?: duplicates.first()
        val completedAt = duplicates.mapNotNull { it.completedAt }.minOrNull()
        val lastAccessed = duplicates.maxOf { it.lastAccessed }

        canonical.copy(
            userId = canonical.userId.ifBlank {
                duplicates.firstNotNullOfOrNull { it.userId.takeIf(String::isNotBlank) }.orEmpty()
            },
            isCompleted = duplicates.any { it.isCompleted },
            completedActivityIds = duplicates
                .flatMap { it.completedActivityIds }
                .distinct()
                .sorted(),
            lastAccessed = lastAccessed,
            completedAt = completedAt,
        )
    }
