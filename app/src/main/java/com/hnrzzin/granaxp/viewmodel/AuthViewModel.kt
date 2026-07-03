package com.hnrzzin.granaxp.viewmodel

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.hnrzzin.granaxp.model.UserModel
import com.hnrzzin.granaxp.repositories.AuthRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

sealed class AuthUiState {
    object Idle : AuthUiState()
    object Loading : AuthUiState()
    data class Authenticated(val userId: String, val userProfile: UserModel? = null) : AuthUiState()
    object Unauthenticated : AuthUiState()  // ← deve ser object, não class
    data class Error(val message: String) : AuthUiState()
}

class AuthViewModel : ViewModel() {

    private val repository = AuthRepository()
    private val _uiState = MutableStateFlow<AuthUiState>(AuthUiState.Idle)
    val uiState: StateFlow<AuthUiState> = _uiState.asStateFlow()

    init {

        val t = Throwable()

        Log.d(
            "TraceLogin",
            "======================================"
        )

        Log.d(
            "TraceLogin",
            "VM = ${hashCode()}"
        )

        t.stackTrace.forEach {
            Log.d(
                "TraceLogin",
                it.toString()
            )
        }

        checkCurrentUser()
    }

    fun checkCurrentUser() {
        viewModelScope.launch {

            Log.d("TraceLogin", "AuthViewModel criado ${hashCode()}")

            _uiState.value = AuthUiState.Loading

            try {

                val userId = repository.getCurrentUserId()

                Log.d("TraceInit", "UID retornado = $userId")

                if (userId != null) {

                    Log.d("TraceState", "Authenticated")

                    val profile = repository.getUserProfile(userId)

                    _uiState.value =
                        AuthUiState.Authenticated(userId, profile)

                } else {

                    Log.d("TraceState", "Unauthenticated")

                    _uiState.value =
                        AuthUiState.Unauthenticated
                }

            } catch (e: Exception) {

                Log.e("TraceState", "Erro", e)

                _uiState.value =
                    AuthUiState.Error(e.message ?: "")
            }
        }
    }

    fun login(email: String, pass: String) {

        Log.d("TraceLogin", "Entrou no login()")

        viewModelScope.launch {

            Log.d("TraceLogin", "Coroutine iniciada")

            _uiState.value = AuthUiState.Loading

            try {

                Log.d("TraceLogin", "Chamando Firebase")

                val userId = repository.loginWithEmail(email, pass)

                Log.d("TraceLogin", "UID = $userId")

                val profile = repository.getUserProfile(userId)

                Log.d("TraceLogin", "Perfil = $profile")

                _uiState.value =
                    AuthUiState.Authenticated(userId, profile)

                Log.d("TraceLogin", "Estado atualizado")

            } catch (e: Exception) {

                Log.e("TraceLogin", "Erro", e)

                _uiState.value =
                    AuthUiState.Error(e.message ?: "")
            }
        }
    }

    fun register(name: String, email: String, pass: String) {
        viewModelScope.launch {
            Log.d("TraceRegister", "AuthViewModel: Iniciando register para email=$email")
            _uiState.value = AuthUiState.Loading
            try {
                val userId = repository.registerWithEmail(email, pass)
                Log.d("TraceRegister", "AuthViewModel: Retornou UID do repositório: $userId")

                val newUser = UserModel(
                    id = userId,
                    name = name,
                    email = email,
                    level = 1,
                    xp = 0,
                    nextLevelXp = 100
                )
                Log.d("TraceRegister", "AuthViewModel: UserModel montado antes do set: $newUser")

                repository.createUserProfile(newUser)
                Log.d("TraceRegister", "AuthViewModel: createUserProfile concluído sem exceptions.")

                _uiState.value = AuthUiState.Authenticated(userId, newUser)
                Log.d("TraceRegister", "AuthViewModel: Estado atualizado para Authenticated.")
            } catch (e: Exception) {
                Log.e("TraceRegister", "AuthViewModel: Exceção capturada: ${e.message}", e)
                _uiState.value = AuthUiState.Error("Falha ao registrar: ${e.message}")
            }
        }
    }

    fun logout() {
        // Mantido sem alterações
        viewModelScope.launch {
            try {
                repository.logout()
                _uiState.value = AuthUiState.Unauthenticated
            } catch (e: Exception) {
                _uiState.value = AuthUiState.Error("Erro ao sair: ${e.message}")
            }
        }
    }
}