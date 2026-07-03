package com.hnrzzin.granaxp.model

import com.google.firebase.firestore.DocumentId

data class LessonModel(
    @DocumentId
    var id: String = "",
    var title: String = "",
    var description: String = "",
    var videoUrl: String? = null,
    var duration: Int? = null,
    var category: String = "",
    var xpReward: Int = 50,
    var order: Int = 0
)