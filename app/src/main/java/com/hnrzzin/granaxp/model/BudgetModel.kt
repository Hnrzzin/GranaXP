package com.hnrzzin.granaxp.model

import com.google.firebase.Timestamp
import java.math.BigDecimal



data class BudgetModel(
    val id: String? = null,
    val title: String= "",
    val amount: BigDecimal = BigDecimal.ZERO,
    val type: BudgetType = BudgetType.VARIÁVEL,
    val dueDay: Int? = null,
    val isPaid: Boolean? = null,
    val lastPaymentDate: Timestamp? = null
)
