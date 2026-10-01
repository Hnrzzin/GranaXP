package com.hnrzzin.granaxp.viewmodel

import com.hnrzzin.granaxp.model.DailyMissionModel
import com.hnrzzin.granaxp.model.LessonSelectionMode
import kotlinx.datetime.DateTimeUnit
import kotlinx.datetime.Instant
import kotlinx.datetime.TimeZone
import kotlinx.datetime.atStartOfDayIn
import kotlinx.datetime.plus
import kotlinx.datetime.toLocalDateTime

private val missionZone = TimeZone.of("America/Sao_Paulo")

internal fun missionDateAt(epochMillis: Long): String =
    Instant.fromEpochMilliseconds(epochMillis).toLocalDateTime(missionZone).date.toString()

internal fun millisUntilNextMissionDate(epochMillis: Long): Long {
    val today = Instant.fromEpochMilliseconds(epochMillis).toLocalDateTime(missionZone).date
    val nextMidnight = today.plus(1, DateTimeUnit.DAY).atStartOfDayIn(missionZone)
    return (nextMidnight.toEpochMilliseconds() - epochMillis).coerceAtLeast(1L)
}

internal fun selectDailyMissionAnswer(
    mission: DailyMissionModel,
    currentSelection: Set<String>,
    answerId: String,
): Set<String> {
    if (answerId !in mission.alternatives) return currentSelection
    return if (mission.selectionMode == LessonSelectionMode.MULTIPLE) {
        if (answerId in currentSelection) currentSelection - answerId else currentSelection + answerId
    } else {
        setOf(answerId)
    }
}

sealed interface DailyMissionUiState {
    data object Loading : DailyMissionUiState
    data object Unavailable : DailyMissionUiState
    data class Error(val message: String) : DailyMissionUiState
    data class Active(
        val date: String,
        val mission: DailyMissionModel,
        val selectedAnswerIds: Set<String> = emptySet(),
        val feedback: String? = null,
        val isSubmitting: Boolean = false,
        val error: String? = null,
    ) : DailyMissionUiState
    data class Completed(val date: String, val mission: DailyMissionModel) : DailyMissionUiState
}
