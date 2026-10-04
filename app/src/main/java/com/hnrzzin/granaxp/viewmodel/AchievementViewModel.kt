package com.hnrzzin.granaxp.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.hnrzzin.granaxp.model.AchievementModel
import com.hnrzzin.granaxp.model.AchievementProgressModel
import com.hnrzzin.granaxp.model.CategoriaConquista
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

internal suspend fun loadLegacyAchievementCheckInputs(
    loadCount: suspend () -> Int,
    loadCatalog: suspend () -> List<AchievementModel>,
    loadProgress: suspend () -> List<AchievementProgressModel>,
    requirementType: RequirementType,
    category: CategoriaConquista? = null,
): Triple<Int, List<AchievementModel>, List<AchievementProgressModel>> {
    val count = loadCount()
    val achievements = loadCatalog().filter { achievement ->
        achievement.requirementType == requirementType &&
            (category == null || achievement.category == category)
    }
    return Triple(count, achievements, loadProgress())
}

sealed class AchievementUiState {
    object Loading : AchievementUiState()
    data class Success(val achievements: List<AchievementWithProgress>) : AchievementUiState()
    data class Error(val message: String) : AchievementUiState()
}

internal suspend fun loadAchievementUiState(
    loadCatalog: suspend () -> List<AchievementModel>,
    loadCanonicalProgress: suspend () -> List<AchievementProgressModel>,
    loadLegacyProgress: suspend () -> List<AchievementProgressModel>,
): AchievementUiState = try {
    AchievementUiState.Success(
        combineAchievements(loadCatalog(), loadCanonicalProgress(), loadLegacyProgress()),
    )
} catch (e: Exception) {
    AchievementUiState.Error("Falha ao buscar conquistas: ${e.message}")
}

/**
 * Responsável apenas por buscar e exibir conquistas + progresso (usado no ProfileScreen).
 *
 * Os gatilhos legados continuam nos ViewModels da V1:
 * - TransactionViewModel.checkFinancialAchievements() (categoria FINANCAS)
 * - LessonViewModel.checkEducationAchievements() (categoria EDUCACAO)
 *
 * MODULE_COMPLETION e STREAK são concedidas pelo backend em completeLesson
 * e submitDailyMissionAnswer, respectivamente.
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
            _uiState.value = loadAchievementUiState(
                loadCatalog = { repository.getAchievements() },
                loadCanonicalProgress = { repository.getUserAchievementProgress(userId) },
                loadLegacyProgress = { repository.getAchievementProgress(userId) },
            )
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
