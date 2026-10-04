package com.hnrzzin.granaxp.model

data class StreakModel(
    val currentStreak: Int = 0,
    val highestStreak: Int = 0,
    val lastMissionCompletedDate: String? = null,
    val claimedMilestones: List<Int> = emptyList(),
)
