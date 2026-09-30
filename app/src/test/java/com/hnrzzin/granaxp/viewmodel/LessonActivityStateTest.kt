package com.hnrzzin.granaxp.viewmodel

import com.hnrzzin.granaxp.model.ActivityType
import com.hnrzzin.granaxp.model.LessonBlock
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class LessonActivityStateTest {

    @Test
    fun activityWithOneCorrectAnswerUsesSingleSelection() {
        val block = activity(correctAnswerIds = listOf("b"))
        val first = updateSelectedAnswers(block, emptySet(), "a")
        val second = updateSelectedAnswers(block, first, "b")

        assertEquals(setOf("a"), first)
        assertEquals(setOf("b"), second)
    }

    @Test
    fun activityWithMultipleCorrectAnswersTogglesMultipleSelection() {
        val block = activity(correctAnswerIds = listOf("a", "c"))

        val selectedA = updateSelectedAnswers(block, emptySet(), "a")
        val selectedAC = updateSelectedAnswers(block, selectedA, "c")
        val selectedC = updateSelectedAnswers(block, selectedAC, "a")

        assertEquals(setOf("a"), selectedA)
        assertEquals(setOf("a", "c"), selectedAC)
        assertEquals(setOf("c"), selectedC)
    }

    @Test
    fun answerMustMatchTheExactCorrectSet() {
        val block = activity(correctAnswerIds = listOf("a", "c"))

        assertTrue(activityAnswerIsCorrect(block, setOf("a", "c")))
        assertFalse(activityAnswerIsCorrect(block, setOf("a")))
        assertFalse(activityAnswerIsCorrect(block, setOf("a", "b", "c")))
        assertFalse(activityAnswerIsCorrect(block, emptySet()))
    }

    @Test
    fun onlyActivityBlocksAreRequiredForLessonCompletion() {
        val state = LessonContentUiState.Success(
            lessonId = "lesson-1",
            progressDocumentId = "lesson-1",
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
            progressDocumentId = "lesson-1",
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
    fun activityWithUnknownCorrectAnswerIsRejected() {
        validateLessonActivities(
            listOf(activity(correctAnswerIds = listOf("missing"))),
        )
    }

    @Test(expected = IllegalStateException::class)
    fun activityWithoutAlternativesIsRejected() {
        validateLessonActivities(
            listOf(activity().copy(alternatives = emptyMap())),
        )
    }

    @Test(expected = IllegalStateException::class)
    fun activityWithoutFeedbackIsRejected() {
        validateLessonActivities(
            listOf(activity().copy(feedback = "")),
        )
    }

    private fun activity(
        id: String = "activity-1",
        correctAnswerIds: List<String> = listOf("a"),
    ) = LessonBlock(
        id = id,
        type = "ACTIVITY",
        activityType = ActivityType.MULTIPLE_CHOICE,
        alternatives = mapOf(
            "a" to "Alternativa A",
            "b" to "Alternativa B",
            "c" to "Alternativa C",
        ),
        correctAnswerIds = correctAnswerIds,
        feedback = "Revise o conteúdo e tente novamente.",
    )
}
