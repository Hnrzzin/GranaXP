package com.hnrzzin.granaxp.repositories

import com.hnrzzin.granaxp.model.LessonSelectionMode
import com.hnrzzin.granaxp.model.orderedAlternatives
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class DailyMissionResultsTest {
    @Test fun `projeção pública é interpretada sem gabarito e conserva ordem de alternativas`() {
        val result = parseDailyMissionOpenResult(mapOf(
            "available" to true, "date" to "2026-10-01", "isCompleted" to false,
            "streak" to mapOf(
                "currentStreak" to 2L, "highestStreak" to 7L,
                "lastMissionCompletedDate" to "2026-09-30", "claimedMilestones" to listOf(3L, 7L),
            ),
            "mission" to mapOf(
                "id" to "mission-a", "title" to "Missão", "description" to "Escolha",
                "activityType" to "TRUE_FALSE",
                "alternatives" to mapOf("false" to "Falso", "true" to "Verdadeiro"),
                "selectionMode" to "SINGLE", "chartImageUrl" to null,
            ),
        ))
        assertTrue(result.available)
        assertFalse(result.isCompleted)
        assertEquals(LessonSelectionMode.SINGLE, result.mission!!.selectionMode)
        assertEquals(listOf("true", "false"), result.mission!!.orderedAlternatives().map { it.key })
        assertEquals(2, result.streak!!.currentStreak)
        assertEquals(7, result.streak!!.highestStreak)
        assertEquals(listOf(3, 7), result.streak!!.claimedMilestones)
    }

    @Test fun `resposta indisponível não inventa missão`() {
        val result = parseDailyMissionOpenResult(mapOf("available" to false))
        assertFalse(result.available)
        assertEquals(null, result.mission)
    }

    @Test fun `resultado errado preserva feedback e acerto repetido é identificado`() {
        assertEquals("Revise.", parseDailyMissionSubmissionResult(mapOf(
            "correct" to false, "alreadyCompleted" to false, "feedback" to "Revise.",
        )).feedback)
        val repeated = parseDailyMissionSubmissionResult(mapOf(
            "correct" to true, "alreadyCompleted" to true, "xpEarned" to 0L,
            "leveledUp" to false,
            "streak" to mapOf("currentStreak" to 3L, "highestStreak" to 3L,
                "lastMissionCompletedDate" to "2026-10-03", "claimedMilestones" to listOf(3L)),
        ))
        assertTrue(repeated.correct)
        assertTrue(repeated.alreadyCompleted)
        assertEquals(0, repeated.xpEarned)
        assertEquals(3, repeated.streak!!.currentStreak)
    }

    @Test fun `resultado de marco preserva XP e nova sequência autoritativa`() {
        val result = parseDailyMissionSubmissionResult(mapOf(
            "correct" to true, "alreadyCompleted" to false, "xpEarned" to 100L,
            "leveledUp" to true,
            "streak" to mapOf("currentStreak" to 7L, "highestStreak" to 7L,
                "lastMissionCompletedDate" to "2026-10-07", "claimedMilestones" to listOf(3L, 7L)),
        ))
        assertEquals(100, result.xpEarned)
        assertTrue(result.leveledUp)
        assertEquals(listOf(3, 7), result.streak!!.claimedMilestones)
    }
}
