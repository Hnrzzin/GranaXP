package com.hnrzzin.granaxp.model

import com.google.firebase.Timestamp
import com.google.firebase.firestore.DocumentId

enum class BudgetPlanType {
    FIXO,
    VARIAVEL
}

data class BudgetModel(
    @DocumentId
    var id: String = "",
    val category: String = "",
    val limitAmount: Double = 0.0,
    val spentAmount: Double = 0.0,
    val type: BudgetPlanType = BudgetPlanType.VARIAVEL,
    // Exclusivos do FIXO
    val dueDay: Int? = null,
    val isPaid: Boolean? = null,
    val lastPaymentDate: Timestamp? = null,
    // Exclusivo do VARIAVEL — marca a referência do mês em que foi declarado/rolado
    val lastClosedMonth: Timestamp? = null
)