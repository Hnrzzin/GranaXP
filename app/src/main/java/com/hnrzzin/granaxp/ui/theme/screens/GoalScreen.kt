package com.hnrzzin.granaxp.ui.theme.screens


import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.hnrzzin.granaxp.ui.theme.states.GoalListState
import com.hnrzzin.granaxp.viewmodel.GoalViewModel

@Composable
fun GoalScreen(viewModel: GoalViewModel = viewModel()) {
    val listState by viewModel.goalListState.collectAsState()

    LaunchedEffect(Unit) { viewModel.getGoals() }

    Column(modifier = Modifier.fillMaxSize().padding(16.dp)) {
        Text("As minhas Metas", style = MaterialTheme.typography.headlineMedium)

        when (val state = listState) {
            is GoalListState.Success -> {
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
            is GoalListState.Loading -> CircularProgressIndicator()
            else -> Text("Sem metas registadas.")
        }
    }
}