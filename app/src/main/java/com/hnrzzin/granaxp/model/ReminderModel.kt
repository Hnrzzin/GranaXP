package com.hnrzzin.granaxp.model
import java.math.BigDecimal
import com.google.firebase.Timestamp

data class ReminderModel(

    // modelo responsavel pelos lembretes de pagamento
    val id: String? = null,
    val title: String= "",
    val isCompleted: Boolean = false,
    val dueDate: Timestamp = Timestamp.now(), // data de expiração
    val amount: BigDecimal = BigDecimal.ZERO // termo padrao para valores monetarios

)
