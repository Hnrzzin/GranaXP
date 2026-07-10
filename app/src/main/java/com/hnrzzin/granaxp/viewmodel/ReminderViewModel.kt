package com.hnrzzin.granaxp.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.google.firebase.Timestamp
import com.hnrzzin.granaxp.model.ReminderModel
import com.hnrzzin.granaxp.model.TransactionType
import com.hnrzzin.granaxp.repositories.ReminderRepository
import com.hnrzzin.granaxp.repositories.TransactionRepository
import com.hnrzzin.granaxp.utils.DateUtils
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

// 1. Estado específico para carregar e listar os dados na tela
sealed class ReminderUiState {
    object Loading : ReminderUiState()
    data class Success(val reminders: List<ReminderModel>) : ReminderUiState()
    data class Error(val message: String) : ReminderUiState()
}

// 2. Estado específico para gerenciar cliques de botões e formulários (Criar, Concluir, Deletar)
sealed class ReminderActionState {
    object Idle : ReminderActionState()
    object Loading : ReminderActionState()
    object Success : ReminderActionState()
    data class Error(val message: String) : ReminderActionState()
}

class ReminderViewModel(private val userId: String) : ViewModel() {

    private val repository = ReminderRepository(userId)
    private val transactionRepository = TransactionRepository(userId)

    // CORRIGIDO: Agora usa corretamente o ReminderUiState para a listagem
    private val _uiState = MutableStateFlow<ReminderUiState>(ReminderUiState.Loading)
    val uiState: StateFlow<ReminderUiState> = _uiState.asStateFlow()

    private val _actionState = MutableStateFlow<ReminderActionState>(ReminderActionState.Idle)
    val actionState: StateFlow<ReminderActionState> = _actionState.asStateFlow()

    init {
        fetchReminders()
    }

    fun resetActionState() {
        _actionState.value = ReminderActionState.Idle
    }

    fun fetchReminders() {
        viewModelScope.launch {
            _uiState.value = ReminderUiState.Loading
            try {
                val reminders = repository.getReminders()
                _uiState.value = ReminderUiState.Success(reminders)
            } catch (e: Exception) {
                _uiState.value = ReminderUiState.Error("Falha ao buscar lembretes: ${e.message}")
            }
        }
    }

    fun createReminder(
        title: String,
        description: String,
        amount: Double,
        dateRaw: String,
        time: String
    ) {
        viewModelScope.launch {
            _actionState.value = ReminderActionState.Loading

            val timestamp = when (val result = DateUtils.validateFutureDate(dateRaw)) {
                is DateUtils.DateValidationResult.Valid -> result.timestamp
                DateUtils.DateValidationResult.InvalidFormat -> {
                    _actionState.value = ReminderActionState.Error("Verifique se a data está correta.")
                    return@launch
                }
                DateUtils.DateValidationResult.PastDate -> {
                    _actionState.value = ReminderActionState.Error("A data não pode ser no passado.")
                    return@launch
                }
            }

            try {
                repository.createReminder(title, description, amount, timestamp, time)
                fetchReminders()
                _actionState.value = ReminderActionState.Success
            } catch (e: Exception) {
                _actionState.value = ReminderActionState.Error("Falha ao criar lembrete: ${e.message}")
            }
        }
    }

    fun markAsCompleted(reminder: ReminderModel) {
        if (reminder.isCompleted) return

        viewModelScope.launch {
            _actionState.value = ReminderActionState.Loading
            try {
                transactionRepository.createTransaction(
                    title = reminder.title,
                    amount = reminder.amount,
                    type = TransactionType.DESPESA, // Ajustado para bater com seu enum padrão
                    category = reminder.title,
                    isAutomatic = true
                )

                val updatedReminder = reminder.copy(isCompleted = true)
                repository.updateReminder(updatedReminder)
                fetchReminders()
                _actionState.value = ReminderActionState.Success
            } catch (e: Exception) {
                _actionState.value = ReminderActionState.Error("Falha ao concluir lembrete: ${e.message}")
            }
        }
    }

    fun updateReminder(reminder: ReminderModel) {
        viewModelScope.launch {
            try {
                repository.updateReminder(reminder)
                fetchReminders()
            } catch (e: Exception) {
                _uiState.value = ReminderUiState.Error("Falha ao atualizar lembrete: ${e.message}")
            }
        }
    }

    fun deleteReminder(reminder: ReminderModel) {
        viewModelScope.launch {
            try {
                repository.deleteReminder(reminder)
                fetchReminders()
            } catch (e: Exception) {
                _uiState.value = ReminderUiState.Error("Falha ao deletar lembrete: ${e.message}")
            }
        }
    }
}