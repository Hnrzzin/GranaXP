package com.hnrzzin.granaxp.model

import com.google.firebase.Timestamp
import com.google.firebase.firestore.DocumentId
import com.google.firebase.firestore.PropertyName

data class DailyMissionProgressModel(
    @DocumentId var id: String = "",
    var missionId: String = "",
    var date: String = "",
    @get:PropertyName("isCompleted") @set:PropertyName("isCompleted")
    var isCompleted: Boolean = false,
    var completedAt: Timestamp? = null,
)
