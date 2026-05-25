package com.hnrzzin.granaxp.model

data class LessonProgressModel(
    val referenceLessonId: String,
    val idUser: String,
    val id: String,
    val lessonComplete: Boolean
)
