package com.hnrzzin.granaxp.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.hnrzzin.granaxp.model.AchievementModel
import com.hnrzzin.granaxp.model.AchievementProgressModel
import com.hnrzzin.granaxp.repositories.AchievementRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

data class AchievementWithProgress(
    val achievement: AchievementModel,
    val progress: AchievementProgressModel?
)

sealed class AchievementUiState {
    object Loading : AchievementUiState()
    data class Success(val achievements: List<AchievementWithProgress>) : AchievementUiState()
    data class Error(val message: String) : AchievementUiState()
}

/**
 * Responsável apenas por buscar e exibir conquistas + progresso (usado no ProfileScreen).
 *
 * O desbloqueio em si (checagem de requisito x progresso atual) acontece dentro
 * de cada feature ViewModel logo após a ação relevante:
 * - TransactionViewModel.checkFinancialAchievements() (categoria FINANCAS)
 * - LessonViewModel.checkEducationAchievements() (categoria EDUCACAO)
 *
 * Isso evita acoplamento cruzado entre ViewModels — nenhum precisa conhecer o outro.
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
                val progressList = repository.getAchievementProgress(userId)

                val achievementsWithProgress = achievements.map { achievement ->
                    val progress = progressList.find { it.achievementId == achievement.id }
                    AchievementWithProgress(achievement = achievement, progress = progress)
                }

                _uiState.value = AchievementUiState.Success(achievementsWithProgress)
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