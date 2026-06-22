package com.hnrzzin.granaxp.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.google.firebase.Timestamp
import com.hnrzzin.granaxp.model.BudgetModel
import com.hnrzzin.granaxp.model.BudgetPlanType
import com.hnrzzin.granaxp.repositories.BudgetRepository
import com.hnrzzin.granaxp.repositories.TransactionRepository
import com.hnrzzin.granaxp.model.TransactionType
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.util.Calendar

sealed class BudgetUiState {
    object Loading : BudgetUiState()
    data class Success(val budgets: List<BudgetModel>) : BudgetUiState()
    data class Error(val message: String) : BudgetUiState()
}

class BudgetViewModel(private val userId: String) : ViewModel() {
    private val repository = BudgetRepository(userId)
    private val transactionRepository = TransactionRepository(userId)
    private val _uiState = MutableStateFlow<BudgetUiState>(BudgetUiState.Loading)
    val uiState: StateFlow<BudgetUiState> = _uiState.asStateFlow()

    init {
        checkAndPayFixedBudgets()
        fetchBudgets()
    }

    fun fetchBudgets() {
        viewModelScope.launch {
            _uiState.value = BudgetUiState.Loading
            try {
                val budgets = repository.getBudgets()
                _uiState.value = BudgetUiState.Success(budgets)
            } catch (e: Exception) {
                _uiState.value = BudgetUiState.Error("Falha ao buscar orçamentos: ${e.message}")
            }
        }
    }

    private fun checkAndPayFixedBudgets() {
        viewModelScope.launch {
            try {
                val unpaidBudgets = repository.getUnpaidBudgets()
                val currentMonth = Calendar.getInstance().get(Calendar.MONTH)
                unpaidBudgets.forEach { budget ->
                    val lastPaymentMonth = budget.lastPaymentDate?.toDate()
                        ?.let { Calendar.getInstance().apply { time = it }.get(Calendar.MONTH) }

                    if (lastPaymentMonth == null || lastPaymentMonth != currentMonth) {
                        // AQUI: Passando isAutomatic = true para a transação do sistema
                        transactionRepository.createTransaction(
                            title = budget.category,
                            amount = budget.limitAmount,
                            type = TransactionType.DESPESA,
                            category = budget.category,
                            isAutomatic = true
                        )

                        val updatedBudget = budget.copy(
                            isPaid = true,
                            lastPaymentDate = Timestamp.now()
                        )
                        repository.updateBudget(updatedBudget)
                    }
                }
            } catch (e: Exception) {
                println("Falha ao verificar gastos fixos: $e")
            }
        }
    }

    fun createBudget(category: String, limitAmount: Double, type: BudgetPlanType, dueDay: Int? = null) {
        viewModelScope.launch {
            try {
                repository.createBudget(category, limitAmount, type, dueDay)
                fetchBudgets()
            } catch (e: Exception) {
                _uiState.value = BudgetUiState.Error("Falha ao criar orçamento: ${e.message}")
            }
        }
    }

    fun updateBudget(budget: BudgetModel) {
        viewModelScope.launch {
            try {
                repository.updateBudget(budget)
                fetchBudgets()
            } catch (e: Exception) {
                _uiState.value = BudgetUiState.Error("Falha ao atualizar orçamento: ${e.message}")
            }
        }
    }

    fun deleteBudget(budget: BudgetModel) {
        viewModelScope.launch {
            try {
                repository.deleteBudget(budget)
                fetchBudgets()
            } catch (e: Exception) {
                _uiState.value = BudgetUiState.Error("Falha ao deletar orçamento: ${e.message}")
            }
        }
    }
}