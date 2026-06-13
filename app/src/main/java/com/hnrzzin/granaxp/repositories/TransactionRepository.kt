package com.hnrzzin.granaxp.repositories
import com.google.firebase.Timestamp
import java.math.BigDecimal
import com.google.firebase.firestore.FirebaseFirestore
import com.hnrzzin.granaxp.model.TransactionModel
import kotlinx.coroutines.tasks.await
import com.hnrzzin.granaxp.model.TransactionType


class TransactionRepository(private val userID: String) {
    // instancia do bd
    private val db = FirebaseFirestore.getInstance()
    private val collection = db.collection("Users").document(userID).collection("Transactions")
    // metodo responsavel por fazer as inserções no bd
    suspend fun createTransaction(
        // contrutor pra gente criar uma transação
        title: String,
        amount: BigDecimal,
        type: TransactionType,
        category: String
    ) {
        // montando o obj
        val transaction = TransactionModel(
            title = title,
            amount = amount,
            type = type,
            date = Timestamp.now(),
            category = category
        )
        // fazendo a criação
        try {
            collection
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
           collection
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
            collection
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
        ): List<TransactionModel> // retorna uma lista
        {

        // fazendo a busca
        try {
            val getAll = collection
                .get().await()
            if (getAll.isEmpty) {
                println("Nenhum registro encontrado!")
                return emptyList()
            }else{
                val lista = getAll.mapNotNull { documento -> documento.toObject(TransactionModel::class.java) }
                println("Sucesso ao buscar a transações")
                return lista
            }

        } catch (e: Exception) {
            println("Falha ao buscar a transações $e")
            return emptyList()
        }

    }
}

