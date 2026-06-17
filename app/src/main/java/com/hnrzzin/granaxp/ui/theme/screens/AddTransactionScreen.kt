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
import com.hnrzzin.granaxp.enums.TransactionType
import com.hnrzzin.granaxp.ui.theme.states.TransactionState
import com.hnrzzin.granaxp.viewmodel.TransactionViewModel

@Composable
fun AddTransactionScreen(
    viewModel: TransactionViewModel = viewModel(),
    onNavigateBack: () -> Unit = {}
) {
    val actionState by viewModel.transactionState.collectAsState()

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
                        onClick = { viewModel.transactionType = type },
                        modifier = Modifier
                            .weight(1f)
                            .height(48.dp),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = if (viewModel.transactionType == type) {
                                MaterialTheme.colorScheme.primary
                            } else {
                                MaterialTheme.colorScheme.surfaceVariant
                            }
                        )
                    ) {
                        Text(
                            text = if (type == TransactionType.RECEITA) "Receita" else "Despesa",
                            color = if (viewModel.transactionType == type) {
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
                value = viewModel.amount,
                onValueChange = { viewModel.amount = it },
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
                value = viewModel.title,
                onValueChange = { viewModel.title = it },
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
                value = viewModel.category,
                onValueChange = { viewModel.category = it },
                modifier = Modifier.fillMaxWidth(),
                placeholder = { Text("Ex: Alimentação") }
            )

            Spacer(modifier = Modifier.weight(1f))

            // Error Message Handling
            if (actionState is TransactionState.Error) {
                Text(
                    text = (actionState as TransactionState.Error).exception.message ?: "Erro desconhecido",
                    color = MaterialTheme.colorScheme.error,
                    modifier = Modifier.align(Alignment.CenterHorizontally)
                )
            }

            // Submit Button
            Button(
                onClick = { viewModel.addTransaction() },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(56.dp),
                enabled = actionState !is TransactionState.Loading,
                colors = ButtonDefaults.buttonColors(
                    containerColor = MaterialTheme.colorScheme.primary
                )
            ) {
                if (actionState is TransactionState.Loading) {
                    CircularProgressIndicator(color = MaterialTheme.colorScheme.onPrimary, modifier = Modifier.size(24.dp))
                } else {
                    Text("Adicionar Transação")
                }
            }
        }
    }

    // Success Navigation
    LaunchedEffect(actionState) {
        if (actionState is TransactionState.Success) {
            viewModel.resetState()
            onNavigateBack()
        }
    }
}