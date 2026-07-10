package com.hnrzzin.granaxp.ui.theme.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
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
    var goalToEdit by remember { mutableStateOf<GoalModel?>(null) }
    var goalToDelete by remember { mutableStateOf<GoalModel?>(null) }

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
                            onAddValueClick = { goalToUpdate = goal },
                            onEditClick = { goalToEdit = goal },
                            onDeleteClick = { goalToDelete = goal }
                        )
                    }
                }
            }
        }
    }

    // Modal de adicionar valor (já existia)
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

    // Modal de editar título/valor-alvo/prazo (novo)
    goalToEdit?.let { goal ->
        EditGoalSheet(
            goal = goal,
            viewModel = viewModel,
            onDismiss = { goalToEdit = null }
        )
    }

    // Confirmação de exclusão (novo)
    goalToDelete?.let { goal ->
        AlertDialog(
            onDismissRequest = { goalToDelete = null },
            title = { Text("Excluir meta?") },
            text = { Text("Tem certeza que deseja excluir \"${goal.title}\"? Essa ação não pode ser desfeita.") },
            confirmButton = {
                TextButton(onClick = {
                    viewModel.deleteGoal(goal)
                    goalToDelete = null
                }) {
                    Text("Excluir", color = GranaXPColors.Red600)
                }
            },
            dismissButton = {
                TextButton(onClick = { goalToDelete = null }) { Text("Cancelar") }
            }
        )
    }
}

// ==========================================
// 3. TELA DE ADICIONAR NOVA META (FORMULÁRIO)
// ==========================================
@Composable
fun AddGoalSheet(
    viewModel: GoalViewModel,
    onDismiss: () -> Unit
) {
    val actionState by viewModel.actionState.collectAsStateWithLifecycle()
    val isSaving = actionState is GoalActionState.Loading

    var deadlineType by remember { mutableStateOf(GoalDeadline.CURTO) }
    var title by remember { mutableStateOf("") }
    var targetAmount by remember { mutableStateOf("") }
    var currentAmount by remember { mutableStateOf("") }
    var deadlineDateRaw by remember { mutableStateOf("") }
    var alreadyDeclared by remember { mutableStateOf(false) }

    com.hnrzzin.granaxp.ui.theme.components.AppModalBottomSheet(onDismiss = onDismiss) {
        com.hnrzzin.granaxp.ui.theme.components.AppModalHeader(title = "Nova Meta Financeira", onClose = onDismiss)

        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            GoalDeadline.entries.filter { it != GoalDeadline.MEDIO }.forEach { deadline ->
                val selected = deadlineType == deadline
                Surface(
                    modifier = Modifier.weight(1f).clickable { deadlineType = deadline },
                    shape = RoundedCornerShape(8.dp),
                    color = if (selected) GranaXPColors.Purple50 else GranaXPColors.Gray100,
                    border = androidx.compose.foundation.BorderStroke(
                        1.dp, if (selected) GranaXPColors.Purple500 else GranaXPColors.Gray100
                    )
                ) {
                    Text(
                        if (deadline == GoalDeadline.CURTO) "Curto Prazo" else "Longo Prazo",
                        modifier = Modifier.padding(vertical = 10.dp),
                        textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Medium,
                        color = if (selected) GranaXPColors.Purple600 else GranaXPColors.Gray600
                    )
                }
            }
        }

        Spacer(Modifier.height(20.dp))

        OutlinedTextField(
            value = title,
            onValueChange = { title = it },
            label = { Text("O que quer alcançar?") },
            placeholder = { Text("Ex: Trocar de Carro") },
            modifier = Modifier.fillMaxWidth(),
            colors = OutlinedTextFieldDefaults.colors(focusedBorderColor = GranaXPColors.Purple500)
        )

        Spacer(Modifier.height(12.dp))

        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedTextField(
                value = targetAmount,
                onValueChange = { input -> targetAmount = input.filter { it.isDigit() } },
                label = { Text("Alvo (R$)") },
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                visualTransformation = com.hnrzzin.granaxp.utils.CurrencyVisualTransformation(),
                modifier = Modifier.weight(1f),
                colors = OutlinedTextFieldDefaults.colors(focusedBorderColor = GranaXPColors.Purple500)
            )
            OutlinedTextField(
                value = currentAmount,
                onValueChange = { input -> currentAmount = input.filter { it.isDigit() } },
                label = { Text("Já Guardado (R$)") },
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                visualTransformation = com.hnrzzin.granaxp.utils.CurrencyVisualTransformation(),
                modifier = Modifier.weight(1f),
                colors = OutlinedTextFieldDefaults.colors(focusedBorderColor = GranaXPColors.Purple500)
            )
        }

        Spacer(Modifier.height(12.dp))

        OutlinedTextField(
            value = deadlineDateRaw,
            onValueChange = { input -> deadlineDateRaw = input.filter { it.isDigit() }.take(8) },
            label = { Text("Data Limite") },
            placeholder = { Text("dd/mm/aaaa") },
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
            visualTransformation = com.hnrzzin.granaxp.utils.DateVisualTransformation(),
            modifier = Modifier.fillMaxWidth(),
            colors = OutlinedTextFieldDefaults.colors(focusedBorderColor = GranaXPColors.Purple500)
        )


        // Regra de negócio crítica #1 — só entra em jogo se currentAmount > 0
        val hasInitialAmount = currentAmount.toLongOrNull()?.let { it > 0 } == true

        if (hasInitialAmount) {
            Spacer(Modifier.height(16.dp))
            Row(
                Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    "O valor já guardado foi declarado como receita?",
                    fontSize = 13.sp,
                    modifier = Modifier.weight(1f),
                    color = GranaXPColors.Gray700
                )
                Switch(
                    checked = alreadyDeclared,
                    onCheckedChange = { alreadyDeclared = it },
                    colors = SwitchDefaults.colors(checkedTrackColor = GranaXPColors.Primary)
                )
            }
            if (!alreadyDeclared) {
                Spacer(Modifier.height(4.dp))
                Text(
                    "Registre esse valor como receita em Transações, ou deixe o campo em branco e adicione depois.",
                    fontSize = 11.sp,
                    color = GranaXPColors.Error
                )
            }
        }

        if (actionState is GoalActionState.Error) {
            Spacer(Modifier.height(8.dp))
            Text((actionState as GoalActionState.Error).message, color = GranaXPColors.Error, fontSize = 12.sp)
        }

        Spacer(Modifier.height(20.dp))


        val canSave = title.isNotBlank() &&
                targetAmount.isNotBlank() &&
                !isSaving

        Button(
            onClick = {
                viewModel.createGoal(
                    title = title,
                    targetAmount = com.hnrzzin.granaxp.utils.rawDigitsToAmount(targetAmount),
                    currentAmount = com.hnrzzin.granaxp.utils.rawDigitsToAmount(currentAmount),
                    deadline = deadlineType,
                    alreadyDeclared = alreadyDeclared,
                    deadlineDateRaw = deadlineDateRaw
                )
            },
            enabled = canSave,
            modifier = Modifier.fillMaxWidth().height(52.dp),
            colors = ButtonDefaults.buttonColors(containerColor = GranaXPColors.Purple600),
            shape = RoundedCornerShape(8.dp)
        ) {
            if (isSaving) {
                CircularProgressIndicator(color = GranaXPColors.White, modifier = Modifier.size(20.dp))
            } else {
                Text("Salvar Meta", color = GranaXPColors.White, fontWeight = FontWeight.SemiBold)
            }
        }
    }

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
    onAddValueClick: () -> Unit,
    onEditClick: () -> Unit,
    onDeleteClick: () -> Unit
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
            Spacer(Modifier.height(12.dp))
            Row(
                Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.End,
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(onClick = onEditClick, modifier = Modifier.size(32.dp)) {
                    Icon(Icons.Default.Edit, contentDescription = "Editar", tint = GranaXPColors.Gray500, modifier = Modifier.size(16.dp))
                }
                IconButton(onClick = onDeleteClick, modifier = Modifier.size(32.dp)) {
                    Icon(Icons.Default.Delete, contentDescription = "Deletar", tint = GranaXPColors.Red600, modifier = Modifier.size(16.dp))
                }
                Spacer(Modifier.width(4.dp))
                IconButton(onClick = onAddValueClick, modifier = Modifier.size(32.dp)) {
                    Icon(Icons.Default.Add, contentDescription = "Adicionar valor", tint = GranaXPColors.Secondary, modifier = Modifier.size(18.dp))
                }
            }
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
@Composable
fun EditGoalSheet(
    goal: GoalModel,
    viewModel: GoalViewModel,
    onDismiss: () -> Unit
) {
    val actionState by viewModel.actionState.collectAsStateWithLifecycle()
    var title by remember { mutableStateOf(goal.title) }
    var targetAmount by remember { mutableStateOf((goal.targetAmount * 100).toLong().toString()) }
    var deadlineType by remember { mutableStateOf(goal.deadline) }

    com.hnrzzin.granaxp.ui.theme.components.AppModalBottomSheet(onDismiss = onDismiss) {
        com.hnrzzin.granaxp.ui.theme.components.AppModalHeader(title = "Editar Meta Financeira", onClose = onDismiss)

        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            GoalDeadline.entries.filter { it != GoalDeadline.MEDIO }.forEach { deadline ->
                val selected = deadlineType == deadline
                Surface(
                    modifier = Modifier.weight(1f).clickable { deadlineType = deadline },
                    shape = RoundedCornerShape(8.dp),
                    color = if (selected) GranaXPColors.Purple50 else GranaXPColors.Gray100,
                    border = androidx.compose.foundation.BorderStroke(1.dp, if (selected) GranaXPColors.Purple500 else GranaXPColors.Gray100)
                ) {
                    Text(
                        if (deadline == GoalDeadline.CURTO) "Curto Prazo" else "Longo Prazo",
                        modifier = Modifier.padding(vertical = 10.dp),
                        textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                        fontSize = 13.sp,
                        color = if (selected) GranaXPColors.Purple600 else GranaXPColors.Gray600
                    )
                }
            }
        }

        Spacer(Modifier.height(16.dp))

        OutlinedTextField(
            value = title,
            onValueChange = { title = it },
            label = { Text("O que quer alcançar?") },
            modifier = Modifier.fillMaxWidth()
        )

        Spacer(Modifier.height(12.dp))

        OutlinedTextField(
            value = targetAmount,
            onValueChange = { input -> targetAmount = input.filter { it.isDigit() } },
            label = { Text("Valor Alvo (R$)") },
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
            visualTransformation = com.hnrzzin.granaxp.utils.CurrencyVisualTransformation(),
            modifier = Modifier.fillMaxWidth()
        )

        Spacer(Modifier.height(12.dp))

        // Data Limite travada — só é definida na criação da meta (regra de negócio)
        Column {
            Text("Data Limite", fontSize = 12.sp, color = GranaXPColors.Gray500)
            Spacer(Modifier.height(4.dp))
            Text(
                goal.deadlineDate?.toDate()?.let {
                    java.text.SimpleDateFormat("dd/MM/yyyy", java.util.Locale("pt", "BR")).format(it)
                } ?: "Sem data definida",
                fontSize = 14.sp,
                fontWeight = FontWeight.Medium,
                color = GranaXPColors.Gray700
            )
        }

        Spacer(Modifier.height(4.dp))
        Text(
            "O valor já guardado (R$ %.2f) só muda ao declarar receitas na meta.".format(goal.currentAmount),
            fontSize = 11.sp,
            color = GranaXPColors.Gray500
        )

        if (actionState is GoalActionState.Error) {
            Spacer(Modifier.height(8.dp))
            Text((actionState as GoalActionState.Error).message, color = GranaXPColors.Error, fontSize = 12.sp)
        }

        Spacer(Modifier.height(20.dp))

        Button(
            onClick = {
                viewModel.updateGoalDetails(
                    goal = goal,
                    newTitle = title,
                    newTargetAmount = com.hnrzzin.granaxp.utils.rawDigitsToAmount(targetAmount),
                    newDeadline = deadlineType,
                    newDeadlineDate = goal.deadlineDate // mantém a data original — não editável
                )
            },
            enabled = title.isNotBlank() && actionState !is GoalActionState.Loading,
            modifier = Modifier.fillMaxWidth().height(48.dp),
            colors = ButtonDefaults.buttonColors(containerColor = GranaXPColors.Purple600),
            shape = RoundedCornerShape(8.dp)
        ) {
            if (actionState is GoalActionState.Loading) {
                CircularProgressIndicator(color = GranaXPColors.White, modifier = Modifier.size(20.dp))
            } else {
                Text("Salvar Alterações", color = GranaXPColors.White, fontWeight = FontWeight.SemiBold)
            }
        }
    }

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