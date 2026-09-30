package com.hnrzzin.granaxp.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.hnrzzin.granaxp.model.UserModel
import com.hnrzzin.granaxp.repositories.AuthRepository
import com.hnrzzin.granaxp.repositories.UserRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

// Estado do perfil (dados do usuário)
sealed class UserUiState {
    object Loading : UserUiState()
    data class Success(val user: UserModel) : UserUiState()
    data class Error(val message: String) : UserUiState()
}

// Estado separado para ações do perfil; não deve travar nem se misturar com
// o carregamento dos dados do usuário.
sealed class UserActionState {
    object Idle : UserActionState()
    object Loading : UserActionState()
    object Success : UserActionState()
    object AccountDeleted : UserActionState()
    data class Error(val message: String) : UserActionState()
}

class UserViewModel(private val userId: String) : ViewModel() {

    private val userRepository = UserRepository(userId)
    private val authRepository = AuthRepository()

    private val _uiState = MutableStateFlow<UserUiState>(UserUiState.Loading)
    val uiState: StateFlow<UserUiState> = _uiState.asStateFlow()

    private val _actionState = MutableStateFlow<UserActionState>(UserActionState.Idle)
    val actionState: StateFlow<UserActionState> = _actionState.asStateFlow()

    init {
        fetchUser()
    }

    fun fetchUser() {
        viewModelScope.launch {
            _uiState.value = UserUiState.Loading
            try {
                val user = userRepository.getUser()
                if (user != null) {
                    _uiState.value = UserUiState.Success(user)
                } else {
                    _uiState.value = UserUiState.Error("Usuário não encontrado.")
                }
            } catch (e: Exception) {
                _uiState.value = UserUiState.Error("Falha ao buscar perfil: ${e.message}")
            }

        }

    }

    fun updateName(newName: String) {
        if (newName.isBlank()) {
            _actionState.value = UserActionState.Error("O nome não pode ficar vazio.")
            return
        }
        viewModelScope.launch {
            _actionState.value = UserActionState.Loading
            try {
                userRepository.updateName(newName)
                fetchUser() // Atualiza o estado local com o novo nome
                _actionState.value = UserActionState.Success
            } catch (e: Exception) {
                _actionState.value = UserActionState.Error("Falha ao atualizar nome: ${e.message}")
            }
        }
    }

    fun logout() {
        authRepository.logout()
    }

    /** Exclusão autoritativa: o backend remove os dados privados antes do Auth. */
    fun deleteAccount() {
        viewModelScope.launch {
            _actionState.value = UserActionState.Loading
            try {
                authRepository.deleteAccount()
                _actionState.value = UserActionState.AccountDeleted
            } catch (e: Exception) {
                _actionState.value = UserActionState.Error("Falha ao excluir conta: ${e.message}")
            }
        }
    }

    fun resetActionState() {
        _actionState.value = UserActionState.Idle
    }

}
