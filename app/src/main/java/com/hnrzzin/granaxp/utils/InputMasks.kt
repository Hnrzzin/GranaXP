package com.hnrzzin.granaxp.utils

import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.input.OffsetMapping
import androidx.compose.ui.text.input.TransformedText
import androidx.compose.ui.text.input.VisualTransformation
import java.text.NumberFormat
import java.util.Locale

/**
 * Formata dígitos crus como moeda brasileira (R$ 0,00) enquanto o usuário digita.
 * O valor armazenado no State continua sendo só os dígitos (ex: "150050" = R$ 1.500,50).
 */
class CurrencyVisualTransformation : VisualTransformation {
    override fun filter(text: AnnotatedString): TransformedText {
        val digits = text.text.filter { it.isDigit() }
        val amount = digits.toLongOrNull() ?: 0L
        val formatted = NumberFormat.getCurrencyInstance(Locale.forLanguageTag("pt-BR"))
            .format(amount / 100.0)

        val offsetMapping = object : OffsetMapping {
            override fun originalToTransformed(offset: Int) = formatted.length
            override fun transformedToOriginal(offset: Int) = digits.length
        }

        return TransformedText(AnnotatedString(formatted), offsetMapping)
    }
}

/**
 * Formata dígitos crus como data dd/mm/aaaa enquanto o usuário digita.
 * O valor armazenado no State continua sendo só os dígitos (ex: "01072026").
 */
class DateVisualTransformation : VisualTransformation {
    override fun filter(text: AnnotatedString): TransformedText {
        val digits = text.text.filter { it.isDigit() }.take(8)
        val sb = StringBuilder()
        for (i in digits.indices) {
            sb.append(digits[i])
            if (i == 1 || i == 3) sb.append('/')
        }
        val formatted = sb.toString()

        val offsetMapping = object : OffsetMapping {
            override fun originalToTransformed(offset: Int): Int {
                return when {
                    offset <= 1 -> offset
                    offset <= 3 -> offset + 1
                    else -> offset + 2
                }.coerceAtMost(formatted.length)
            }
            override fun transformedToOriginal(offset: Int): Int {
                return when {
                    offset <= 2 -> offset
                    offset <= 5 -> offset - 1
                    else -> offset - 2
                }.coerceAtMost(digits.length)
            }
        }

        return TransformedText(AnnotatedString(formatted), offsetMapping)
    }
}

/** Converte os dígitos crus do campo de valor (ex: "150050") para Double (1500.50). */
fun rawDigitsToAmount(raw: String): Double {
    val digits = raw.filter { it.isDigit() }
    val amount = digits.toLongOrNull() ?: 0L
    return amount / 100.0
}