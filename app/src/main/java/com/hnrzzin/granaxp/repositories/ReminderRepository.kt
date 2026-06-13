package com.hnrzzin.granaxp.repositories

import com.google.firebase.Timestamp
import com.google.firebase.firestore.FirebaseFirestore
import com.hnrzzin.granaxp.model.ReminderModel
import com.hnrzzin.granaxp.model.TransactionModel
import com.hnrzzin.granaxp.model.UserModel
import kotlinx.coroutines.tasks.await
import java.time.LocalDate
import java.math.BigDecimal

class ReminderRepository(private val userID: String){

    private val db = FirebaseFirestore.getInstance()
    private val collection = db.collection("Users").document(userID).collection("Reminders")
    suspend fun createReminder(
        title: String,
        isCompleted: Boolean,
        dueDate: Timestamp,
        amount: BigDecimal
    ){
        val reminder = ReminderModel(
            title = title,
            isCompleted = isCompleted,
            dueDate = dueDate,
            amount = amount
        )
        try {
            collection
                .add(reminder).await()
            println("Sucesso ao criar um lembrete!")
        }catch (e: Exception){
            println("Falha ao adicionar um lembrete: $e")
        }
    }

    suspend fun updateReminder(
        reminder: ReminderModel
    ){
        val reminderID = requireNotNull(reminder.id){"ID não pode ser nulo!"}

        val updates = mapOf(
            "title" to reminder.title,
            "isCompleted" to reminder.isCompleted,
            "dueDate" to reminder.dueDate,
            "amount" to reminder.amount
        )
        try {
            collection.document(reminderID)
                .update(updates).await()
            println("Sucesso ao atualizar  um lembrete!")
        }catch (e: Exception){
            println("Falha ao atualizar um lembrete: $e")
        }

    }

    suspend fun getReminder(
    ): List<ReminderModel>{
        try {
            val getAll = collection
                .get().await()
            if (getAll.isEmpty){
                println("Nenhum registro encontrado!")
                return emptyList()
            }else{
                val lista = getAll.map { documento -> documento.toObject(ReminderModel::class.java) }
                val listaSemNull = lista.filterNotNull()
                println("Sucesso ao buscar lembretes!")
                return listaSemNull
            }

        }catch (e: Exception){
            println("Falha ao buscar lembretes: $e")
            return emptyList()
        }
    }

    suspend fun deleteReminder(
        reminder: ReminderModel
    ){
        val reminderID = requireNotNull(reminder.id){"O ID não pode ser nulo"}
        try {
            collection
                .document(reminderID)
                .delete().await()
            println("Sucesso ao deletar o lembrete!")
        } catch (e: Exception) {
            println("Falha ao deletar o lembrete: $e")
        }

    }
}