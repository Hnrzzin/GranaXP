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

                // Combina conquista com seu progresso
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

    // Chamado após qualquer ação do usuário para verificar
    // se alguma conquista deve ser desbloqueada
    fun checkAndUnlockAchievements(
        transactionCount: Int,
        completedLessonsCount: Int,
        totalSaved: Double
    ) {
        viewModelScope.launch {
            try {
                val achievements = repository.getAchievements()
                val progressList = repository.getAchievementProgress(userId)

                achievements.forEach { achievement ->
                    val progress = progressList.find { it.achievementId == achievement.id }

                    // Só verifica conquistas ainda não desbloqueadas
                    if (progress?.isUnlocked == true) return@forEach

                    // Define o progresso atual baseado na categoria
                    val currentProgress = when (achievement.category) {
                        com.hnrzzin.granaxp.model.CategoriaConquista.FINANCAS -> transactionCount
                        com.hnrzzin.granaxp.model.CategoriaConquista.EDUCACAO -> completedLessonsCount
                        com.hnrzzin.granaxp.model.CategoriaConquista.GERAL -> totalSaved.toInt()
                    }

                    val shouldUnlock = currentProgress >= achievement.requirementValue

                    if (progress != null) {
                        repository.updateAchievementProgress(
                            progressId = progress.id,
                            currentProgress = currentProgress,
                            isUnlocked = shouldUnlock
                        )
                    } else {
                        // Cria o progresso se ainda não existir
                        repository.createAchievementProgress(
                            userId = userId,
                            achievementId = achievement.id,
                            currentProgress = currentProgress,
                            isUnlocked = shouldUnlock
                        )
                    }
                }

                fetchAchievements()
            } catch (e: Exception) {
                _uiState.value = AchievementUiState.Error("Falha ao verificar conquistas: ${e.message}")
            }
        }
    }

    // Conta conquistas desbloqueadas
    fun getUnlockedCount(achievements: List<AchievementWithProgress>): Int {
        return achievements.count { it.progress?.isUnlocked == true }
    }
}

class AchievementViewModelFactory(private val userId: String) : ViewModelProvider.Factory {
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        return AchievementViewModel(userId) as T
    }
}