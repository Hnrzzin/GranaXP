package com.hnrzzin.granaxp.viewmodel


import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.google.firebase.auth.FirebaseAuth
import com.hnrzzin.granaxp.model.GoalModel
import com.hnrzzin.granaxp.enums.GoalDeadline
import com.hnrzzin.granaxp.repositories.GoalRepository
import com.hnrzzin.granaxp.ui.theme.states.GoalState
import com.hnrzzin.granaxp.ui.theme.states.GoalListState
import com.hnrzzin.granaxp.utils.DateUtils
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import java.math.BigDecimal

class GoalViewModel : ViewModel() {

    private val auth: FirebaseAuth = FirebaseAuth.getInstance()
    private val userId = auth.currentUser?.uid ?: ""
    private val repository = GoalRepository(userId)

    private val _goalState = MutableStateFlow<GoalState>(GoalState.Idle)
    val goalState: StateFlow<GoalState> = _goalState

    private val _goalListState = MutableStateFlow<GoalListState>(GoalListState.Idle)
    val goalListState: StateFlow<GoalListState> = _goalListState

    var title by mutableStateOf("")
    var targetAmount by mutableStateOf("")
    var currentAmount by mutableStateOf("") // Adicionado (Já Guardado)
    var deadlineDate by mutableStateOf("") // Adicionado (Data Limite)
    var deadlineType by mutableStateOf(GoalDeadline.CURTO) // Seu Enum original

    fun addGoal() {
        // 1. Validação dos campos obrigatórios
        if (title.isBlank() || targetAmount.toBigDecimalOrNull() == null) {
            _goalState.value = GoalState.Error(Exception("Preencha os campos obrigatórios"))
            return
        }

        // 2. Conversão da Data (String -> Timestamp)
        val timestamp = DateUtils.parseDateToTimestamp(deadlineDate)

        if (timestamp == null) {
            _goalState.value = GoalState.Error(Exception("Data inválida. Use o formato dd/mm/aaaa"))
            return
        }

        _goalState.value = GoalState.Loading

        viewModelScope.launch {
            try {
                repository.createGoal(
                    title = title,
                    targetAmount = targetAmount.toBigDecimal(),
                    currentAmount = currentAmount.toBigDecimal(),
                    targetDate = timestamp, // Enviando a String da data
                    deadline = deadlineType
                )
                _goalState.value = GoalState.Success("Meta salva com sucesso!")
                clearFields()
            } catch (e: Exception) {
                _goalState.value = GoalState.Error(e)
            }
        }
    }

    fun getGoals() {
        _goalListState.value = GoalListState.Loading
        viewModelScope.launch {
            try {
                val goals = repository.getGoal()
                _goalListState.value = GoalListState.Success(goals)
            } catch (e: Exception) {
                _goalListState.value = GoalListState.Error(e)
            }
        }
    }

    private fun validateGoal(): Boolean {
        if (title.isBlank() || targetAmount.toBigDecimalOrNull() == null) {
            _goalState.value = GoalState.Error(Exception("Preencha todos os campos corretamente"))
            return false
        }
        return true
    }

    private fun clearFields() {
        title = ""
        targetAmount = ""
        deadlineType = GoalDeadline.CURTO
    }

    fun resetState() { _goalState.value = GoalState.Idle }
}