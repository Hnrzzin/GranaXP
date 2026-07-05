package com.hnrzzin.granaxp.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.hnrzzin.granaxp.model.TransactionModel
import com.hnrzzin.granaxp.model.TransactionType
import com.hnrzzin.granaxp.repositories.TransactionRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

sealed class TransactionUiState {
    object Loading : TransactionUiState()
    data class Success(val transactions: List<TransactionModel>) : TransactionUiState()
    data class Error(val message: String) : TransactionUiState()
}

sealed class TransactionSaveState {
    object Idle : TransactionSaveState()
    object Saving : TransactionSaveState()
    object Success : TransactionSaveState()
    data class Error(val message: String) : TransactionSaveState()
}

class TransactionViewModel(private val userId: String) : ViewModel() {
    private val repository = TransactionRepository(userId)

    private val _uiState = MutableStateFlow<TransactionUiState>(TransactionUiState.Loading)
    val uiState: StateFlow<TransactionUiState> = _uiState.asStateFlow()

    private val _saveState = MutableStateFlow<TransactionSaveState>(TransactionSaveState.Idle)
    val saveState: StateFlow<TransactionSaveState> = _saveState.asStateFlow()

    init {
        fetchTransactions()
    }

    fun fetchTransactions() {
        viewModelScope.launch {
            _uiState.value = TransactionUiState.Loading
            try {
                val transactions = repository.getTransactions()
                _uiState.value = TransactionUiState.Success(transactions)
            } catch (e: Exception) {
                _uiState.value = TransactionUiState.Error("Falha ao buscar transações: ${e.message}")
            }
        }
    }

    fun createTransaction(
        title: String,
        amount: Double,
        type: TransactionType,
        category: String,
        isAutomatic: Boolean = false
    ) {
        viewModelScope.launch {
            _saveState.value = TransactionSaveState.Saving

            // Regra de negócio crítica #1 — DESPESA exige histórico de RECEITA prévio.
            // Guarda automática (isAutomatic=true, gastos fixos) não passa por essa checagem
            // porque já pressupõe orçamento configurado pelo próprio usuário.
            if (type == TransactionType.DESPESA && !isAutomatic) {
                val hasIncome = repository.hasAnyIncome()
                if (!hasIncome) {
                    _saveState.value = TransactionSaveState.Error(
                        "Você precisa registrar pelo menos uma receita antes de declarar uma despesa."
                    )
                    return@launch
                }
            }

            try {
                repository.createTransaction(title, amount, type, category, isAutomatic)
                fetchTransactions()
                _saveState.value = TransactionSaveState.Success
            } catch (e: Exception) {
                _saveState.value = TransactionSaveState.Error("Falha ao criar transação: ${e.message}")
            }
        }
    }

    fun updateTransaction(transaction: TransactionModel) {
        viewModelScope.launch {
            try {
                repository.updateTransaction(transaction)
                fetchTransactions()
            } catch (e: Exception) {
                _uiState.value = TransactionUiState.Error("Falha ao atualizar transação: ${e.message}")
            }
        }
    }

    fun deleteTransaction(transaction: TransactionModel) {
        viewModelScope.launch {
            try {
                repository.deleteTransaction(transaction)
                fetchTransactions()
            } catch (e: Exception) {
                _uiState.value = TransactionUiState.Error("Falha ao deletar transação: ${e.message}")
            }
        }
    }
}