package com.hnrzzin.granaxp.model

/** Missão educacional Spark. O gabarito é público no Firestore e não é segredo. */
data class DailyMissionModel(
    val id: String,
    val title: String,
    val description: String,
    val activityType: ActivityType,
    val alternatives: Map<String, String>,
    val selectionMode: LessonSelectionMode,
    val chartImageUrl: String? = null,
    val correctAnswerIds: Set<String> = emptySet(),
    val feedback: String = "",
)
