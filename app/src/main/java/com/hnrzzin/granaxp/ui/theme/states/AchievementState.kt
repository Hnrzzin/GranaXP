package com.hnrzzin.granaxp.ui.theme.states

import com.hnrzzin.granaxp.model.AchievementModel

sealed class AchievementState {
    object Idle : AchievementState()
    object Loading : AchievementState()
    data class Success(val achievements: List<AchievementModel>) : AchievementState()
    data class Error(val exception: Exception) : AchievementState()
}

