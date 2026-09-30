package com.hnrzzin.granaxp.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.google.firebase.Timestamp
import com.hnrzzin.granaxp.model.GoalDeadline
import com.hnrzzin.granaxp.model.GoalModel
import com.hnrzzin.granaxp.model.RequirementType
import com.hnrzzin.granaxp.repositories.AchievementRepository
import com.hnrzzin.granaxp.repositories.GoalRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import com.hnrzzin.granaxp.utils.DateUtils

sealed class GoalUiState {
    object Loading : GoalUiState()
    data class Success(val goals: List<GoalModel>) : GoalUiState()
    data class Error(val message: String) : GoalUiState()
}

// Estado isolado para ações de criar/atualizar/deletar,
// desacoplado do carregamento da lista (mesmo padrão de TransactionSaveState).
sealed class GoalActionState {
    object Idle : GoalActionState()
    object Loading : GoalActionState()
    object Success : GoalActionState()
    data class Error(val message: String) : GoalActionState()
}

// Sinaliza para a UI que uma meta foi concluída pela primeira vez nesta
// chamada, permitindo exibir feedback de XP (mesmo padrão de LessonCompletionEvent).
sealed class GoalCompletionEvent {
    object Idle : GoalCompletionEvent()
    data class GoalCompleted(val xpEarned: Int, val leveledUp: Boolean) : GoalCompletionEvent()
}

class GoalViewModel(private val userId: String) : ViewModel() {
    private val repository = GoalRepository(userId)
    private val achievementRepository = AchievementRepository()

    private val _uiState = MutableStateFlow<GoalUiState>(GoalUiState.Loading)
    val uiState: StateFlow<GoalUiState> = _uiState.asStateFlow()

    private val _actionState = MutableStateFlow<GoalActionState>(GoalActionState.Idle)
    val actionState: StateFlow<GoalActionState> = _actionState.asStateFlow()

    private val _completionEvent = MutableStateFlow<GoalCompletionEvent>(GoalCompletionEvent.Idle)
    val completionEvent: StateFlow<GoalCompletionEvent> = _completionEvent.asStateFlow()

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
        requestId: String,
        title: String,
        targetAmount: Double,
        currentAmount: Double,
        deadline: GoalDeadline,
        alreadyDeclared: Boolean,
        deadlineDateRaw: String = "" // dígitos crus (8 chars)
    ) {
        viewModelScope.launch {
            _actionState.value = GoalActionState.Loading

            // Regra de negócio crítica #1
            val hasInitialAmount = currentAmount > 0.0
            if (hasInitialAmount && !alreadyDeclared) {
                _actionState.value = GoalActionState.Error(
                    "Declare esse valor como receita antes de adicioná-lo à meta."
                )
                return@launch
            }

            // Regra de negócio #3 — validação de data
            var deadlineTimestamp: Timestamp? = null
            if (deadlineDateRaw.length == 8) {
                when (val result = DateUtils.validateFutureDate(deadlineDateRaw)) {
                    is DateUtils.DateValidationResult.Valid -> deadlineTimestamp = result.timestamp
                    DateUtils.DateValidationResult.InvalidFormat -> {
                        _actionState.value = GoalActionState.Error("Verifique se a data está correta.")
                        return@launch
                    }
                    DateUtils.DateValidationResult.PastDate -> {
                        _actionState.value = GoalActionState.Error("A data não pode ser no passado.")
                        return@launch
                    }
                }
            }

            try {
                val result = repository.createGoal(
                    requestId,
                    title,
                    targetAmount,
                    currentAmount,
                    deadline,
                    deadlineTimestamp,
                )
                fetchGoals()
                _actionState.value = GoalActionState.Success

                try {
                    checkGoalAchievements()
                } catch (e: Exception) {
                    println("Falha ao checar conquistas de meta: $e")
                }

                if (result.xpEarned > 0) {
                    _completionEvent.value = GoalCompletionEvent.GoalCompleted(
                        result.xpEarned,
                        result.leveledUp,
                    )
                }
            } catch (e: Exception) {
                _actionState.value = GoalActionState.Error("Falha ao criar meta: ${e.message}")
            }
        }
    }
    private suspend fun checkGoalAchievements() {
        val goalCount = repository.getGoals().size
        val achievements = achievementRepository.getAchievements()
            .filter { it.requirementType == RequirementType.GOAL_COUNT }
        val progressList = achievementRepository.getAchievementProgress(userId)

        achievements.forEach { achievement ->
            val progress = progressList.find { it.achievementId == achievement.id }
            if (progress?.isUnlocked == true) return@forEach
            val shouldUnlock = goalCount >= achievement.requirementValue
            if (progress != null) {
                achievementRepository.updateAchievementProgress(progress.id, goalCount, shouldUnlock)
            } else {
                achievementRepository.createAchievementProgress(userId, achievement.id, goalCount, shouldUnlock)
            }
        }
    }

    /**
     * O backend soma o progresso e concede o bônus uma única vez, usando
     * completedAt como marcador persistente e operação transacional.
     */
    fun updateGoalProgress(goal: GoalModel, amountToAdd: Double, alreadyDeclared: Boolean) {
        viewModelScope.launch {
            _actionState.value = GoalActionState.Loading
            try {
                if (!alreadyDeclared) {
                    _actionState.value = GoalActionState.Error("Declare este valor como receita antes de adicionar à meta.")
                    return@launch
                }

                val result = repository.updateGoalProgress(goal.id, amountToAdd)
                fetchGoals()
                _actionState.value = GoalActionState.Success

                if (result.xpEarned > 0) {
                    _completionEvent.value = GoalCompletionEvent.GoalCompleted(
                        result.xpEarned,
                        result.leveledUp,
                    )
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

    fun resetCompletionEvent() {
        _completionEvent.value = GoalCompletionEvent.Idle
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
                repository.updateGoalDetails(
                    goalId = goal.id,
                    title = newTitle,
                    targetAmount = newTargetAmount,
                    deadline = newDeadline,
                    deadlineDate = newDeadlineDate,
                )
                fetchGoals()
                _actionState.value = GoalActionState.Success
            } catch (e: Exception) {
                _actionState.value = GoalActionState.Error("Falha ao atualizar meta: ${e.message}")
            }
        }
    }

}
