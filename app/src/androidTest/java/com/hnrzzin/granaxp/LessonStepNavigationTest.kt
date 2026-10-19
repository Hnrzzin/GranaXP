package com.hnrzzin.granaxp

import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.swipeUp
import androidx.test.espresso.Espresso
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.hnrzzin.granaxp.model.ActivityType
import com.hnrzzin.granaxp.model.LessonBlock
import com.hnrzzin.granaxp.model.LessonModel
import com.hnrzzin.granaxp.model.LessonProgressModel
import com.hnrzzin.granaxp.model.LessonSelectionMode
import com.hnrzzin.granaxp.ui.theme.screens.LessonContentPage
import com.hnrzzin.granaxp.viewmodel.ActivityAnswerResult
import com.hnrzzin.granaxp.viewmodel.ActivityAnswerState
import com.hnrzzin.granaxp.viewmodel.LessonContentUiState
import com.hnrzzin.granaxp.viewmodel.LessonWithProgress
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/** Pure UI fixtures; no repository, authentication, catalog writer or reward implementation. */
@RunWith(AndroidJUnit4::class)
class LessonStepNavigationTest {
    @get:Rule val proof = InstrumentationExecutionProof()
    @get:Rule val compose = createComposeRule()

    private val lesson = LessonWithProgress(LessonModel(id = "fixture", title = "Lição de UI"), null)
    private val first = LessonBlock(id = "first", type = "TEXT", title = "Primeiro bloco", content = "Texto inicial.")
    private val second = LessonBlock(id = "second", type = "TEXT", title = "Segundo bloco", content = "Texto seguinte.")
    private val activity = LessonBlock(id = "activity", type = "ACTIVITY", title = "Atividade obrigatória",
        activityType = ActivityType.MULTIPLE_CHOICE, selectionMode = LessonSelectionMode.SINGLE,
        alternatives = mapOf("a" to "Alternativa A", "b" to "Alternativa B"))

    @Test fun activityTypeHeadingsArePortugueseWithoutChangingCatalogContent() {
        val state = mutableStateOf(LessonContentUiState.Success("fixture", listOf(activity)))
        compose.setContent {
            MaterialTheme { LessonContentPage(lesson, 1, state.value, {}, {}, { _, _ -> }, {}) }
        }
        val headings = mapOf(
            ActivityType.MULTIPLE_CHOICE to "Múltipla escolha",
            ActivityType.TRUE_FALSE to "Verdadeiro ou falso",
            ActivityType.CLASSIFICATION to "Classificação",
            ActivityType.FINANCIAL_SCENARIO to "Cenário financeiro",
            ActivityType.CHART_ANALYSIS to "Análise de gráfico",
        )
        for ((type, heading) in headings) {
            compose.runOnIdle { state.value = state.value.copy(blocks = listOf(activity.copy(activityType = type))) }
            compose.onNodeWithText(heading).assertIsDisplayed()
            compose.onNodeWithText("Atividade obrigatória").assertIsDisplayed()
            compose.onNodeWithText("Alternativa A").assertIsDisplayed()
        }
    }

    @Test fun showsOnlyCurrentBlockAndBackReviewsWithoutCompleting() {
        var completions = 0
        var exits = 0
        compose.setContent {
            MaterialTheme {
                LessonContentPage(lesson, 1, LessonContentUiState.Success("fixture", listOf(first, second)),
                    { exits++ }, { completions++ }, { _, _ -> }, {})
            }
        }
        compose.onNodeWithText("Primeiro bloco").assertIsDisplayed()
        compose.onNodeWithText("Segundo bloco").assertDoesNotExist()
        compose.onNodeWithText("Continuar").performClick()
        compose.onNodeWithText("Segundo bloco").assertIsDisplayed()
        compose.onNodeWithText("Primeiro bloco").assertDoesNotExist()
        Espresso.pressBack()
        compose.onNodeWithText("Primeiro bloco").assertIsDisplayed()
        compose.runOnIdle { assertEquals(0, completions); assertEquals(0, exits) }
        Espresso.pressBack()
        compose.runOnIdle { assertEquals(1, exits) }
    }

    @Test fun incorrectOrUnconfirmedCorrectFeedbackCannotAdvance() {
        val state = mutableStateOf(LessonContentUiState.Success("fixture", listOf(activity, second),
            activityAnswers = mapOf("activity" to ActivityAnswerState(result = ActivityAnswerResult.INCORRECT,
                message = "Tente novamente."))))
        var completions = 0
        compose.setContent {
            MaterialTheme {
                LessonContentPage(lesson, 1, state.value, {}, { completions++ }, { _, _ -> }, {})
            }
        }
        compose.onNodeWithText("Continuar").assertIsNotEnabled()
        compose.onNodeWithText("Segundo bloco").assertDoesNotExist()
        compose.runOnIdle {
            state.value = state.value.copy(activityAnswers = mapOf("activity" to
                ActivityAnswerState(result = ActivityAnswerResult.CORRECT)))
        }
        compose.onNodeWithText("Continuar").assertIsNotEnabled()
        compose.runOnIdle { state.value = state.value.copy(completedActivityIds = setOf("activity")) }
        compose.onNodeWithText("Continuar").assertIsEnabled().performClick()
        compose.onNodeWithText("Segundo bloco").assertIsDisplayed()
        compose.runOnIdle { assertEquals(0, completions) }
        compose.onNodeWithText("Concluir lição").performClick()
        compose.runOnIdle { assertEquals(1, completions) }
    }

    @Test fun lastActivityDoesNotCompleteOnViewingOrWrongAnswer() {
        val state = mutableStateOf(LessonContentUiState.Success("fixture", listOf(first, activity)))
        var completions = 0
        compose.setContent {
            MaterialTheme {
                LessonContentPage(lesson, 1, state.value, {}, { completions++ }, { _, _ -> }, {})
            }
        }
        compose.onNodeWithText("Continuar").performClick()
        compose.onNodeWithText("Concluir lição").assertIsNotEnabled()
        compose.runOnIdle {
            state.value = state.value.copy(activityAnswers = mapOf("activity" to
                ActivityAnswerState(result = ActivityAnswerResult.INCORRECT, message = "Incorreta.")))
            assertEquals(0, completions)
        }
        compose.onNodeWithText("Concluir lição").assertIsNotEnabled()
        compose.runOnIdle { state.value = state.value.copy(completedActivityIds = setOf("activity")) }
        compose.onNodeWithText("Concluir lição").assertIsEnabled()
        compose.runOnIdle { assertEquals(0, completions) }
        compose.onNodeWithText("Concluir lição").performClick()
        compose.runOnIdle { assertEquals(1, completions) }
    }

    @Test fun completedLessonAllowsHistoricalReviewWithoutRewardCallback() {
        var completions = 0
        var exits = 0
        val completed = lesson.copy(progress = LessonProgressModel(lessonId = "fixture", isCompleted = true))
        compose.setContent {
            MaterialTheme {
                LessonContentPage(completed, 1, LessonContentUiState.Success("fixture", listOf(activity, second)),
                    { exits++ }, { completions++ }, { _, _ -> }, {})
            }
        }
        compose.onNodeWithText("Continuar").assertIsEnabled().performClick()
        compose.onNodeWithText("Concluir lição").assertDoesNotExist()
        compose.onNodeWithText("Concluir revisão").performClick()
        compose.runOnIdle { assertEquals(0, completions); assertEquals(1, exits) }
    }

    @Test fun reopeningRetainsConfirmedActivitiesAndRestartsPresentation() {
        val shown = mutableStateOf(true)
        val state = LessonContentUiState.Success("fixture", listOf(first, activity, second), setOf("activity"))
        compose.setContent {
            if (shown.value) MaterialTheme {
                LessonContentPage(lesson, 1, state, {}, {}, { _, _ -> }, {})
            }
        }
        compose.onNodeWithText("Continuar").performClick()
        compose.onNodeWithText("Continuar").assertIsEnabled().performClick()
        compose.onNodeWithText("Segundo bloco").assertIsDisplayed()
        compose.runOnIdle { shown.value = false }
        compose.waitForIdle()
        compose.runOnIdle { shown.value = true }
        compose.onNodeWithText("Primeiro bloco").assertIsDisplayed()
        compose.onNodeWithText("Continuar").performClick()
        compose.onNodeWithText("Continuar").assertIsEnabled()
    }

    @Test fun revokedConfirmationCannotLeaveCursorBeyondPendingActivity() {
        val state = mutableStateOf(LessonContentUiState.Success("fixture", listOf(first, activity, second), setOf("activity")))
        compose.setContent {
            MaterialTheme { LessonContentPage(lesson, 1, state.value, {}, {}, { _, _ -> }, {}) }
        }
        compose.onNodeWithText("Continuar").performClick()
        compose.onNodeWithText("Continuar").performClick()
        compose.onNodeWithText("Segundo bloco").assertIsDisplayed()
        compose.runOnIdle { state.value = state.value.copy(completedActivityIds = emptySet()) }
        compose.onNodeWithText("Segundo bloco").assertDoesNotExist()
        compose.onNodeWithText("Continuar").assertIsNotEnabled()
        compose.onNodeWithText("Etapa 2 de 3").assertIsDisplayed()
        compose.runOnIdle { state.value = state.value.copy(completedActivityIds = setOf("activity")) }
        compose.onNodeWithText("Etapa 2 de 3").assertIsDisplayed()
        compose.onNodeWithText("Segundo bloco").assertDoesNotExist()
        compose.onNodeWithText("Continuar").assertIsEnabled().performClick()
        compose.onNodeWithText("Segundo bloco").assertIsDisplayed()
    }

    @Test fun longBlockScrollDoesNotRevealNextBlockAndControlsRemainAvailable() {
        val long = first.copy(content = "Conteúdo longo.\n".repeat(150))
        compose.setContent {
            MaterialTheme {
                LessonContentPage(lesson, 1, LessonContentUiState.Success("fixture", listOf(long, second)),
                    {}, {}, { _, _ -> }, {})
            }
        }
        compose.onRoot().performTouchInput { swipeUp() }
        compose.onNodeWithText("Segundo bloco").assertDoesNotExist()
        compose.onNodeWithText("Continuar").assertIsDisplayed().performClick()
        compose.onNodeWithText("Segundo bloco").assertIsDisplayed()
        compose.onNodeWithText("Anterior").performClick()
        compose.onNodeWithText("Primeiro bloco").assertIsDisplayed()
    }

    @Test fun loadingErrorsOrMismatchedLessonNeverExposeCompletion() {
        val state = mutableStateOf<LessonContentUiState>(LessonContentUiState.Loading("fixture"))
        var completions = 0
        compose.setContent {
            MaterialTheme { LessonContentPage(lesson, 1, state.value, {}, { completions++ }, { _, _ -> }, {}) }
        }
        compose.onNodeWithText("Concluir lição").assertDoesNotExist()
        compose.runOnIdle { state.value = LessonContentUiState.Error("fixture", "Erro controlado") }
        compose.onNodeWithText("Erro controlado").assertIsDisplayed()
        compose.onNodeWithText("Continuar").assertDoesNotExist()
        compose.runOnIdle { state.value = LessonContentUiState.Success("another", listOf(first)) }
        compose.onNodeWithText("Primeiro bloco").assertDoesNotExist()
        compose.onNodeWithText("Concluir lição").assertDoesNotExist()
        compose.runOnIdle { assertEquals(0, completions) }
    }
}
