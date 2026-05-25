package com.hnrzzin.granaxp.repositories
import java.time.LocalDate
import java.math.BigDecimal
import com.google.firebase.firestore.FirebaseFirestore
import com.hnrzzin.granaxp.model.TransactionModel
import kotlinx.coroutines.tasks.await
import com.hnrzzin.granaxp.model.TransactionType
class TransactionRepository {
    val db = FirebaseFirestore.getInstance()

    suspend fun createTransaction(
                          title: String,
                          amount: BigDecimal,
                          type: TransactionType,
                          date: LocalDate,
                          category : String )
    {
        // teoricamnet, nessa linha a gente ta juntando os dados em um objeto
        val transaction = TransactionModel( null, title, amount, type, date, category)

            try {
                db.collection("Transactions")
                .add(transaction).await() // cria um id automatico para a transação
                println("Sucesso ao adicionar a transação")
            } catch (e : Exception){
                println("Falha ao adicionar a transação: $e")
            }
    }
}