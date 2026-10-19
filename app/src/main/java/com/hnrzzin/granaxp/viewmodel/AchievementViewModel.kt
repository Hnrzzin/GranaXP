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
    localProgress: List<AchievementProgressModel> = emptyList(),
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
    val localById = localProgress.associateBy { it.achievementId }
    return achievements.map { achievement ->
        val historical = legacyById[achievement.id].takeIf { achievement.requirementType in legacyTypes }
        val canonical = canonicalById[achievement.id]
        val preserved = if (historical?.isUnlocked == true && canonical?.isUnlocked != true) historical
            else canonical ?: historical
        val local = localById[achievement.id]
        val progress = if (preserved?.isUnlocked == true) preserved
            else if (local?.isUnlocked == true || (local != null &&
                    local.currentProgress > (preserved?.currentProgress ?: -1))) local
            else preserved ?: local
        AchievementWithProgress(achievement, progress)
    }
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
    loadLocalProgress: suspend (List<AchievementModel>) -> List<AchievementProgressModel> = { emptyList() },
): AchievementUiState = try {
    val catalog = loadCatalog()
    AchievementUiState.Success(
        combineAchievements(catalog, loadCanonicalProgress(), loadLegacyProgress(),
            loadLocalProgress(catalog)),
    )
} catch (e: Exception) {
    AchievementUiState.Error("Falha ao buscar conquistas: ${e.message}")
}

/** Histórico canônico mais indicadores locais derivados, sem escrita de conquistas. */
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
                loadLocalProgress = { catalog -> repository.getLocalAchievementProgress(userId, catalog) },
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
