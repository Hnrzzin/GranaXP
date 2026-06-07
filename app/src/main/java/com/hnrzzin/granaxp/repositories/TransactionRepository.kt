package com.hnrzzin.granaxp.repositories
import java.time.LocalDate
import java.math.BigDecimal
import com.google.firebase.firestore.FirebaseFirestore
import com.hnrzzin.granaxp.model.TransactionModel
import kotlinx.coroutines.tasks.await
import com.hnrzzin.granaxp.model.TransactionType
import com.hnrzzin.granaxp.model.UserModel
import kotlin.jvm.java

class TransactionRepository {
    // instancia do bd
    val db = FirebaseFirestore.getInstance()

    // metodo responsavel por fazer as inserções no bd
    suspend fun createTransaction(
        // contrutor pra gente criar uma transação
        title: String,
        amount: BigDecimal,
        type: TransactionType,
        date: LocalDate,
        category: String
    ) {
        // montando o obj
        val transaction = TransactionModel(
            title = title,
            amount = amount,
            type = type,
            date = date,
            category = category
        )
        // fazendo a criação
        try {
            db.collection("Transactions")
                .add(transaction).await() // cria um id automatico para a transação
            println("Sucesso ao adicionar a transação")
        } catch (e: Exception) {
            println("Falha ao adicionar a transação: $e")
        }
    }

    // metodo responsavel por fazer as atualizações no bd
    suspend fun updateTransaction(
        // construtor para atualizar uma transação
        updatedTransaction: TransactionModel
    ) {
        // Se nome for nulo, lança IllegalArgumentException.
        // Se passar daqui, o Kotlin sabe que 'id' não é nulo.
        val transactionId = requireNotNull(updatedTransaction.id) { "ID não pode ser nulo!" }

        // mapeando (key e value) para atualizar somente os campos que o usuario trocar, para nao precisar atualizar o modelo inteiro
        val updates = mapOf(
            "title" to updatedTransaction.title,
            "amount" to updatedTransaction.amount,
            "category" to updatedTransaction.category

        )
        // atualização no banco
        try {
            db.collection("Transactions")
                .document(transactionId)
                .update(updates).await()
            println("Sucesso ao atualizar a transação")
        } catch (e: Exception) {
            println("Falha ao atualizar a transação: $e")
        }
    }

    // metodo responsavel por deleytar no bd
    suspend fun deleteTransaction(
        // construtor para pegar o id a ser deletado
        transaction: TransactionModel
    ) {

        val transactionId = requireNotNull(transaction.id) { "ID não pode ser nulo!" }
        // deleção no banco
        try {
            db.collection("Transactions")
                .document(transactionId)
                .delete().await()
            println("Sucesso ao deletar a transação")
        } catch (e: Exception) {
            println("Falha ao deletar a transação: $e")
        }

    }

    // get do banco
    suspend fun getTransactions(
        // construtor do usuario pra pegar o id dele (a fim de mostrar todas as trabszaçpes)
        user: UserModel,
        ): List<TransactionModel> // retorna uma lista
        {

        val userID = user.id
        // fazendo a busca
        try {
            val getAll = db.collection("Transactions")
                .whereEqualTo(
                    "idUser",
                    userID
                )
                .get().await()
            if (getAll.isEmpty) {
                println("Nenhum registro encontrado!")
                return emptyList()
            }else{
                val lista = getAll.map { documento -> documento.toObject(TransactionModel::class.java) }
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

