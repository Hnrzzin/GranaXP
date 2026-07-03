package com.hnrzzin.granaxp.ui.theme.screens

import androidx.compose.foundation.background
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
import com.hnrzzin.granaxp.viewmodel.ReminderUiState
import com.hnrzzin.granaxp.viewmodel.ReminderViewModel

@Composable
fun ReminderContent(viewModel: ReminderViewModel) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()

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
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    item {
                        Text("Próximos Vencimentos", fontSize = 14.sp, fontWeight = FontWeight.Medium, color = GranaXPColors.Gray500)
                    }
                    items(state.reminders, key = { it.id }) { reminder ->
                        ReminderCard(reminder = reminder, onToggleCompleted = { viewModel.markAsCompleted(reminder) })
                    }
                }
            }
        }
    }
}

@Composable
private fun ReminderCard(
    reminder: ReminderModel,
    onToggleCompleted: () -> Unit
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
            .background(bg, RoundedCornerShape(12.dp))
            .then(Modifier.background(androidx.compose.ui.graphics.Color.Transparent))
            .let { base ->
                base
            }
            .alpha(if (reminder.isCompleted) 0.6f else 1f)
            .padding(1.dp)
            .background(bg, RoundedCornerShape(12.dp))
            .padding(16.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        IconButton(onClick = onToggleCompleted, enabled = !reminder.isCompleted, modifier = Modifier.size(28.dp)) {
            Icon(
                if (reminder.isCompleted) Icons.Default.CheckCircle else Icons.Default.Circle,
                contentDescription = null,
                tint = if (reminder.isCompleted) GranaXPColors.Emerald500 else GranaXPColors.Gray300
            )
        }
        Spacer(Modifier.width(10.dp))
        Column(Modifier.weight(1f)) {
            Text(
                reminder.title,
                fontSize = 14.sp,
                fontWeight = FontWeight.SemiBold,
                textDecoration = if (reminder.isCompleted) TextDecoration.LineThrough else TextDecoration.None,
                color = if (reminder.isCompleted) GranaXPColors.Gray500 else GranaXPColors.Gray800
            )
            Spacer(Modifier.height(2.dp))
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(bg, RoundedCornerShape(12.dp))
                    .alpha(if (reminder.isCompleted) 0.6f else 1f)
                    .padding(16.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    Icons.Default.CalendarMonth,
                    contentDescription = null,
                    tint = if (isOverdue) GranaXPColors.Rose500 else GranaXPColors.Gray400,
                    modifier = Modifier.size(12.dp)
                )
                Spacer(Modifier.width(4.dp))
                Text(
                    "Vence dia ${formatDay(reminder.date)}",
                    fontSize = 12.sp,
                    fontWeight = if (isOverdue) FontWeight.Medium else FontWeight.Normal,
                    color = if (isOverdue) GranaXPColors.Rose600 else GranaXPColors.Gray500
                )
            }
        }
        Text(
            "R$ %.2f".format(reminder.amount),
            fontWeight = FontWeight.Bold,
            fontSize = 14.sp,
            color = if (reminder.isCompleted) GranaXPColors.Gray400 else GranaXPColors.Gray800
        )
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
    var dateText by remember { mutableStateOf("") } // formato dd/mm/aaaa

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Novo Lembrete de Pagamento") },
        text = {
            Column(
                modifier = Modifier.verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                OutlinedTextField(
                    value = title,
                    onValueChange = { title = it },
                    label = { Text("Descrição do Pagamento") },
                    placeholder = { Text("Ex: Fatura Cartão de Crédito") },
                    modifier = Modifier.fillMaxWidth()
                )
                OutlinedTextField(
                    value = amount,
                    onValueChange = { amount = it },
                    label = { Text("Valor (R$)") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    modifier = Modifier.fillMaxWidth()
                )
                OutlinedTextField(
                    value = dateText,
                    onValueChange = { dateText = it },
                    label = { Text("Data de Vencimento") },
                    placeholder = { Text("dd/mm/aaaa") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    modifier = Modifier.fillMaxWidth()
                )
            }
        },
        confirmButton = {
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
                colors = ButtonDefaults.buttonColors(containerColor = GranaXPColors.Error)
            ) {
                Text("Agendar Lembrete")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancelar") }
        }
    )
}

private fun parseDateToTimestamp(dateText: String): Timestamp? {
    return try {
        val sdf = java.text.SimpleDateFormat("dd/MM/yyyy", java.util.Locale("pt", "BR"))
        Timestamp(sdf.parse(dateText)!!)
    } catch (e: Exception) {
        null
    }
}