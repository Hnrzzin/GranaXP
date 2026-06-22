package com.hnrzzin.granaxp.model

import com.google.firebase.Timestamp
import com.google.firebase.firestore.DocumentId

data class ReminderModel(
    @DocumentId
    var id: String = "",
    val title: String = "",
    val description: String = "",
    val date: Timestamp = Timestamp.now(),
    val time: String = "",
    val isCompleted: Boolean = false
)