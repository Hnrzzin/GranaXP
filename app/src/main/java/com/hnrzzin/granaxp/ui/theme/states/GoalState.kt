package com.hnrzzin.granaxp.ui.theme.states

import com.hnrzzin.granaxp.model.GoalModel

sealed class GoalState {
    object Idle : GoalState()
    object Loading : GoalState()
    data class Success(val message: String) : GoalState()
    data class Error(val exception: Exception) : GoalState()
}

sealed class GoalListState {
    object Idle : GoalListState()
    object Loading : GoalListState()
    data class Success(val goals: List<GoalModel>) : GoalListState()
    data class Error(val exception: Exception) : GoalListState()
}
