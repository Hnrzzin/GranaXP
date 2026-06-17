package com.hnrzzin.granaxp.ui.theme.screens

import androidx.compose.foundation.layout.*
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp

@Composable
fun HomeScreen(userId: String) {
    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Text("Home — em construção", fontSize = 18.sp, fontWeight = FontWeight.Bold)
    }
}

@Composable
fun TransactionsScreen(userId: String) {
    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Text("Transações — em construção", fontSize = 18.sp, fontWeight = FontWeight.Bold)
    }
}

@Composable
fun LearnScreen(userId: String) {
    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Text("Aprender — em construção", fontSize = 18.sp, fontWeight = FontWeight.Bold)
    }
}

@Composable
fun ProfileScreen(userId: String, onLogout: () -> Unit) {
    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Text("Perfil — em construção", fontSize = 18.sp, fontWeight = FontWeight.Bold)
    }
}