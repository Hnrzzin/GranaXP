package com.hnrzzin.granaxp

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.width
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.unit.dp
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.hnrzzin.granaxp.ui.theme.components.HomeTopBar
import com.hnrzzin.granaxp.model.ActivityType
import com.hnrzzin.granaxp.model.DailyMissionModel
import com.hnrzzin.granaxp.model.StreakModel
import com.hnrzzin.granaxp.model.LessonSelectionMode
import com.hnrzzin.granaxp.viewmodel.DailyMissionUiState
import com.hnrzzin.granaxp.viewmodel.headerStreak
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class HomeTopBarStreakTest {
    @get:Rule val proof = InstrumentationExecutionProof()
    @get:Rule val compose = createComposeRule()

    @Test fun absentStreakShowsZeroWithoutHidingHeaderControlsOnSmallScreens() {
        compose.setContent {
            MaterialTheme {
                Box(Modifier.width(320.dp)) {
                    HomeTopBar(level = 1, xp = 0, nextLevelXp = 100, onProfileClick = {})
                }
            }
        }
        compose.onNodeWithText("🔥 0").assertIsDisplayed()
        compose.onNodeWithText("GranaXP").assertIsDisplayed()
        compose.onNodeWithText("Nível 1").assertIsDisplayed()
        compose.onNodeWithContentDescription("Perfil").assertIsDisplayed()
    }

    @Test fun headerTracksConfirmedMissionStateAndResetsOnUnavailableData() {
        val today = "2026-10-19"
        val mission = DailyMissionModel("fixture", "Missão", "Escolha", ActivityType.MULTIPLE_CHOICE,
            mapOf("a" to "A"), LessonSelectionMode.SINGLE)
        val state = mutableStateOf<DailyMissionUiState>(DailyMissionUiState.Active(today, mission, StreakModel(7, 15)))
        compose.setContent {
            MaterialTheme {
                Box(Modifier.width(320.dp)) {
                    HomeTopBar(1, 0, 100, currentStreak = state.value.headerStreak(today), onProfileClick = {})
                }
            }
        }
        compose.onNodeWithText("🔥 7").assertIsDisplayed()
        compose.runOnIdle { state.value = DailyMissionUiState.Completed(today, mission, StreakModel(8, 15)) }
        compose.onNodeWithText("🔥 8").assertIsDisplayed()
        compose.onNodeWithContentDescription("Sequência diária: 8").assertIsDisplayed()
        compose.runOnIdle { state.value = DailyMissionUiState.Error("offline") }
        compose.onNodeWithText("🔥 0").assertIsDisplayed()
        compose.onNodeWithContentDescription("Perfil").assertIsDisplayed()
    }
}
