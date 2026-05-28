package com.hnrzzin.granaxp.repositories

import com.google.firebase.firestore.FirebaseFirestore
import com.hnrzzin.granaxp.model.GoalDeadline
import com.hnrzzin.granaxp.model.GoalModel
import com.hnrzzin.granaxp.model.UserModel
import kotlinx.coroutines.tasks.await
import java.math.BigDecimal
import java.time.LocalDate

class GoalRepository {
    val db = FirebaseFirestore.getInstance()

    suspend fun createGoal(
        deadline: GoalDeadline,
        title: String,
        targetAmount: BigDecimal,
        currentAmount: BigDecimal,
        targetDate: LocalDate
    ){
        val goal = GoalModel(
            deadline = deadline,
            title = title,
            targetAmount = targetAmount,
            targetDate = targetDate,
            currentAmount = currentAmount
        )
        try {
            db.collection("Goals")
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
            db.collection("Goals").document(goalID)
                .update(goalUpdated).await()
            println("Sucesso ao atualizar meta")
        }catch (e: Exception){
            println("Falha ao criar meta: $e")
        }


    }

    suspend fun deleteGoal(
        goal : GoalModel
    ){
        val goalID = requireNotNull(goal.id){"ID não pode ser nulo!"}
        try {
            db.collection("Goals").document(goalID)
                .delete().await()
            println("Sucesso ao deletar meta")
        }catch (e: Exception){
            println("Falha ao deletar meta: $e")
        }

    }

    suspend fun getGoal(
        user: UserModel
    ): List<GoalModel>{

        val userID = user.id
        try {
            val getAll = db.collection("Goals")
                .whereEqualTo(
                    "idUser",
                    userID
                )
                .get().await()
            if (getAll.isEmpty){
                println("Sem metas cadastradas")
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