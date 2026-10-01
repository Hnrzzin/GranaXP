package com.hnrzzin.granaxp.repositories

import com.google.firebase.functions.FirebaseFunctions
import com.hnrzzin.granaxp.model.ActivityType
import com.hnrzzin.granaxp.model.DailyMissionModel
import com.hnrzzin.granaxp.model.LessonSelectionMode
import kotlinx.coroutines.tasks.await

data class DailyMissionOpenResult(
    val available: Boolean,
    val date: String? = null,
    val mission: DailyMissionModel? = null,
    val isCompleted: Boolean = false,
)

data class DailyMissionSubmissionResult(
    val correct: Boolean,
    val alreadyCompleted: Boolean,
    val feedback: String? = null,
)

class DailyMissionRepository {
    private val functions: FirebaseFunctions = FirebaseFunctionsProvider.instance

    suspend fun openDailyMission(deviceTimeMillis: Long): DailyMissionOpenResult {
        val result = functions.getHttpsCallable("openDailyMission")
            .call(mapOf("deviceTimeMillis" to deviceTimeMillis)).await()
        return parseDailyMissionOpenResult(result.data)
    }

    suspend fun submitAnswer(
        deviceTimeMillis: Long,
        expectedDate: String,
        expectedMissionId: String,
        selectedAnswerIds: Set<String>,
    ): DailyMissionSubmissionResult {
        require(selectedAnswerIds.isNotEmpty()) { "Selecione uma alternativa." }
        val result = functions.getHttpsCallable("submitDailyMissionAnswer")
            .call(mapOf(
                "deviceTimeMillis" to deviceTimeMillis,
                "expectedDate" to expectedDate,
                "expectedMissionId" to expectedMissionId,
                "selectedAnswerIds" to selectedAnswerIds.sorted(),
            )).await()
        return parseDailyMissionSubmissionResult(result.data)
    }
}

internal fun parseDailyMissionOpenResult(value: Any?): DailyMissionOpenResult {
    val data = value as? Map<*, *> ?: error("Resposta da missão inválida.")
    val available = data["available"] as? Boolean ?: error("Disponibilidade ausente.")
    if (!available) return DailyMissionOpenResult(available = false)
    val rawMission = data["mission"] as? Map<*, *> ?: error("Missão ausente.")
    val alternatives = rawMission["alternatives"] as? Map<*, *> ?: error("Alternativas ausentes.")
    check(alternatives.isNotEmpty() && alternatives.keys.all { it is String } &&
        alternatives.values.all { it is String }) { "Alternativas inválidas." }
    val mission = DailyMissionModel(
        id = rawMission["id"] as? String ?: error("ID da missão ausente."),
        title = rawMission["title"] as? String ?: error("Título da missão ausente."),
        description = rawMission["description"] as? String ?: error("Descrição da missão ausente."),
        activityType = ActivityType.valueOf(rawMission["activityType"] as? String
            ?: error("Tipo da missão ausente.")),
        alternatives = alternatives.entries.associate { it.key as String to it.value as String },
        selectionMode = LessonSelectionMode.valueOf(rawMission["selectionMode"] as? String
            ?: error("Modo de seleção ausente.")),
        chartImageUrl = rawMission["chartImageUrl"] as? String,
    )
    return DailyMissionOpenResult(
        available = true,
        date = data["date"] as? String ?: error("Data da missão ausente."),
        mission = mission,
        isCompleted = data["isCompleted"] as? Boolean ?: error("Progresso da missão ausente."),
    )
}

internal fun parseDailyMissionSubmissionResult(value: Any?): DailyMissionSubmissionResult {
    val data = value as? Map<*, *> ?: error("Resposta da atividade inválida.")
    val correct = data["correct"] as? Boolean ?: error("Resultado da atividade ausente.")
    return DailyMissionSubmissionResult(
        correct = correct,
        alreadyCompleted = data["alreadyCompleted"] as? Boolean ?: error("Estado de conclusão ausente."),
        feedback = if (correct) null else data["feedback"] as? String ?: error("Feedback ausente."),
    )
}
