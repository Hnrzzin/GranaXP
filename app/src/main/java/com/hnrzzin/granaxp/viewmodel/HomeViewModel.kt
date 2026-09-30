package com.hnrzzin.granaxp.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.hnrzzin.granaxp.model.UserModel
import com.hnrzzin.granaxp.model.TransactionType
import com.hnrzzin.granaxp.repositories.UserRepository
import com.hnrzzin.granaxp.repositories.TransactionRepository
import com.hnrzzin.granaxp.repositories.ReminderRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

import com.hnrzzin.granaxp.ui.theme.components.TransactionUIData
import java.text.SimpleDateFormat
import java.util.Locale

sealed class HomeUiState {
    object Loading : HomeUiState()

    data class Success(
        val user: UserModel,
        val totalBalance: Double,
        val totalIncome: Double,
        val totalExpense: Double,
        val pendingRemindersCount: Int,
        val recentTransactions: List<TransactionUIData>
    ) : HomeUiState()

    data class Error(val message: String) : HomeUiState()
}

class HomeViewModel(private val userId: String) : ViewModel() {

    private val userRepository = UserRepository(userId)
    private val transactionRepository = TransactionRepository(userId)
    private val reminderRepository = ReminderRepository(userId)

    private val _uiState = MutableStateFlow<HomeUiState>(HomeUiState.Loading)
    val uiState: StateFlow<HomeUiState> = _uiState.asStateFlow()

    init {
        fetchDashboardData()
    }

    fun fetchDashboardData() {
        viewModelScope.launch {
            _uiState.value = HomeUiState.Loading
            try {
                val user = userRepository.getUser() ?: throw Exception("Usuário não encontrado.")

                val transactions = transactionRepository.getTransactions()
                val totalIncome = transactions.filter { it.type == TransactionType.RECEITA }.sumOf { it.amount }
                val totalExpense = transactions.filter { it.type == TransactionType.DESPESA }.sumOf { it.amount }
                val totalBalance = totalIncome - totalExpense

                val reminders = reminderRepository.getReminders()
                val pendingRemindersCount = reminders.count { !it.isCompleted }

                val dateFormatter = SimpleDateFormat("dd/MM/yyyy", Locale("pt", "BR"))
                val recentTransactionsList = transactions
                    .sortedByDescending { it.date }
                    .take(3)
                    .map { model ->
                        TransactionUIData(
                            description = model.title,
                            category = model.category,
                            date = dateFormatter.format(model.date.toDate()),
                            amount = model.amount,
                            type = model.type
                        )
                    }

                _uiState.value = HomeUiState.Success(
                    user = user,
                    totalBalance = totalBalance,
                    totalIncome = totalIncome,
                    totalExpense = totalExpense,
                    pendingRemindersCount = pendingRemindersCount,
                    recentTransactions = recentTransactionsList
                )
            } catch (e: Exception) {
                _uiState.value = HomeUiState.Error("Erro ao carregar o painel: ${e.message}")
            }
        }
    }

    // earnXp() foi removido — era código órfão (nunca chamado por nenhuma tela).
    // A concessão de XP agora vive só onde a regra de negócio realmente acontece:
    // XP é concedido exclusivamente pelas Cloud Functions autenticadas.
}
