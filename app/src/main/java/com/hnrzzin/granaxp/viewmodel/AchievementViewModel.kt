package com.hnrzzin.granaxp.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.hnrzzin.granaxp.model.AchievementModel
import com.hnrzzin.granaxp.model.AchievementProgressModel
import com.hnrzzin.granaxp.model.RequirementType
import com.hnrzzin.granaxp.repositories.AchievementRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

data class AchievementWithProgress(
    val achievement: AchievementModel,
    val progress: AchievementProgressModel?
)

internal fun combineAchievements(
    achievements: List<AchievementModel>,
    canonicalProgress: List<AchievementProgressModel>,
    legacyProgress: List<AchievementProgressModel>,
): List<AchievementWithProgress> {
    val canonicalById = canonicalProgress.associateBy { it.achievementId }
    val legacyById = legacyProgress.groupBy { it.achievementId }.mapValues { (_, records) ->
        records.maxWith(compareBy<AchievementProgressModel> { it.isUnlocked }
            .thenBy { it.currentProgress }
            .thenBy { it.id })
    }
    val legacyTypes = setOf(
        RequirementType.TRANSACTION_COUNT, RequirementType.LESSON_COUNT,
        RequirementType.GOAL_COUNT, RequirementType.MONTHLY_SAVINGS,
    )
    return achievements.map { achievement ->
        val progress = canonicalById[achievement.id]
            ?: legacyById[achievement.id].takeIf { achievement.requirementType in legacyTypes }
        AchievementWithProgress(achievement, progress)
    }
}

sealed class AchievementUiState {
    object Loading : AchievementUiState()
    data class Success(val achievements: List<AchievementWithProgress>) : AchievementUiState()
    data class Error(val message: String) : AchievementUiState()
}

/**
 * Responsável apenas por buscar e exibir conquistas + progresso (usado no ProfileScreen).
 *
 * Os gatilhos legados continuam nos ViewModels da V1:
 * - TransactionViewModel.checkFinancialAchievements() (categoria FINANCAS)
 * - LessonViewModel.checkEducationAchievements() (categoria EDUCACAO)
 *
 * MODULE_COMPLETION é concedida pelo backend em completeLesson.
 */
class AchievementViewModel(private val userId: String) : ViewModel() {

    private val repository = AchievementRepository()

    private val _uiState = MutableStateFlow<AchievementUiState>(AchievementUiState.Loading)
    val uiState: StateFlow<AchievementUiState> = _uiState.asStateFlow()

    init {
        fetchAchievements()
    }

    fun fetchAchievements() {
        viewModelScope.launch {
            _uiState.value = AchievementUiState.Loading
            try {
                val achievements = repository.getAchievements()
                val canonicalProgress = repository.getUserAchievementProgress(userId)
                val legacyProgress = repository.getAchievementProgress(userId)
                _uiState.value = AchievementUiState.Success(
                    combineAchievements(achievements, canonicalProgress, legacyProgress),
                )
            } catch (e: Exception) {
                _uiState.value = AchievementUiState.Error("Falha ao buscar conquistas: ${e.message}")
            }
        }
    }

    fun getUnlockedCount(achievements: List<AchievementWithProgress>): Int {
        return achievements.count { it.progress?.isUnlocked == true }
    }
}

class AchievementViewModelFactory(private val userId: String) : ViewModelProvider.Factory {
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        @Suppress("UNCHECKED_CAST")
        return AchievementViewModel(userId) as T
    }
}
