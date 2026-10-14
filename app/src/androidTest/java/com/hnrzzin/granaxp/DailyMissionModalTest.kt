package com.hnrzzin.granaxp

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.isDialog
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import android.accessibilityservice.AccessibilityService
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.hnrzzin.granaxp.model.ActivityType
import com.hnrzzin.granaxp.model.DailyMissionModel
import com.hnrzzin.granaxp.model.LessonSelectionMode
import com.hnrzzin.granaxp.model.StreakModel
import com.hnrzzin.granaxp.ui.theme.screens.DailyMissionDialog
import com.hnrzzin.granaxp.viewmodel.DailyMissionUiState
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/** Production UI with explicit state fixtures. Backend validation is covered by the separate real UI rehearsal. */
@RunWith(AndroidJUnit4::class)
class DailyMissionModalTest {
    @get:Rule val proof = InstrumentationExecutionProof()
    @get:Rule val compose = createComposeRule()
    private val mission = DailyMissionModel("fixture-modal", "Missão de UI", "Enunciado existente.",
        ActivityType.MULTIPLE_CHOICE, mapOf("a" to "Opção A", "b" to "Opção B"), LessonSelectionMode.SINGLE)
    private val active = DailyMissionUiState.Active("2026-10-14", mission, StreakModel())

    @Test fun opensInNativeDialog() {
        compose.setContent { MaterialTheme { DailyMissionDialog(active, {}, {}, {}, {}) } }
        compose.onAllNodes(isDialog()).assertCountEquals(1)
        compose.onNodeWithText("Missão de UI").assertIsDisplayed()
        compose.onNodeWithContentDescription("Fechar missão diária").assertIsDisplayed()
    }

    @Test fun closeButtonDismissesWithoutChangingUnderlyingState() {
        val visible = mutableStateOf(true)
        val trail = mutableStateOf("Módulo mantido")
        compose.setContent {
            MaterialTheme {
                Text(trail.value)
                if (visible.value) DailyMissionDialog(active, { visible.value = false }, {}, {}, {})
            }
        }
        compose.onNodeWithContentDescription("Fechar missão diária").performClick()
        compose.onAllNodes(isDialog()).assertCountEquals(0)
        compose.onNodeWithText("Módulo mantido").assertIsDisplayed()
        compose.runOnIdle { assertEquals("Módulo mantido", trail.value) }
    }

    @Test fun backDismissesAndReopeningKeepsSelectedAnswer() {
        val visible = mutableStateOf(true)
        val state = mutableStateOf(active.copy(selectedAnswerIds = setOf("a")))
        compose.setContent {
            MaterialTheme {
                Text("Catálogo preservado")
                if (visible.value) DailyMissionDialog(state.value, { visible.value = false }, {}, {}, {})
            }
        }
        assertTrue(InstrumentationRegistry.getInstrumentation().uiAutomation
            .performGlobalAction(AccessibilityService.GLOBAL_ACTION_BACK))
        compose.waitUntil(timeoutMillis = 5_000) {
            compose.onAllNodes(isDialog()).fetchSemanticsNodes().isEmpty()
        }
        compose.onAllNodes(isDialog()).assertCountEquals(0)
        compose.onNodeWithText("Catálogo preservado").assertIsDisplayed()
        compose.runOnIdle { visible.value = true }
        compose.onNodeWithText("Responder").assertIsEnabled()
        compose.runOnIdle { assertEquals(setOf("a"), state.value.selectedAnswerIds) }
    }

    @Test fun submissionWaitsForConfirmationAndDisablesRepeatedInput() {
        val state = mutableStateOf<DailyMissionUiState>(active.copy(selectedAnswerIds = setOf("a")))
        var submissions = 0
        var selections = 0
        compose.setContent {
            MaterialTheme {
                DailyMissionDialog(state.value, {}, { selections++ }, {
                    submissions++
                    state.value = (state.value as DailyMissionUiState.Active).copy(isSubmitting = true)
                }, {})
            }
        }
        compose.onNodeWithText("Responder").performClick()
        compose.onNodeWithText("Verificando...").assertIsNotEnabled().performClick()
        compose.onNodeWithText("Opção B").performClick()
        compose.onNodeWithText("Missão de hoje concluída.").assertDoesNotExist()
        compose.runOnIdle { assertEquals(1, submissions); assertEquals(0, selections) }
        compose.runOnIdle { state.value = DailyMissionUiState.Completed(active.date, mission, StreakModel(1, 1)) }
        compose.onNodeWithText("Missão de hoje concluída.").assertIsDisplayed()
        compose.onNodeWithText("Responder").assertDoesNotExist()
    }

    @Test fun incorrectFeedbackAllowsAnotherAttempt() {
        val state = mutableStateOf(active.copy(feedback = "Resposta incorreta. Tente novamente."))
        var submissions = 0
        compose.setContent {
            MaterialTheme {
                DailyMissionDialog(state.value, {}, { state.value = state.value.copy(selectedAnswerIds = setOf(it)) },
                    { submissions++ }, {})
            }
        }
        compose.onNodeWithText("Resposta incorreta. Tente novamente.").assertIsDisplayed()
        compose.onNodeWithText("Responder").assertIsNotEnabled()
        compose.onNodeWithText("Opção B").performClick()
        compose.onNodeWithText("Responder").assertIsEnabled().performClick()
        compose.runOnIdle { assertEquals(1, submissions) }
    }

    @Test fun completedMissionShowsStreakAndNoRewardAction() {
        var submissions = 0
        compose.setContent {
            MaterialTheme {
                DailyMissionDialog(DailyMissionUiState.Completed(active.date, mission, StreakModel(3, 7),
                    xpEarned = 100, leveledUp = true), {}, {}, { submissions++ }, {})
            }
        }
        compose.onNodeWithText("Missão de hoje concluída.").assertIsDisplayed()
        compose.onNodeWithText("🔥 Sequência: 3 dias · Recorde: 7 dias").assertIsDisplayed()
        compose.onNodeWithText("+100 XP pelo marco da sequência").assertIsDisplayed()
        compose.onNodeWithText("Responder").assertDoesNotExist()
        compose.onNodeWithText("Opção A").assertDoesNotExist()
        compose.runOnIdle { assertEquals(0, submissions) }
    }

    @Test fun loadingErrorRetryAndUnavailableRemainInDialog() {
        val state = mutableStateOf<DailyMissionUiState>(DailyMissionUiState.Loading)
        var retries = 0
        compose.setContent {
            MaterialTheme { DailyMissionDialog(state.value, {}, {}, {}, { retries++ }) }
        }
        compose.onAllNodes(isDialog()).assertCountEquals(1)
        compose.onNodeWithText("Carregando missão...").assertIsDisplayed()
        compose.runOnIdle { state.value = DailyMissionUiState.Error("Erro controlado de UI") }
        compose.onNodeWithText("Erro controlado de UI").assertIsDisplayed()
        compose.onNodeWithText("Tentar novamente").performClick()
        compose.runOnIdle { assertEquals(1, retries); state.value = DailyMissionUiState.Unavailable }
        compose.onNodeWithText("Nenhuma missão diária disponível.").assertIsDisplayed()
        compose.onAllNodes(isDialog()).assertCountEquals(1)
    }

    @Test fun longContentScrollsInsideDialogAndCloseRemainsAccessible() {
        val long = mission.copy(description = List(70) { "Texto longo do enunciado de UI." }.joinToString("\n"))
        compose.setContent { MaterialTheme { DailyMissionDialog(active.copy(mission = long), {}, {}, {}, {}) } }
        compose.onNodeWithContentDescription("Fechar missão diária").assertIsDisplayed()
        compose.onNodeWithText("Responder").performScrollTo().assertIsDisplayed()
        compose.onNodeWithContentDescription("Fechar missão diária").assertIsDisplayed()
        compose.onAllNodes(isDialog()).assertCountEquals(1)
    }
}
