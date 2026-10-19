package com.hnrzzin.granaxp.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.hnrzzin.granaxp.repositories.LessonRepository
import com.hnrzzin.granaxp.repositories.ModuleRepository
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

class LessonViewModel(private val userId: String) : ViewModel() {

    private val repository = LessonRepository(userId)
    private val moduleRepository = ModuleRepository()

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
                val progressList = repository.getLessonProgress()

                _uiState.value = buildLearningContent(
                    modules = modules,
                    allLessons = allLessons,
                    progressList = progressList,
                )
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                _uiState.value = LessonUiState.Error("Falha ao carregar a trilha: ${e.message}")
            }
        }
    }

    fun openLesson(lesson: LessonWithProgress) {
        loadLesson(lesson)
    }

    private fun loadLesson(lessonWithProgress: LessonWithProgress) {
        val lessonId = lessonWithProgress.lesson.id
        loadLessonBlocksJob?.cancel()
        loadLessonBlocksJob = viewModelScope.launch {
            _contentUiState.value = LessonContentUiState.Loading(lessonId)
            try {
                val opened = repository.openLesson(lessonId)
                validateLessonActivities(opened.blocks)
                if ((_contentUiState.value as? LessonContentUiState.Loading)?.lessonId == lessonId) {
                    _contentUiState.value = LessonContentUiState.Success(
                        lessonId = lessonId,
                        blocks = opened.blocks,
                        completedActivityIds = opened.completedActivityIds,
                    )
                    fetchLessons()
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

    fun selectActivityAnswer(blockId: String, answerId: String) {
        val content = _contentUiState.value as? LessonContentUiState.Success ?: return
        val block = content.blocks.find {
            it.id == blockId && it.type.equals("ACTIVITY", ignoreCase = true)
        } ?: return
        val currentAnswer = content.activityAnswers[blockId] ?: ActivityAnswerState()
        if (currentAnswer.isSubmitting) return

        val updatedAnswer = currentAnswer.copy(
            selectedAnswerIds = updateSelectedAnswers(
                block = block,
                currentSelection = currentAnswer.selectedAnswerIds,
                answerId = answerId,
            ),
            result = ActivityAnswerResult.IDLE,
            message = null,
        )
        _contentUiState.value = content.copy(
            activityAnswers = content.activityAnswers + (blockId to updatedAnswer),
        )
    }

    fun submitActivityAnswer(blockId: String) {
        val content = _contentUiState.value as? LessonContentUiState.Success ?: return
        val block = content.blocks.find {
            it.id == blockId && it.type.equals("ACTIVITY", ignoreCase = true)
        } ?: return
        val answer = content.activityAnswers[blockId] ?: return
        if (answer.selectedAnswerIds.isEmpty() || answer.isSubmitting) return

        _contentUiState.value = content.copy(
            activityAnswers = content.activityAnswers + (
                blockId to answer.copy(
                    isSubmitting = true,
                    result = ActivityAnswerResult.IDLE,
                    message = null,
                )
            ),
        )

        viewModelScope.launch {
            try {
                val result = repository.submitLessonActivity(
                    lessonId = content.lessonId,
                    blockId = blockId,
                    selectedAnswerIds = answer.selectedAnswerIds,
                )
                updateActivityAfterPersistence(
                    lessonId = content.lessonId,
                    blockId = blockId,
                    result = if (result.correct) ActivityAnswerResult.CORRECT else ActivityAnswerResult.INCORRECT,
                    message = result.feedback,
                    confirmedCompletedIds = if (result.correct) result.completedActivityIds else null,
                )
                if (result.correct) fetchLessons()
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                updateActivityAfterPersistence(
                    lessonId = content.lessonId,
                    blockId = blockId,
                    result = ActivityAnswerResult.ERROR,
                    message = "Não foi possível salvar a atividade. Tente novamente.",
                )
            }
        }
    }

    private fun updateActivityAfterPersistence(
        lessonId: String,
        blockId: String,
        result: ActivityAnswerResult,
        message: String? = null,
        confirmedCompletedIds: Set<String>? = null,
    ) {
        val current = _contentUiState.value as? LessonContentUiState.Success ?: return
        if (current.lessonId != lessonId) return
        val currentAnswer = current.activityAnswers[blockId] ?: ActivityAnswerState()

        _contentUiState.value = current.copy(
            completedActivityIds = confirmedCompletedIds ?: current.completedActivityIds,
            activityAnswers = current.activityAnswers + (
                blockId to currentAnswer.copy(
                    result = result,
                    isSubmitting = false,
                    message = message,
                )
            ),
        )
    }

    /** Conclusão educacional Spark; o XP exibido não é autoridade financeira. */
    fun completeLesson(lessonWithProgress: LessonWithProgress) {
        val content = _contentUiState.value as? LessonContentUiState.Success
        if (content?.lessonId != lessonWithProgress.lesson.id || !content.canComplete) {
            _completionEvent.value = LessonCompletionEvent.Error(
                "Conclua todas as atividades antes de finalizar a lição.",
            )
            return
        }

        viewModelScope.launch {
            try {
                val result = repository.completeLesson(lessonWithProgress.lesson.id)


                fetchLessons()

                _completionEvent.value = LessonCompletionEvent.Completed(
                    xpEarned = result.xpEarned,
                    leveledUp = result.leveledUp,
                )
            } catch (e: Exception) {
                _completionEvent.value = LessonCompletionEvent.Error("Falha ao completar lição: ${e.message}")
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
