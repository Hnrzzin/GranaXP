package com.hnrzzin.granaxp.viewmodel

import com.hnrzzin.granaxp.model.ActivityType
import com.hnrzzin.granaxp.model.DailyMissionModel
import com.hnrzzin.granaxp.model.LessonSelectionMode
import org.junit.Assert.assertEquals
import org.junit.Test

class DailyMissionStateTest {
    private val mission = DailyMissionModel(
        id = "mission-a", title = "Missão", description = "Escolha",
        activityType = ActivityType.MULTIPLE_CHOICE,
        alternatives = mapOf("a" to "A", "b" to "B", "c" to "C"),
        selectionMode = LessonSelectionMode.MULTIPLE,
    )

    @Test fun `seleção múltipla alterna IDs válidos sem aceitar IDs inventados`() {
        assertEquals(setOf("a"), selectDailyMissionAnswer(mission, emptySet(), "a"))
        assertEquals(setOf("a", "c"), selectDailyMissionAnswer(mission, setOf("a"), "c"))
        assertEquals(setOf("c"), selectDailyMissionAnswer(mission, setOf("a", "c"), "a"))
        assertEquals(setOf("a"), selectDailyMissionAnswer(mission, setOf("a"), "inventado"))
    }

    @Test fun `seleção única substitui a alternativa anterior`() {
        val single = mission.copy(selectionMode = LessonSelectionMode.SINGLE)
        assertEquals(setOf("b"), selectDailyMissionAnswer(single, setOf("a"), "b"))
    }

    @Test fun `dia oficial vira à meia-noite em São Paulo`() {
        assertEquals("2026-09-30", missionDateAt(1790823599000L))
        assertEquals("2026-10-01", missionDateAt(1790823600000L))
        assertEquals(1000L, millisUntilNextMissionDate(1790823599000L))
    }
}
