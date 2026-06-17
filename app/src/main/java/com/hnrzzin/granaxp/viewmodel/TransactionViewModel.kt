package com.hnrzzin.granaxp.viewmodel // Ajuste o pacote se necessário

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.google.firebase.auth.FirebaseAuth
import com.hnrzzin.granaxp.model.TransactionModel
import com.hnrzzin.granaxp.enums.TransactionType
import com.hnrzzin.granaxp.repositories.TransactionRepository
import com.hnrzzin.granaxp.ui.theme.states.TransactionListState
import com.hnrzzin.granaxp.ui.theme.states.TransactionState
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import java.math.BigDecimal

class TransactionViewModel : ViewModel() {

    // Instanciando o repositório de forma limpa pegando o usuário logado
    private val auth: FirebaseAuth = FirebaseAuth.getInstance()
    private val userId = auth.currentUser?.uid ?: ""
    private val repository = TransactionRepository(userId)

    // Gerenciadores de Estado (UDF)
    private val _transactionState = MutableStateFlow<TransactionState>(TransactionState.Idle)
    val transactionState: StateFlow<TransactionState> = _transactionState

    private val _transactionListState = MutableStateFlow<TransactionListState>(TransactionListState.Idle)
    val transactionListState: StateFlow<TransactionListState> = _transactionListState

    // Campos do formulário (Sincronizados com o seu TransactionModel)
    var amount by mutableStateOf("")
    var title by mutableStateOf("")
    var category by mutableStateOf("")
    var transactionType by mutableStateOf(TransactionType.DESPESA)

    fun addTransaction() {
        if (!validateTransaction()) return

        _transactionState.value = TransactionState.Loading

        val finalAmount = amount.toBigDecimalOrNull() ?: BigDecimal.ZERO

        viewModelScope.launch {
            try {
                // Chama a função exata que você criou no TransactionRepository
                repository.createTransaction(
                    title = title,
                    amount = finalAmount,
                    type = transactionType,
                    category = category
                )
                _transactionState.value = TransactionState.Success("Transação adicionada com sucesso!")
                clearFields()
                getTransactions() // Já puxa a lista atualizada do banco
            } catch (exception: Exception) {
                _transactionState.value = TransactionState.Error(exception)
            }
        }
    }

    fun getTransactions() {
        _transactionListState.value = TransactionListState.Loading

        viewModelScope.launch {
            try {
                // Chama o get do seu repositório original
                val transactions = repository.getTransactions()
                _transactionListState.value = TransactionListState.Success(transactions)
            } catch (exception: Exception) {
                _transactionListState.value = TransactionListState.Error(exception)
            }
        }
    }

    fun deleteTransaction(transaction: TransactionModel) {
        _transactionState.value = TransactionState.Loading

        viewModelScope.launch {
            try {
                repository.deleteTransaction(transaction)
                _transactionState.value = TransactionState.Success("Transação deletada!")
                getTransactions()
            } catch (exception: Exception) {
                _transactionState.value = TransactionState.Error(exception)
            }
        }
    }

    fun updateTransaction(updatedTransaction: TransactionModel) {
        _transactionState.value = TransactionState.Loading

        viewModelScope.launch {
            try {
                repository.updateTransaction(updatedTransaction)
                _transactionState.value = TransactionState.Success("Transação atualizada!")
                getTransactions()
            } catch (exception: Exception) {
                _transactionState.value = TransactionState.Error(exception)
            }
        }
    }

    private fun validateTransaction(): Boolean {
        return when {
            amount.isEmpty() || amount.toBigDecimalOrNull() == null -> {
                _transactionState.value = TransactionState.Error(Exception("Valor inválido"))
                false
            }
            amount.toBigDecimal() <= BigDecimal.ZERO -> {
                _transactionState.value = TransactionState.Error(Exception("O valor deve ser maior que zero"))
                false
            }
            title.isEmpty() -> {
                _transactionState.value = TransactionState.Error(Exception("O título/descrição é obrigatório"))
                false
            }
            category.isEmpty() -> {
                _transactionState.value = TransactionState.Error(Exception("A categoria é obrigatória"))
                false
            }
            else -> true
        }
    }

    private fun clearFields() {
        amount = ""
        title = ""
        category = ""
        transactionType = TransactionType.DESPESA
    }

    fun resetState() {
        _transactionState.value = TransactionState.Idle
    }
}