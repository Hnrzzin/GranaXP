package com.hnrzzin.granaxp.utils

import android.annotation.SuppressLint
import com.google.firebase.Timestamp
import java.text.SimpleDateFormat
import java.util.Locale

object DateUtils {
    @SuppressLint("ConstantLocale")
    private val sdf = SimpleDateFormat("dd/MM/yyyy", Locale.getDefault())

    // Alterado o tipo de retorno para Timestamp?
    fun parseDateToTimestamp(dateString: String): Timestamp? {
        return try {
            val date = sdf.parse(dateString)
            // Retorna o objeto Timestamp se a data for válida
            date?.let { Timestamp(it) }
        } catch (e: Exception) {
            e.printStackTrace() // É uma boa prática logar o erro
            null
        }
    }

    private fun parseDateToTimestampOrNull(dateText: String): Timestamp? {
        return try {
            val sdf = java.text.SimpleDateFormat("dd/MM/yyyy", java.util.Locale("pt", "BR"))
            Timestamp(sdf.parse(dateText)!!)
        } catch (e: Exception) {
            null
        }
    }
}