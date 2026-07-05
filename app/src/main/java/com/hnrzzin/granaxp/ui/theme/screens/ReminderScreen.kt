package com.hnrzzin.granaxp.ui.theme.screens

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CalendarMonth
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Circle
import androidx.compose.material.icons.filled.RadioButtonUnchecked
import androidx.compose.material3.*
import androidx.compose.material.icons.filled.Delete
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.google.firebase.Timestamp
import com.hnrzzin.granaxp.model.ReminderModel
import com.hnrzzin.granaxp.ui.theme.GranaXPColors
import com.hnrzzin.granaxp.utils.CurrencyVisualTransformation
import com.hnrzzin.granaxp.utils.DateUtils
import com.hnrzzin.granaxp.utils.rawDigitsToAmount
import com.hnrzzin.granaxp.viewmodel.ReminderUiState
import com.hnrzzin.granaxp.viewmodel.ReminderViewModel
import com.hnrzzin.granaxp.utils.DateVisualTransformation

@Composable
fun ReminderContent(viewModel: ReminderViewModel) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    var reminderToDelete by remember { mutableStateOf<ReminderModel?>(null) }

    when (val state = uiState) {
        is ReminderUiState.Loading -> {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                CircularProgressIndicator(color = GranaXPColors.Rose600)
            }
        }
        is ReminderUiState.Error -> {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Text(state.message, color = GranaXPColors.Error, modifier = Modifier.padding(16.dp))
            }
        }
        is ReminderUiState.Success -> {
            if (state.reminders.isEmpty()) {
                Box(Modifier.fillMaxSize().padding(16.dp)) {
                    EmptyStateCard("Nenhum lembrete cadastrado")
                }
            } else {
                LazyColumn(
                    modifier = Modifier.fillMaxSize().background(GranaXPColors.Background),
                    contentPadding = PaddingValues(16.dp, 16.dp, 16.dp, 96.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    item {
                        Text("Próximos Vencimentos", fontSize = 14.sp, fontWeight = FontWeight.Medium, color = GranaXPColors.Gray500)
                    }
                    items(state.reminders, key = { it.id }) { reminder ->
                        ReminderCard(
                            reminder = reminder,
                            onToggleCompleted = { viewModel.markAsCompleted(reminder) },
                            onDelete = { reminderToDelete = reminder }
                        )
                    }
                }
            }
        }
    }

    reminderToDelete?.let { reminder ->
        AlertDialog(
            onDismissRequest = { reminderToDelete = null },
            title = { Text("Excluir lembrete?") },
            text = { Text("Tem certeza que deseja excluir \"${reminder.title}\"? Essa ação não pode ser desfeita.") },
            confirmButton = {
                TextButton(onClick = {
                    viewModel.deleteReminder(reminder)
                    reminderToDelete = null
                }) {
                    Text("Excluir", color = GranaXPColors.Red600)
                }
            },
            dismissButton = {
                TextButton(onClick = { reminderToDelete = null }) { Text("Cancelar") }
            }
        )
    }
}
@Composable
private fun ReminderCard(
    reminder: ReminderModel,
    onToggleCompleted: () -> Unit,
    onDelete: () -> Unit
) {
    val isOverdue = !reminder.isCompleted && isDateOverdue(reminder.date)

    val (bg, border) = when {
        reminder.isCompleted -> GranaXPColors.White to GranaXPColors.Gray100
        isOverdue -> GranaXPColors.Rose50 to GranaXPColors.Rose300
        else -> GranaXPColors.White to GranaXPColors.Gray100
    }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .alpha(if (reminder.isCompleted) 0.6f else 1f)
            .background(bg, RoundedCornerShape(12.dp))
            .border(BorderStroke(1.dp, border), RoundedCornerShape(12.dp))
            .padding(12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        // Ícone de concluir com animação de troca
        AnimatedContent(
            targetState = reminder.isCompleted,
            transitionSpec = {
                (scaleIn(initialScale = 0.6f) togetherWith scaleOut(targetScale = 0.6f))
            },
            label = "reminderCheckAnim"
        ) { completed ->
            IconButton(onClick = onToggleCompleted, enabled = !completed, modifier = Modifier.size(24.dp)) {
                Icon(
                    if (completed) Icons.Default.CheckCircle else Icons.Default.Circle,
                    contentDescription = null,
                    tint = if (completed) GranaXPColors.Emerald500 else GranaXPColors.Gray300
                )
            }
        }
        Spacer(Modifier.width(8.dp))
        Column(Modifier.weight(1f)) {
            Text(
                reminder.title,
                fontSize = 13.sp,
                fontWeight = FontWeight.SemiBold,
                textDecoration = if (reminder.isCompleted) TextDecoration.LineThrough else TextDecoration.None,
                color = if (reminder.isCompleted) GranaXPColors.Gray500 else GranaXPColors.Gray800
            )
            Spacer(Modifier.height(2.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    Icons.Default.CalendarMonth,
                    contentDescription = null,
                    tint = if (isOverdue) GranaXPColors.Rose500 else GranaXPColors.Gray400,
                    modifier = Modifier.size(11.dp)
                )
                Spacer(Modifier.width(4.dp))
                Text(
                    "Vence dia ${formatDay(reminder.date)}",
                    fontSize = 11.sp,
                    fontWeight = if (isOverdue) FontWeight.Medium else FontWeight.Normal,
                    color = if (isOverdue) GranaXPColors.Rose600 else GranaXPColors.Gray500
                )
            }
        }
        Text(
            "R$ %.2f".format(reminder.amount),
            fontWeight = FontWeight.Bold,
            fontSize = 13.sp,
            color = if (reminder.isCompleted) GranaXPColors.Gray400 else GranaXPColors.Gray800
        )
        IconButton(onClick = onDelete, modifier = Modifier.size(28.dp)) {
            Icon(Icons.Default.Delete, contentDescription = "Deletar", tint = GranaXPColors.Red600, modifier = Modifier.size(15.dp))
        }
    }
}

private fun isDateOverdue(timestamp: Timestamp): Boolean {
    return timestamp.toDate().before(java.util.Date())
}

private fun formatDay(timestamp: Timestamp): String {
    val sdf = java.text.SimpleDateFormat("dd 'de' MMM", java.util.Locale("pt", "BR"))
    return sdf.format(timestamp.toDate())
}

@Composable
fun AddReminderDialog(
    viewModel: ReminderViewModel,
    onDismiss: () -> Unit
) {
    var title by remember { mutableStateOf("") }
    var amount by remember { mutableStateOf("") }
    var dateText by remember { mutableStateOf("") }
    var dateError by remember { mutableStateOf<String?>(null) }

    com.hnrzzin.granaxp.ui.theme.components.AppModalBottomSheet(onDismiss = onDismiss) {
        com.hnrzzin.granaxp.ui.theme.components.AppModalHeader(title = "Novo Lembrete de Pagamento", onClose = onDismiss)

        Text(
            "Ao concluir este lembrete, o valor será descontado do seu saldo atual.",
            fontSize = 11.sp,
            color = GranaXPColors.Gray500
        )
        OutlinedTextField(
            value = title,
            onValueChange = { title = it },
            label = { Text("Descrição do Pagamento") },
            placeholder = { Text("Ex: Fatura Cartão de Crédito") },
            modifier = Modifier.fillMaxWidth(),
            colors = OutlinedTextFieldDefaults.colors(focusedBorderColor = GranaXPColors.Rose600)
        )



        Spacer(Modifier.height(12.dp))

        OutlinedTextField(
            value = amount,
            onValueChange = { input -> amount = input.filter { it.isDigit() } },
            label = { Text("Valor (R$)") },
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
            visualTransformation = CurrencyVisualTransformation(),
            modifier = Modifier.fillMaxWidth(),
            colors = OutlinedTextFieldDefaults.colors(focusedBorderColor = GranaXPColors.Rose600)
        )

        Spacer(Modifier.height(12.dp))

        OutlinedTextField(
            value = dateText,
            onValueChange = { input ->
                dateText = input.filter { it.isDigit() }.take(8)
                dateError = null // limpa o erro ao editar de novo
            },
            label = { Text("Data de Vencimento") },
            placeholder = { Text("dd/mm/aaaa") },
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
            visualTransformation = DateVisualTransformation(),
            modifier = Modifier.fillMaxWidth(),
            colors = OutlinedTextFieldDefaults.colors(focusedBorderColor = GranaXPColors.Rose600)
        )

        dateError?.let {
            Spacer(Modifier.height(4.dp))
            Text(it, fontSize = 11.sp, color = GranaXPColors.Error)
        }

        Spacer(Modifier.height(24.dp))

        Button(
            onClick = {
                when (val result = DateUtils.validateFutureDate(dateText)) {
                    is DateUtils.DateValidationResult.Valid -> {
                        viewModel.createReminder(
                            title = title,
                            description = "",
                            amount = rawDigitsToAmount(amount),
                            date = result.timestamp,
                            time = ""
                        )
                        onDismiss()
                    }
                    DateUtils.DateValidationResult.InvalidFormat -> {
                        dateError = "Verifique se a data está correta."
                    }
                    DateUtils.DateValidationResult.PastDate -> {
                        dateError = "A data não pode ser no passado."
                    }
                }
            },
            enabled = title.isNotBlank() && amount.isNotBlank() && dateText.length == 8,
            modifier = Modifier.fillMaxWidth().height(52.dp),
            colors = ButtonDefaults.buttonColors(containerColor = GranaXPColors.Rose600),
            shape = RoundedCornerShape(8.dp)
        ) {
            Text("Agendar Lembrete", color = GranaXPColors.White, fontSize = 16.sp, fontWeight = FontWeight.SemiBold)
        }
    }
}

private fun parseDateToTimestamp(rawDigits: String): Timestamp? {
    if (rawDigits.length != 8) return null
    return try {
        val day = rawDigits.substring(0, 2)
        val month = rawDigits.substring(2, 4)
        val year = rawDigits.substring(4, 8)
        val sdf = java.text.SimpleDateFormat("dd/MM/yyyy", java.util.Locale("pt", "BR"))
        Timestamp(sdf.parse("$day/$month/$year")!!)
    } catch (e: Exception) {
        null
    }
}

