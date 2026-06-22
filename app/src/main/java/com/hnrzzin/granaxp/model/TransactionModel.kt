package com.hnrzzin.granaxp.model
import com.google.firebase.Timestamp
import com.google.firebase.firestore.DocumentId
enum class TransactionType {
    RECEITA,
    DESPESA
}
data class TransactionModel(
    @DocumentId
    var id: String = "",
    val title: String = "",
    val category: String = "",
    val amount: Double = 0.0,
    val type: TransactionType = TransactionType.DESPESA,
    val date: Timestamp = Timestamp.now(),
    val isAutomatic: Boolean = false
)
