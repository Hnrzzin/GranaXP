package com.hnrzzin.granaxp.ui.theme.screens

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Book
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.SwapHoriz
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.hnrzzin.granaxp.model.TransactionType
import com.hnrzzin.granaxp.ui.theme.components.*
import com.hnrzzin.granaxp.ui.theme.GranaXPTheme
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import com.hnrzzin.granaxp.viewmodel.HomeViewModel

@Composable
fun HomeScreen(
    // A tela agora OBRIGA quem a chama (AppNavigation) a fornecer o ViewModel já instanciado
    viewModel: HomeViewModel,

    // Callbacks de navegação restaurados para manter compatibilidade com o NavHost
    onNavigateToAddTransaction: () -> Unit = {},
    onNavigateToTransactions: () -> Unit = {},
    onNavigateToProfile: () -> Unit = {},
    modifier: Modifier = Modifier
) {
    val uiState by viewModel.uiState.collectAsState()
    // Mock de dados para espelhar fielmente o protótipo visual
    val mockTransactions = listOf(
        TransactionUIData(
            description = "Salário Mensal",
            category = "Salário",
            date = "", // Omissão de data para focar no layout do protótipo
            amount = 3500.00,
            type = TransactionType.RECEITA
        ),
        TransactionUIData(
            description = "Mercado",
            category = "Alimentação",
            date = "",
            amount = 150.00,
            type = TransactionType.DESPESA
        ),
        TransactionUIData(
            description = "Uber",
            category = "Transporte",
            date = "",
            amount = 60.00,
            type = TransactionType.DESPESA
        )
    )

    Scaffold(
        modifier = modifier.fillMaxSize(),
        topBar = {
            HomeTopBar(
                level = 2,
                xp = 120, // Simula a barra amarela em ~40% conforme o protótipo
                nextLevelXp = 300,
                onProfileClick = { /* TODO: Manter callback de navegação */ }
            )
        },
        bottomBar = {
            HomeBottomNavigationPlaceholder()
        }
    ) { paddingValues ->
        // LazyColumn exigida: excelente para performance e sem conflito de scroll
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {

            // 1. Card de Saldo
            item {
                BalanceCard(
                    currentBalance = 3290.00,
                    monthlyIncome = 3500.00,
                    monthlyExpense = 210.00
                )
            }

            // 2. Insight Inteligente
            item {
                InsightCard(
                    title = "Insight Inteligente",
                    description = "Excelente! Você está poupando bem. Considere aprender sobre investimentos.",
                    actionText = "Aprender mais sobre isso >",
                    onLearnMoreClick = { /* TODO: Manter callback de navegação */ }
                )
            }

            // 3. Seção de Transações (Já embute o SectionTitle e os Itens)
            item {
                TransactionSection(
                    transactions = mockTransactions,
                    onSeeAllClick = { /* TODO: Manter callback de navegação */ },
                    onItemClick = { /* TODO: Lidar com o clique no item */ }
                )
            }

            // Espaçamento inferior para a lista não colar na Bottom Navigation
            item {
                Spacer(modifier = Modifier.height(8.dp))
            }
        }
    }
}

/**
 * Mantido o componente existente simulando a barra de navegação inferior (BottomNavigation) do projeto,
 * conforme orientação de não mexer no AppNavigation global agora.
 */
@Composable
private fun HomeBottomNavigationPlaceholder() {
    NavigationBar(
        containerColor = MaterialTheme.colorScheme.surface,
        tonalElevation = 8.dp
    ) {
        NavigationBarItem(
            selected = true,
            onClick = { /* Preparado para rotas */ },
            icon = { Icon(Icons.Default.Home, contentDescription = null) },
            label = { Text("Início") }
        )
        NavigationBarItem(
            selected = false,
            onClick = { /* Preparado para rotas */ },
            icon = { Icon(Icons.Default.SwapHoriz, contentDescription = null) },
            label = { Text("Transações") }
        )
        NavigationBarItem(
            selected = false,
            onClick = { /* Preparado para rotas */ },
            icon = { Icon(Icons.Default.Book, contentDescription = null) },
            label = { Text("Aprender") }
        )
        NavigationBarItem(
            selected = false,
            onClick = { /* Preparado para rotas */ },
            icon = { Icon(Icons.Default.Person, contentDescription = null) },
            label = { Text("Perfil") }
        )
    }
}


