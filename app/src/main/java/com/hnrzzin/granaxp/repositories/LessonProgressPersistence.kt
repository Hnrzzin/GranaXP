package com.hnrzzin.granaxp.repositories

import com.hnrzzin.granaxp.model.LessonProgressModel

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
