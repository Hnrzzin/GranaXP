package com.hnrzzin.granaxp.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.hnrzzin.granaxp.repositories.DailyMissionRepository
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

class DailyMissionViewModel : ViewModel() {
    private val repository = DailyMissionRepository()
    private val _uiState = MutableStateFlow<DailyMissionUiState>(DailyMissionUiState.Loading)
    val uiState: StateFlow<DailyMissionUiState> = _uiState.asStateFlow()
    private var refreshJob: Job? = null

    init {
        refresh()
        viewModelScope.launch {
            while (true) {
                delay(millisUntilNextMissionDate(System.currentTimeMillis()))
                refresh()
            }
        }
    }

    fun refresh() {
        refreshJob?.cancel()
        refreshJob = viewModelScope.launch {
            _uiState.value = DailyMissionUiState.Loading
            try {
                val requestTime = System.currentTimeMillis()
                val result = repository.openDailyMission(requestTime)
                if (missionDateAt(requestTime) != missionDateAt(System.currentTimeMillis())) {
                    refresh()
                    return@launch
                }
                _uiState.value = if (!result.available) {
                    DailyMissionUiState.Unavailable
                } else {
                    val mission = checkNotNull(result.mission)
                    val date = checkNotNull(result.date)
                    if (result.isCompleted) DailyMissionUiState.Completed(date, mission)
                    else DailyMissionUiState.Active(date, mission)
                }
            } catch (error: CancellationException) {
                throw error
            } catch (error: Exception) {
                _uiState.value = DailyMissionUiState.Error("Falha ao carregar a missão diária: ${error.message}")
            }
        }
    }

    fun selectAnswer(answerId: String) {
        val active = _uiState.value as? DailyMissionUiState.Active ?: return
        if (active.isSubmitting) return
        _uiState.value = active.copy(
            selectedAnswerIds = selectDailyMissionAnswer(active.mission, active.selectedAnswerIds, answerId),
            feedback = null,
            error = null,
        )
    }

    fun submitAnswer() {
        val active = _uiState.value as? DailyMissionUiState.Active ?: return
        if (active.isSubmitting || active.selectedAnswerIds.isEmpty()) return
        _uiState.value = active.copy(isSubmitting = true, error = null)
        viewModelScope.launch {
            try {
                val result = repository.submitAnswer(
                    deviceTimeMillis = System.currentTimeMillis(),
                    expectedDate = active.date,
                    expectedMissionId = active.mission.id,
                    selectedAnswerIds = active.selectedAnswerIds,
                )
                if (missionDateAt(System.currentTimeMillis()) != active.date) {
                    refresh()
                    return@launch
                }
                val current = _uiState.value as? DailyMissionUiState.Active ?: return@launch
                if (current.date != active.date || current.mission.id != active.mission.id) return@launch
                _uiState.value = if (result.correct) {
                    DailyMissionUiState.Completed(active.date, active.mission)
                } else {
                    current.copy(
                        selectedAnswerIds = emptySet(), feedback = result.feedback,
                        isSubmitting = false, error = null,
                    )
                }
            } catch (error: CancellationException) {
                throw error
            } catch (error: Exception) {
                if (missionDateAt(System.currentTimeMillis()) != active.date) {
                    refresh()
                    return@launch
                }
                val current = _uiState.value as? DailyMissionUiState.Active ?: return@launch
                if (current.date == active.date && current.mission.id == active.mission.id) {
                    _uiState.value = current.copy(
                        isSubmitting = false,
                        error = "Não foi possível confirmar a resposta. Tente novamente.",
                    )
                }
            }
        }
    }
}
