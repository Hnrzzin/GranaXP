package com.hnrzzin.granaxp.ui.theme.screens

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalWindowInfo
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import coil3.compose.AsyncImage
import com.hnrzzin.granaxp.model.DailyMissionModel
import com.hnrzzin.granaxp.model.LessonSelectionMode
import com.hnrzzin.granaxp.model.StreakModel
import com.hnrzzin.granaxp.model.orderedAlternatives
import com.hnrzzin.granaxp.ui.theme.GranaXPColors
import com.hnrzzin.granaxp.viewmodel.DailyMissionUiState

@Composable
fun DailyMissionEntry(state: DailyMissionUiState, onOpen: () -> Unit, onRefresh: () -> Unit) {
    Card(
        modifier = Modifier.fillMaxWidth().clickable(onClick = onOpen),
        colors = CardDefaults.cardColors(containerColor = GranaXPColors.Emerald100),
        shape = RoundedCornerShape(16.dp),
    ) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Text("Missão diária", style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold, color = GranaXPColors.Emerald800)
            when (state) {
                is DailyMissionUiState.Active -> {
                    Text("Responder missão de hoje")
                    MissionStreak(state.streak)
                }
                is DailyMissionUiState.Completed -> {
                    Text("Concluída hoje")
                    MissionStreak(state.streak)
                }
                is DailyMissionUiState.Error -> {
                    Text("Não foi possível carregar a missão diária.")
                    Button(onClick = onRefresh) { Text("Tentar novamente") }
                }
                DailyMissionUiState.Loading -> Text("Carregando missão...")
                DailyMissionUiState.Unavailable -> Text("Nenhuma missão diária disponível.")
            }
        }
    }
}

@Composable
fun DailyMissionDialog(
    state: DailyMissionUiState,
    onBack: () -> Unit,
    onSelect: (String) -> Unit,
    onSubmit: () -> Unit,
    onRefresh: () -> Unit,
) {
    val maxHeight = with(LocalDensity.current) {
        LocalWindowInfo.current.containerSize.height.toDp()
    } * 0.82f
    Dialog(
        onDismissRequest = onBack,
        properties = DialogProperties(usePlatformDefaultWidth = false, dismissOnClickOutside = false),
    ) {
        Surface(
            modifier = Modifier.padding(20.dp).widthIn(max = 480.dp).fillMaxWidth()
                .heightIn(max = maxHeight),
            shape = RoundedCornerShape(24.dp),
            color = GranaXPColors.Surface,
            tonalElevation = 0.dp,
        ) {
            Column {
                Row(
                    modifier = Modifier.fillMaxWidth().padding(start = 20.dp, end = 8.dp, top = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text("Missão diária", modifier = Modifier.weight(1f),
                        style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold,
                        color = GranaXPColors.PrimaryDark)
                    IconButton(onClick = onBack) {
                        Icon(Icons.Default.Close, contentDescription = "Fechar missão diária",
                            tint = GranaXPColors.Gray600)
                    }
                }
                Column(
                    modifier = Modifier.weight(1f, fill = false).verticalScroll(rememberScrollState())
                        .padding(start = 20.dp, end = 20.dp, top = 8.dp, bottom = 24.dp),
                    verticalArrangement = Arrangement.spacedBy(16.dp),
                ) {
                    when (state) {
                        DailyMissionUiState.Loading -> {
                            CircularProgressIndicator()
                            Text("Carregando missão...")
                        }
                        DailyMissionUiState.Unavailable -> Text("Nenhuma missão diária disponível.")
                        is DailyMissionUiState.Error -> {
                            MissionFeedback(state.message, isError = true)
                            Button(onClick = onRefresh, modifier = Modifier.fillMaxWidth()) {
                                Text("Tentar novamente")
                            }
                        }
                        is DailyMissionUiState.Completed -> {
                            MissionContent(state.mission)
                            MissionFeedback("Missão de hoje concluída.", isError = false)
                            MissionStreak(state.streak)
                            if (state.xpEarned > 0) Text("+${state.xpEarned} XP pelo marco da sequência",
                                color = GranaXPColors.PrimaryDark, fontWeight = FontWeight.SemiBold)
                            if (state.leveledUp) Text("Você alcançou um novo nível!")
                        }
                        is DailyMissionUiState.Active -> {
                            MissionContent(state.mission)
                            MissionStreak(state.streak)
                            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                state.mission.orderedAlternatives().forEach { (id, label) ->
                                    val selected = id in state.selectedAnswerIds
                                    Surface(
                                        modifier = Modifier.fillMaxWidth(),
                                        color = if (selected) GranaXPColors.Emerald100 else GranaXPColors.Gray50,
                                        shape = RoundedCornerShape(12.dp),
                                        border = BorderStroke(1.dp,
                                            if (selected) GranaXPColors.Primary else GranaXPColors.Gray200),
                                    ) {
                                        Row(
                                            modifier = Modifier.clickable(enabled = !state.isSubmitting,
                                                onClick = { onSelect(id) }).padding(horizontal = 8.dp, vertical = 4.dp),
                                            verticalAlignment = Alignment.CenterVertically,
                                        ) {
                                            if (state.mission.selectionMode == LessonSelectionMode.MULTIPLE) {
                                                Checkbox(checked = selected, onCheckedChange = null,
                                                    enabled = !state.isSubmitting)
                                            } else {
                                                RadioButton(selected = selected, onClick = null,
                                                    enabled = !state.isSubmitting)
                                            }
                                            Text(label, modifier = Modifier.weight(1f).padding(start = 8.dp, end = 8.dp),
                                                color = GranaXPColors.OnSurface)
                                        }
                                    }
                                }
                            }
                            state.feedback?.let { MissionFeedback(it, isError = true) }
                            state.error?.let { MissionFeedback(it, isError = true) }
                            Button(
                                onClick = onSubmit,
                                modifier = Modifier.fillMaxWidth(),
                                enabled = state.selectedAnswerIds.isNotEmpty() && !state.isSubmitting,
                                shape = RoundedCornerShape(12.dp),
                            ) { Text(if (state.isSubmitting) "Verificando..." else "Responder") }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun MissionStreak(streak: StreakModel) {
    val currentUnit = if (streak.currentStreak == 1) "dia" else "dias"
    val highestUnit = if (streak.highestStreak == 1) "dia" else "dias"
    Text("🔥 Sequência: ${streak.currentStreak} $currentUnit · Recorde: ${streak.highestStreak} $highestUnit",
        style = MaterialTheme.typography.bodySmall, color = GranaXPColors.Gray700)
}

@Composable
private fun MissionFeedback(message: String, isError: Boolean) {
    Surface(
        color = if (isError) GranaXPColors.Red50 else GranaXPColors.Emerald100,
        shape = RoundedCornerShape(12.dp),
    ) {
        Text(message, modifier = Modifier.fillMaxWidth().padding(12.dp),
            color = if (isError) GranaXPColors.Error else GranaXPColors.Emerald800,
            style = MaterialTheme.typography.bodyMedium)
    }
}

@Composable
private fun MissionContent(mission: DailyMissionModel) {
    Text(mission.title, style = MaterialTheme.typography.headlineSmall,
        fontWeight = FontWeight.Bold, color = GranaXPColors.OnSurface)
    Text(mission.description, color = GranaXPColors.Gray700)
    mission.chartImageUrl?.takeIf { it.isNotBlank() }?.let { url ->
        AsyncImage(model = url, contentDescription = "Gráfico da missão diária",
            modifier = Modifier.fillMaxWidth().height(220.dp), contentScale = ContentScale.Fit)
    }
}
