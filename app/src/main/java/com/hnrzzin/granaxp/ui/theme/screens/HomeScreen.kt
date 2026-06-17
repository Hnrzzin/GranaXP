package com.hnrzzin.granaxp.ui.theme.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.hnrzzin.granaxp.model.TransactionModel
import com.hnrzzin.granaxp.enums.TransactionType
import com.hnrzzin.granaxp.ui.theme.states.TransactionListState
import com.hnrzzin.granaxp.viewmodel.TransactionViewModel
import java.math.BigDecimal
import java.math.RoundingMode

@Composable
fun HomeScreen(
    userId: String = "", // Mantido para compatibilidade com sua AppNavigation
    viewModel: TransactionViewModel = viewModel(),
    onNavigateToAddTransaction: () -> Unit = {},
    onNavigateToTransactions: () -> Unit = {},
    onNavigateToProfile: () -> Unit = {}
) {
    // Observa o estado da lista de transações
    val listState by viewModel.transactionListState.collectAsState()

    LaunchedEffect(Unit) {
        viewModel.getTransactions()
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .padding(16.dp)
    ) {
        // Header
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = 24.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column {
                Text(
                    text = "Bem-vindo!",
                    fontSize = 24.sp,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onBackground
                )
                Text(
                    text = "Seu saldo financeiro",
                    fontSize = 14.sp,
                    color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.7f)
                )
            }
            Button(
                onClick = onNavigateToProfile,
                modifier = Modifier.size(48.dp),
                shape = MaterialTheme.shapes.small
            ) {
                Text("👤")
            }
        }

        // Recupera a lista de transações do estado de sucesso
        val transactions = if (listState is TransactionListState.Success) {
            (listState as TransactionListState.Success).transactions
        } else {
            emptyList()
        }

        // Balance Card
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = 24.dp),
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.primary
            )
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(20.dp)
            ) {
                Text(
                    text = "Saldo Total",
                    fontSize = 14.sp,
                    color = MaterialTheme.colorScheme.onPrimary.copy(alpha = 0.8f)
                )
                Text(
                    text = "R$ ${calculateBalance(transactions)}",
                    fontSize = 32.sp,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onPrimary
                )
            }
        }

        // Recent Transactions Header
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = 12.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "Transações Recentes",
                fontSize = 18.sp,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onBackground
            )
            Button(
                onClick = onNavigateToTransactions,
                modifier = Modifier.size(40.dp),
                shape = MaterialTheme.shapes.small,
                contentPadding = PaddingValues(0.dp)
            ) {
                Text("→")
            }
        }

        // Transactions List State Handling
        when (listState) {
            is TransactionListState.Loading -> {
                Box(modifier = Modifier.fillMaxWidth().height(200.dp), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator()
                }
            }
            is TransactionListState.Success -> {
                if (transactions.isEmpty()) {
                    Box(
                        modifier = Modifier.fillMaxWidth().height(200.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = "Nenhuma transação registrada",
                            color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.5f)
                        )
                    }
                } else {
                    LazyColumn(
                        modifier = Modifier.fillMaxWidth(),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        items(transactions.take(5)) { transaction ->
                            TransactionItem(transaction)
                        }
                    }
                }
            }
            is TransactionListState.Error -> {
                Text("Erro ao carregar transações", color = MaterialTheme.colorScheme.error)
            }
            else -> {}
        }

        Spacer(modifier = Modifier.weight(1f))

        // Add Transaction Button
        Button(
            onClick = onNavigateToAddTransaction,
            modifier = Modifier
                .fillMaxWidth()
                .height(56.dp),
            colors = ButtonDefaults.buttonColors(
                containerColor = MaterialTheme.colorScheme.primary
            )
        ) {
            Icon(Icons.Default.Add, contentDescription = "Adicionar")
            Spacer(modifier = Modifier.width(8.dp))
            Text("Nova Transação")
        }
    }
}

@Composable
fun TransactionItem(transaction: TransactionModel) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant
        )
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = transaction.title,
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Medium,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Text(
                    text = transaction.category,
                    fontSize = 12.sp,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
                )
            }
            Text(
                text = "${if (transaction.type == TransactionType.RECEITA) "+" else "-"} R$ ${transaction.amount}",
                fontSize = 14.sp,
                fontWeight = FontWeight.Bold,
                color = if (transaction.type == TransactionType.RECEITA) {
                    MaterialTheme.colorScheme.primary
                } else {
                    MaterialTheme.colorScheme.error
                }
            )
        }
    }
}

// Matemática precisa usando BigDecimal
private fun calculateBalance(transactions: List<TransactionModel>): String {
    var balance = BigDecimal.ZERO
    for (t in transactions) {
        if (t.type == TransactionType.RECEITA) {
            balance += t.amount
        } else {
            balance -= t.amount
        }
    }
    return balance.setScale(2, RoundingMode.HALF_UP).toString()
}