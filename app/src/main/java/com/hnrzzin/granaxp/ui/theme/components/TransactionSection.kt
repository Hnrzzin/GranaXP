package com.hnrzzin.granaxp.ui.theme.components

import androidx.compose.foundation.layout.*
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.hnrzzin.granaxp.model.TransactionType // Importação corrigida para a camada Model

@Composable
fun TransactionSection(
    transactions: List<TransactionUIData>,
    onSeeAllClick: () -> Unit,
    onItemClick: (TransactionUIData) -> Unit,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        SectionTitle(
            title = "Últimas transações",
            buttonText = "Ver todas",
            onActionClick = onSeeAllClick,
            modifier = Modifier.padding(horizontal = 16.dp)
        )

        Column(modifier = Modifier.fillMaxWidth()) {
            transactions.forEach { transaction ->
                TransactionItem(
                    description = transaction.description,
                    category = transaction.category,
                    date = transaction.date,
                    amount = transaction.amount,
                    transactionType = transaction.type,
                    onClick = { onItemClick(transaction) }
                )
            }
        }
    }
}

@Preview(showBackground = true)
@Composable
fun TransactionSectionPreview() {
    val mockList = listOf(
        TransactionUIData("Bolsa Estágio", "Renda", "05/06/2026", 800.0, TransactionType.RECEITA),
        TransactionUIData("Spotify", "Assinaturas", "02/06/2026", 21.90, TransactionType.DESPESA)
    )
    MaterialTheme {
        TransactionSection(
            transactions = mockList,
            onSeeAllClick = {},
            onItemClick = {}
        )
    }
}
