package com.hnrzzin.granaxp.repositories

import com.google.firebase.firestore.FirebaseFirestore
import com.hnrzzin.granaxp.model.AchievementModel
import com.hnrzzin.granaxp.model.AchievementProgressModel
import com.hnrzzin.granaxp.model.TransactionType
import com.hnrzzin.granaxp.model.LessonModel
import com.hnrzzin.granaxp.model.LessonProgressModel
import java.util.Calendar
import java.util.TimeZone
import kotlinx.coroutines.tasks.await

private fun validStreakDefinition(data: Map<String, Any?>): Boolean {
    val requirement = data["requirementValue"] as? Number ?: return false
    val threshold = requirement.toInt()
    return requirement.toDouble() == threshold.toDouble() && threshold in setOf(7, 15, 30) &&
        data["category"] in setOf("EDUCACAO", "FINANCAS") &&
        listOf("title", "description", "icon").all { data[it] is String } &&
        (data["referenceId"] == null || data["referenceId"] is String)
}

internal fun <T> mapAchievementDocuments(
    documents: List<T>,
    fields: (T) -> Map<String, Any?>,
    decode: (T) -> AchievementModel?,
): List<AchievementModel> = documents.mapNotNull { document ->
    try {
        val data = fields(document)
        if (data["requirementType"] == "STREAK" && !validStreakDefinition(data)) null
        else decode(document)
    } catch (_: Exception) {
        null
    }
}

internal suspend fun <T> readAchievementCatalog(
    fetch: suspend () -> List<T>,
    fields: (T) -> Map<String, Any?>,
    decode: (T) -> AchievementModel?,
): List<AchievementModel> = mapAchievementDocuments(fetch(), fields, decode)

class AchievementRepository {
    private val db = FirebaseFirestore.getInstance()

    suspend fun getAchievements(): List<AchievementModel> {
        return readAchievementCatalog(
            fetch = { db.collection("achievements").get().await().documents },
            fields = { it.data.orEmpty() },
            decode = { it.toObject(AchievementModel::class.java) },
        )
    }

    suspend fun getAchievementProgress(userId: String): List<AchievementProgressModel> {
        return db.collection("achievementProgress")
            .whereEqualTo("userId", userId)
            .get()
            .await()
            .toObjects(AchievementProgressModel::class.java)
    }

    suspend fun getUserAchievementProgress(userId: String): List<AchievementProgressModel> {
        return db.collection("users").document(userId).collection("achievementProgress")
            .get().await()
            .map { document ->
                document.toObject(AchievementProgressModel::class.java).also {
                    check(it.achievementId == document.id) {
                        "Progresso de conquista com ID inconsistente."
                    }
                }
            }
    }

    suspend fun getLocalAchievementProgress(
        userId: String,
        catalog: List<AchievementModel>,
    ): List<AchievementProgressModel> {
        val transactions = TransactionRepository(userId).getTransactions()
        val goals = GoalRepository(userId).getGoals()
        val learning = LessonRepository(userId)
        val lessons = learning.getLessons()
        val progress = learning.getLessonProgress()
        val completed = progress.filter { it.isCompleted }.map { it.lessonId }.toSet()
        val modules = ModuleRepository().getModules()
        val completedModules = modules.map { it.idModule }.filter { moduleId ->
            val moduleLessons = lessons.filter { it.moduleId == moduleId }
            moduleLessons.isNotEmpty() && moduleLessons.all { it.id in completed }
        }.toSet()
        val currentMonth = Calendar.getInstance(TimeZone.getTimeZone("America/Sao_Paulo"))
        val savings = transactions.filter { item ->
            val itemDate = Calendar.getInstance(TimeZone.getTimeZone("America/Sao_Paulo"))
            itemDate.time = item.date.toDate()
            itemDate.get(Calendar.YEAR) == currentMonth.get(Calendar.YEAR) &&
                itemDate.get(Calendar.MONTH) == currentMonth.get(Calendar.MONTH)
        }.sumOf { if (it.type == TransactionType.RECEITA) it.amount else -it.amount }
        val dates = db.collection("users").document(userId).collection("dailyMissionProgress")
            .get().await().documents.filter { it.getBoolean("isCompleted") == true }.map { it.id }
        val today = java.text.SimpleDateFormat("yyyy-MM-dd", java.util.Locale.US).apply {
            timeZone = TimeZone.getTimeZone("America/Sao_Paulo")
        }.format(java.util.Date())
        return deriveSparkAchievements(catalog, SparkAchievementFacts(
            transactionCount = transactions.size,
            goalCount = goals.size,
            lessonCount = completed.size,
            monthlySavings = savings.coerceIn(0.0, Int.MAX_VALUE.toDouble()).toInt(),
            completedModules = completedModules,
            highestStreak = educationalStreak(dates, today).highestStreak,
        ))
    }

}
