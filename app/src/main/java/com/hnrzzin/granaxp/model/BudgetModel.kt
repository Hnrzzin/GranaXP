package com.hnrzzin.granaxp.model

import com.google.firebase.Timestamp
import com.google.firebase.firestore.DocumentId
import com.google.firebase.firestore.PropertyName

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
    val dueDay: Int? = null,
    @get:PropertyName("isPaid") @set:PropertyName("isPaid")
    var isPaid: Boolean? = null,
    val lastPaymentDate: Timestamp? = null,
    val lastClosedMonth: Timestamp? = null
)