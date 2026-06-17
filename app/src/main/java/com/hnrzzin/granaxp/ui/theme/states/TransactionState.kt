package com.hnrzzin.granaxp.ui.theme.states

import com.hnrzzin.granaxp.model.TransactionModel

sealed class TransactionState {
    object Idle : TransactionState()
    object Loading : TransactionState()
    data class Success(val message: String) : TransactionState()
    data class Error(val exception: Exception) : TransactionState()
}

sealed class TransactionListState {
    object Idle : TransactionListState()
    object Loading : TransactionListState()
    data class Success(val transactions: List<TransactionModel>) : TransactionListState()
    data class Error(val exception: Exception) : TransactionListState()
}
