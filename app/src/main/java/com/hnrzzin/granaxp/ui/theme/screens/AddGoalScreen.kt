package com.hnrzzin.granaxp.ui.theme.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.hnrzzin.granaxp.enums.GoalDeadline
import com.hnrzzin.granaxp.ui.theme.states.GoalState
import com.hnrzzin.granaxp.viewmodel.GoalViewModel

@Composable
fun AddGoalScreen(
    viewModel: GoalViewModel = viewModel(),
    onNavigateBack: () -> Unit = {}
) {
    val actionState by viewModel.goalState.collectAsState()

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
    ) {
        // Header
        Row(
            modifier = Modifier.fillMaxWidth().padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(onClick = onNavigateBack) {
                Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Voltar")
            }
            Text("Nova Meta Financeira", fontSize = 20.sp, fontWeight = FontWeight.Bold)
        }

        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(16.dp)
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // Seleção de Prazo (Tabs)
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                GoalDeadline.entries.forEach { deadline ->
                    Button(
                        onClick = { viewModel.deadlineType = deadline },
                        modifier = Modifier.weight(1f),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = if (viewModel.deadlineType == deadline)
                                MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceVariant
                        )
                    ) {
                        // Exibição amigável do Enum
                        Text(if (deadline == GoalDeadline.CURTO) "Curto Prazo" else "Longo Prazo")
                    }
                }
            }

            // Input: Título
            OutlinedTextField(
                value = viewModel.title,
                onValueChange = { viewModel.title = it },
                label = { Text("O que você quer alcançar?") },
                placeholder = { Text("Ex: Trocar de Carro") },
                modifier = Modifier.fillMaxWidth()
            )

            // Inputs: Valores (Linha dupla)
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(
                    value = viewModel.targetAmount,
                    onValueChange = { viewModel.targetAmount = it },
                    label = { Text("Valor Alvo (R$)") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    modifier = Modifier.weight(1f)
                )
                OutlinedTextField(
                    value = viewModel.currentAmount,
                    onValueChange = { viewModel.currentAmount = it },
                    label = { Text("Já Guardado (R$)") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    modifier = Modifier.weight(1f)
                )
            }

            // Input: Data
            OutlinedTextField(
                value = viewModel.deadlineDate,
                onValueChange = { viewModel.deadlineDate = it },
                label = { Text("Data Limite") },
                placeholder = { Text("dd/mm/aaaa") },
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                modifier = Modifier.fillMaxWidth()
            )

            Spacer(modifier = Modifier.weight(1f))

            // Erros
            if (actionState is GoalState.Error) {
                Text(
                    text = (actionState as GoalState.Error).exception.message ?: "Erro ao salvar",
                    color = MaterialTheme.colorScheme.error,
                    modifier = Modifier.align(Alignment.CenterHorizontally)
                )
            }

            // Botão Salvar
            Button(
                onClick = { viewModel.addGoal() },
                modifier = Modifier.fillMaxWidth().height(56.dp),
                enabled = actionState !is GoalState.Loading
            ) {
                if (actionState is GoalState.Loading) {
                    CircularProgressIndicator(modifier = Modifier.size(24.dp), color = MaterialTheme.colorScheme.onPrimary)
                } else {
                    Text("Salvar Meta")
                }
            }
        }
    }

    // Navegação após sucesso
    LaunchedEffect(actionState) {
        if (actionState is GoalState.Success) {
            viewModel.resetState()
            onNavigateBack()
        }
    }
}