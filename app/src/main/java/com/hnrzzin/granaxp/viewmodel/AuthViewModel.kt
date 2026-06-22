package com.hnrzzin.granaxp.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.hnrzzin.granaxp.model.UserModel
import com.hnrzzin.granaxp.repositories.AuthRepository // Assumindo que você tem/terá este repositório
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

sealed class AuthUiState {
    object Idle : AuthUiState()
    object Loading : AuthUiState()
    data class Authenticated(val userId: String, val userProfile: UserModel? = null) : AuthUiState()
    object Unauthenticated : AuthUiState()
    data class Error(val message: String) : AuthUiState()
}

class AuthViewModel : ViewModel() {

    // O AuthRepository não recebe userId no construtor porque ele lida com a sessão global do Firebase
    private val repository = AuthRepository()

    private val _uiState = MutableStateFlow<AuthUiState>(AuthUiState.Idle)
    val uiState: StateFlow<AuthUiState> = _uiState.asStateFlow()

    init {
        checkCurrentUser()
    }

    // Verifica se o usuário já está logado ao abrir o app
    fun checkCurrentUser() {
        viewModelScope.launch {
            _uiState.value = AuthUiState.Loading
            try {
                val userId = repository.getCurrentUserId()
                if (userId != null) {
                    // Busca os dados complementares do usuário (XP, Nível, Nome) no Firestore
                    val userProfile = repository.getUserProfile(userId)
                    _uiState.value = AuthUiState.Authenticated(userId, userProfile)
                } else {
                    _uiState.value = AuthUiState.Unauthenticated
                }
            } catch (e: Exception) {
                _uiState.value = AuthUiState.Error("Erro ao verificar sessão: ${e.message}")
            }
        }
    }

    fun login(email: String, pass: String) {
        viewModelScope.launch {
            _uiState.value = AuthUiState.Loading
            try {
                val userId = repository.loginWithEmail(email, pass)
                val userProfile = repository.getUserProfile(userId)
                _uiState.value = AuthUiState.Authenticated(userId, userProfile)
            } catch (e: Exception) {
                _uiState.value = AuthUiState.Error("Falha no login: ${e.message}")
            }
        }
    }

    fun register(name: String, email: String, pass: String) {
        viewModelScope.launch {
            _uiState.value = AuthUiState.Loading
            try {
                // 1. Cria a conta no Firebase Auth
                val userId = repository.registerWithEmail(email, pass)

                // 2. Prepara o modelo de usuário do seu banco de dados
                val newUser = UserModel(
                    id = userId,
                    name = name,
                    email = email,
                    level = 1,
                    xp = 0,
                    nextLevelXp = 100
                )

                // 3. Salva no Firestore
                repository.createUserProfile(newUser)

                _uiState.value = AuthUiState.Authenticated(userId, newUser)
            } catch (e: Exception) {
                _uiState.value = AuthUiState.Error("Falha ao registrar: ${e.message}")
            }
        }
    }

    fun logout() {
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