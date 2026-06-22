package com.hnrzzin.granaxp.model

import com.google.firebase.Timestamp
import com.google.firebase.firestore.DocumentId

data class AchievementProgressModel(
    @DocumentId
    var id: String = "",
    val userId: String = "",
    val achievementId: String = "",
    val currentProgress: Int = 0,
    val isUnlocked: Boolean = false,
    val lastUpdated: Timestamp = Timestamp.now()
)