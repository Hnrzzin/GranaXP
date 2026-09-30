package com.hnrzzin.granaxp.repositories

import com.google.firebase.Timestamp
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.functions.FirebaseFunctions
import com.hnrzzin.granaxp.model.GoalDeadline
import com.hnrzzin.granaxp.model.GoalModel
import kotlinx.coroutines.tasks.await

class GoalRepository(private val userId: String) {
    private val collection = FirebaseFirestore.getInstance()
        .collection("users").document(userId).collection("goals")
    private val functions: FirebaseFunctions = FirebaseFunctionsProvider.instance

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
        val result = functions.getHttpsCallable("createGoal").call(
            mapOf(
                "requestId" to requestId,
                "title" to title,
                "targetAmount" to targetAmount,
                "currentAmount" to currentAmount,
                "deadline" to deadline.name,
                "deadlineDate" to deadlineDate.toCallableTimestamp(),
            ),
        ).await()
        return parseGoalCreationResult(result.data)
    }

    suspend fun updateGoalProgress(goalId: String, amountToAdd: Double): XpMutationResult {
        val result = functions.getHttpsCallable("updateGoalProgress").call(
            mapOf("goalId" to goalId, "amountToAdd" to amountToAdd),
        ).await()
        return parseXpMutationResult(result.data)
    }

    suspend fun updateGoalDetails(
        goalId: String,
        title: String,
        targetAmount: Double,
        deadline: GoalDeadline,
        deadlineDate: Timestamp?,
    ): XpMutationResult {
        val result = functions.getHttpsCallable("updateGoalDetails").call(
            mapOf(
                "goalId" to goalId,
                "title" to title,
                "targetAmount" to targetAmount,
                "deadline" to deadline.name,
                "deadlineDate" to deadlineDate.toCallableTimestamp(),
            ),
        ).await()
        return parseXpMutationResult(result.data)
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
