package com.hnrzzin.granaxp.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.hnrzzin.granaxp.model.RequirementType
import com.hnrzzin.granaxp.repositories.AchievementRepository
import com.hnrzzin.granaxp.repositories.LessonRepository
import com.hnrzzin.granaxp.repositories.ModuleRepository
import com.hnrzzin.granaxp.repositories.UserRepository
import com.hnrzzin.granaxp.utils.XpUtils
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

class LessonViewModel(private val userId: String) : ViewModel() {

    private val repository = LessonRepository(userId)
    private val moduleRepository = ModuleRepository()
    private val userRepository = UserRepository(userId)
    private val achievementRepository = AchievementRepository()

    private val _uiState = MutableStateFlow<LessonUiState>(LessonUiState.Loading)
    val uiState: StateFlow<LessonUiState> = _uiState.asStateFlow()

    private val _completionEvent = MutableStateFlow<LessonCompletionEvent>(LessonCompletionEvent.Idle)
    val completionEvent: StateFlow<LessonCompletionEvent> = _completionEvent.asStateFlow()

    private val _contentUiState = MutableStateFlow<LessonContentUiState>(LessonContentUiState.Idle)
    val contentUiState: StateFlow<LessonContentUiState> = _contentUiState.asStateFlow()

    private var fetchLessonsJob: Job? = null
    private var loadLessonBlocksJob: Job? = null

    init {
        fetchLessons()
    }

    fun fetchLessons() {
        fetchLessonsJob?.cancel()
        fetchLessonsJob = viewModelScope.launch {
            _uiState.value = LessonUiState.Loading
            try {
                val modules = moduleRepository.getModules()
                val allLessons = repository.getLessons()
                val progressList = repository.getAllLessonProgress()
                val lessonsByModule = modules.associate { module ->
                    module.idModule to repository.getLessonsByModule(module.idModule)
                }

                _uiState.value = buildLearningContent(
                    modules = modules,
                    allLessons = allLessons,
                    progressList = progressList,
                    lessonsByModule = lessonsByModule,
                )
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                _uiState.value = LessonUiState.Error("Falha ao carregar a trilha: ${e.message}")
            }
        }
    }

    fun openLesson(lesson: LessonWithProgress) {
        if (!lesson.isStarted) {
            startLesson(lesson.lesson.id)
        }
        loadLessonBlocks(lesson.lesson.id)
    }

    private fun loadLessonBlocks(lessonId: String) {
        loadLessonBlocksJob?.cancel()
        loadLessonBlocksJob = viewModelScope.launch {
            _contentUiState.value = LessonContentUiState.Loading(lessonId)
            try {
                val blocks = repository.getLessonBlocks(lessonId)
                if ((_contentUiState.value as? LessonContentUiState.Loading)?.lessonId == lessonId) {
                    _contentUiState.value = LessonContentUiState.Success(lessonId, blocks)
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                if ((_contentUiState.value as? LessonContentUiState.Loading)?.lessonId == lessonId) {
                    _contentUiState.value = LessonContentUiState.Error(
                        lessonId = lessonId,
                        message = "Falha ao carregar o conteúdo: ${e.message}",
                    )
                }
            }
        }
    }

    fun clearLessonContent() {
        loadLessonBlocksJob?.cancel()
        loadLessonBlocksJob = null
        _contentUiState.value = LessonContentUiState.Idle
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
    fun completeLesson(lessonWithProgress: LessonWithProgress) {
        viewModelScope.launch {
            try {
                val lesson = lessonWithProgress.lesson
                val existingProgressId = lessonWithProgress.progress?.id
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
    private fun startLesson(lessonId: String) {
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

}

class LessonViewModelFactory(private val userId: String) : ViewModelProvider.Factory {
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        @Suppress("UNCHECKED_CAST")
        return LessonViewModel(userId) as T
    }
}
