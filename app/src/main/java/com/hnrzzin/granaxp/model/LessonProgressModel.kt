package com.hnrzzin.granaxp.model

import com.google.firebase.Timestamp
import com.google.firebase.firestore.DocumentId
import com.google.firebase.firestore.PropertyName

data class LessonProgressModel(
    @DocumentId
    var id: String = "",
    var userId: String = "",
    var lessonId: String = "",
    @get:PropertyName("isCompleted") @set:PropertyName("isCompleted")
    var isCompleted: Boolean = false,
    var lastAccessed: Timestamp = Timestamp.now()
)