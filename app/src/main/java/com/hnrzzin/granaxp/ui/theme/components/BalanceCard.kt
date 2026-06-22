package com.hnrzzin.granaxp.ui.theme.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowDownward
import androidx.compose.material.icons.filled.ArrowUpward
import androidx.compose.material.icons.outlined.AccountBalanceWallet
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.hnrzzin.granaxp.ui.theme.GranaXPTheme
import java.text.NumberFormat
import java.util.Locale

/**
 * Componente responsável por exibir o resumo financeiro do usuário.
 * Totalmente independente (Stateless), recebe todos os dados via parâmetros.
 *
 * @param currentBalance Saldo atual consolidado.
 * @param monthlyIncome Total de receitas do mês.
 * @param monthlyExpense Total de despesas do mês.
 * @param modifier Modificador para customização de layout externo.
 */
@Composable
fun BalanceCard(
    currentBalance: Double,
    monthlyIncome: Double,
    monthlyExpense: Double,
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.primary, // Verde Esmeralda do tema
            contentColor = MaterialTheme.colorScheme.onPrimary  // Contraste (Branco mapeado no tema)
        ),
        shape = RoundedCornerShape(16.dp),
        elevation = CardDefaults.cardElevation(defaultElevation = 4.dp)
    ) {
        Column(
            modifier = Modifier.padding(24.dp)
        ) {
            // Cabeçalho: Ícone e Título
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    imageVector = Icons.Outlined.AccountBalanceWallet,
                    contentDescription = "Ícone de Saldo",
                    tint = MaterialTheme.colorScheme.onPrimary.copy(alpha = 0.8f),
                    modifier = Modifier.size(20.dp)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = "Saldo Atual",
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onPrimary.copy(alpha = 0.8f)
                )
            }

            Spacer(modifier = Modifier.height(8.dp))

            // Valor do Saldo Atual
            Text(
                text = formatCurrency(currentBalance),
                style = MaterialTheme.typography.headlineLarge.copy(fontWeight = FontWeight.Bold),
                color = MaterialTheme.colorScheme.onPrimary
            )

            Spacer(modifier = Modifier.height(24.dp))

            // Container de Receitas e Despesas
            Surface(
                color = MaterialTheme.colorScheme.onPrimary.copy(alpha = 0.15f),
                shape = RoundedCornerShape(12.dp),
                contentColor = MaterialTheme.colorScheme.onPrimary
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 16.dp, horizontal = 12.dp),
                    horizontalArrangement = Arrangement.SpaceEvenly,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    // Indicador de Receita
                    FinancialIndicator(
                        title = "Receitas",
                        value = monthlyIncome,
                        isIncome = true,
                        modifier = Modifier.weight(1f)
                    )

                    // Divisor Vertical
                    Divider(
                        color = MaterialTheme.colorScheme.onPrimary.copy(alpha = 0.3f),
                        modifier = Modifier
                            .height(40.dp)
                            .width(1.dp)
                    )

                    // Indicador de Despesa
                    FinancialIndicator(
                        title = "Despesas",
                        value = monthlyExpense,
                        isIncome = false,
                        modifier = Modifier.weight(1f)
                    )
                }
            }
        }
    }
}

/**
 * Subcomponente reutilizável para exibir os indicadores individuais de Receita ou Despesa.
 */
@Composable
private fun FinancialIndicator(
    title: String,
    value: Double,
    isIncome: Boolean,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier,
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.Center
    ) {
        // Ícone circular com fundo translúcido
        Box(
            modifier = Modifier
                .size(32.dp)
                .clip(CircleShape)
                .background(MaterialTheme.colorScheme.onPrimary.copy(alpha = 0.2f)),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = if (isIncome) Icons.Default.ArrowUpward else Icons.Default.ArrowDownward,
                contentDescription = if (isIncome) "Ícone de Receita" else "Ícone de Despesa",
                tint = MaterialTheme.colorScheme.onPrimary,
                modifier = Modifier.size(16.dp)
            )
        }

        Spacer(modifier = Modifier.width(12.dp))

        Column {
            Text(
                text = title,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onPrimary.copy(alpha = 0.8f)
            )
            Text(
                text = formatCurrency(value),
                style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                color = MaterialTheme.colorScheme.onPrimary
            )
        }
    }
}

/**
 * Função auxiliar para formatar valores do tipo Double no padrão de moeda Brasileira (BRL).
 */
private fun formatCurrency(value: Double): String {
    return NumberFormat.getCurrencyInstance(Locale("pt", "BR")).format(value)
}

// ============================================================================
// PREVIEWS
// ============================================================================



