package com.hnrzzin.granaxp.ui.theme.components

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.google.firebase.Timestamp
import com.hnrzzin.granaxp.model.GoalDeadline
import com.hnrzzin.granaxp.ui.theme.GranaXPColors
import com.hnrzzin.granaxp.viewmodel.GoalActionState
import com.hnrzzin.granaxp.viewmodel.GoalViewModel
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Locale

private val goalIcons = listOf(
    Icons.Default.FlightTakeoff, Icons.Default.DirectionsCar, Icons.Default.Home,
    Icons.Default.School, Icons.Default.Savings, Icons.Default.Celebration,
    Icons.Default.Favorite, Icons.Default.Star
)

@Composable
fun AddGoalSheet(
    viewModel: GoalViewModel,
    onDismiss: () -> Unit
) {
    val actionState by viewModel.actionState.collectAsState()
    val coroutineScope = rememberCoroutineScope()

    var deadlineType by remember { mutableStateOf(GoalDeadline.CURTO) }
    var title by remember { mutableStateOf("") }
    var targetAmount by remember { mutableStateOf("") }
    var currentAmount by remember { mutableStateOf("") }
    var deadlineDate by remember { mutableStateOf("") }
    var alreadyDeclared by remember { mutableStateOf(false) }
    var selectedIconIndex by remember { mutableIntStateOf(0) } // visual apenas, não persiste ainda

    // Shake effect state — um Animatable por campo obrigatório
    val titleShake = remember { Animatable(0f) }
    val targetShake = remember { Animatable(0f) }
    val deadlineShake = remember { Animatable(0f) }

    suspend fun shake(animatable: Animatable<Float, *>) {
        animatable.animateTo(12f, tween(50))
        animatable.animateTo(-12f, tween(50))
        animatable.animateTo(8f, tween(50))
        animatable.animateTo(0f, tween(50))
    }

    LaunchedEffect(actionState) {
        if (actionState is GoalActionState.Success) {
            viewModel.resetActionState()
            onDismiss()
        }
    }

    fun validateAndSave() {
        var hasError = false
        if (title.isBlank()) { coroutineScope.launch { shake(titleShake) }; hasError = true }
        if (targetAmount.toDoubleOrNull() == null || targetAmount.toDoubleOrNull() == 0.0) {
            coroutineScope.launch { shake(targetShake) }; hasError = true
        }
        if (deadlineDate.isBlank()) { coroutineScope.launch { shake(deadlineShake) }; hasError = true }

        if (!hasError) {
            viewModel.createGoal(
                title = title,
                targetAmount = targetAmount.toDoubleOrNull() ?: 0.0,
                currentAmount = currentAmount.toDoubleOrNull() ?: 0.0,
                deadline = deadlineType,
                alreadyDeclared = alreadyDeclared,
                deadlineDate = parseDateOrNull(deadlineDate)
            )
        }
    }

    Scaffold(
        containerColor = GranaXPColors.White,
        topBar = {
            Row(
                modifier = Modifier.fillMaxWidth().padding(16.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(onClick = onDismiss) {
                    Icon(Icons.Default.Close, contentDescription = "Fechar", tint = GranaXPColors.Gray700)
                }
                Spacer(Modifier.width(8.dp))
                Text("Nova Meta Financeira", fontSize = 18.sp, fontWeight = FontWeight.Bold, color = GranaXPColors.Gray800)
            }
        }
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .imePadding()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(20.dp)
        ) {
            // Prazo — segmented
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                GoalDeadline.entries.filter { it != GoalDeadline.MEDIO }.forEach { deadline ->
                    val selected = deadlineType == deadline
                    Surface(
                        modifier = Modifier.weight(1f).clip(RoundedCornerShape(50)),
                        color = if (selected) GranaXPColors.Purple50 else GranaXPColors.Gray100,
                        border = BorderStroke(1.dp, if (selected) GranaXPColors.Purple500 else GranaXPColors.Gray100),
                        shape = androidx.compose.foundation.shape.RoundedCornerShape(50)
                    ) {
                        Box(
                            modifier = Modifier
                                .clickableNoRipple { deadlineType = deadline }
                                .padding(vertical = 12.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                if (deadline == GoalDeadline.CURTO) "Curto Prazo" else "Longo Prazo",
                                color = if (selected) GranaXPColors.Purple600 else GranaXPColors.Gray600,
                                fontWeight = FontWeight.Medium,
                                fontSize = 14.sp
                            )
                        }
                    }
                }
            }

            // Nome da meta
            OutlinedTextField(
                value = title,
                onValueChange = { title = it },
                label = { Text("O que você quer alcançar?") },
                placeholder = { Text("Ex: Trocar de Carro") },
                modifier = Modifier
                    .fillMaxWidth()
                    .offset(x = titleShake.value.dp),
                colors = OutlinedTextFieldDefaults.colors(focusedBorderColor = GranaXPColors.Purple500)
            )

            // Seletor de ícone
            Column {
                Text("Escolha um ícone", fontSize = 12.sp, color = GranaXPColors.Gray500, fontWeight = FontWeight.Medium)
                Spacer(Modifier.height(8.dp))
                LazyRow(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    itemsIndexed(goalIcons) { index, icon ->
                        val selected = selectedIconIndex == index
                        Box(
                            modifier = Modifier
                                .size(48.dp)
                                .clip(CircleShape)
                                .background(if (selected) GranaXPColors.Purple100 else GranaXPColors.Gray100)
                                .border(
                                    width = if (selected) 2.dp else 0.dp,
                                    color = GranaXPColors.Purple500,
                                    shape = CircleShape
                                )
                                .clickableNoRipple { selectedIconIndex = index },
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                icon,
                                contentDescription = null,
                                tint = if (selected) GranaXPColors.Purple600 else GranaXPColors.Gray500,
                                modifier = Modifier.size(22.dp)
                            )
                        }
                    }
                }
            }

            // Valores
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(
                    value = targetAmount,
                    onValueChange = { targetAmount = it },
                    label = { Text("Valor Alvo (R$)") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    modifier = Modifier.weight(1f).offset(x = targetShake.value.dp),
                    colors = OutlinedTextFieldDefaults.colors(focusedBorderColor = GranaXPColors.Purple500)
                )
                OutlinedTextField(
                    value = currentAmount,
                    onValueChange = { currentAmount = it },
                    label = { Text("Já Guardado (R$)") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    modifier = Modifier.weight(1f),
                    colors = OutlinedTextFieldDefaults.colors(focusedBorderColor = GranaXPColors.Purple500)
                )
            }

            // Regra de negócio #1 — só relevante se já declarou valor inicial
            if (currentAmount.toDoubleOrNull()?.let { it > 0.0 } == true) {
                Row(
                    Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text("Esse valor já foi declarado como receita?", fontSize = 13.sp, modifier = Modifier.weight(1f), color = GranaXPColors.Gray700)
                    Switch(
                        checked = alreadyDeclared,
                        onCheckedChange = { alreadyDeclared = it },
                        colors = SwitchDefaults.colors(checkedTrackColor = GranaXPColors.Primary)
                    )
                }
                if (!alreadyDeclared) {
                    Text(
                        "Se não, o valor inicial não será somado à meta agora.",
                        fontSize = 11.sp,
                        color = GranaXPColors.Error
                    )
                }
            }

            // Data limite
            OutlinedTextField(
                value = deadlineDate,
                onValueChange = { deadlineDate = it },
                label = { Text("Data Limite") },
                placeholder = { Text("dd/mm/aaaa") },
                leadingIcon = { Icon(Icons.Default.CalendarMonth, contentDescription = null, tint = GranaXPColors.Purple500) },
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                modifier = Modifier
                    .fillMaxWidth()
                    .offset(x = deadlineShake.value.dp),
                colors = OutlinedTextFieldDefaults.colors(focusedBorderColor = GranaXPColors.Purple500)
            )

            if (actionState is GoalActionState.Error) {
                Text(
                    (actionState as GoalActionState.Error).message,
                    color = GranaXPColors.Error,
                    fontSize = 12.sp
                )
            }

            Spacer(Modifier.height(8.dp))

            Button(
                onClick = { validateAndSave() },
                enabled = actionState !is GoalActionState.Loading,
                modifier = Modifier.fillMaxWidth().height(52.dp),
                colors = ButtonDefaults.buttonColors(containerColor = GranaXPColors.Purple600),
                shape = androidx.compose.foundation.shape.RoundedCornerShape(8.dp)
            ) {
                if (actionState is GoalActionState.Loading) {
                    CircularProgressIndicator(color = GranaXPColors.White, modifier = Modifier.size(20.dp))
                } else {
                    Text("Salvar Meta", color = GranaXPColors.White, fontSize = 16.sp, fontWeight = FontWeight.SemiBold)
                }
            }

            Spacer(Modifier.height(24.dp))
        }
    }
}

private fun parseDateOrNull(dateText: String): Timestamp? {
    return try {
        val sdf = SimpleDateFormat("dd/MM/yyyy", Locale("pt", "BR"))
        Timestamp(sdf.parse(dateText)!!)
    } catch (e: Exception) {
        null
    }
}

// Clique sem ripple, para os seletores tipo chip/ícone
@Composable
private fun Modifier.clickableNoRipple(onClick: () -> Unit): Modifier = this.then(
    Modifier.clickable(
        indication = null,
        interactionSource = remember { MutableInteractionSource() },
        onClick = onClick
    )
)