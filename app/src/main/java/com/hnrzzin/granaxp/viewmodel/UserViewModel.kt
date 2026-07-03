package com.hnrzzin.granaxp.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.hnrzzin.granaxp.model.UserModel
import com.hnrzzin.granaxp.repositories.*
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

// Estado separado para ações de edição (nome), seguindo o padrão de
// TransactionSaveState — não deve travar/misturar com o carregamento do perfil.
sealed class UserActionState {
    object Idle : UserActionState()
    object Loading : UserActionState()
    object Success : UserActionState()
    data class Error(val message: String) : UserActionState()
}

class UserViewModel(private val userId: String) : ViewModel() {

    private val userRepository = UserRepository(userId)
    private val authRepository = AuthRepository()

    // Repositories necessários apenas para a limpeza de subcoleções na exclusão de conta
    private val transactionRepository = TransactionRepository(userId)
    private val goalRepository = GoalRepository(userId)
    private val reminderRepository = ReminderRepository(userId)
    private val budgetRepository = BudgetRepository(userId)

    private val lessonRepository = LessonRepository(userId)
    private val achievementRepository = AchievementRepository()


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

    /**
     * Exclusão de conta — Regra de Negócio Crítica #6:
     * subcoleções DEVEM ser apagadas antes da conta no Auth.
     * TODO consciente: cada delete abaixo apaga documento por documento;
     * se o volume de dados crescer muito, migrar para Cloud Function
     * com batch delete no backend seria mais seguro e atômico.
     */
    fun deleteAccount() {
        viewModelScope.launch {
            _actionState.value = UserActionState.Loading
            try {
                val transactions = transactionRepository.getTransactions()
                transactions.forEach { transactionRepository.deleteTransaction(it) }

                val goals = goalRepository.getGoals()
                goals.forEach { goalRepository.deleteGoal(it) }

                val reminders = reminderRepository.getReminders()
                reminders.forEach { reminderRepository.deleteReminder(it) }

                val budgets = budgetRepository.getBudgets()
                budgets.forEach { budgetRepository.deleteBudget(it) }

                val lessonProgress = lessonRepository.getAllLessonProgress() // ✅ corrigido
                lessonProgress.forEach { lessonRepository.deleteLessonProgress(it.id) } // ✅ forEach, não ?.let

                val achievementProgress = achievementRepository.getAchievementProgress(userId)
                achievementProgress.forEach { achievementRepository.deleteAchievementProgress(it.id) }

                authRepository.deleteAccount() // por último: todas as subcoleções já limpas
                _actionState.value = UserActionState.Success
            } catch (e: Exception) {
                _actionState.value = UserActionState.Error("Falha ao excluir conta: ${e.message}")
            }
        }
    }

    fun resetActionState() {
        _actionState.value = UserActionState.Idle
    }
}