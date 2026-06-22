package com.hnrzzin.granaxp.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.google.firebase.Timestamp
import com.hnrzzin.granaxp.model.ReminderModel
import com.hnrzzin.granaxp.repositories.ReminderRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

sealed class ReminderUiState {
    object Loading : ReminderUiState()
    data class Success(val reminders: List<ReminderModel>) : ReminderUiState()
    data class Error(val message: String) : ReminderUiState()
}

class ReminderViewModel(private val userId: String) : ViewModel() {

    private val repository = ReminderRepository(userId)

    private val _uiState = MutableStateFlow<ReminderUiState>(ReminderUiState.Loading)
    val uiState: StateFlow<ReminderUiState> = _uiState.asStateFlow()

    init {
        fetchReminders()
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
        date: Timestamp,
        time: String
    ) {
        viewModelScope.launch {
            try {
                repository.createReminder(title, description, date, time)
                fetchReminders()
            } catch (e: Exception) {
                _uiState.value = ReminderUiState.Error("Falha ao criar lembrete: ${e.message}")
            }
        }
    }

    fun markAsCompleted(reminder: ReminderModel) {
        viewModelScope.launch {
            try {
                val updatedReminder = reminder.copy(isCompleted = true)
                repository.updateReminder(updatedReminder)
                fetchReminders()
            } catch (e: Exception) {
                _uiState.value = ReminderUiState.Error("Falha ao atualizar lembrete: ${e.message}")
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

    // Filtra só os lembretes pendentes
    fun getPendingReminders(reminders: List<ReminderModel>): List<ReminderModel> {
        return reminders.filter { !it.isCompleted }
    }
}

class ReminderViewModelFactory(private val userId: String) : ViewModelProvider.Factory {
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        return ReminderViewModel(userId) as T
    }
}