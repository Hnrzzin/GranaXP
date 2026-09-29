package com.hnrzzin.granaxp.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class LearningContentModelsTest {

    @Test
    fun plannedLessonModelKeepsModuleAndRequiredContentMetadata() {
        val lesson = LessonModel(
            id = "lesson-1",
            moduleId = "module-1",
            title = "Orçamento",
            description = "Resumo",
            duration = 5,
            xpReward = 100,
            order = 2,
        )

        assertEquals("module-1", lesson.moduleId)
        assertEquals(5, lesson.duration)
        assertEquals(2, lesson.order)
    }

    @Suppress("DEPRECATION")
    @Test
    fun lessonModelKeepsLegacyFieldsDuringCompatibilityWindow() {
        val lesson = LessonModel(
            category = "Orçamento",
            videoUrl = "https://example.com/video",
        )

        assertEquals("Orçamento", lesson.category)
        assertEquals("https://example.com/video", lesson.videoUrl)
        assertEquals("", lesson.moduleId)
    }

    @Test
    fun lessonBlockRepresentsActivityFieldsWithoutProgressState() {
        val block = LessonBlock(
            id = "activity-1",
            type = "ACTIVITY",
            order = 4,
            title = "Calcule",
            content = "Quanto deve ser reservado?",
            activityType = ActivityType.MULTIPLE_CHOICE,
            alternatives = mapOf("b" to "R$ 600", "a" to "R$ 300"),
            correctAnswerIds = listOf("b"),
            feedback = "20% corresponde a R$ 600.",
        )

        assertEquals(listOf("a", "b"), block.orderedAlternatives().map { it.key })
        assertNull(block.chartImageUrl)
    }

    @Test
    fun trueFalseAlternativesAlwaysUseTrueThenFalse() {
        val block = LessonBlock(
            type = "ACTIVITY",
            activityType = ActivityType.TRUE_FALSE,
            alternatives = hashMapOf(
                "false" to "Falso",
                "true" to "Verdadeiro",
            ),
        )

        assertEquals(
            listOf("true", "false"),
            block.orderedAlternatives().map { it.key },
        )
    }

    @Test
    fun regularAlternativesAreSortedByStableId() {
        val block = LessonBlock(
            type = "ACTIVITY",
            activityType = ActivityType.FINANCIAL_SCENARIO,
            alternatives = hashMapOf(
                "c" to "Terceira",
                "a" to "Primeira",
                "b" to "Segunda",
            ),
        )

        assertEquals(
            listOf("a", "b", "c"),
            block.orderedAlternatives().map { it.key },
        )
    }
}
