package com.hnrzzin.granaxp.repositories

import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.DocumentSnapshot
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.Source
import com.hnrzzin.granaxp.model.ActivityType
import com.hnrzzin.granaxp.model.DailyMissionModel
import com.hnrzzin.granaxp.model.LessonSelectionMode
import com.hnrzzin.granaxp.model.StreakModel
import kotlinx.datetime.Instant
import kotlinx.datetime.LocalDate
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime
import kotlinx.coroutines.tasks.await

data class DailyMissionOpenResult(
    val available: Boolean,
    val date: String? = null,
    val mission: DailyMissionModel? = null,
    val isCompleted: Boolean = false,
    val streak: StreakModel? = null,
)

data class DailyMissionSubmissionResult(
    val correct: Boolean,
    val alreadyCompleted: Boolean,
    val feedback: String? = null,
    val streak: StreakModel? = null,
    val xpEarned: Int = 0,
    val leveledUp: Boolean = false,
)

class DailyMissionRepository {
    private val db = FirebaseFirestore.getInstance()
    private val auth = FirebaseAuth.getInstance()

    private fun userId(): String = checkNotNull(auth.currentUser?.uid) { "Entre na conta para abrir a missão." }

    private fun missionDate(epochMillis: Long): String = Instant.fromEpochMilliseconds(epochMillis)
        .toLocalDateTime(TimeZone.of("America/Sao_Paulo")).date.toString()

    private fun DocumentSnapshot.asMission(): DailyMissionModel {
        val alternatives = (get("alternatives") as? Map<*, *>)?.entries
            ?.associate { (key, value) ->
                (key as? String ?: error("Alternativa inválida.")) to
                    (value as? String ?: error("Texto de alternativa inválido."))
            }.orEmpty()
        val answers = (get("correctAnswerIds") as? List<*>)
            ?.map { it as? String ?: error("Gabarito inválido.") }?.toSet().orEmpty()
        check(alternatives.isNotEmpty() && answers.isNotEmpty() && answers.all { it in alternatives }) {
            "Missão inválida no catálogo."
        }
        return DailyMissionModel(
            id = id,
            title = getString("title") ?: error("Título da missão ausente."),
            description = getString("description") ?: error("Enunciado ausente."),
            activityType = ActivityType.valueOf(getString("activityType") ?: error("Tipo ausente.")),
            alternatives = alternatives,
            selectionMode = if (answers.size > 1) LessonSelectionMode.MULTIPLE else LessonSelectionMode.SINGLE,
            chartImageUrl = getString("chartImageUrl"),
            correctAnswerIds = answers,
            feedback = getString("feedback") ?: "Tente novamente.",
        )
    }

    private suspend fun completedDates(uid: String): List<String> =
        db.collection("users").document(uid).collection("dailyMissionProgress")
            .get(Source.SERVER).await().documents.filter { it.getBoolean("isCompleted") == true }
            .map { it.id }

    suspend fun openDailyMission(deviceTimeMillis: Long): DailyMissionOpenResult {
        val uid = userId()
        val date = missionDate(deviceTimeMillis)
        val offset = LocalDate.parse(date).toEpochDays() - LocalDate(2026, 10, 1).toEpochDays()
        if (offset < 0) return DailyMissionOpenResult(available = false)
        val catalog = db.collection("dailyMissions").orderBy("order")
            .get(Source.SERVER).await().documents
        if (catalog.isEmpty()) return DailyMissionOpenResult(available = false)
        val mission = catalog[offset % catalog.size].asMission()
        val progress = db.collection("users").document(uid).collection("dailyMissionProgress")
            .document(date).get(Source.SERVER).await()
        check(!progress.exists() || progress.getString("missionId") == mission.id) {
            "A missão do dia mudou; atualize o catálogo."
        }
        return DailyMissionOpenResult(
            available = true, date = date, mission = mission,
            isCompleted = progress.getBoolean("isCompleted") == true,
            streak = educationalStreak(completedDates(uid), date),
        )
    }

    suspend fun submitAnswer(
        deviceTimeMillis: Long,
        expectedDate: String,
        expectedMissionId: String,
        selectedAnswerIds: Set<String>,
    ): DailyMissionSubmissionResult {
        require(selectedAnswerIds.isNotEmpty()) { "Selecione uma alternativa." }
        val uid = userId()
        val opened = openDailyMission(deviceTimeMillis)
        val mission = checkNotNull(opened.mission) { "Missão indisponível." }
        check(opened.date == expectedDate && mission.id == expectedMissionId) {
            "A missão do dia mudou. Atualize a tela."
        }
        if (opened.isCompleted) return DailyMissionSubmissionResult(
            correct = true, alreadyCompleted = true, streak = opened.streak,
        )
        if (!isCorrectEducationalAnswer(selectedAnswerIds, mission.correctAnswerIds,
                mission.alternatives.keys)) {
            return DailyMissionSubmissionResult(false, false, feedback = mission.feedback)
        }
        val progressRef = db.collection("users").document(uid).collection("dailyMissionProgress")
            .document(expectedDate)
        val firstCompletion = try {
            db.runTransaction { transaction ->
                val previous = transaction.get(progressRef)
                if (previous.exists()) {
                    check(previous.getString("missionId") == expectedMissionId) { "Missão alterada." }
                    false
                } else {
                    transaction.set(progressRef, mapOf(
                        "missionId" to expectedMissionId,
                        "date" to expectedDate,
                        "isCompleted" to true,
                        "completedAt" to FieldValue.serverTimestamp(),
                    ))
                    true
                }
            }.await()
        } catch (failure: Exception) {
            val saved = progressRef.get(Source.SERVER).await()
            if (saved.getBoolean("isCompleted") == true && saved.getString("missionId") == expectedMissionId) false
            else throw failure
        }
        return DailyMissionSubmissionResult(
            correct = true, alreadyCompleted = !firstCompletion,
            streak = educationalStreak(completedDates(uid), expectedDate),
        )
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
        streak = parseStreakResult(data["streak"]),
    )
}

internal fun parseStreakResult(value: Any?): StreakModel {
    val data = value as? Map<*, *> ?: error("Streak ausente.")
    fun count(key: String): Int {
        val number = data[key] as? Number ?: error("Campo $key do streak ausente.")
        val longValue = number.toLong()
        check(number.toDouble() == longValue.toDouble() && longValue in 0..Int.MAX_VALUE.toLong()) {
            "Campo $key do streak inválido."
        }
        return longValue.toInt()
    }
    val claimed = data["claimedMilestones"] as? List<*> ?: error("Marcos do streak ausentes.")
    val claimedMilestones = claimed.map { value ->
        val number = value as? Number ?: error("Marco do streak inválido.")
        val longValue = number.toLong()
        check(number.toDouble() == longValue.toDouble() && longValue in 0..Int.MAX_VALUE.toLong()) {
            "Marco do streak inválido."
        }
        longValue.toInt()
    }
    return StreakModel(
        currentStreak = count("currentStreak"),
        highestStreak = count("highestStreak"),
        lastMissionCompletedDate = data["lastMissionCompletedDate"] as? String,
        claimedMilestones = claimedMilestones,
    )
}

internal fun parseDailyMissionSubmissionResult(value: Any?): DailyMissionSubmissionResult {
    val data = value as? Map<*, *> ?: error("Resposta da atividade inválida.")
    val correct = data["correct"] as? Boolean ?: error("Resultado da atividade ausente.")
    return DailyMissionSubmissionResult(
        correct = correct,
        alreadyCompleted = data["alreadyCompleted"] as? Boolean ?: error("Estado de conclusão ausente."),
        feedback = if (correct) null else data["feedback"] as? String ?: error("Feedback ausente."),
        streak = if (correct) parseStreakResult(data["streak"]) else null,
        xpEarned = if (correct) {
            val number = data["xpEarned"] as? Number ?: error("XP ausente.")
            number.toInt().also { check(it >= 0 && it.toDouble() == number.toDouble()) }
        } else 0,
        leveledUp = if (correct) data["leveledUp"] as? Boolean ?: error("Nível ausente.") else false,
    )
}
