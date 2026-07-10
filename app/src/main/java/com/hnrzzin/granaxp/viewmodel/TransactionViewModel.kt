package com.hnrzzin.granaxp.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.hnrzzin.granaxp.model.CategoriaConquista
import com.hnrzzin.granaxp.model.RequirementType
import com.hnrzzin.granaxp.model.TransactionModel
import com.hnrzzin.granaxp.model.TransactionType
import com.hnrzzin.granaxp.repositories.AchievementRepository
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

    // Usado só para checar/desbloquear conquistas de categoria FINANCAS
    // após criar uma transação — mesmo padrão do LessonViewModel com EDUCACAO.
    private val achievementRepository = AchievementRepository()

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

                // Isolado de propósito, mesmo raciocínio do LessonViewModel: a transação já
                // foi salva com sucesso nesse ponto — uma falha ao checar conquistas não pode
                // fazer o usuário achar que a transação inteira falhou.
                try {
                    checkFinancialAchievements()
                } catch (e: Exception) {
                    println("Falha ao checar conquistas financeiras: $e")
                }

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

    /**
     * Regra de negócio (conquistas categoria FINANCAS): baseada na contagem total
     * de transações do usuário. Conta direto do repository, sem depender de nada
     * vindo da UI — mesmo padrão de LessonViewModel.checkEducationAchievements().
     */
    private suspend fun checkFinancialAchievements() {
        val transactionCount = repository.getTransactions().size

        val achievements = achievementRepository.getAchievements()
            .filter { it.category == CategoriaConquista.FINANCAS }
            .filter { it.requirementType == RequirementType.TRANSACTION_COUNT }
        val progressList = achievementRepository.getAchievementProgress(userId)

        achievements.forEach { achievement ->
            val progress = progressList.find { it.achievementId == achievement.id }
            if (progress?.isUnlocked == true) return@forEach

            val shouldUnlock = transactionCount >= achievement.requirementValue

            if (progress != null) {
                achievementRepository.updateAchievementProgress(progress.id, transactionCount, shouldUnlock)
            } else {
                achievementRepository.createAchievementProgress(userId, achievement.id, transactionCount, shouldUnlock)
            }
        }
    }
}