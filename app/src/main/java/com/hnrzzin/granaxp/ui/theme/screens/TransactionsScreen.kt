package com.hnrzzin.granaxp.ui.theme.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items // Import crucial adicionado para a lista
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.hnrzzin.granaxp.model.TransactionModel
import com.hnrzzin.granaxp.model.TransactionType
import com.hnrzzin.granaxp.viewmodel.TransactionViewModel
import com.hnrzzin.granaxp.viewmodel.TransactionUiState // Import do estado unificado correto

@Composable
fun TransactionsScreen(
    userId: String = "",
    viewModel: TransactionViewModel = viewModel(),
    onNavigateBack: () -> Unit = {},
    onNavigateToAddTransaction: () -> Unit = {}
) {
    // 1. Substituído o duplo estado pelo Single Source of Truth
    val uiState by viewModel.uiState.collectAsState()

    // 2. Corrigida a função para a assinatura real do ViewModel
    LaunchedEffect(Unit) {
        viewModel.fetchTransactions()
    }

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
                text = "Transações",
                fontSize = 20.sp,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onBackground
            )
            IconButton(onClick = onNavigateToAddTransaction) {
                Icon(Icons.Default.Add, contentDescription = "Adicionar")
            }
        }

        // Content
        // 3. Implementação limpa do when com smart cast na sealed class
        when (val state = uiState) {
            is TransactionUiState.Loading -> {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator()
                }
            }
            is TransactionUiState.Success -> {
                if (state.transactions.isEmpty()) {
                    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        Text(
                            text = "Nenhuma transação registrada",
                            color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.5f)
                        )
                    }
                } else {
                    LazyColumn(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(horizontal = 16.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        items(state.transactions) { transaction ->
                            TransactionListItem(
                                transaction = transaction,
                                // Função de delete já existente e compatível
                                onDelete = { viewModel.deleteTransaction(transaction) }
                            )
                        }
                    }
                }
            }
            is TransactionUiState.Error -> {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    // Aproveitando a propriedade message do estado de erro
                    Text(
                        text = state.message,
                        color = MaterialTheme.colorScheme.error,
                        modifier = Modifier.padding(16.dp)
                    )
                }
            }
        }
    }

    // 4. O bloco LaunchedEffect que chamava o resetState() inexistente foi deletado,
    // pois a arquitetura do ViewModel já reage chamando o fetchTransactions() após ações como delete.
}

@Composable
fun TransactionListItem(
    transaction: TransactionModel,
    onDelete: () -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
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
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Text(
                    text = "${if (transaction.type == TransactionType.RECEITA) "+" else "-"} R$ ${transaction.amount}",
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Bold,
                    color = if (transaction.type == TransactionType.RECEITA) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.error
                )
                IconButton(onClick = onDelete, modifier = Modifier.size(24.dp)) {
                    Icon(Icons.Default.Delete, contentDescription = "Deletar", tint = MaterialTheme.colorScheme.error)
                }
            }
        }
    }
}