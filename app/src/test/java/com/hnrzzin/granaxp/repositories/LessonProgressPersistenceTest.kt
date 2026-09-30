package com.hnrzzin.granaxp.repositories

import com.google.firebase.Timestamp
import com.hnrzzin.granaxp.model.LessonProgressModel
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

@Suppress("DEPRECATION")
class LessonProgressPersistenceTest {

    @Test
    fun mixedLegacyAndDeterministicDocumentsBecomeOneSafeLogicalProgress() {
        val legacyCompletion = Timestamp(1_700_000_000, 0)
        val deterministicAccess = Timestamp(1_800_000_000, 0)
        val progress = listOf(
            LessonProgressModel(
                id = "legacy-auto-id",
                userId = "user-1",
                lessonId = "lesson-1",
                isCompleted = true,
                completedActivityIds = listOf("activity-1"),
                lastAccessed = Timestamp(1_600_000_000, 0),
                completedAt = legacyCompletion,
            ),
            LessonProgressModel(
                id = "lesson-1",
                lessonId = "lesson-1",
                isCompleted = false,
                completedActivityIds = listOf("activity-2", "activity-1"),
                lastAccessed = deterministicAccess,
            ),
        )

        val consolidated = consolidateLessonProgressDocuments(progress).single()

        assertEquals("lesson-1", consolidated.id)
        assertEquals("lesson-1", consolidated.lessonId)
        assertTrue(consolidated.isCompleted)
        assertEquals(listOf("activity-1", "activity-2"), consolidated.completedActivityIds)
        assertEquals(deterministicAccess, consolidated.lastAccessed)
        assertEquals(legacyCompletion, consolidated.completedAt)
    }

}
