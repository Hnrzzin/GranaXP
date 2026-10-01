package com.hnrzzin.granaxp.ui.theme.screens

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage
import com.hnrzzin.granaxp.model.DailyMissionModel
import com.hnrzzin.granaxp.model.LessonSelectionMode
import com.hnrzzin.granaxp.model.orderedAlternatives
import com.hnrzzin.granaxp.viewmodel.DailyMissionUiState

@Composable
fun DailyMissionEntry(
    state: DailyMissionUiState,
    onOpen: () -> Unit,
    onRefresh: () -> Unit,
) {
    when (state) {
        is DailyMissionUiState.Active, is DailyMissionUiState.Completed -> {
            Card(modifier = Modifier.fillMaxWidth().clickable(onClick = onOpen)) {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text("Missão diária", style = MaterialTheme.typography.titleMedium)
                    Text(if (state is DailyMissionUiState.Completed) "Concluída hoje" else "Responder missão de hoje")
                }
            }
        }
        is DailyMissionUiState.Error -> {
            Card(modifier = Modifier.fillMaxWidth()) {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("Não foi possível carregar a missão diária.")
                    Button(onClick = onRefresh) { Text("Tentar novamente") }
                }
            }
        }
        DailyMissionUiState.Loading, DailyMissionUiState.Unavailable -> Unit
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DailyMissionPage(
    state: DailyMissionUiState,
    onBack: () -> Unit,
    onSelect: (String) -> Unit,
    onSubmit: () -> Unit,
    onRefresh: () -> Unit,
) {
    Scaffold(topBar = {
        TopAppBar(
            title = { Text("Missão diária") },
            navigationIcon = {
                IconButton(onClick = onBack) {
                    Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Voltar")
                }
            },
        )
    }) { padding ->
        Column(
            modifier = Modifier.fillMaxSize().padding(padding).verticalScroll(rememberScrollState())
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            when (state) {
                DailyMissionUiState.Loading -> CircularProgressIndicator()
                DailyMissionUiState.Unavailable -> Text("Nenhuma missão diária disponível.")
                is DailyMissionUiState.Error -> {
                    Text(state.message)
                    Button(onClick = onRefresh) { Text("Tentar novamente") }
                }
                is DailyMissionUiState.Completed -> {
                    MissionContent(state.mission)
                    Text("Missão de hoje concluída.", style = MaterialTheme.typography.titleMedium)
                }
                is DailyMissionUiState.Active -> {
                    MissionContent(state.mission)
                    state.mission.orderedAlternatives().forEach { (id, label) ->
                        Row(
                            modifier = Modifier.fillMaxWidth().clickable(
                                enabled = !state.isSubmitting, onClick = { onSelect(id) },
                            ).padding(vertical = 4.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            if (state.mission.selectionMode == LessonSelectionMode.MULTIPLE) {
                                Checkbox(
                                    checked = id in state.selectedAnswerIds,
                                    onCheckedChange = { onSelect(id) },
                                    enabled = !state.isSubmitting,
                                )
                            } else {
                                RadioButton(
                                    selected = id in state.selectedAnswerIds,
                                    onClick = { onSelect(id) },
                                    enabled = !state.isSubmitting,
                                )
                            }
                            Text(label, modifier = Modifier.padding(start = 8.dp))
                        }
                    }
                    state.feedback?.let { Text(it) }
                    state.error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
                    Button(
                        onClick = onSubmit,
                        enabled = state.selectedAnswerIds.isNotEmpty() && !state.isSubmitting,
                        contentPadding = PaddingValues(horizontal = 20.dp, vertical = 12.dp),
                    ) { Text(if (state.isSubmitting) "Verificando..." else "Responder") }
                }
            }
        }
    }
}

@Composable
private fun MissionContent(mission: DailyMissionModel) {
    Text(mission.title, style = MaterialTheme.typography.headlineSmall)
    Text(mission.description)
    mission.chartImageUrl?.takeIf { it.isNotBlank() }?.let { url ->
        AsyncImage(
            model = url,
            contentDescription = "Gráfico da missão diária",
            modifier = Modifier.fillMaxWidth().height(220.dp),
            contentScale = ContentScale.Fit,
        )
    }
}
