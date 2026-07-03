package com.hnrzzin.granaxp.ui.theme.components

import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CalendarMonth
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.google.firebase.Timestamp
import com.hnrzzin.granaxp.ui.theme.GranaXPColors
import com.hnrzzin.granaxp.viewmodel.ReminderViewModel
import java.text.SimpleDateFormat
import java.util.Locale

private enum class NotifyOption(val label: String) {
    NO_DIA("No dia"), UM_DIA_ANTES("1 dia antes"), TRES_DIAS_ANTES("3 dias antes")
}

@Composable
fun AddReminderSheet(
    viewModel: ReminderViewModel,
    onDismiss: () -> Unit
) {
    var title by remember { mutableStateOf("") }
    var amount by remember { mutableStateOf("") }
    var dateText by remember { mutableStateOf("") }
    var isRecurring by remember { mutableStateOf(false) }               // visual apenas, não persiste ainda
    var selectedNotifyOptions by remember { mutableStateOf(setOf(NotifyOption.NO_DIA)) } // visual apenas

    AppModalBottomSheet(onDismiss = onDismiss) {
        AppModalHeader(title = "Novo Lembrete de Pagamento", onClose = onDismiss)

        OutlinedTextField(
            value = title,
            onValueChange = { title = it },
            label = { Text("Descrição do Pagamento") },
            placeholder = { Text("Ex: Fatura Cartão de Crédito") },
            modifier = Modifier.fillMaxWidth(),
            colors = OutlinedTextFieldDefaults.colors(focusedBorderColor = GranaXPColors.Rose500)
        )

        Spacer(Modifier.height(12.dp))

        OutlinedTextField(
            value = amount,
            onValueChange = { amount = it },
            label = { Text("Valor (R$)") },
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
            modifier = Modifier.fillMaxWidth(),
            colors = OutlinedTextFieldDefaults.colors(focusedBorderColor = GranaXPColors.Rose500)
        )

        Spacer(Modifier.height(12.dp))

        OutlinedTextField(
            value = dateText,
            onValueChange = { dateText = it },
            label = { Text("Data de Vencimento") },
            placeholder = { Text("dd/mm/aaaa") },
            leadingIcon = { Icon(Icons.Default.CalendarMonth, contentDescription = null, tint = GranaXPColors.Rose500) },
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
            modifier = Modifier.fillMaxWidth(),
            colors = OutlinedTextFieldDefaults.colors(focusedBorderColor = GranaXPColors.Rose500)
        )

        Spacer(Modifier.height(20.dp))

        // Toggle de recorrência
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text("Repetir mensalmente", fontSize = 14.sp, color = GranaXPColors.Gray800, fontWeight = FontWeight.Medium)
            Switch(
                checked = isRecurring,
                onCheckedChange = { isRecurring = it },
                colors = SwitchDefaults.colors(
                    checkedThumbColor = GranaXPColors.White,
                    checkedTrackColor = GranaXPColors.Primary
                )
            )
        }

        Spacer(Modifier.height(20.dp))

        // Chips de notificação (seleção múltipla)
        Text("Notificar", fontSize = 12.sp, color = GranaXPColors.Gray500, fontWeight = FontWeight.Medium)
        Spacer(Modifier.height(8.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            NotifyOption.entries.forEach { option ->
                val selected = option in selectedNotifyOptions
                Surface(
                    shape = RoundedCornerShape(50),
                    color = if (selected) GranaXPColors.Primary else GranaXPColors.Gray100
                ) {
                    Box(
                        modifier = Modifier
                            .clickable(
                                indication = null,
                                interactionSource = remember { MutableInteractionSource() }
                            ) {
                                selectedNotifyOptions = if (selected) {
                                    selectedNotifyOptions - option
                                } else {
                                    selectedNotifyOptions + option
                                }
                            }
                            .padding(horizontal = 14.dp, vertical = 8.dp)
                    ) {
                        Text(
                            option.label,
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Medium,
                            color = if (selected) GranaXPColors.White else GranaXPColors.Gray600
                        )
                    }
                }
            }
        }

        Spacer(Modifier.height(24.dp))

        Button(
            onClick = {
                val timestamp = parseDateToTimestamp(dateText) ?: Timestamp.now()
                viewModel.createReminder(
                    title = title,
                    description = "",
                    amount = amount.toDoubleOrNull() ?: 0.0,
                    date = timestamp,
                    time = ""
                )
                onDismiss()
            },
            enabled = title.isNotBlank() && amount.toDoubleOrNull() != null,
            modifier = Modifier.fillMaxWidth().height(52.dp),
            colors = ButtonDefaults.buttonColors(containerColor = GranaXPColors.Rose600),
            shape = RoundedCornerShape(8.dp)
        ) {
            Text("Agendar Lembrete", color = GranaXPColors.White, fontSize = 16.sp, fontWeight = FontWeight.SemiBold)
        }
    }
}

private fun parseDateToTimestamp(dateText: String): Timestamp? {
    return try {
        val sdf = SimpleDateFormat("dd/MM/yyyy", Locale("pt", "BR"))
        Timestamp(sdf.parse(dateText)!!)
    } catch (e: Exception) {
        null
    }
}