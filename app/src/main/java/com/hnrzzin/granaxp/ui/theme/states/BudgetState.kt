package com.hnrzzin.granaxp.ui.theme.states

import com.hnrzzin.granaxp.model.BudgetModel

sealed class BudgetState {
    object Idle : BudgetState()
    object Loading : BudgetState()
    data class Success(val message: String) : BudgetState()
    data class Error(val exception: Exception) : BudgetState()
}

sealed class BudgetListState {
    object Idle : BudgetListState()
    object Loading : BudgetListState()
    data class Success(val budgets: List<BudgetModel>) : BudgetListState()
    data class Error(val exception: Exception) : BudgetListState()
}
