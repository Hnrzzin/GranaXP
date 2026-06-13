package com.hnrzzin.granaxp.model
import com.google.firebase.Timestamp
import java.math.BigDecimal

data class GoalModel(
    // modelo responsavel pelas metas e sonhos
    val id: String? = null,
    val deadline: GoalDeadline = GoalDeadline.CURTO ,
    val title: String= "",
    val targetAmount: BigDecimal = BigDecimal.ZERO,
    val currentAmount: BigDecimal = BigDecimal.ZERO,
    val targetDate: Timestamp = Timestamp.now(),
)
