package com.hnrzzin.granaxp.ui.theme.states

import com.hnrzzin.granaxp.model.LessonModel

sealed class LessonState {
    object Idle : LessonState()
    object Loading : LessonState()
    data class Success(val lessons: List<LessonModel>) : LessonState()
    data class Error(val exception: Exception) : LessonState()
}

sealed class LessonDetailState {
    object Idle : LessonDetailState()
    object Loading : LessonDetailState()
    data class Success(val lesson: LessonModel) : LessonDetailState()
    data class Error(val exception: Exception) : LessonDetailState()
}
