package com.hnrzzin.granaxp.viewmodel

import com.hnrzzin.granaxp.model.ActivityType
import com.hnrzzin.granaxp.model.LessonBlock
import com.hnrzzin.granaxp.model.LessonSelectionMode
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class LessonActivityStateTest {

    @Test
    fun activityWithSingleSelectionModeUsesSingleSelection() {
        val block = activity(selectionMode = LessonSelectionMode.SINGLE)
        val first = updateSelectedAnswers(block, emptySet(), "a")
        val second = updateSelectedAnswers(block, first, "b")

        assertEquals(setOf("a"), first)
        assertEquals(setOf("b"), second)
    }

    @Test
    fun activityWithMultipleSelectionModeTogglesMultipleSelection() {
        val block = activity(selectionMode = LessonSelectionMode.MULTIPLE)

        val selectedA = updateSelectedAnswers(block, emptySet(), "a")
        val selectedAC = updateSelectedAnswers(block, selectedA, "c")
        val selectedC = updateSelectedAnswers(block, selectedAC, "a")

        assertEquals(setOf("a"), selectedA)
        assertEquals(setOf("a", "c"), selectedAC)
        assertEquals(setOf("c"), selectedC)
    }

    @Test
    fun onlyActivityBlocksAreRequiredForLessonCompletion() {
        val state = LessonContentUiState.Success(
            lessonId = "lesson-1",
            blocks = listOf(
                LessonBlock(id = "text-1", type = "TEXT"),
                activity(id = "activity-1"),
                LessonBlock(id = "video-1", type = "VIDEO"),
                activity(id = "activity-2"),
            ),
            completedActivityIds = setOf("activity-1"),
        )

        assertEquals(setOf("activity-1", "activity-2"), state.requiredActivityIds)
        assertFalse(state.canComplete)
        assertTrue(
            state.copy(completedActivityIds = setOf("activity-1", "activity-2")).canComplete,
        )
    }

    @Test
    fun lessonWithoutActivitiesCanBeCompleted() {
        val state = LessonContentUiState.Success(
            lessonId = "lesson-1",
            blocks = listOf(LessonBlock(id = "summary", type = "SUMMARY")),
        )

        assertTrue(state.canComplete)
    }

    @Test
    fun everyApprovedActivityTypeAcceptsTheSharedAnswerContract() {
        val blocks = ActivityType.entries.mapIndexed { index, activityType ->
            activity(id = "activity-$index").copy(activityType = activityType)
        }

        validateLessonActivities(blocks)
    }

    @Test(expected = IllegalStateException::class)
    fun activityWithoutSelectionModeIsRejected() {
        validateLessonActivities(
            listOf(activity().copy(selectionMode = null)),
        )
    }

    @Test(expected = IllegalStateException::class)
    fun activityWithoutAlternativesIsRejected() {
        validateLessonActivities(
            listOf(activity().copy(alternatives = emptyMap())),
        )
    }

    private fun activity(
        id: String = "activity-1",
        selectionMode: LessonSelectionMode = LessonSelectionMode.SINGLE,
    ) = LessonBlock(
        id = id,
        type = "ACTIVITY",
        activityType = ActivityType.MULTIPLE_CHOICE,
        alternatives = mapOf(
            "a" to "Alternativa A",
            "b" to "Alternativa B",
            "c" to "Alternativa C",
        ),
        selectionMode = selectionMode,
        feedback = "Revise o conteúdo e tente novamente.",
    )
}
