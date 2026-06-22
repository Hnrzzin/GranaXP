package com.hnrzzin.granaxp.navigation

import androidx.compose.runtime.Composable
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.hnrzzin.granaxp.ui.theme.screens.*
import com.hnrzzin.granaxp.viewmodel.AppViewModelFactory

sealed class Screen(val route: String) {
    object Home : Screen("home")
    object AddTransaction : Screen("add_transaction")
    object AddGoal : Screen("add_goal")
    object AddBudget : Screen("add_budget")
    object AddReminder : Screen("add_reminder")
    object Profile : Screen("profile")
    object TransactionList : Screen("transaction_list")
}

@Composable
fun AppNavigation() {
    val navController = rememberNavController()
    val factory = AppViewModelFactory()

    NavHost(
        navController = navController,
        startDestination = Screen.Home.route
    ) {
        // Rota Inicial (Dashboard)
        composable(Screen.Home.route) {
            HomeScreen(
                viewModel = viewModel(factory = factory),
                onNavigateToAddTransaction = { navController.navigate(Screen.AddTransaction.route) },
                onNavigateToTransactions = { navController.navigate(Screen.TransactionList.route) },
                onNavigateToProfile = { navController.navigate(Screen.Profile.route) }
            )
        }

        // Rota: Nova Transação
        composable(Screen.AddTransaction.route) {
            // Se AddTransactionScreen não existir ainda, crie um arquivo base para ela não quebrar
            AddTransactionScreen(
                viewModel = viewModel(factory = factory),
                onNavigateBack = { navController.popBackStack() }
            )
        }

        // Rota: Nova Meta Financeira
        composable(Screen.AddGoal.route) {
            AddGoalScreen(
                viewModel = viewModel(factory = factory),
                onNavigateBack = { navController.popBackStack() }
            )
        }

        // Rota: Lista de Transações
        composable(Screen.TransactionList.route) {
            TransactionsScreen(
                viewModel = viewModel(factory = factory),
                onNavigateBack = { navController.popBackStack() },
                onNavigateToAddTransaction = { navController.navigate(Screen.AddTransaction.route) }
            )
        }
    }
}