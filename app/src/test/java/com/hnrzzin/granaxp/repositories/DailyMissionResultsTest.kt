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
            "correct" to true, "alreadyCompleted" to true,
        ))
        assertTrue(repeated.correct)
        assertTrue(repeated.alreadyCompleted)
    }
}
