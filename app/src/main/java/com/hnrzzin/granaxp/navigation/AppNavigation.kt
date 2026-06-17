package com.hnrzzin.granaxp.ui.navigation

import androidx.compose.runtime.Composable
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.hnrzzin.granaxp.ui.theme.screens.* // Certifique-se de importar suas telas aqui

// 1. Definição segura das rotas
sealed class Screen(val route: String) {
    object Home : Screen("home")
    object AddTransaction : Screen("add_transaction")
    object AddGoal : Screen("add_goal")
    object AddBudget : Screen("add_budget")
    object AddReminder : Screen("add_reminder")
}

@Composable
fun AppNavigation() {
    val navController = rememberNavController()

    NavHost(
        navController = navController,
        startDestination = Screen.Home.route
    ) {
        // Rota Inicial (Dashboard)
        composable(Screen.Home.route) {
            // HomeScreen(onNavigate = { route -> navController.navigate(route) })
        }

        // Rota: Nova Transação
        composable(Screen.AddTransaction.route) {
            AddTransactionScreen(
                viewModel = viewModel(),
                onNavigateBack = { navController.popBackStack() }
            )
        }

        // Rota: Nova Meta Financeira
        composable(Screen.AddGoal.route) {
            AddGoalScreen(
                viewModel = viewModel(),
                onNavigateBack = { navController.popBackStack() }
            )
        }

        // Rota: Novo Gasto Planejado
        composable(Screen.AddBudget.route) {
            // AddBudgetScreen(viewModel = viewModel(), onNavigateBack = { navController.popBackStack() })
        }

        // Rota: Novo Lembrete
        composable(Screen.AddReminder.route) {
            // AddReminderScreen(viewModel = viewModel(), onNavigateBack = { navController.popBackStack() })
        }
    }
}