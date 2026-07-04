package com.hnrzzin.granaxp.model

import com.google.firebase.Timestamp
import com.google.firebase.firestore.DocumentId
import com.google.firebase.firestore.PropertyName

data class ReminderModel(
    @DocumentId
    var id: String = "",
    val title: String = "",
    val description: String = "",
    val amount: Double = 0.0,
    val date: Timestamp = Timestamp.now(),
    val time: String = "",
    @get:PropertyName("isCompleted")
    @set:PropertyName("isCompleted")
    var isCompleted: Boolean = false
)