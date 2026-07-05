package com.hnrzzin.granaxp.repositories

import com.google.firebase.firestore.FirebaseFirestore
import com.hnrzzin.granaxp.model.TransactionModel
import com.hnrzzin.granaxp.model.TransactionType
import kotlinx.coroutines.tasks.await

class TransactionRepository(private val userId: String) {
    private val db = FirebaseFirestore.getInstance()
    private val collection = db.collection("users").document(userId).collection("transactions")

    suspend fun getTransactions(): List<TransactionModel> {
        return try {
            val result = collection.get().await()
            result.mapNotNull { it.toObject(TransactionModel::class.java) }
        } catch (e: Exception) {
            println("Falha ao buscar transações: $e")
            emptyList()
        }
    }

    /**
     * Regra de negócio crítica #1 — verifica direto no Firestore se já existe
     * pelo menos uma RECEITA declarada. Consulta independente do cache da UI,
     * usada como guarda autoritativa antes de permitir uma DESPESA.
     */
    suspend fun hasAnyIncome(): Boolean {
        return try {
            val result = collection
                .whereEqualTo("type", TransactionType.RECEITA.name)
                .limit(1)
                .get()
                .await()
            !result.isEmpty
        } catch (e: Exception) {
            println("Falha ao verificar histórico de receitas: $e")
            false // em caso de falha na checagem, bloqueia por segurança
        }
    }

    suspend fun createTransaction(
        title: String,
        amount: Double,
        type: TransactionType,
        category: String,
        isAutomatic: Boolean = false
    ) {
        val transaction = TransactionModel(
            title = title,
            amount = amount,
            type = type,
            category = category,
            isAutomatic = isAutomatic
        )
        try {
            collection.add(transaction).await()
        } catch (e: Exception) {
            println("Falha ao criar transação: $e")
        }
    }

    suspend fun updateTransaction(transaction: TransactionModel) {
        if (transaction.id.isEmpty()) return
        val updates = mapOf(
            "title" to transaction.title,
            "amount" to transaction.amount,
            "category" to transaction.category,
            "type" to transaction.type.name,
            "isAutomatic" to transaction.isAutomatic
        )
        try {
            collection.document(transaction.id).update(updates).await()
        } catch (e: Exception) {
            println("Falha ao atualizar transação: $e")
        }
    }

    suspend fun deleteTransaction(transaction: TransactionModel) {
        if (transaction.id.isEmpty()) return
        try {
            collection.document(transaction.id).delete().await()
        } catch (e: Exception) {
            println("Falha ao deletar transação: $e")
        }
    }
}