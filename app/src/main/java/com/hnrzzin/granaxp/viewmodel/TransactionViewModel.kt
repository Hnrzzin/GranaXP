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

// Estado original: Responsável EXCLUSIVAMENTE pela Lista de Transações
sealed class TransactionUiState {
    object Loading : TransactionUiState()
    data class Success(val transactions: List<TransactionModel>) : TransactionUiState()
    data class Error(val message: String) : TransactionUiState()
}

// NOVO ESTADO: Responsável EXCLUSIVAMENTE pelo fluxo de Salvar Transação
sealed class TransactionSaveState {
    object Idle : TransactionSaveState()
    object Saving : TransactionSaveState()
    object Success : TransactionSaveState()
    data class Error(val message: String) : TransactionSaveState()
}

class TransactionViewModel(private val userId: String) : ViewModel() {
    private val repository = TransactionRepository(userId)

    // Fluxo da lista
    private val _uiState = MutableStateFlow<TransactionUiState>(TransactionUiState.Loading)
    val uiState: StateFlow<TransactionUiState> = _uiState.asStateFlow()

    // NOVO FLUXO: Controle da Ação de Salvar
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
            _saveState.value = TransactionSaveState.Saving // 1. Trava o botão
            try {
                repository.createTransaction(title, amount, type, category, isAutomatic)
                fetchTransactions() // Atualiza a lista em background silenciosamente
                _saveState.value = TransactionSaveState.Success // 2. Dispara a navegação
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