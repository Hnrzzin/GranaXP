package com.hnrzzin.granaxp.repositories

import com.google.firebase.Timestamp
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.hnrzzin.granaxp.model.GoalDeadline
import com.hnrzzin.granaxp.model.GoalModel
import kotlinx.coroutines.tasks.await

class GoalRepository(private val userId: String) {
    private val collection = FirebaseFirestore.getInstance()
        .collection("users").document(userId).collection("goals")

    suspend fun getGoals(): List<GoalModel> = collection.get().await()
        .mapNotNull { it.toObject(GoalModel::class.java) }

    suspend fun createGoal(
        requestId: String,
        title: String,
        targetAmount: Double,
        currentAmount: Double,
        deadline: GoalDeadline,
        deadlineDate: Timestamp?,
    ): GoalCreationResult {
        require(requestId.isNotBlank() && title.isNotBlank()) { "Meta inválida." }
        require(targetAmount >= 0 && currentAmount >= 0) { "Valores da meta inválidos." }
        val ref = collection.document(requestId)
        val goalId = FirebaseFirestore.getInstance().runTransaction { transaction ->
            val previous = transaction.get(ref)
            if (previous.exists()) {
                check(previous.getString("title") == title &&
                    previous.getDouble("targetAmount") == targetAmount &&
                    previous.getDouble("currentAmount") == currentAmount &&
                    previous.getString("deadline") == deadline.name) {
                    "Identificador de criação já usado para outra meta."
                }
            } else {
                transaction.set(ref, mapOf(
                    "title" to title, "targetAmount" to targetAmount,
                    "currentAmount" to currentAmount, "deadline" to deadline.name,
                    "deadlineDate" to deadlineDate,
                    "completedAt" to if (targetAmount > 0 && currentAmount >= targetAmount)
                        FieldValue.serverTimestamp() else null,
                ))
            }
            ref.id
        }.await()
        return GoalCreationResult(goalId, 0, false)
    }

    suspend fun updateGoalProgress(goalId: String, amountToAdd: Double): XpMutationResult {
        require(amountToAdd > 0 && amountToAdd.isFinite()) { "Valor inválido." }
        val ref = collection.document(goalId)
        FirebaseFirestore.getInstance().runTransaction { transaction ->
            val goal = transaction.get(ref)
            check(goal.exists()) { "Meta não encontrada." }
            val current = goal.getDouble("currentAmount") ?: error("Valor acumulado inválido.")
            val target = goal.getDouble("targetAmount") ?: error("Alvo inválido.")
            val updated = current + amountToAdd
            require(updated.isFinite() && updated <= 1_000_000_000_000.0) { "Valor acumulado inválido." }
            val changes = mutableMapOf<String, Any>("currentAmount" to updated)
            if (target > 0 && updated >= target && goal.getTimestamp("completedAt") == null) {
                changes["completedAt"] = FieldValue.serverTimestamp()
            }
            transaction.update(ref, changes)
        }.await()
        return XpMutationResult(0, false)
    }

    suspend fun updateGoalDetails(
        goalId: String,
        title: String,
        targetAmount: Double,
        deadline: GoalDeadline,
        deadlineDate: Timestamp?,
    ): XpMutationResult {
        require(title.isNotBlank() && targetAmount >= 0 && targetAmount.isFinite()) { "Meta inválida." }
        val ref = collection.document(goalId)
        FirebaseFirestore.getInstance().runTransaction { transaction ->
            val goal = transaction.get(ref)
            check(goal.exists()) { "Meta não encontrada." }
            val current = goal.getDouble("currentAmount") ?: error("Valor acumulado inválido.")
            val changes = mutableMapOf<String, Any?>(
                "title" to title, "targetAmount" to targetAmount,
                "deadline" to deadline.name, "deadlineDate" to deadlineDate,
            )
            if (targetAmount > 0 && current >= targetAmount && goal.getTimestamp("completedAt") == null) {
                changes["completedAt"] = FieldValue.serverTimestamp()
            }
            transaction.update(ref, changes)
        }.await()
        return XpMutationResult(0, false)
    }

    suspend fun deleteGoal(goal: GoalModel) {
        if (goal.id.isNotEmpty()) collection.document(goal.id).delete().await()
    }
}

internal fun Timestamp?.toCallableTimestamp(): Map<String, Double>? = this?.let { timestamp ->
    mapOf(
        "seconds" to timestamp.seconds.toDouble(),
        "nanoseconds" to timestamp.nanoseconds.toDouble(),
    )
}
