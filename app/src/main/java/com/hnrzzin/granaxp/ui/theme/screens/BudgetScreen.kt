package com.hnrzzin.granaxp.ui.theme.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
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
import com.hnrzzin.granaxp.viewmodel.BudgetUiState
import com.hnrzzin.granaxp.viewmodel.BudgetViewModel

@Composable
fun BudgetContent(viewModel: BudgetViewModel) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()

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
                        // Em BudgetSummaryCard, na chamada:
                        BudgetSummaryCard("Gastos Fixos", fixedTotal, GranaXPColors.Info, Modifier.weight(1f))
                        BudgetSummaryCard("Gastos Variáveis", variableTotal, GranaXPColors.Orange600, Modifier.weight(1f)) // ✅ corrigido
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
                                    BudgetRow(budget)
                                    HorizontalDivider()
                                }
                            }
                        }
                    }
                }
            }
        }
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
private fun BudgetRow(budget: BudgetModel) {
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
    }
}

@Composable
fun AddBudgetSheet(
    viewModel: BudgetViewModel,
    onDismiss: () -> Unit
) {
    var type by remember { mutableStateOf(BudgetPlanType.FIXO) }
    var description by remember { mutableStateOf("") }
    var amount by remember { mutableStateOf("") }

    com.hnrzzin.granaxp.ui.theme.components.AppModalBottomSheet(onDismiss = onDismiss) {
        com.hnrzzin.granaxp.ui.theme.components.AppModalHeader(title = "Novo Gasto Planejado", onClose = onDismiss)

        // Toggle Fixo/Variável — pill com fundo cinza, opção ativa em branco elevado
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

        Spacer(Modifier.height(20.dp))

        OutlinedTextField(
            value = description,
            onValueChange = { description = it },
            label = { Text("Descrição") },
            placeholder = { Text("Ex: Aluguel") },
            modifier = Modifier.fillMaxWidth(),
            colors = OutlinedTextFieldDefaults.colors(
                focusedBorderColor = if (type == BudgetPlanType.FIXO) GranaXPColors.Blue600 else GranaXPColors.Orange600
            )
        )

        Spacer(Modifier.height(12.dp))

        OutlinedTextField(
            value = amount,
            onValueChange = { amount = it },
            label = { Text("Valor Estimado (R$)") },
            keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(keyboardType = androidx.compose.ui.text.input.KeyboardType.Decimal),
            modifier = Modifier.fillMaxWidth(),
            colors = OutlinedTextFieldDefaults.colors(
                focusedBorderColor = if (type == BudgetPlanType.FIXO) GranaXPColors.Blue600 else GranaXPColors.Orange600
            )
        )

        Spacer(Modifier.height(24.dp))

        Button(
            onClick = {
                viewModel.createBudget(
                    category = description,
                    limitAmount = amount.toDoubleOrNull() ?: 0.0,
                    type = type
                )
                onDismiss()
            },
            enabled = description.isNotBlank() && amount.toDoubleOrNull() != null,
            modifier = Modifier.fillMaxWidth().height(52.dp),
            colors = ButtonDefaults.buttonColors(
                containerColor = if (type == BudgetPlanType.FIXO) GranaXPColors.Blue600 else GranaXPColors.Orange600
            ),
            shape = RoundedCornerShape(8.dp)
        ) {
            Text("Salvar no Orçamento", color = GranaXPColors.White, fontSize = 16.sp, fontWeight = FontWeight.SemiBold)
        }
    }
}