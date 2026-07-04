package com.hnrzzin.granaxp.repositories

import com.google.firebase.Timestamp
import com.google.firebase.firestore.FirebaseFirestore
import com.hnrzzin.granaxp.model.GoalDeadline
import com.hnrzzin.granaxp.model.GoalModel
import kotlinx.coroutines.tasks.await

class GoalRepository(private val userId: String) {
    private val db = FirebaseFirestore.getInstance()
    private val collection = db.collection("users").document(userId).collection("goals")

    suspend fun getGoals(): List<GoalModel> {
        return try {
            val result = collection.get().await()
            result.mapNotNull { it.toObject(GoalModel::class.java) }
        } catch (e: Exception) {
            println("Falha ao buscar metas: $e")
            emptyList()
        }
    }

    suspend fun createGoal(
        title: String,
        targetAmount: Double,
        currentAmount: Double,
        deadline: GoalDeadline,
        deadlineDate: Timestamp?
    ) {
        val goal = GoalModel(
            title = title,
            targetAmount = targetAmount,
            currentAmount = currentAmount,
            deadline = deadline,
            deadlineDate = deadlineDate
        )
        try {
            collection.add(goal).await()
        } catch (e: Exception) {
            println("Falha ao criar meta: $e")
        }
    }

    suspend fun updateGoal(goal: GoalModel) {
        if (goal.id.isEmpty()) return
        val updates = mapOf(
            "title" to goal.title,
            "targetAmount" to goal.targetAmount,
            "currentAmount" to goal.currentAmount,
            "deadline" to goal.deadline.name,
            "deadlineDate" to goal.deadlineDate
        )
        try {
            collection.document(goal.id).update(updates).await()
        } catch (e: Exception) {
            println("Falha ao atualizar meta: $e")
        }
    }

    suspend fun deleteGoal(goal: GoalModel) {
        if (goal.id.isEmpty()) return
        try {
            collection.document(goal.id).delete().await()
        } catch (e: Exception) {
            println("Falha ao deletar meta: $e")
        }
    }
}