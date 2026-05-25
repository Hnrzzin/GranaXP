package com.hnrzzin.granaxp.model

data class AchievementProgressModel(
    val referenceAchievementId: String,
    val idUser: String,
    val achievementComplete: Boolean,
)
