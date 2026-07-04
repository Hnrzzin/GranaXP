package com.hnrzzin.granaxp.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.google.firebase.Timestamp
import com.hnrzzin.granaxp.model.GoalDeadline
import com.hnrzzin.granaxp.model.GoalModel
import com.hnrzzin.granaxp.repositories.GoalRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

sealed class GoalUiState {
    object Loading : GoalUiState()
    data class Success(val goals: List<GoalModel>) : GoalUiState()
    data class Error(val message: String) : GoalUiState()
}

// ✅ Novo: estado isolado para ações de criar/atualizar/deletar,
// desacoplado do carregamento da lista (mesmo padrão de TransactionSaveState).
sealed class GoalActionState {
    object Idle : GoalActionState()
    object Loading : GoalActionState()
    object Success : GoalActionState()
    data class Error(val message: String) : GoalActionState()
}

class GoalViewModel(private val userId: String) : ViewModel() {
    private val repository = GoalRepository(userId)

    private val _uiState = MutableStateFlow<GoalUiState>(GoalUiState.Loading)
    val uiState: StateFlow<GoalUiState> = _uiState.asStateFlow()

    private val _actionState = MutableStateFlow<GoalActionState>(GoalActionState.Idle)
    val actionState: StateFlow<GoalActionState> = _actionState.asStateFlow()

    init {
        fetchGoals()
    }

    fun fetchGoals() {
        viewModelScope.launch {
            _uiState.value = GoalUiState.Loading
            try {
                val goals = repository.getGoals()
                _uiState.value = GoalUiState.Success(goals)
            } catch (e: Exception) {
                _uiState.value = GoalUiState.Error("Falha ao buscar metas: ${e.message}")
            }
        }
    }

    fun createGoal(
        title: String,
        targetAmount: Double,
        currentAmount: Double,
        deadline: GoalDeadline,
        alreadyDeclared: Boolean,
        deadlineDate: Timestamp? = null
    ) {
        viewModelScope.launch {
            _actionState.value = GoalActionState.Loading
            try {
                val validatedCurrentAmount = if (alreadyDeclared) currentAmount else 0.0
                repository.createGoal(title, targetAmount, validatedCurrentAmount, deadline, deadlineDate)
                fetchGoals()
                _actionState.value = GoalActionState.Success
            } catch (e: Exception) {
                _actionState.value = GoalActionState.Error("Falha ao criar meta: ${e.message}")
            }
        }
    }

    fun updateGoalProgress(goal: GoalModel, amountToAdd: Double, alreadyDeclared: Boolean) {
        viewModelScope.launch {
            _actionState.value = GoalActionState.Loading
            try {
                if (alreadyDeclared) {
                    val newCurrentAmount = goal.currentAmount + amountToAdd
                    val updatedGoal = goal.copy(currentAmount = newCurrentAmount)
                    repository.updateGoal(updatedGoal)
                    fetchGoals()
                    _actionState.value = GoalActionState.Success
                } else {
                    _actionState.value = GoalActionState.Error("Declare este valor como receita antes de adicionar à meta.")
                }
            } catch (e: Exception) {
                _actionState.value = GoalActionState.Error("Falha ao atualizar meta: ${e.message}")
            }
        }
    }

    fun deleteGoal(goal: GoalModel) {
        viewModelScope.launch {
            try {
                repository.deleteGoal(goal)
                fetchGoals()
            } catch (e: Exception) {
                _uiState.value = GoalUiState.Error("Falha ao deletar meta: ${e.message}")
            }
        }
    }

    fun calculateProgress(goal: GoalModel): Float {
        if (goal.targetAmount == 0.0) return 0f
        return (goal.currentAmount / goal.targetAmount).toFloat().coerceIn(0f, 1f)
    }

    fun resetActionState() {
        _actionState.value = GoalActionState.Idle
    }

    fun updateGoalDetails(
        goal: GoalModel,
        newTitle: String,
        newTargetAmount: Double,
        newDeadline: GoalDeadline,
        newDeadlineDate: Timestamp?
    ) {
        viewModelScope.launch {
            _actionState.value = GoalActionState.Loading
            try {
                val updatedGoal = goal.copy(
                    title = newTitle,
                    targetAmount = newTargetAmount,
                    deadline = newDeadline,
                    deadlineDate = newDeadlineDate
                    // currentAmount NÃO entra aqui — preservado do goal original via copy()
                )
                repository.updateGoal(updatedGoal)
                fetchGoals()
                _actionState.value = GoalActionState.Success
            } catch (e: Exception) {
                _actionState.value = GoalActionState.Error("Falha ao atualizar meta: ${e.message}")
            }
        }
    }
}