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

// 1. CORRIGIDO: Estado isolado para carregar e listar os orçamentos na tela
sealed class BudgetUiState {
    object Loading : BudgetUiState()
    data class Success(val budgets: List<BudgetModel>) : BudgetUiState()
    data class Error(val message: String) : BudgetUiState()
}

// 2. CORRIGIDO: Estado isolado para os fluxos de Criar, Editar e Deletar (Formulários)
sealed class BudgetActionState {
    object Idle : BudgetActionState()
    object Loading : BudgetActionState()
    object Success : BudgetActionState()
    data class Error(val message: String) : BudgetActionState()
}

sealed class BudgetAutomationWarning {
    data class FixedPaymentFailed(val message: String) : BudgetAutomationWarning()
    data class VariableCloseFailed(val message: String) : BudgetAutomationWarning()
}

class BudgetViewModel(private val userId: String) : ViewModel() {
    private val repository = BudgetRepository(userId)
    private val transactionRepository = TransactionRepository(userId)

    // CORRIGIDO: Agora usa BudgetUiState para monitorar a lista
    private val _uiState = MutableStateFlow<BudgetUiState>(BudgetUiState.Loading)
    val uiState: StateFlow<BudgetUiState> = _uiState.asStateFlow()

    private val _actionState = MutableStateFlow<BudgetActionState>(BudgetActionState.Idle)
    val actionState: StateFlow<BudgetActionState> = _actionState.asStateFlow()

    private val _automationWarning = MutableStateFlow<BudgetAutomationWarning?>(null)
    val automationWarning: StateFlow<BudgetAutomationWarning?> = _automationWarning.asStateFlow()

    init {
        checkAndPayFixedBudgets()
        checkAndCloseVariableBudgets()
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

    fun resetActionState() {
        _actionState.value = BudgetActionState.Idle
    }

    fun dismissAutomationWarning() {
        _automationWarning.value = null
    }

    private fun checkAndPayFixedBudgets() {
        viewModelScope.launch {
            try {
                val fixedBudgets = repository.getFixedBudgets()
                val today = Calendar.getInstance()
                val currentDay = today.get(Calendar.DAY_OF_MONTH)
                val currentMonth = today.get(Calendar.MONTH)
                val currentYear = today.get(Calendar.YEAR)

                fixedBudgets.forEach { budget ->
                    val lastPayment = budget.lastPaymentDate?.toDate()?.let {
                        Calendar.getInstance().apply { time = it }
                    }
                    val alreadyPaidThisMonth = lastPayment != null &&
                            lastPayment.get(Calendar.MONTH) == currentMonth &&
                            lastPayment.get(Calendar.YEAR) == currentYear

                    val dueDayReached = budget.dueDay?.let { currentDay >= it } ?: true

                    if (!alreadyPaidThisMonth && dueDayReached) {
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
                _automationWarning.value = BudgetAutomationWarning.FixedPaymentFailed(
                    "Não foi possível confirmar o pagamento de alguns gastos fixos. Verifique sua conexão."
                )
            }
        }
    }

    /**
     * Regra de negócio #4 (Gasto Variável):
     * No momento em que o app detecta que o mês virou desde a última
     * referência (`lastClosedMonth`), gera a transação automática com o
     * valor atual declarado (que pode ter sido editado pelo usuário ao
     * longo do mês) e rola o mesmo valor para o mês seguinte, atualizando
     * apenas a referência de mês.
     */
    private fun checkAndCloseVariableBudgets() {
        viewModelScope.launch {
            try {
                val variableBudgets = repository.getVariableBudgets()
                val today = Calendar.getInstance()
                val currentMonth = today.get(Calendar.MONTH)
                val currentYear = today.get(Calendar.YEAR)

                variableBudgets.forEach { budget ->
                    val lastClosed = budget.lastClosedMonth?.toDate()?.let {
                        Calendar.getInstance().apply { time = it }
                    }
                    val sameMonthAsReference = lastClosed != null &&
                            lastClosed.get(Calendar.MONTH) == currentMonth &&
                            lastClosed.get(Calendar.YEAR) == currentYear

                    if (!sameMonthAsReference && budget.limitAmount > 0.0) {
                        transactionRepository.createTransaction(
                            title = budget.category,
                            amount = budget.limitAmount,
                            type = TransactionType.DESPESA,
                            category = budget.category,
                            isAutomatic = true
                        )

                        val updatedBudget = budget.copy(
                            lastClosedMonth = Timestamp.now()
                            // limitAmount permanece o mesmo — rola pro próximo mês
                        )
                        repository.updateBudget(updatedBudget)
                    }
                }
            } catch (e: Exception) {
                _automationWarning.value = BudgetAutomationWarning.VariableCloseFailed(
                    "Não foi possível fechar alguns gastos variáveis do mês. Verifique sua conexão."
                )
            }
        }
    }

    fun createBudget(category: String, limitAmount: Double, type: BudgetPlanType, dueDay: Int? = null) {
        viewModelScope.launch {
            _actionState.value = BudgetActionState.Loading

            if (type == BudgetPlanType.FIXO && (dueDay == null || dueDay !in 1..31)) {
                _actionState.value = BudgetActionState.Error("Informe um dia de vencimento válido entre 1 e 31.")
                return@launch
            }

            try {
                repository.createBudget(category, limitAmount, type, dueDay)
                fetchBudgets()
                _actionState.value = BudgetActionState.Success // CORRIGIDO: Agora chama o objeto sem parâmetros perfeitamente
            } catch (e: Exception) {
                _actionState.value = BudgetActionState.Error("Falha ao criar orçamento: ${e.message}")
            }
        }
    }

    fun updateBudget(budget: BudgetModel) {
        viewModelScope.launch {
            _actionState.value = BudgetActionState.Loading

            if (budget.type == BudgetPlanType.FIXO && (budget.dueDay == null || budget.dueDay !in 1..31)) {
                _actionState.value = BudgetActionState.Error("Informe um dia de vencimento válido entre 1 e 31.")
                return@launch
            }

            try {
                repository.updateBudget(budget)
                fetchBudgets()
                _actionState.value = BudgetActionState.Success // CORRIGIDO: Sucesso simples de ação concluída
            } catch (e: Exception) {
                _actionState.value = BudgetActionState.Error("Falha ao atualizar orçamento: ${e.message}")
            }
        }
    }

    fun deleteBudget(budget: BudgetModel) {
        viewModelScope.launch {
            try {
                repository.deleteBudget(budget)
                fetchBudgets()
            } catch (e: Exception) {
                _actionState.value = BudgetActionState.Error("Falha ao deletar orçamento: ${e.message}")
            }
        }
    }
}