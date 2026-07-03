package com.hnrzzin.granaxp.ui.theme.screens

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.hnrzzin.granaxp.ui.theme.components.*
import com.hnrzzin.granaxp.viewmodel.HomeUiState
import com.hnrzzin.granaxp.viewmodel.HomeViewModel
import com.hnrzzin.granaxp.ui.theme.components.AppBottomNavigationBar

@Composable
fun HomeScreen(
    viewModel: HomeViewModel,
    onNavigateToAddTransaction: () -> Unit = {},
    onNavigateToTransactions: () -> Unit = {},
    onNavigateToLearn: () -> Unit = {},
    onNavigateToProfile: () -> Unit = {},
    modifier: Modifier = Modifier
) {
    val uiState by viewModel.uiState.collectAsState()

    when (val state = uiState) {
        is HomeUiState.Loading -> {
            Box(modifier = modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                CircularProgressIndicator()
            }
        }

        is HomeUiState.Error -> {
            Box(modifier = modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Text(
                    text = state.message,
                    color = MaterialTheme.colorScheme.error,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.padding(16.dp)
                )
            }
        }

        is HomeUiState.Success -> {
            Scaffold(
                modifier = modifier.fillMaxSize(),
                topBar = {
                    HomeTopBar(
                        level = state.user.level,
                        xp = state.user.xp,
                        nextLevelXp = state.user.nextLevelXp,
                        onProfileClick = onNavigateToProfile
                    )
                },
                bottomBar = {
                    AppBottomNavigationBar(
                        selectedTab = AppTab.HOME,
                        onNavigateToHome = onNavigateToLearn,
                        onNavigateToTransactions = onNavigateToTransactions,
                        onNavigateToLearn = onNavigateToLearn,
                        onNavigateToProfile = onNavigateToProfile
                    )
                }
            ) { paddingValues ->
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
                            currentBalance = state.totalBalance,
                            monthlyIncome = state.totalIncome,
                            monthlyExpense = state.totalExpense
                        )
                    }

                    // 2. Insight Inteligente (Mantido estático conforme restrição)
                    item {
                        InsightCard(
                            title = "Insight Inteligente",
                            description = "Excelente! Você está poupando bem. Considere aprender sobre investimentos.",
                            actionText = "Aprender mais sobre isso >",
                            onLearnMoreClick = onNavigateToLearn
                        )
                    }

                    // 3. Seção de Transações
                    item {
                        TransactionSection(
                            transactions = state.recentTransactions,
                            onSeeAllClick = onNavigateToTransactions,
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
    }
}

