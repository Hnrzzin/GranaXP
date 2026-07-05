package com.hnrzzin.granaxp.utils

import android.annotation.SuppressLint
import com.google.firebase.Timestamp
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Locale

object DateUtils {

    @SuppressLint("ConstantLocale")
    private val sdf = SimpleDateFormat("dd/MM/yyyy", Locale("pt", "BR")).apply {
        isLenient = false // rejeita datas absurdas tipo 35/13/2026
    }

    sealed class DateValidationResult {
        data class Valid(val timestamp: Timestamp) : DateValidationResult()
        object InvalidFormat : DateValidationResult()
        object PastDate : DateValidationResult()
    }

    /**
     * Recebe os dígitos crus de um campo de data (ex: "05072026" = 05/07/2026)
     * e valida se é uma data real e não anterior ao dia de hoje.
     */
    fun validateFutureDate(rawDigits: String): DateValidationResult {
        if (rawDigits.length != 8) return DateValidationResult.InvalidFormat

        val day = rawDigits.substring(0, 2)
        val month = rawDigits.substring(2, 4)
        val year = rawDigits.substring(4, 8)

        val parsedDate = try {
            sdf.parse("$day/$month/$year")
        } catch (e: Exception) {
            null
        } ?: return DateValidationResult.InvalidFormat

        val today = Calendar.getInstance().apply {
            set(Calendar.HOUR_OF_DAY, 0)
            set(Calendar.MINUTE, 0)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
        }.time

        return if (parsedDate.before(today)) {
            DateValidationResult.PastDate
        } else {
            DateValidationResult.Valid(Timestamp(parsedDate))
        }
    }

    /** Parse simples sem validação de passado — mantido para compatibilidade com chamadas existentes. */
    fun parseDateToTimestamp(dateString: String): Timestamp? {
        return try {
            val date = sdf.parse(dateString)
            date?.let { Timestamp(it) }
        } catch (e: Exception) {
            e.printStackTrace()
            null
        }
    }
}