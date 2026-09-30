package com.hnrzzin.granaxp.model

import com.google.firebase.Timestamp
import com.google.firebase.firestore.DocumentId

enum class GoalDeadline{
    CURTO,
    MEDIO,
    LONGO
}

// GoalModel.kt
data class GoalModel(
    @DocumentId
    var id: String = "",
    val title: String = "",
    val targetAmount: Double = 0.0,
    val currentAmount: Double = 0.0,
    val deadline: GoalDeadline = GoalDeadline.CURTO,
    val deadlineDate: Timestamp? = null,
    val completedAt: Timestamp? = null,
)
