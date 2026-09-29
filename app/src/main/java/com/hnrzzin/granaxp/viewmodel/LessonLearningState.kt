package com.hnrzzin.granaxp.viewmodel

import com.hnrzzin.granaxp.model.LessonBlock
import com.hnrzzin.granaxp.model.LessonModel
import com.hnrzzin.granaxp.model.LessonProgressModel
import com.hnrzzin.granaxp.model.ModuleModel

data class LessonWithProgress(
    val lesson: LessonModel,
    val progress: LessonProgressModel?,
) {
    val isStarted: Boolean
        get() = progress != null

    val isCompleted: Boolean
        get() = progress?.isCompleted == true
}

data class ModuleWithLessons(
    val module: ModuleModel,
    val lessons: List<LessonWithProgress>,
) {
    val completedLessons: Int
        get() = lessons.count { it.isCompleted }

    val totalLessons: Int
        get() = lessons.size

    val progress: Float
        get() = if (totalLessons == 0) 0f else completedLessons.toFloat() / totalLessons

    val isCompleted: Boolean
        get() = totalLessons > 0 && completedLessons == totalLessons

    fun isLessonUnlocked(index: Int): Boolean = lessonIsUnlocked(index, lessons)
}

sealed class LessonUiState {
    object Loading : LessonUiState()

    data class Success(
        val modules: List<ModuleWithLessons>,
        val legacyLessons: List<LessonWithProgress>,
    ) : LessonUiState() {
        val lessons: List<LessonWithProgress>
            get() = modules.flatMap { it.lessons } + legacyLessons

        fun isLegacyLessonUnlocked(index: Int): Boolean = lessonIsUnlocked(index, legacyLessons)
    }

    data class Error(val message: String) : LessonUiState()
}

sealed class LessonContentUiState {
    object Idle : LessonContentUiState()
    data class Loading(val lessonId: String) : LessonContentUiState()
    data class Success(
        val lessonId: String,
        val blocks: List<LessonBlock>,
    ) : LessonContentUiState()

    data class Error(
        val lessonId: String,
        val message: String,
    ) : LessonContentUiState()
}

sealed class LessonCompletionEvent {
    object Idle : LessonCompletionEvent()
    data class Completed(val xpEarned: Int, val leveledUp: Boolean) : LessonCompletionEvent()
    data class Error(val message: String) : LessonCompletionEvent()
}

private fun lessonIsUnlocked(index: Int, lessons: List<LessonWithProgress>): Boolean {
    if (index == 0) return true
    val previous = lessons.getOrNull(index - 1) ?: return false
    return previous.isCompleted
}

internal fun buildLearningContent(
    modules: List<ModuleModel>,
    allLessons: List<LessonModel>,
    progressList: List<LessonProgressModel>,
    lessonsByModule: Map<String, List<LessonModel>>,
): LessonUiState.Success {
    val moduleIds = modules.map { it.idModule }.toSet()
    val orphanLessons = allLessons.filter {
        it.moduleId.isNotBlank() && it.moduleId !in moduleIds
    }
    check(orphanLessons.isEmpty()) {
        "Há lições vinculadas a módulos inexistentes."
    }

    fun withProgress(lessons: List<LessonModel>): List<LessonWithProgress> = lessons.map { lesson ->
        LessonWithProgress(
            lesson = lesson,
            progress = progressList.find { it.lessonId == lesson.id },
        )
    }

    return LessonUiState.Success(
        modules = modules.map { module ->
            ModuleWithLessons(
                module = module,
                lessons = withProgress(lessonsByModule[module.idModule].orEmpty()),
            )
        },
        legacyLessons = withProgress(allLessons.filter { it.moduleId.isBlank() }),
    )
}
