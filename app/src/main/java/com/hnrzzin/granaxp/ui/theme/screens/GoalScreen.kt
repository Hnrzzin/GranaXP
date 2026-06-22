package com.hnrzzin.granaxp.ui.theme.screens

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.hnrzzin.granaxp.viewmodel.GoalViewModel
import com.hnrzzin.granaxp.viewmodel.GoalUiState // Adicionado import do estado correto

@Composable
fun GoalScreen(
    viewModel: GoalViewModel = viewModel(),
    onNavigateBack: () -> Unit = {}
) {
    val listState by viewModel.uiState.collectAsState()

    // Corrigido: getGoals() substituído pela função correta fetchGoals()
    LaunchedEffect(Unit) { viewModel.fetchGoals() }

    Column(modifier = Modifier.fillMaxSize().padding(16.dp)) {
        Text("As minhas Metas", style = MaterialTheme.typography.headlineMedium)

        when (val state = listState) {
            // Corrigido: Substituído GoalListState por GoalUiState
            is GoalUiState.Success -> {
                LazyColumn {
                    items(state.goals) { goal ->
                        Card(modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp)) {
                            Column(modifier = Modifier.padding(16.dp)) {
                                Text(goal.title, style = MaterialTheme.typography.titleMedium)
                                Text("Objetivo: R$ ${goal.targetAmount}")
                            }
                        }
                    }
                }
            }
            is GoalUiState.Loading -> {
                CircularProgressIndicator(modifier = Modifier.padding(top = 16.dp))
            }
            // Adicionado bloco explícito para tratar o estado de Erro presente no ViewModel
            is GoalUiState.Error -> {
                Text(
                    text = state.message,
                    color = MaterialTheme.colorScheme.error,
                    modifier = Modifier.padding(top = 16.dp)
                )
            }
            else -> {
                Text(
                    text = "Sem metas registradas.",
                    modifier = Modifier.padding(top = 16.dp)
                )
            }
        }
    }
}