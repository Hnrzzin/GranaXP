package com.hnrzzin.granaxp.repositories

import com.google.firebase.Timestamp
import com.google.firebase.firestore.FirebaseFirestore
import com.hnrzzin.granaxp.model.BudgetModel
import com.hnrzzin.granaxp.model.BudgetPlanType
import kotlinx.coroutines.tasks.await

class BudgetRepository(private val userId: String) {
    private val db = FirebaseFirestore.getInstance()
    private val collection = db.collection("users").document(userId).collection("budgets")

    suspend fun getBudgets(): List<BudgetModel> {
        return try {
            val result = collection.get().await()
            result.mapNotNull { it.toObject(BudgetModel::class.java) }
        } catch (e: Exception) {
            println("Falha ao buscar orçamentos: $e")
            emptyList()
        }
    }

    suspend fun getFixedBudgets(): List<BudgetModel> {
        return try {
            val result = collection
                .whereEqualTo("type", BudgetPlanType.FIXO.name)
                .get().await()
            result.mapNotNull { it.toObject(BudgetModel::class.java) }
        } catch (e: Exception) {
            println("Falha ao buscar gastos fixos: $e")
            emptyList()
        }
    }

    suspend fun getUnpaidBudgets(): List<BudgetModel> {
        return try {
            val result = collection
                .whereEqualTo("type", BudgetPlanType.FIXO.name)
                .whereEqualTo("isPaid", false)
                .get().await()
            result.mapNotNull { it.toObject(BudgetModel::class.java) }
        } catch (e: Exception) {
            println("Falha ao buscar gastos não pagos: $e")
            emptyList()
        }
    }

    suspend fun createBudget(
        category: String,
        limitAmount: Double,
        type: BudgetPlanType,
        dueDay: Int? = null
    ) {
        val budget = BudgetModel(
            category = category,
            limitAmount = limitAmount,
            type = type,
            dueDay = dueDay,
            isPaid = if (type == BudgetPlanType.FIXO) false else null
        )
        try {
            collection.add(budget).await()
        } catch (e: Exception) {
            println("Falha ao criar orçamento: $e")
        }
    }

    suspend fun updateBudget(budget: BudgetModel) {
        if (budget.id.isEmpty()) return
        val updates = mapOf(
            "category" to budget.category,
            "limitAmount" to budget.limitAmount,
            "spentAmount" to budget.spentAmount,
            "dueDay" to budget.dueDay,
            "isPaid" to budget.isPaid,
            "lastPaymentDate" to budget.lastPaymentDate
        )
        try {
            collection.document(budget.id).update(updates).await()
        } catch (e: Exception) {
            println("Falha ao atualizar orçamento: $e")
        }
    }

    suspend fun deleteBudget(budget: BudgetModel) {
        if (budget.id.isEmpty()) return
        try {
            collection.document(budget.id).delete().await()
        } catch (e: Exception) {
            println("Falha ao deletar orçamento: $e")
        }
    }
}