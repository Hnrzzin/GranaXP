package com.hnrzzin.granaxp.ui.theme.components

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.hnrzzin.granaxp.model.TransactionType // Importação corrigida para a camada Model

@Composable
fun TransactionItem(
    description: String,
    category: String,
    date: String,
    amount: Double,
    transactionType: TransactionType,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    // Define a cor baseada no tipo da transação
    val amountColor = if (transactionType == TransactionType.RECEITA) {
        MaterialTheme.colorScheme.primary // Verde/Primária
    } else {
        MaterialTheme.colorScheme.error // Vermelho/Erro
    }

    val amountPrefix = if (transactionType == TransactionType.RECEITA) "+ R$" else "- R$"
    val formattedAmount = String.format("%.2f", amount)

    Row(
        modifier = modifier
            .fillMaxWidth()
            .clickable { onClick() }
            .padding(vertical = 12.dp, horizontal = 16.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = description,
                style = MaterialTheme.typography.bodyLarge,
                fontWeight = FontWeight.Medium,
                color = MaterialTheme.colorScheme.onSurface
            )
            Text(
                text = category,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }

        Column(horizontalAlignment = Alignment.End) {
            Text(
                text = "$amountPrefix $formattedAmount",
                style = MaterialTheme.typography.bodyLarge,
                fontWeight = FontWeight.Bold,
                color = amountColor
            )
            Text(
                text = date,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}


