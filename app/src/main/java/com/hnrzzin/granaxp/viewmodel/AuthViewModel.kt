package com.hnrzzin.granaxp.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.hnrzzin.granaxp.model.UserModel
import com.hnrzzin.granaxp.repositories.UserAuth
import com.hnrzzin.granaxp.repositories.UserRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

sealed class AuthUiState {
    object Idle    : AuthUiState()
    object Loading : AuthUiState()
    object Success : AuthUiState()
    data class Error(val message: String) : AuthUiState()
}

class AuthViewModel : ViewModel() {

    private val userAuth = UserAuth()

    // REMOVIDO: private val userRepository = UserRepository() daqui do topo.

    private val _uiState = MutableStateFlow<AuthUiState>(AuthUiState.Idle)
    val uiState: StateFlow<AuthUiState> = _uiState.asStateFlow()

    fun loginWithEmail(email: String, password: String) {
        viewModelScope.launch {
            _uiState.value = AuthUiState.Loading
            val user = userAuth.loginWithEmail(email, password)
            _uiState.value = if (user != null) AuthUiState.Success
            else AuthUiState.Error("Email ou senha incorretos")
        }
    }

    fun registerWithEmail(email: String, password: String, name: String) {
        viewModelScope.launch {
            _uiState.value = AuthUiState.Loading
            val firebaseUser = userAuth.createAuthUserWithEmail(email, password)

            if (firebaseUser != null) {
                // SOLUÇÃO: Instancia o UserRepository AQUI, passando o UID que acabou de ser criado
                val userRepository = UserRepository(firebaseUser.uid)
                userRepository.createUser(UserModel(id = firebaseUser.uid, name = name))
                _uiState.value = AuthUiState.Success
            } else {
                _uiState.value = AuthUiState.Error("Falha ao criar conta. Tente novamente.")
            }
        }
    }

    fun resetState() { _uiState.value = AuthUiState.Idle }
}