package com.hnrzzin.granaxp.model

data class LessonBlock(
    var id: String = "",
    var type: String = "",
    var order: Int = 0,
    var title: String = "",
    var content: String = "",
    var activityType: ActivityType? = null,
    var alternatives: Map<String, String> = emptyMap(),
    var correctAnswerIds: List<String> = emptyList(),
    var feedback: String = "",
    var chartImageUrl: String? = null,
)
