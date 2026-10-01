package com.hnrzzin.granaxp.model

/** Projeção pública da missão; o gabarito existe somente no catálogo do backend. */
data class DailyMissionModel(
    val id: String,
    val title: String,
    val description: String,
    val activityType: ActivityType,
    val alternatives: Map<String, String>,
    val selectionMode: LessonSelectionMode,
    val chartImageUrl: String? = null,
)
