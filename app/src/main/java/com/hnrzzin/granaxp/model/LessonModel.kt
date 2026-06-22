package com.hnrzzin.granaxp.model

import com.google.firebase.firestore.DocumentId

data class LessonModel(
    @DocumentId
    var id: String = "",
    var title: String = "",
    var description: String = "",
    var videoUrl: String = "",
    var duration: Int = 0,
    var category: String = ""
)