package com.hnrzzin.granaxp.repositories

import com.google.firebase.Timestamp
import com.google.firebase.firestore.FirebaseFirestore
import com.hnrzzin.granaxp.model.GoalDeadline
import com.hnrzzin.granaxp.model.GoalModel
import com.hnrzzin.granaxp.model.UserModel
import kotlinx.coroutines.tasks.await
import java.math.BigDecimal
import java.time.LocalDate

class GoalRepository(private val userID: String) {
    private val db = FirebaseFirestore.getInstance()
    private val collection = db.collection("Users").document(userID).collection("Goals")
    suspend fun createGoal(
        deadline: GoalDeadline,
        title: String,
        targetAmount: BigDecimal,
        currentAmount: BigDecimal,
        targetDate: Timestamp
    ){
        val goal = GoalModel(
            deadline = deadline,
            title = title,
            targetAmount = targetAmount,
            targetDate = targetDate,
            currentAmount = currentAmount
        )
        try {
            collection
                .add(goal).await()
            println("Sucesso ao adicionar meta")
        }catch (e: Exception){
            println("Falha ao criar meta: $e")
        }

    }

    suspend fun editGoal(
        goal : GoalModel
    ){
        val goalUpdated = mapOf(
            "title" to goal.title,
            "targetAmount" to goal.targetAmount,
            "currentAmount" to goal.currentAmount,
            "targetDate" to goal.targetDate

        )
        val goalID = requireNotNull(goal.id){"ID não pode ser nulo!"}
        try {
            collection.document(goalID)
                .update(goalUpdated).await()
            println("Sucesso ao atualizar meta")
        }catch (e: Exception){
            println("Falha ao atualizar meta: $e")
        }


    }

    suspend fun deleteGoal(
        goal : GoalModel
    ){
        val goalID = requireNotNull(goal.id){"ID não pode ser nulo!"}
        try {
            collection.document(goalID)
                .delete().await()
            println("Sucesso ao deletar meta")
        }catch (e: Exception){
            println("Falha ao deletar meta: $e")
        }

    }

    suspend fun getGoal(
    ): List<GoalModel>{


        try {
            val getAll = collection
                .get().await()
            if (getAll.isEmpty){
                println("Nenhum registro encontrado!")
                return emptyList()
            }else{
                val lista = getAll.map { document -> document.toObject(GoalModel::class.java) }
                val listaSemNull = lista.filterNotNull()
                println("Sucesso ao buscar a metas")
                return listaSemNull
            }
        }catch (e : Exception){
            println("Erro ao buscar a metas")
            return emptyList()
        }

    }
}