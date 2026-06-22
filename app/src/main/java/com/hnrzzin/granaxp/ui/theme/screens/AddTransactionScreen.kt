package com.hnrzzin.granaxp.ui.theme.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.hnrzzin.granaxp.model.TransactionType
import com.hnrzzin.granaxp.viewmodel.TransactionViewModel
import com.hnrzzin.granaxp.viewmodel.TransactionUiState

@Composable
fun AddTransactionScreen(
    viewModel: TransactionViewModel = viewModel(),
    onNavigateBack: () -> Unit = {}
) {
    val actionState by viewModel.uiState.collectAsState()

    // 1. Variáveis locais injetadas para suportar o estado do formulário (substituindo viewModel.x)
    var transactionType by remember { mutableStateOf(TransactionType.DESPESA) }
    var amount by remember { mutableStateOf("") }
    var title by remember { mutableStateOf("") }
    var category by remember { mutableStateOf("") }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
    ) {
        // Header
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(onClick = onNavigateBack) {
                Icon(Icons.Default.ArrowBack, contentDescription = "Voltar")
            }
            Text(
                text = "Nova Transação",
                fontSize = 20.sp,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onBackground
            )
            Spacer(modifier = Modifier.width(48.dp))
        }

        // Form
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(16.dp)
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // Transaction Type
            Text(
                text = "Tipo de Transação",
                fontSize = 14.sp,
                fontWeight = FontWeight.Medium,
                color = MaterialTheme.colorScheme.onBackground
            )
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                TransactionType.values().forEach { type ->
                    Button(
                        onClick = { transactionType = type },
                        modifier = Modifier
                            .weight(1f)
                            .height(48.dp),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = if (transactionType == type) {
                                MaterialTheme.colorScheme.primary
                            } else {
                                MaterialTheme.colorScheme.surfaceVariant
                            }
                        )
                    ) {
                        Text(
                            text = if (type == TransactionType.RECEITA) "Receita" else "Despesa",
                            color = if (transactionType == type) {
                                MaterialTheme.colorScheme.onPrimary
                            } else {
                                MaterialTheme.colorScheme.onSurface
                            }
                        )
                    }
                }
            }

            // Value
            Text(
                text = "Valor",
                fontSize = 14.sp,
                fontWeight = FontWeight.Medium,
                color = MaterialTheme.colorScheme.onBackground
            )
            OutlinedTextField(
                value = amount,
                onValueChange = { amount = it },
                modifier = Modifier.fillMaxWidth(),
                placeholder = { Text("0.00") },
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal)
            )

            // Description
            Text(
                text = "Título",
                fontSize = 14.sp,
                fontWeight = FontWeight.Medium,
                color = MaterialTheme.colorScheme.onBackground
            )
            OutlinedTextField(
                value = title,
                onValueChange = { title = it },
                modifier = Modifier.fillMaxWidth(),
                placeholder = { Text("Ex: Compra no supermercado") }
            )

            // Category
            Text(
                text = "Categoria",
                fontSize = 14.sp,
                fontWeight = FontWeight.Medium,
                color = MaterialTheme.colorScheme.onBackground
            )
            OutlinedTextField(
                value = category,
                onValueChange = { category = it },
                modifier = Modifier.fillMaxWidth(),
                placeholder = { Text("Ex: Alimentação") }
            )

            Spacer(modifier = Modifier.weight(1f))

            // 2. Correção do tratamento de erro: TransactionUiState e acesso direto à .message
            if (actionState is TransactionUiState.Error) {
                Text(
                    text = (actionState as TransactionUiState.Error).message,
                    color = MaterialTheme.colorScheme.error,
                    modifier = Modifier.align(Alignment.CenterHorizontally)
                )
            }

            // Submit Button
            Button(
                onClick = {
                    // 3. Substituição do addTransaction vazio pela API real de createTransaction
                    viewModel.createTransaction(
                        title = title,
                        amount = amount.toDoubleOrNull() ?: 0.0,
                        type = transactionType,
                        category = category
                        // isAutomatic usa o valor default = false
                    )
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(56.dp),
                enabled = actionState !is TransactionUiState.Loading, // Corrigido estado de loading
                colors = ButtonDefaults.buttonColors(
                    containerColor = MaterialTheme.colorScheme.primary
                )
            ) {
                if (actionState is TransactionUiState.Loading) { // Corrigido estado de loading
                    CircularProgressIndicator(color = MaterialTheme.colorScheme.onPrimary, modifier = Modifier.size(24.dp))
                } else {
                    Text("Adicionar Transação")
                }
            }
        }
    }

    // Success Navigation
    LaunchedEffect(actionState) {
        if (actionState is TransactionUiState.Success) { // Corrigido estado de sucesso
            // 4. Removida a chamada fantasma para viewModel.resetState()
            onNavigateBack()
        }
    }
}