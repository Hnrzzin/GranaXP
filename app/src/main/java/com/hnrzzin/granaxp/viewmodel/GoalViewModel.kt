package com.hnrzzin.granaxp.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
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

class GoalViewModel(private val userId: String) : ViewModel() {
    private val repository = GoalRepository(userId)
    private val _uiState = MutableStateFlow<GoalUiState>(GoalUiState.Loading)
    val uiState: StateFlow<GoalUiState> = _uiState.asStateFlow()

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
        alreadyDeclared: Boolean
    ) {
        viewModelScope.launch {
            try {
                // Regra: só registra o valor inicial se já foi declarado como receita
                val validatedCurrentAmount = if (alreadyDeclared) currentAmount else 0.0
                repository.createGoal(title, targetAmount, validatedCurrentAmount, deadline)
                fetchGoals()
            } catch (e: Exception) {
                _uiState.value = GoalUiState.Error("Falha ao criar meta: ${e.message}")
            }
        }
    }

    fun updateGoalProgress(goal: GoalModel, amountToAdd: Double, alreadyDeclared: Boolean) {
        viewModelScope.launch {
            try {
                // Regra: só adiciona à meta se o valor já faz parte do saldo (declarado)
                if (alreadyDeclared) {
                    val newCurrentAmount = goal.currentAmount + amountToAdd
                    val updatedGoal = goal.copy(currentAmount = newCurrentAmount)
                    repository.updateGoal(updatedGoal)
                    fetchGoals()
                } else {
                    // Se não foi declarado, a interface deve orientar o usuário a cadastrar a receita primeiro
                    _uiState.value = GoalUiState.Error("Declare este valor como receita antes de adicionar à meta.")
                }
            } catch (e: Exception) {
                _uiState.value = GoalUiState.Error("Falha ao atualizar meta: ${e.message}")
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
}