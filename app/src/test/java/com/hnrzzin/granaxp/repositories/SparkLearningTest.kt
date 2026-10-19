package com.hnrzzin.granaxp.repositories

import com.hnrzzin.granaxp.model.LessonModel
import com.hnrzzin.granaxp.model.LessonProgressModel
import com.hnrzzin.granaxp.model.UserModel
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SparkLearningTest {
    @Test fun answerRequiresExactCatalogSet() {
        assertTrue(isCorrectEducationalAnswer(setOf("a", "b"), setOf("a", "b"), setOf("a", "b", "c")))
        assertFalse(isCorrectEducationalAnswer(setOf("a"), setOf("a", "b"), setOf("a", "b", "c")))
        assertFalse(isCorrectEducationalAnswer(setOf("a", "c"), setOf("a", "b"), setOf("a", "b", "c")))
        assertFalse(isCorrectEducationalAnswer(setOf("x"), setOf("a", "b"), setOf("a", "b", "c")))
    }

    @Test fun displayXpCountsCompletedLessonsOnce() {
        val lessons = listOf(
            LessonModel(id = "first", xpReward = 50),
            LessonModel(id = "second", xpReward = 100),
            LessonModel(id = "third", xpReward = 150),
        )
        val progress = listOf(
            LessonProgressModel(lessonId = "first", isCompleted = true),
            LessonProgressModel(lessonId = "first", isCompleted = true),
            LessonProgressModel(lessonId = "second", isCompleted = false),
        )
        assertEquals(50, educationalXpTotal(lessons, progress))
        assertEquals(300, educationalXpTotal(lessons,
            progress + LessonProgressModel(lessonId = "second", isCompleted = true) +
                LessonProgressModel(lessonId = "third", isCompleted = true)))
    }

    @Test fun streakUsesDistinctDatesAndExpiresAfterFourMissedDays() {
        val dates = listOf("2026-10-01", "2026-10-01", "2026-10-03", "2026-10-05")
        val active = educationalStreak(dates, "2026-10-06")
        assertEquals(3, active.currentStreak)
        assertEquals(3, active.highestStreak)
        assertEquals(listOf(3), active.claimedMilestones)
        assertEquals(0, educationalStreak(dates, "2026-10-10").currentStreak)
    }

    @Test fun displayLevelUsesExistingCurveWithoutPersistingXp() {
        val stored = UserModel(level = 1, xp = 0, nextLevelXp = 100)
        val displayed = educationalDisplayUser(stored, 300)
        assertEquals(3, displayed.level)
        assertEquals(80, displayed.xp)
        assertEquals(144, displayed.nextLevelXp)
        assertEquals(0, stored.xp)
    }
}
