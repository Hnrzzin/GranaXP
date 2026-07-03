package com.hnrzzin.granaxp.navigation

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.hnrzzin.granaxp.ui.theme.screens.HomeScreen
import com.hnrzzin.granaxp.ui.theme.screens.ProfileScreen
import com.hnrzzin.granaxp.ui.theme.screens.TransactionsScreen
import com.hnrzzin.granaxp.ui.theme.screens.auth.LoginScreen
import com.hnrzzin.granaxp.ui.theme.screens.auth.RegisterScreen
import com.hnrzzin.granaxp.viewmodel.AppViewModelFactory
import com.hnrzzin.granaxp.viewmodel.AuthUiState
import com.hnrzzin.granaxp.viewmodel.AuthViewModel
import com.hnrzzin.granaxp.ui.theme.screens.LearnScreen

sealed class Screen(val route: String) {
    object Login : Screen("login")
    object Register : Screen("register")
    object Home : Screen("home")

    object Profile : Screen("profile")
    object TransactionList : Screen("transaction_list")

    object Learn : Screen("learn")
}

@Composable
fun AppNavigation() {
    val navController = rememberNavController()

    // AuthViewModel é instanciado UMA VEZ aqui, fora do NavHost.
    // Todas as telas que precisarem dele recebem ESTA instância.
    val authViewModel: AuthViewModel = viewModel()
    val authUiState by authViewModel.uiState.collectAsStateWithLifecycle()

    // Navegação baseada no estado de autenticação.
    // LaunchedEffect(authUiState) reage apenas quando o estado muda.
    LaunchedEffect(authUiState) {
        when (authUiState) {
            is AuthUiState.Authenticated -> {
                navController.navigate(Screen.Home.route) {
                    popUpTo(navController.graph.id) { inclusive = true }
                    launchSingleTop = true
                }
            }
            is AuthUiState.Unauthenticated -> {
                // Guard: só navega se não estiver já em Login
                if (navController.currentDestination?.route != Screen.Login.route) {
                    navController.navigate(Screen.Login.route) {
                        popUpTo(navController.graph.id) { inclusive = true }
                        launchSingleTop = true
                    }
                }
            }
            else -> Unit
        }
    }

    NavHost(
        navController = navController,
        // startDestination aponta para Login.
        // Se o usuário já estiver autenticado, o LaunchedEffect
        // acima navega imediatamente para Home sem piscar a tela de login,
        // pois checkCurrentUser() é chamado no init do AuthViewModel.
        startDestination = Screen.Login.route
    ) {

        // --- Rotas de Autenticação ---

        composable(Screen.Login.route) {
            LoginScreen(
                // Passamos A MESMA instância de AuthViewModel criada acima.
                // Isto evita o bug de instâncias duplicadas com estados diferentes.
                viewModel = authViewModel,
                onLoginSuccess = {
                    // A navegação é controlada pelo LaunchedEffect(authUiState).
                    // Este callback pode ser deixado vazio ou usado para logs.
                },
                onNavigateToRegister = {
                    navController.navigate(Screen.Register.route)
                }
            )
        }

        composable(Screen.Register.route) {
            RegisterScreen(
                viewModel = authViewModel,
                onRegisterSuccess = {
                    // A navegação é controlada pelo LaunchedEffect(authUiState).
                },
                onNavigateToLogin = {
                    navController.popBackStack()
                }
            )
        }

        // --- Rotas Principais (requerem userId) ---

        composable(Screen.Home.route) {
            val userId = (authUiState as? AuthUiState.Authenticated)?.userId
                ?: return@composable
            val factory = AppViewModelFactory(userId)

            HomeScreen(
                viewModel = viewModel(factory = factory),

                onNavigateToTransactions = {
                    navController.navigate(Screen.TransactionList.route)
                },
                onNavigateToLearn = {
                    navController.navigate(Screen.Learn.route)
                },
                onNavigateToProfile = {
                    navController.navigate(Screen.Profile.route)
                }
            )
        }


        composable(Screen.TransactionList.route) {
            // Recupera o ID do usuário logado para a factory
            val userId = (authUiState as? AuthUiState.Authenticated)?.userId
                ?: return@composable
            val factory = AppViewModelFactory(userId)

            TransactionsScreen(
                // Injeta os 4 ViewModels exigidos pela nova TransactionsScreen
                transactionViewModel = viewModel(factory = factory),
                budgetViewModel = viewModel(factory = factory),
                goalViewModel = viewModel(factory = factory),
                reminderViewModel = viewModel(factory = factory),

                // Mapeia as novas ações de navegação da tela
                onNavigateToHome = {
                    navController.navigate(Screen.Home.route) {
                        popUpTo(Screen.Home.route) { inclusive = true }
                    }
                },
                onNavigateToLearn = {
                    navController.navigate(Screen.Learn.route)
                },
                onNavigateToProfile = {
                    navController.navigate(Screen.Profile.route)
                },

            )
        }
        composable(Screen.Profile.route) {
            val userId = (authUiState as? AuthUiState.Authenticated)?.userId
                ?: return@composable
            val factory = AppViewModelFactory(userId)

            ProfileScreen(
                userViewModel = viewModel(factory = factory),
                achievementViewModel = viewModel(factory = factory),
                onNavigateToHome = {
                    navController.navigate(Screen.Home.route) {
                        popUpTo(Screen.Home.route) { inclusive = true }
                    }
                },
                onNavigateToTransactions = {
                    navController.navigate(Screen.TransactionList.route)
                },
                onNavigateToLearn = {
                    navController.navigate(Screen.Learn.route)
                },
                onLogout = { authViewModel.logout() },
                onAccountDeleted = { authViewModel.logout() }
            )
        }


        composable(Screen.Learn.route) {
            val userId = (authUiState as? AuthUiState.Authenticated)?.userId
                ?: return@composable
            val factory = AppViewModelFactory(userId)

            LearnScreen(
                viewModel = viewModel(factory = factory),
                onNavigateToHome = {
                    navController.navigate(Screen.Home.route) {
                        popUpTo(Screen.Home.route) { inclusive = true }
                    }
                },
                onNavigateToTransactions = {
                    navController.navigate(Screen.TransactionList.route)
                },
                onNavigateToProfile = {
                    navController.navigate(Screen.Profile.route)
                }
            )
        }
    }
}