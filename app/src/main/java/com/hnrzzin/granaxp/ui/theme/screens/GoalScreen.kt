package com.hnrzzin.granaxp.ui.theme.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.hnrzzin.granaxp.model.GoalDeadline
import com.hnrzzin.granaxp.model.GoalModel
import com.hnrzzin.granaxp.ui.theme.GranaXPColors
import com.hnrzzin.granaxp.utils.DateUtils
import com.hnrzzin.granaxp.viewmodel.GoalActionState
import com.hnrzzin.granaxp.viewmodel.GoalUiState
import com.hnrzzin.granaxp.viewmodel.GoalViewModel
import java.text.SimpleDateFormat
import java.util.Locale

// ==========================================
// 1. TELA PRINCIPAL DE VISUALIZAÇÃO DE METAS
// ==========================================
@Composable
fun GoalScreen(
    viewModel: GoalViewModel = viewModel(),
    onNavigateBack: () -> Unit = {}
) {
    // Dispara a busca de metas sempre que a tela inicia
    LaunchedEffect(Unit) {
        viewModel.fetchGoals()
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
    ) {
        // Cabeçalho unificado da lista
        Row(
            modifier = Modifier.fillMaxWidth().padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(onClick = onNavigateBack) {
                Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Voltar")
            }
            Text("Minhas Metas", fontSize = 20.sp, fontWeight = FontWeight.Bold)
        }

        // Conteúdo rico com estados de Sucesso, Carregando e Erro
        GoalContent(viewModel = viewModel)
    }
}

// ==========================================
// 2. CONTEÚDO RIO DE LISTAGEM E REGRA DE NEGÓCIO
// ==========================================
@Composable
fun GoalContent(viewModel: GoalViewModel) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    var goalToUpdate by remember { mutableStateOf<GoalModel?>(null) }

    when (val state = uiState) {
        is GoalUiState.Loading -> {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                CircularProgressIndicator()
            }
        }
        is GoalUiState.Error -> {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Text(state.message, color = GranaXPColors.Error, modifier = Modifier.padding(16.dp))
            }
        }
        is GoalUiState.Success -> {
            if (state.goals.isEmpty()) {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Text("Nenhuma meta cadastrada", color = GranaXPColors.Gray500)
                }
            } else {
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(bottom = 16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    items(state.goals) { goal ->
                        GoalCard(
                            goal = goal,
                            progress = viewModel.calculateProgress(goal),
                            onAddValueClick = { goalToUpdate = goal }
                        )
                    }
                }
            }
        }
    }

    // Gerencia a abertura do diálogo de incremento de valor
    goalToUpdate?.let { goal ->
        AddValueToGoalSheet(
            goal = goal,
            viewModel = viewModel,
            onConfirm = { amount, alreadyDeclared ->
                viewModel.updateGoalProgress(goal, amount, alreadyDeclared)
            },
            onDismiss = { goalToUpdate = null }
        )
    }
} // 👈 AQUI ESTAVA FALTANDO ESSA CHAVE DE FECHAMENTO

// ==========================================
// 3. TELA DE ADICIONAR NOVA META (FORMULÁRIO)
// ==========================================
@Composable
fun AddGoalDialog(
    viewModel: GoalViewModel,
    onDismiss: () -> Unit
) {
    val actionState by viewModel.actionState.collectAsStateWithLifecycle()
    var alreadyDeclared by remember { mutableStateOf(false) }

    var deadlineType by remember { mutableStateOf(GoalDeadline.CURTO) }
    var title by remember { mutableStateOf("") }
    var targetAmount by remember { mutableStateOf("") }
    var currentAmount by remember { mutableStateOf("") }
    var deadlineDate by remember { mutableStateOf("") }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Nova Meta Financeira", fontWeight = FontWeight.Bold, fontSize = 18.sp) },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                // Seleção de Prazo
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    GoalDeadline.entries.forEach { deadline ->
                        Button(
                            onClick = { deadlineType = deadline },
                            modifier = Modifier.weight(1f),
                            colors = ButtonDefaults.buttonColors(
                                containerColor = if (deadlineType == deadline)
                                    GranaXPColors.Purple600 else MaterialTheme.colorScheme.surfaceVariant
                            )
                        ) {
                            Text(if (deadline == GoalDeadline.CURTO) "Curto" else "Longo", fontSize = 12.sp)
                        }
                    }
                }

                // Inputs
                OutlinedTextField(
                    value = title,
                    onValueChange = { title = it },
                    label = { Text("O que quer alcançar?") },
                    placeholder = { Text("Ex: Trocar de Carro") },
                    modifier = Modifier.fillMaxWidth()
                )

                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(
                        value = targetAmount,
                        onValueChange = { targetAmount = it },
                        label = { Text("Alvo (R$)") },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                        modifier = Modifier.weight(1f)
                    )
                    OutlinedTextField(
                        value = currentAmount,
                        onValueChange = { currentAmount = it },
                        label = { Text("Guardado (R$)") },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                        modifier = Modifier.weight(1f)
                    )
                }

                // Validação de Receita Condicional
                if (currentAmount.toDoubleOrNull()?.let { it > 0.0 } == true) {
                    Card(
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f))
                    ) {
                        Column(Modifier.padding(8.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                            Row(
                                Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text("Já declarado como receita?", fontSize = 12.sp, modifier = Modifier.weight(1f))
                                Switch(checked = alreadyDeclared, onCheckedChange = { alreadyDeclared = it })
                            }
                            if (!alreadyDeclared) {
                                Text(
                                    "O valor não somará agora — registre a receita antes.",
                                    fontSize = 10.sp,
                                    color = MaterialTheme.colorScheme.error
                                )
                            }
                        }
                    }
                }

                OutlinedTextField(
                    value = deadlineDate,
                    onValueChange = { deadlineDate = it },
                    label = { Text("Data Limite (dd/mm/aaaa)") },
                    placeholder = { Text("Ex: 31/12/2026") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    modifier = Modifier.fillMaxWidth()
                )

                if (actionState is GoalActionState.Error) {
                    Text(
                        text = (actionState as GoalActionState.Error).message,
                        color = MaterialTheme.colorScheme.error,
                        fontSize = 12.sp
                    )
                }
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    viewModel.createGoal(
                        title = title,
                        targetAmount = targetAmount.toDoubleOrNull() ?: 0.0,
                        currentAmount = currentAmount.toDoubleOrNull() ?: 0.0,
                        deadline = deadlineType,
                        alreadyDeclared = alreadyDeclared,
                        deadlineDate = com.hnrzzin.granaxp.utils.DateUtils.parseDateToTimestamp(deadlineDate)
                    )
                },
                enabled = actionState !is GoalActionState.Loading,
                colors = ButtonDefaults.buttonColors(containerColor = GranaXPColors.Purple600)
            ) {
                if (actionState is GoalActionState.Loading) {
                    CircularProgressIndicator(modifier = Modifier.size(18.dp), color = MaterialTheme.colorScheme.onPrimary)
                } else {
                    Text("Salvar")
                }
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancelar") }
        }
    )

    // Fecha o modal automaticamente quando salvar com sucesso
    LaunchedEffect(actionState) {
        if (actionState is GoalActionState.Success) {
            viewModel.resetActionState()
            onDismiss()
        }
    }
}

// ==========================================
// 4. COMPONENTES DE APOIO (VISUALIZAÇÃO INTERNA)
// ==========================================
@Composable
private fun GoalCard(
    goal: GoalModel,
    progress: Float,
    onAddValueClick: () -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp),
        shape = MaterialTheme.shapes.large
    ) {
        Column(Modifier.padding(16.dp)) {
            Row(
                Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(Modifier.weight(1f)) {
                    Text(goal.title, fontWeight = FontWeight.Bold, fontSize = 16.sp)
                    Text(
                        "Meta: R$ %.2f · ${if (goal.deadline.name == "CURTO") "Curto Prazo" else "Longo Prazo"}".format(goal.targetAmount),
                        fontSize = 12.sp,
                        color = GranaXPColors.Gray600
                    )
                }
                goal.deadlineDate?.let {
                    Surface(shape = MaterialTheme.shapes.small, color = GranaXPColors.Secondary.copy(alpha = 0.15f)) {
                        Text(
                            "Até ${formatMonthYear(it.toDate())}",
                            modifier = Modifier.padding(8.dp, 4.dp),
                            fontSize = 11.sp,
                            color = GranaXPColors.Secondary
                        )
                    }
                }
                IconButton(onClick = onAddValueClick) {
                    Icon(Icons.Default.Add, contentDescription = "Adicionar valor", tint = GranaXPColors.Secondary)
                }
            }
            Spacer(Modifier.height(8.dp))
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Text("R$ %.2f guardados".format(goal.currentAmount), fontSize = 12.sp)
                Text("${(progress * 100).toInt()}%", fontSize = 12.sp, fontWeight = FontWeight.Bold)
            }
            Spacer(Modifier.height(4.dp))
            LinearProgressIndicator(
                progress = { progress },
                modifier = Modifier.fillMaxWidth().height(6.dp),
                color = GranaXPColors.Secondary,
                trackColor = GranaXPColors.Gray200
            )
        }
    }
}

@Composable
fun AddValueToGoalSheet(
    goal: GoalModel,
    viewModel: GoalViewModel,
    onConfirm: (amount: Double, alreadyDeclared: Boolean) -> Unit,
    onDismiss: () -> Unit
) {
    // Coleta o estado de ação atual do ViewModel
    val actionState by viewModel.actionState.collectAsStateWithLifecycle()
    val isSaving = actionState is GoalActionState.Loading
    val errorMessage = (actionState as? GoalActionState.Error)?.message

    var amount by remember { mutableStateOf("") }
    var alreadyDeclared by remember { mutableStateOf(false) }

    com.hnrzzin.granaxp.ui.theme.components.AppModalBottomSheet(onDismiss = onDismiss) {
        com.hnrzzin.granaxp.ui.theme.components.AppModalHeader(title = "Adicionar valor à meta", onClose = onDismiss)

        Text(goal.title, fontWeight = FontWeight.Medium, fontSize = 14.sp, color = GranaXPColors.Gray800)
        Spacer(Modifier.height(12.dp))
        OutlinedTextField(
            value = amount,
            onValueChange = { amount = it },
            label = { Text("Valor (R$)") },
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
            modifier = Modifier.fillMaxWidth(),
            colors = OutlinedTextFieldDefaults.colors(focusedBorderColor = GranaXPColors.Purple500)
        )
        Spacer(Modifier.height(16.dp))
        Row(
            Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text("Esse valor já foi declarado como receita?", fontSize = 13.sp, modifier = Modifier.weight(1f), color = GranaXPColors.Gray700)
            Switch(
                checked = alreadyDeclared,
                onCheckedChange = { alreadyDeclared = it },
                colors = SwitchDefaults.colors(checkedTrackColor = GranaXPColors.Primary)
            )
        }
        if (!alreadyDeclared) {
            Spacer(Modifier.height(4.dp))
            Text(
                "Registre esse valor como receita em Transações antes de adicioná-lo aqui.",
                fontSize = 11.sp,
                color = GranaXPColors.Error
            )
        }
        errorMessage?.let {
            Spacer(Modifier.height(8.dp))
            Text(it, fontSize = 12.sp, color = GranaXPColors.Error)
        }
        Spacer(Modifier.height(20.dp))
        Button(
            onClick = { onConfirm(amount.toDoubleOrNull() ?: 0.0, alreadyDeclared) },
            enabled = alreadyDeclared && !isSaving,
            modifier = Modifier.fillMaxWidth().height(48.dp),
            colors = ButtonDefaults.buttonColors(containerColor = GranaXPColors.Purple600),
            shape = RoundedCornerShape(8.dp)
        ) {
            if (isSaving) {
                CircularProgressIndicator(color = GranaXPColors.White, modifier = Modifier.size(20.dp))
            } else {
                Text("Adicionar", color = GranaXPColors.White, fontWeight = FontWeight.SemiBold)
            }
        }
    }

    // Fecha o bottom sheet automaticamente quando salvar com sucesso no Firebase
    LaunchedEffect(actionState) {
        if (actionState is GoalActionState.Success) {
            viewModel.resetActionState()
            onDismiss()
        }
    }
}

private fun formatMonthYear(date: java.util.Date): String {
    val sdf = SimpleDateFormat("MMM/yyyy", Locale("pt", "BR"))
    return sdf.format(date)
}