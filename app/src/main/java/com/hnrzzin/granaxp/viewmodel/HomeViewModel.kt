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

    // SUBSTITUA A CLASSE Success POR ESTA:
    data class Success(
        val user: UserModel,
        val totalBalance: Double,
        val totalIncome: Double,
        val totalExpense: Double,
        val pendingRemindersCount: Int,
        val recentTransactions: List<TransactionUIData> // NOVO: Propriedade adicionada
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
                // Ajustado para o nome correto do seu repositório: getUser()
                val user = userRepository.getUser() ?: throw Exception("Usuário não encontrado.")

                val transactions = transactionRepository.getTransactions()
                val totalIncome = transactions.filter { it.type == TransactionType.RECEITA }.sumOf { it.amount }
                val totalExpense = transactions.filter { it.type == TransactionType.DESPESA }.sumOf { it.amount }
                val totalBalance = totalIncome - totalExpense

                val reminders = reminderRepository.getReminders()
                val pendingRemindersCount = reminders.count { !it.isCompleted }

                // --- INÍCIO DA ALTERAÇÃO ---

                // 1. Mapeamento e formatação dos dados
                val dateFormatter = SimpleDateFormat("dd/MM/yyyy", Locale("pt", "BR"))
                val recentTransactionsList = transactions
                    .sortedByDescending { it.date } // Ordena das mais recentes para as mais antigas
                    .take(3) // Extrai apenas as 3 últimas transações para o dashboard
                    .map { model ->
                        TransactionUIData(
                            description = model.title,
                            category = model.category,
                            date = dateFormatter.format(model.date.toDate()), // Converte Timestamp para String
                            amount = model.amount,
                            type = model.type
                        )
                    }

                // 2. Emissão do estado atualizado
                _uiState.value = HomeUiState.Success(
                    user = user,
                    totalBalance = totalBalance,
                    totalIncome = totalIncome,
                    totalExpense = totalExpense,
                    pendingRemindersCount = pendingRemindersCount,
                    recentTransactions = recentTransactionsList // NOVO: Injentando a lista mapeada
                )

                // --- FIM DA ALTERAÇÃO ---

            } catch (e: Exception) {
                _uiState.value = HomeUiState.Error("Erro ao carregar o painel: ${e.message}")
            }
        }
    }

    fun earnXp(amount: Int) {
        viewModelScope.launch {
            val currentState = _uiState.value
            if (currentState is HomeUiState.Success) {
                val user = currentState.user
                var newXp = user.xp + amount
                var newLevel = user.level
                var newNextLevelXp = user.nextLevelXp

                while (newXp >= newNextLevelXp) {
                    newXp -= newNextLevelXp
                    newLevel++
                    newNextLevelXp = (newNextLevelXp * 1.2).toInt()
                }

                val updatedUser = user.copy(
                    level = newLevel,
                    xp = newXp,
                    nextLevelXp = newNextLevelXp
                )

                try {
                    // Ajustado para o nome correto do seu repositório: updateUser()
                    userRepository.updateUser(updatedUser)
                    fetchDashboardData()
                } catch (e: Exception) {
                    _uiState.value = HomeUiState.Error("Erro ao salvar progresso de XP: ${e.message}")
                }
            }
        }
    }
}