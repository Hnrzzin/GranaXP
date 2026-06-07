package com.hnrzzin.granaxp.repositories

import com.google.firebase.Firebase
import com.google.firebase.firestore.FirebaseFirestore
import com.hnrzzin.granaxp.model.BudgetModel
import com.hnrzzin.granaxp.model.BudgetType
import com.hnrzzin.granaxp.model.TransactionModel
import com.hnrzzin.granaxp.model.UserModel
import kotlinx.coroutines.tasks.await
import java.math.BigDecimal
import java.time.LocalDate

class BudgetRepository {

    val db = FirebaseFirestore.getInstance()

    suspend fun createBudget(
        title: String,
        amount: BigDecimal,
        type: BudgetType,
        dueDay: Int? = null,
        isPaid: Boolean? = null,
        lastPaymentDate: LocalDate? = null

    ){
        // antes de inserir eu preciso colocar alguma validação? tipo, pra distinguir se o que o usuario criar é fixo ou variavel?
        try {
                val budget = BudgetModel(
                    title = title,
                    amount = amount,
                    type = type,
                    dueDay = dueDay,
                    isPaid = isPaid,
                    lastPaymentDate = lastPaymentDate
                )
                db.collection("Budgets")
                    .add(budget).await() // cria um id automatico para a transação
                println("Sucesso ao adicionar a transação")

        } catch (e: Exception) {
            println("Falha ao adicionar a transação: $e")
        }
    }

    suspend fun editBudget(
        budget: BudgetModel
    ){
        val budgetID = requireNotNull(budget.id) { "ID não pode ser nulo!" }
        val updates = mapOf(
            "title" to budget.title,
            "amount" to budget.amount,
            "dueDay" to budget.dueDay
        )
        try {
            db.collection("Budgets").document(budgetID)
                .update(updates).await()
            println("Sucesso ao atualizar a planilha")
        } catch (e: Exception) {
            println("Falha ao atualizar a planilha: $e")
        }

    }

    suspend fun deleteBudget(
        budget: BudgetModel
    ){
        val budgetID = requireNotNull(budget.id) { "ID não pode ser nulo!" }
        try {
            db.collection("Budgets").document(budgetID)
                .delete().await()
            println("Sucesso ao deletar a planilha")
        } catch (e: Exception) {
            println("Falha ao deletar a planilha: $e")
        }
    }

    suspend fun getBudget(
        user: UserModel
    ): List<BudgetModel>
    {
        val userID = user.id
        try {
            val getAll = db.collection("Budgets")
                .whereEqualTo(
                    "idUser",
                    userID
                )
                .get().await()
            if (getAll.isEmpty) {
                println("Nenhum registro encontrado!")
                return emptyList()
            }else{
                val lista = getAll.map { documento -> documento.toObject(BudgetModel::class.java) }
                val listaSemNull = lista.filterNotNull()
                println("Sucesso ao buscar a transações")
                return listaSemNull
            }

        } catch (e: Exception) {
            println("Falha ao buscar a transações $e")
            return emptyList()
        }

    }

    suspend fun getUnpaidBudgets(
        user: UserModel
    ): List<BudgetModel>{
    // funçao auxiliar respponsavel por verfificar se o pagamento ja foi feito (isPaid)
        val userID = user.id
        try {
            val allFixed = db.collection("Budgets")
                .whereEqualTo(
                    "idUser",
                    userID
                )
                .whereEqualTo(
                    "isPaid",
                    false
                )
                .get().await()
            if (allFixed.isEmpty) {
                println("Nenhum registro encontrado!")
                return emptyList()
            }
            val lista = allFixed.map { documento -> documento.toObject(BudgetModel::class.java) }
            val listaSemNull = lista.filterNotNull()
            println("Sucesso ao buscar a transações")
            return listaSemNull

        } catch (e: Exception) {
            println("Falha ao buscar a transações $e")
            return emptyList()
        }

    }

    suspend fun getFixedBudget(
        user: UserModel,
    ): List<BudgetModel>
    {
        val userID = user.id
        try {
            val getAll = db.collection("Budgets")
                .whereEqualTo(
                    "idUser",
                    userID
                )
                .whereEqualTo(
                    "type",
                    "FIXO"
                )

                .get().await()
            if (getAll.isEmpty) {
                println("Nenhum registro encontrado!")
                return emptyList()
            }else{
                val lista = getAll.map { documento -> documento.toObject(BudgetModel::class.java) }
                val listaSemNull = lista.filterNotNull()
                println("Sucesso ao buscar a transações")
                return listaSemNull
            }

        } catch (e: Exception) {
            println("Falha ao buscar a transações $e")
            return emptyList()
        }
    }


}