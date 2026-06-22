package com.hnrzzin.granaxp.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.hnrzzin.granaxp.model.LessonModel
import com.hnrzzin.granaxp.model.LessonProgressModel
import com.hnrzzin.granaxp.repositories.LessonRepository
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

class LessonViewModel(private val userId: String) : ViewModel() {

    private val repository = LessonRepository(userId)

    private val _uiState = MutableStateFlow<LessonUiState>(LessonUiState.Loading)
    val uiState: StateFlow<LessonUiState> = _uiState.asStateFlow()

    init {
        fetchLessons()
    }

    fun fetchLessons() {
        viewModelScope.launch {
            _uiState.value = LessonUiState.Loading
            try {
                val lessons = repository.getLessons()
                val progressList = repository.getLessonProgress()

                // Combina lição com seu progresso
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

    fun completeLesson(lessonId: String, progressId: String) {
        viewModelScope.launch {
            try {
                repository.updateLessonProgress(progressId, isCompleted = true)
                fetchLessons()
            } catch (e: Exception) {
                _uiState.value = LessonUiState.Error("Falha ao completar lição: ${e.message}")
            }
        }
    }

    fun startLesson(lessonId: String) {
        viewModelScope.launch {
            try {
                // Cria o progresso se o usuário ainda não acessou essa lição
                repository.createLessonProgress(lessonId)
                fetchLessons()
            } catch (e: Exception) {
                _uiState.value = LessonUiState.Error("Falha ao iniciar lição: ${e.message}")
            }
        }
    }

    // Verifica se uma lição está desbloqueada
    // Regra: a primeira lição sempre está desbloqueada,
    // as demais dependem da anterior estar completa
    fun isLessonUnlocked(index: Int, lessons: List<LessonWithProgress>): Boolean {
        if (index == 0) return true
        val previous = lessons.getOrNull(index - 1) ?: return false
        return previous.progress?.isCompleted == true
    }

    // Calcula quantas lições foram completadas
    fun getCompletedCount(lessons: List<LessonWithProgress>): Int {
        return lessons.count { it.progress?.isCompleted == true }
    }
}

class LessonViewModelFactory(private val userId: String) : ViewModelProvider.Factory {
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        return LessonViewModel(userId) as T
    }
}