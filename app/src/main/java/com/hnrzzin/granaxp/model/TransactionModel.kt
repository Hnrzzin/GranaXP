package com.hnrzzin.granaxp.model
import java.math.BigDecimal
import java.time.LocalDate



data class TransactionModel(
    // modelo responsavel pelas transações (despezas/receitas)

    val id: String? = null,
    val title: String,
    val amount: BigDecimal,
    val type: TransactionType,
    val date: LocalDate,
    val category: String
)





