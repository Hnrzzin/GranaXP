package com.hnrzzin.granaxp.ui.theme.components

import androidx.compose.foundation.border
import androidx.compose.foundation.layout.height
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.ShowChart
import androidx.compose.material.icons.filled.MenuBook
import androidx.compose.material.icons.filled.AccountCircle
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.hnrzzin.granaxp.ui.theme.GranaXPColors

enum class AppTab { HOME, TRANSACTIONS, LEARN, PROFILE }

@Composable
fun AppBottomNavigationBar(
    selectedTab: AppTab,
    onNavigateToHome: () -> Unit,
    onNavigateToTransactions: () -> Unit,
    onNavigateToLearn: () -> Unit,
    onNavigateToProfile: () -> Unit
) {
    NavigationBar(
        containerColor = GranaXPColors.White,
        contentColor = GranaXPColors.Gray400,
        tonalElevation = 0.dp,
        modifier = Modifier.border(width = 1.dp, color = GranaXPColors.Gray200)
    ) {
        NavigationBarItem(
            selected = selectedTab == AppTab.HOME,
            onClick = onNavigateToHome,
            icon = { Icon(Icons.Default.Home, contentDescription = null) },
            label = { Text("Início", fontSize = 10.sp) },
            colors = navItemColors()
        )
        NavigationBarItem(
            selected = selectedTab == AppTab.TRANSACTIONS,
            onClick = onNavigateToTransactions,
            icon = { Icon(Icons.Default.ShowChart, contentDescription = null) },
            label = { Text("Transações", fontSize = 10.sp) },
            colors = navItemColors()
        )
        NavigationBarItem(
            selected = selectedTab == AppTab.LEARN,
            onClick = onNavigateToLearn,
            icon = { Icon(Icons.Default.MenuBook, contentDescription = null) },
            label = { Text("Aprender", fontSize = 10.sp) },
            colors = navItemColors()
        )
        NavigationBarItem(
            selected = selectedTab == AppTab.PROFILE,
            onClick = onNavigateToProfile,
            icon = { Icon(Icons.Default.AccountCircle, contentDescription = null) },
            label = { Text("Perfil", fontSize = 10.sp) },
            colors = navItemColors()
        )
    }
}

@Composable
private fun navItemColors() = NavigationBarItemDefaults.colors(
    selectedIconColor = GranaXPColors.Emerald600,
    selectedTextColor = GranaXPColors.Emerald600,
    unselectedIconColor = GranaXPColors.Gray400,
    unselectedTextColor = GranaXPColors.Gray400,
    indicatorColor = GranaXPColors.White // remove o "pill" de fundo padrão do Material3 atrás do ícone ativo
)