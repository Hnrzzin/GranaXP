package com.hnrzzin.granaxp.model

import java.math.BigDecimal
import java.time.LocalDate


data class BudgetModel(
    val id: String? = null,
    val title: String,
    val amount: BigDecimal,
    val type: BudgetType,
    val dueDay: Int? = null,
    val isPaid: Boolean? = null,
    val lastPaymentDate: LocalDate? = null
)
