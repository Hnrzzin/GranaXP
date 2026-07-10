package com.hnrzzin.granaxp.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.hnrzzin.granaxp.model.LessonModel
import com.hnrzzin.granaxp.model.LessonProgressModel
import com.hnrzzin.granaxp.model.RequirementType
import com.hnrzzin.granaxp.repositories.AchievementRepository
import com.hnrzzin.granaxp.repositories.LessonRepository
import com.hnrzzin.granaxp.repositories.UserRepository
import com.hnrzzin.granaxp.utils.XpUtils
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

data class LessonWithProgress(
    val lesson: LessonModel,
    val progress: LessonProgressModel?
)

sealed class LessonUiState {
    object Loading : LessonUiState()
    data class Success(val lessons: List<LessonWithProgress>) : LessonUiState()
    data class Error(val message: String) : LessonUiState()
}

// Sinaliza para a UI que uma lição foi concluída com sucesso,
// permitindo exibir feedback (ex: "+100 XP", subiu de nível) e fechar o modal.
sealed class LessonCompletionEvent {
    object Idle : LessonCompletionEvent()
    data class Completed(val xpEarned: Int, val leveledUp: Boolean) : LessonCompletionEvent()
    data class Error(val message: String) : LessonCompletionEvent()
}

class LessonViewModel(private val userId: String) : ViewModel() {

    private val repository = LessonRepository(userId)
    private val userRepository = UserRepository(userId)
    private val achievementRepository = AchievementRepository()

    private val _uiState = MutableStateFlow<LessonUiState>(LessonUiState.Loading)
    val uiState: StateFlow<LessonUiState> = _uiState.asStateFlow()

    private val _completionEvent = MutableStateFlow<LessonCompletionEvent>(LessonCompletionEvent.Idle)
    val completionEvent: StateFlow<LessonCompletionEvent> = _completionEvent.asStateFlow()

    init {
        fetchLessons()
    }

    fun fetchLessons() {
        viewModelScope.launch {
            _uiState.value = LessonUiState.Loading
            try {
                val lessons = repository.getLessons()
                val progressList = repository.getAllLessonProgress()

                val lessonsWithProgress = lessons.map { lesson ->
                    val progress = progressList.find { it.lessonId == lesson.id }
                    LessonWithProgress(lesson = lesson, progress = progress)
                }

                _uiState.value = LessonUiState.Success(lessonsWithProgress)
            } catch (e: Exception) {
                _uiState.value = LessonUiState.Error("Falha ao buscar lições: ${e.message}")
            }
        }
    }

    /**
     * Conclui a lição: marca/cria o progresso, concede XP (regra #5) e
     * verifica desbloqueio de conquistas de educação (regra #4).
     *
     * A checagem de conquistas é isolada em seu próprio try/catch: nesse ponto
     * o progresso já foi persistido e o XP já foi creditado, então uma falha
     * ao checar conquistas (ex: instabilidade de rede) não deve fazer o
     * usuário achar que a conclusão inteira falhou — o que antes causava
     * risco de XP duplicado numa nova tentativa.
     */
    fun completeLesson(lesson: LessonModel, existingProgressId: String?) {
        viewModelScope.launch {
            try {
                if (existingProgressId != null) {
                    repository.updateLessonProgress(existingProgressId, isCompleted = true)
                } else {
                    val newProgressId = repository.createLessonProgress(lesson.id)
                    newProgressId?.let { repository.updateLessonProgress(it, isCompleted = true) }
                }

                val leveledUp = XpUtils.grantXp(userRepository, lesson.xpReward)

                // Isolado de propósito — ver doc acima.
                try {
                    checkEducationAchievements()
                } catch (e: Exception) {
                    println("Falha ao checar conquistas de educação: $e")
                }

                fetchLessons()

                _completionEvent.value = LessonCompletionEvent.Completed(
                    xpEarned = lesson.xpReward,
                    leveledUp = leveledUp
                )
            } catch (e: Exception) {
                _completionEvent.value = LessonCompletionEvent.Error("Falha ao completar lição: ${e.message}")
            }
        }
    }

    // Inicia (abre) uma lição ainda não acessada, sem marcar como concluída.
    fun startLesson(lessonId: String) {
        viewModelScope.launch {
            try {
                repository.createLessonProgress(lessonId)
                fetchLessons()
            } catch (e: Exception) {
                _uiState.value = LessonUiState.Error("Falha ao iniciar lição: ${e.message}")
            }
        }
    }

    // Regra #4: conquistas de categoria EDUCACAO baseadas em completedLessonsCount.
    private suspend fun checkEducationAchievements() {
        val completedCount = repository.getAllLessonProgress().count { it.isCompleted }
        val achievements = achievementRepository.getAchievements()
            .filter { it.category == com.hnrzzin.granaxp.model.CategoriaConquista.EDUCACAO }
            .filter { it.requirementType == RequirementType.LESSON_COUNT }
        val progressList = achievementRepository.getAchievementProgress(userId)

        achievements.forEach { achievement ->
            val progress = progressList.find { it.achievementId == achievement.id }
            if (progress?.isUnlocked == true) return@forEach

            val shouldUnlock = completedCount >= achievement.requirementValue

            if (progress != null) {
                achievementRepository.updateAchievementProgress(progress.id, completedCount, shouldUnlock)
            } else {
                achievementRepository.createAchievementProgress(userId, achievement.id, completedCount, shouldUnlock)
            }
        }
    }

    fun resetCompletionEvent() {
        _completionEvent.value = LessonCompletionEvent.Idle
    }

    // Regra #3: a primeira lição sempre desbloqueada; as demais dependem da anterior completa.
    fun isLessonUnlocked(index: Int, lessons: List<LessonWithProgress>): Boolean {
        if (index == 0) return true
        val previous = lessons.getOrNull(index - 1) ?: return false
        return previous.progress?.isCompleted == true
    }

    fun getCompletedCount(lessons: List<LessonWithProgress>): Int {
        return lessons.count { it.progress?.isCompleted == true }
    }
}

class LessonViewModelFactory(private val userId: String) : ViewModelProvider.Factory {
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        @Suppress("UNCHECKED_CAST")
        return LessonViewModel(userId) as T
    }
}