package com.hnrzzin.granaxp.model

import com.google.firebase.Timestamp
import com.google.firebase.firestore.DocumentId

data class LessonProgressModel(
    @DocumentId
    var id: String = "",
    var userId: String = "",
    var lessonId: String = "",
    var isCompleted: Boolean = false,
    var lastAccessed: Timestamp = Timestamp.now()
)