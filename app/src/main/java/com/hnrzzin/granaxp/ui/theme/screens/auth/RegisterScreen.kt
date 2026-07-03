package com.hnrzzin.granaxp.ui.theme.screens.auth

import android.util.Log
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.hnrzzin.granaxp.viewmodel.AuthUiState
import com.hnrzzin.granaxp.viewmodel.AuthViewModel

private val GreenPrimary = Color(0xFF2E7D32)

@Composable
fun RegisterScreen(
    onRegisterSuccess: () -> Unit,
    onNavigateToLogin: () -> Unit,
    viewModel: AuthViewModel = viewModel()
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    var name by remember { mutableStateOf("") }
    var email by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    var passwordVisible by remember { mutableStateOf(false) }

    LaunchedEffect(uiState) {
        if (uiState is AuthUiState.Authenticated) {
            Log.d("TraceRegister", "RegisterScreen: Estado mudou para Authenticated. Disparando navegação para Home.")
            onRegisterSuccess()
        }
    }

    Column(
        modifier = Modifier.fillMaxSize().background(GreenPrimary),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Spacer(Modifier.height(80.dp))
        Text("GranaXP", color = Color.White, fontSize = 36.sp, fontWeight = FontWeight.Bold)
        Text("Gestão & Aprendizado", color = Color.White.copy(alpha = 0.8f), fontSize = 14.sp)
        Spacer(Modifier.height(48.dp))

        Card(
            modifier = Modifier.fillMaxSize(),
            shape = RoundedCornerShape(topStart = 32.dp, topEnd = 32.dp),
            colors = CardDefaults.cardColors(containerColor = Color.White)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(horizontal = 24.dp, vertical = 32.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text("Crie sua conta", fontSize = 22.sp, fontWeight = FontWeight.Bold)
                Text(
                    "Comece sua jornada financeira",
                    fontSize = 14.sp, color = Color.Gray,
                    modifier = Modifier.padding(top = 4.dp, bottom = 32.dp)
                )

                OutlinedTextField(
                    value = name, onValueChange = { name = it },
                    label = { Text("Nome") },
                    leadingIcon = { Icon(Icons.Default.Person, null) },
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp), singleLine = true
                )
                Spacer(Modifier.height(16.dp))
                OutlinedTextField(
                    value = email, onValueChange = { email = it },
                    label = { Text("Email") },
                    leadingIcon = { Icon(Icons.Default.Email, null) },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email),
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp), singleLine = true
                )
                Spacer(Modifier.height(16.dp))
                OutlinedTextField(
                    value = password, onValueChange = { password = it },
                    label = { Text("Senha") },
                    leadingIcon = { Icon(Icons.Default.Lock, null) },
                    trailingIcon = {
                        IconButton(onClick = { passwordVisible = !passwordVisible }) {
                            Icon(
                                if (passwordVisible) Icons.Default.VisibilityOff
                                else Icons.Default.Visibility, null
                            )
                        }
                    },
                    visualTransformation = if (passwordVisible) VisualTransformation.None
                    else PasswordVisualTransformation(),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp), singleLine = true
                )

                Spacer(Modifier.height(24.dp))

                if (uiState is AuthUiState.Error) {
                    Text(
                        text = (uiState as AuthUiState.Error).message,
                        color = MaterialTheme.colorScheme.error,
                        fontSize = 14.sp, textAlign = TextAlign.Center,
                        modifier = Modifier.padding(bottom = 12.dp)
                    )
                }

                Button(
                    onClick = {
                        Log.d("TraceRegister", "RegisterScreen: Botão clicado. Enviando dados: email=$email")
                        viewModel.register(name = name, email = email, pass = password)
                    },
                    modifier = Modifier.fillMaxWidth().height(52.dp),
                    shape = RoundedCornerShape(12.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = GreenPrimary),
                    enabled = uiState !is AuthUiState.Loading
                ) {
                    if (uiState is AuthUiState.Loading) {
                        CircularProgressIndicator(color = Color.White, modifier = Modifier.size(24.dp), strokeWidth = 2.dp)
                    } else {
                        Text("Criar conta", fontSize = 16.sp, fontWeight = FontWeight.SemiBold)
                    }
                }

                Spacer(Modifier.height(24.dp))

                TextButton(onClick = onNavigateToLogin) {
                    Text("Já tem conta? ", color = Color.Gray)
                    Text("Entrar", color = GreenPrimary, fontWeight = FontWeight.Bold)
                }
            }
        }
    }
}