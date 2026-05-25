package com.hnrzzin.granaxp.model
import java.math.BigDecimal
import java.time.LocalDate

data class GoalModel(
    // modelo responsavel pelas metas e sonhos
    val idUser: String,
    val id: String,
    val deadline: GoalDeadline,
    val title: String,
    val targetAmount: BigDecimal,
    val currentAmount: BigDecimal,
    val targetDate: LocalDate
)
