package com.hnrzzin.granaxp.ui.theme.components

import com.hnrzzin.granaxp.model.TransactionType

/**
 * Classe de dados exclusiva para a interface (UI).
 * Desacopla os componentes visuais do TransactionModel proveniente do Firestore.
 */
data class TransactionUIData(
    val description: String,
    val category: String,
    val date: String,
    val amount: Double,
    val type: TransactionType
)

