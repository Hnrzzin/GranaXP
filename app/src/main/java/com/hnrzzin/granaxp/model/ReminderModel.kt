package com.hnrzzin.granaxp.model
import java.math.BigDecimal
import java.time.LocalDate

data class ReminderModel(

    // modelo responsavel pelos lembretes de pagamento
    val idUser: String,
    val id: String,
    val isCompleted: Boolean,
    val dueDate: LocalDate, // data de expiração
    val amount: BigDecimal, // termo padrao para valores monetarios
    val description: String
)
