package com.hnrzzin.granaxp.repositories

import com.google.firebase.Timestamp
import com.google.firebase.firestore.FirebaseFirestore
import com.hnrzzin.granaxp.model.ReminderModel
import kotlinx.coroutines.tasks.await

class ReminderRepository(private val userId: String) {
    private val db = FirebaseFirestore.getInstance()
    private val collection = db.collection("users").document(userId).collection("reminders")

    suspend fun getReminders(): List<ReminderModel> {
        return try {
            val result = collection.get().await()
            result.mapNotNull { it.toObject(ReminderModel::class.java) }
        } catch (e: Exception) {
            println("Falha ao buscar lembretes: $e")
            emptyList()
        }
    }

    suspend fun createReminder(
        title: String,
        description: String,
        date: Timestamp,
        time: String
    ) {
        val reminder = ReminderModel(
            title = title,
            description = description,
            date = date,
            time = time
        )
        try {
            collection.add(reminder).await()
        } catch (e: Exception) {
            println("Falha ao criar lembrete: $e")
        }
    }

    suspend fun updateReminder(reminder: ReminderModel) {
        if (reminder.id.isEmpty()) return
        val updates = mapOf(
            "title" to reminder.title,
            "description" to reminder.description,
            "date" to reminder.date,
            "time" to reminder.time,
            "isCompleted" to reminder.isCompleted
        )
        try {
            collection.document(reminder.id).update(updates).await()
        } catch (e: Exception) {
            println("Falha ao atualizar lembrete: $e")
        }
    }

    suspend fun deleteReminder(reminder: ReminderModel) {
        if (reminder.id.isEmpty()) return
        try {
            collection.document(reminder.id).delete().await()
        } catch (e: Exception) {
            println("Falha ao deletar lembrete: $e")
        }
    }
}