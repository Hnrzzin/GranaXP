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
import com.hnrzzin.granaxp.model.GoalModel
import com.hnrzzin.granaxp.model.GoalDeadline
import com.hnrzzin.granaxp.viewmodel.GoalViewModel
import androidx.compose.runtime.getValue
import androidx.compose.runtime.collectAsState
import com.hnrzzin.granaxp.viewmodel.GoalUiState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember

@Composable
fun AddGoalScreen(
    viewModel: GoalViewModel = viewModel(),
    onNavigateBack: () -> Unit = {}
) {
    val actionState by viewModel.uiState.collectAsState()

    var deadlineType by remember { mutableStateOf(GoalDeadline.CURTO) }
    var title by remember { mutableStateOf("") }
    var targetAmount by remember { mutableStateOf("") }
    // 1. ADICIONE ESTAS DUAS VARIÁVEIS QUE FALTAVAM:
    var currentAmount by remember { mutableStateOf("") }
    var deadlineDate by remember { mutableStateOf("") }
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
            // Seleção de Prazo (Tabs)
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                GoalDeadline.entries.forEach { deadline ->
                    Button(
                        onClick = { deadlineType = deadline }, // Alterado
                        modifier = Modifier.weight(1f),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = if (deadlineType == deadline) // Alterado
                                MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceVariant
                        )
                    ) {
                        Text(if (deadline == GoalDeadline.CURTO) "Curto Prazo" else "Longo Prazo")
                    }
                }
            }

            // Input: Título
            OutlinedTextField(
                value = title, // Alterado
                onValueChange = { title = it }, // Alterado
                label = { Text("O que você quer alcançar?") },
                placeholder = { Text("Ex: Trocar de Carro") },
                modifier = Modifier.fillMaxWidth()
            )

            // Inputs: Valores (Linha dupla)
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(
                    value = targetAmount, // Alterado
                    onValueChange = { targetAmount = it }, // Alterado
                    label = { Text("Valor Alvo (R$)") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    modifier = Modifier.weight(1f)
                )
                OutlinedTextField(
                    value = currentAmount, // Alterado
                    onValueChange = { currentAmount = it }, // Alterado
                    label = { Text("Já Guardado (R$)") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    modifier = Modifier.weight(1f)
                )
            }

            // Input: Data
            OutlinedTextField(
                value = deadlineDate, // Alterado
                onValueChange = { deadlineDate = it }, // Alterado
                label = { Text("Data Limite") },
                placeholder = { Text("dd/mm/aaaa") },
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                modifier = Modifier.fillMaxWidth()
            )

            Spacer(modifier = Modifier.weight(1f))

            // Erros
            if (actionState is GoalUiState.Error) { // Alterado de GoalState para GoalUiState
                Text(
                    text = (actionState as GoalUiState.Error).message, // Alterado: acessa .message diretamente
                    color = MaterialTheme.colorScheme.error,
                    modifier = Modifier.align(Alignment.CenterHorizontally)
                )
            }

            // Botão Salvar
            Button(
                onClick = {
                    // Alterado: Invoca a função real do ViewModel passando os estados locais
                    viewModel.createGoal(
                        title = title,
                        targetAmount = targetAmount.toDoubleOrNull() ?: 0.0,
                        currentAmount = currentAmount.toDoubleOrNull() ?: 0.0,
                        deadline = deadlineType,
                        alreadyDeclared = true // Mantendo a regra de negócio do seu createGoal
                    )
                },
                modifier = Modifier.fillMaxWidth().height(56.dp),
                enabled = actionState !is GoalUiState.Loading // Alterado de GoalState para GoalUiState
            ) {
                if (actionState is GoalUiState.Loading) { // Alterado de GoalState para GoalUiState
                    CircularProgressIndicator(modifier = Modifier.size(24.dp), color = MaterialTheme.colorScheme.onPrimary)
                } else {
                    Text("Salvar Meta")
                }
            }
        }
    }

    // Navegação após sucesso
    LaunchedEffect(key1 = actionState) {
        if (actionState is GoalUiState.Success) {
            // REMOVIDO: viewModel.resetState()
            onNavigateBack()
        }
    }
}
