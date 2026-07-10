package com.hnrzzin.granaxp.ui.theme.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
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
import com.hnrzzin.granaxp.model.BudgetModel
import com.hnrzzin.granaxp.model.BudgetPlanType
import com.hnrzzin.granaxp.ui.theme.GranaXPColors
import com.hnrzzin.granaxp.utils.CurrencyVisualTransformation
import com.hnrzzin.granaxp.utils.rawDigitsToAmount
import com.hnrzzin.granaxp.viewmodel.BudgetActionState
import com.hnrzzin.granaxp.viewmodel.BudgetAutomationWarning
import com.hnrzzin.granaxp.viewmodel.BudgetUiState
import com.hnrzzin.granaxp.viewmodel.BudgetViewModel

@Composable
fun BudgetContent(viewModel: BudgetViewModel) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val automationWarning by viewModel.automationWarning.collectAsStateWithLifecycle()
    var budgetToEdit by remember { mutableStateOf<BudgetModel?>(null) }
    var budgetToDelete by remember { mutableStateOf<BudgetModel?>(null) }

    when (val state = uiState) {
        is BudgetUiState.Loading -> {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { CircularProgressIndicator() }
        }
        is BudgetUiState.Error -> {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Text(state.message, color = GranaXPColors.Error)
            }
        }
        is BudgetUiState.Success -> {
            val fixedTotal = state.budgets.filter { it.type == BudgetPlanType.FIXO }.sumOf { it.limitAmount }
            val variableTotal = state.budgets.filter { it.type == BudgetPlanType.VARIAVEL }.sumOf { it.limitAmount }

            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                item {
                    Text("Orçamento Mensal Planejado", fontWeight = FontWeight.Bold, fontSize = 16.sp)
                }
                item {
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        BudgetSummaryCard("Gastos Fixos", fixedTotal, GranaXPColors.Info, Modifier.weight(1f))
                        BudgetSummaryCard("Gastos Variáveis", variableTotal, GranaXPColors.Orange600, Modifier.weight(1f))
                    }
                }
                if (state.budgets.isEmpty()) {
                    item {
                        Box(Modifier.fillMaxWidth().padding(top = 24.dp), contentAlignment = Alignment.Center) {
                            Text("Nenhum gasto planejado ainda", color = GranaXPColors.Gray500)
                        }
                    }
                } else {
                    item {
                        Card(shape = MaterialTheme.shapes.large) {
                            Column {
                                Row(
                                    Modifier.fillMaxWidth().padding(12.dp),
                                    horizontalArrangement = Arrangement.SpaceBetween
                                ) {
                                    Text("Despesa", color = GranaXPColors.Gray600, fontSize = 12.sp)
                                    Text("Valor", color = GranaXPColors.Gray600, fontSize = 12.sp)
                                }
                                HorizontalDivider()
                                state.budgets.forEach { budget ->
                                    BudgetRow(
                                        budget = budget,
                                        onEdit = { budgetToEdit = budget },
                                        onDelete = { budgetToDelete = budget }
                                    )
                                    HorizontalDivider()
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    // Modal de edição — reaproveita o AddBudgetSheet em modo edição
    budgetToEdit?.let { budget ->
        AddBudgetSheet(
            viewModel = viewModel,
            budgetToEdit = budget,
            onDismiss = { budgetToEdit = null }
        )
    }

    // Confirmação antes de deletar
    budgetToDelete?.let { budget ->
        AlertDialog(
            onDismissRequest = { budgetToDelete = null },
            title = { Text("Excluir gasto planejado?") },
            text = { Text("Tem certeza que deseja excluir \"${budget.category}\"? Essa ação não pode ser desfeita.") },
            confirmButton = {
                TextButton(onClick = {
                    viewModel.deleteBudget(budget)
                    budgetToDelete = null
                }) {
                    Text("Excluir", color = GranaXPColors.Red600)
                }
            },
            dismissButton = {
                TextButton(onClick = { budgetToDelete = null }) { Text("Cancelar") }
            }
        )
    }

    // Aviso não-bloqueante: falha nas automações mensais (pagamento fixo / fechamento variável)
    // Não substitui a lista da tela — só informa, e o usuário dispensa.
    automationWarning?.let { warning ->
        val message = when (warning) {
            is BudgetAutomationWarning.FixedPaymentFailed -> warning.message
            is BudgetAutomationWarning.VariableCloseFailed -> warning.message
        }
        AlertDialog(
            onDismissRequest = { viewModel.dismissAutomationWarning() },
            title = { Text("Aviso") },
            text = { Text(message) },
            confirmButton = {
                TextButton(onClick = { viewModel.dismissAutomationWarning() }) { Text("OK") }
            }
        )
    }
}

@Composable
private fun BudgetSummaryCard(label: String, value: Double, color: androidx.compose.ui.graphics.Color, modifier: Modifier) {
    Card(
        modifier = modifier,
        colors = CardDefaults.cardColors(containerColor = color.copy(alpha = 0.12f)),
        shape = MaterialTheme.shapes.large
    ) {
        Column(Modifier.padding(14.dp)) {
            Text(label, color = color, fontSize = 12.sp, fontWeight = FontWeight.Medium)
            Text("R$ %.2f".format(value), fontWeight = FontWeight.Bold, fontSize = 18.sp)
        }
    }
}

@Composable
private fun BudgetRow(
    budget: BudgetModel,
    onEdit: () -> Unit,
    onDelete: () -> Unit
) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text(budget.category, modifier = Modifier.weight(1f))
        Text("R$ %.2f".format(budget.limitAmount))
        Spacer(Modifier.width(8.dp))
        Surface(
            shape = MaterialTheme.shapes.extraSmall,
            color = if (budget.type == BudgetPlanType.FIXO) GranaXPColors.Blue100 else GranaXPColors.Orange100
        ) {
            Text(
                if (budget.type == BudgetPlanType.FIXO) "FIXO" else "VAR",
                color = if (budget.type == BudgetPlanType.FIXO) GranaXPColors.Blue900 else GranaXPColors.Orange900,
                modifier = Modifier.padding(6.dp, 2.dp),
                fontSize = 10.sp
            )
        }
        IconButton(onClick = onEdit, modifier = Modifier.size(32.dp)) {
            Icon(Icons.Default.Edit, contentDescription = "Editar", tint = GranaXPColors.Gray500, modifier = Modifier.size(16.dp))
        }
        IconButton(onClick = onDelete, modifier = Modifier.size(32.dp)) {
            Icon(Icons.Default.Delete, contentDescription = "Deletar", tint = GranaXPColors.Red600, modifier = Modifier.size(16.dp))
        }
    }
}

@Composable
fun AddBudgetSheet(
    viewModel: BudgetViewModel,
    budgetToEdit: BudgetModel? = null,
    onDismiss: () -> Unit
) {
    val isEditing = budgetToEdit != null

    var type by remember { mutableStateOf(budgetToEdit?.type ?: BudgetPlanType.FIXO) }
    var description by remember { mutableStateOf(budgetToEdit?.category ?: "") }
    var amount by remember {
        mutableStateOf(
            budgetToEdit?.limitAmount?.let { (it * 100).toLong().toString() } ?: ""
        )
    }
    var dueDayRaw by remember {
        mutableStateOf(budgetToEdit?.dueDay?.toString() ?: "")
    }

    val typeColor = if (type == BudgetPlanType.FIXO) GranaXPColors.Blue600 else GranaXPColors.Orange600

    // Estado de ação vindo do ViewModel — regra de dueDay válido agora mora lá, não aqui.
    val actionState by viewModel.actionState.collectAsStateWithLifecycle()
    val isSaving = actionState is BudgetActionState.Loading
    val errorMessage = (actionState as? BudgetActionState.Error)?.message

    // Fecha o modal só quando o ViewModel confirmar sucesso (antes fechava direto no onClick).
    LaunchedEffect(actionState) {
        if (actionState is BudgetActionState.Success) {
            viewModel.resetActionState()
            onDismiss()
        }
    }

    com.hnrzzin.granaxp.ui.theme.components.AppModalBottomSheet(onDismiss = onDismiss) {
        com.hnrzzin.granaxp.ui.theme.components.AppModalHeader(
            title = if (isEditing) "Editar Gasto Planejado" else "Novo Gasto Planejado",
            onClose = onDismiss
        )

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .background(GranaXPColors.Gray100, RoundedCornerShape(8.dp))
                .padding(4.dp)
        ) {
            listOf(BudgetPlanType.FIXO to "Fixo", BudgetPlanType.VARIAVEL to "Variável").forEach { (planType, label) ->
                val selected = type == planType
                Surface(
                    modifier = Modifier.weight(1f),
                    shape = RoundedCornerShape(6.dp),
                    color = if (selected) GranaXPColors.White else androidx.compose.ui.graphics.Color.Transparent,
                    tonalElevation = if (selected) 1.dp else 0.dp,
                    shadowElevation = if (selected) 1.dp else 0.dp
                ) {
                    Box(
                        modifier = Modifier
                            .clickable(
                                enabled = !isEditing,
                                indication = null,
                                interactionSource = remember { MutableInteractionSource() }
                            ) { type = planType }
                            .padding(vertical = 10.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            label,
                            fontSize = 14.sp,
                            fontWeight = FontWeight.Medium,
                            color = if (selected) {
                                if (planType == BudgetPlanType.FIXO) GranaXPColors.Blue600 else GranaXPColors.Orange600
                            } else GranaXPColors.Gray600
                        )
                    }
                }
            }
        }

        if (isEditing) {
            Spacer(Modifier.height(6.dp))
            Text(
                "Para mudar o tipo, exclua e crie um novo gasto.",
                fontSize = 11.sp,
                color = GranaXPColors.Gray500
            )
        }

        Spacer(Modifier.height(20.dp))

        OutlinedTextField(
            value = description,
            onValueChange = { description = it },
            label = { Text("Descrição") },
            placeholder = { Text("Ex: Aluguel") },
            modifier = Modifier.fillMaxWidth(),
            colors = OutlinedTextFieldDefaults.colors(focusedBorderColor = typeColor)
        )

        Spacer(Modifier.height(12.dp))

        OutlinedTextField(
            value = amount,
            onValueChange = { input -> amount = input.filter { it.isDigit() } },
            label = { Text("Valor Estimado (R$)") },
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
            visualTransformation = CurrencyVisualTransformation(),
            modifier = Modifier.fillMaxWidth(),
            colors = OutlinedTextFieldDefaults.colors(focusedBorderColor = typeColor)
        )

        // Campo dueDay — só aparece pra Gastos Fixos, editável mesmo depois de criado.
        // A validação de range (1-31) NÃO é mais calculada aqui: o ViewModel valida
        // ao salvar e devolve o erro via actionState (errorMessage abaixo).
        val dueDayInt = dueDayRaw.toIntOrNull()

        if (type == BudgetPlanType.FIXO) {
            Spacer(Modifier.height(12.dp))
            OutlinedTextField(
                value = dueDayRaw,
                onValueChange = { input ->
                    dueDayRaw = input.filter { it.isDigit() }.take(2)
                },
                label = { Text("Dia do Vencimento") },
                placeholder = { Text("Ex: 10") },
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                modifier = Modifier.fillMaxWidth(),
                colors = OutlinedTextFieldDefaults.colors(focusedBorderColor = typeColor),
                isError = errorMessage != null
            )
        }

        errorMessage?.let {
            Spacer(Modifier.height(4.dp))
            Text(
                it,
                fontSize = 11.sp,
                color = GranaXPColors.Error
            )
        }

        Spacer(Modifier.height(24.dp))

        
        val canSave = description.isNotBlank() && amount.isNotBlank() && !isSaving

        Button(
            onClick = {
                if (isEditing) {
                    viewModel.updateBudget(
                        budgetToEdit!!.copy(
                            category = description,
                            limitAmount = rawDigitsToAmount(amount),
                            dueDay = if (type == BudgetPlanType.FIXO) dueDayInt else null
                        )
                    )
                } else {
                    viewModel.createBudget(
                        category = description,
                        limitAmount = rawDigitsToAmount(amount),
                        type = type,
                        dueDay = if (type == BudgetPlanType.FIXO) dueDayInt else null
                    )
                }

            },
            enabled = canSave,
            modifier = Modifier.fillMaxWidth().height(52.dp),
            colors = ButtonDefaults.buttonColors(containerColor = typeColor),
            shape = RoundedCornerShape(8.dp)
        ) {
            if (isSaving) {
                CircularProgressIndicator(
                    color = GranaXPColors.White,
                    modifier = Modifier.size(20.dp)
                )
            } else {
                Text(
                    if (isEditing) "Salvar Alterações" else "Salvar no Orçamento",
                    color = GranaXPColors.White,
                    fontSize = 16.sp,
                    fontWeight = FontWeight.SemiBold
                )
            }
        }
    }
}