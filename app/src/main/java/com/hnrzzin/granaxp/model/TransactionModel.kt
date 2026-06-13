package com.hnrzzin.granaxp.model
import com.google.firebase.Timestamp
import java.math.BigDecimal



data class TransactionModel(
    // modelo responsavel pelas transações (despezas/receitas)

    val id: String? = null,
    val title: String = "",
    val amount: BigDecimal= BigDecimal.ZERO,
    val type: TransactionType = TransactionType.DESPESA,
    val date: Timestamp = Timestamp.now(),
    val category: String = ""
)





