package com.hnrzzin.granaxp.model

enum class LessonSelectionMode { SINGLE, MULTIPLE }

data class LessonBlock(
    var id: String = "",
    var type: String = "",
    var order: Int = 0,
    var title: String = "",
    var content: String = "",
    var activityType: ActivityType? = null,
    var alternatives: Map<String, String> = emptyMap(),
    var selectionMode: LessonSelectionMode? = null,
    var feedback: String = "",
    var chartImageUrl: String? = null,
)
