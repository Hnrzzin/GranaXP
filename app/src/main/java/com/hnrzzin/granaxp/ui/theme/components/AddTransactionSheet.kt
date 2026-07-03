package com.hnrzzin.granaxp.ui.theme.screens

import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.OffsetMapping
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.text.input.TransformedText
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.hnrzzin.granaxp.model.TransactionType
import com.hnrzzin.granaxp.ui.theme.GranaXPColors
import com.hnrzzin.granaxp.ui.theme.components.AppModalBottomSheet
import com.hnrzzin.granaxp.ui.theme.components.AppModalHeader
import com.hnrzzin.granaxp.viewmodel.TransactionSaveState
import com.hnrzzin.granaxp.viewmodel.TransactionViewModel
import java.text.NumberFormat
import java.util.Locale

@Composable
fun AddTransactionSheet(
    viewModel: TransactionViewModel,
    onDismiss: () -> Unit
) {
    val saveState by viewModel.saveState.collectAsState()

    var type by remember { mutableStateOf(TransactionType.DESPESA) }
    var amountCents by remember { mutableStateOf(0L) } // valor em centavos, evita erro de ponto flutuante
    var title by remember { mutableStateOf("") }
    var category by remember { mutableStateOf("") }

    LaunchedEffect(saveState) {
        if (saveState is TransactionSaveState.Success) {
            onDismiss()
        }
    }

    val typeColor = if (type == TransactionType.DESPESA) GranaXPColors.Red500 else GranaXPColors.Primary

    AppModalBottomSheet(onDismiss = onDismiss) {
        AppModalHeader(title = "Nova Transação", onClose = onDismiss)

        // Toggle Despesa/Receita com fundo deslizante animado
        SegmentedTypeToggle(
            selected = type,
            onSelect = { type = it }
        )

        Spacer(Modifier.height(24.dp))

        // Hero Input — valor gigante, centralizado, sem borda
        HeroAmountInput(
            amountCents = amountCents,
            onAmountChange = { amountCents = it },
            color = typeColor
        )

        Spacer(Modifier.height(24.dp))

        OutlinedTextField(
            value = title,
            onValueChange = { title = it },
            label = { Text("Título") },
            placeholder = { Text("Ex: Compra no supermercado") },
            modifier = Modifier.fillMaxWidth(),
            colors = OutlinedTextFieldDefaults.colors(focusedBorderColor = typeColor)
        )

        Spacer(Modifier.height(12.dp))

        OutlinedTextField(
            value = category,
            onValueChange = { category = it },
            label = { Text("Categoria") },
            placeholder = { Text("Ex: Alimentação") },
            modifier = Modifier.fillMaxWidth(),
            colors = OutlinedTextFieldDefaults.colors(focusedBorderColor = typeColor)
        )

        if (saveState is TransactionSaveState.Error) {
            Spacer(Modifier.height(8.dp))
            Text(
                (saveState as TransactionSaveState.Error).message,
                color = GranaXPColors.Error,
                fontSize = 12.sp
            )
        }

        Spacer(Modifier.height(24.dp))

        Button(
            onClick = {
                viewModel.createTransaction(
                    title = title,
                    amount = amountCents / 100.0,
                    type = type,
                    category = category
                )
            },
            enabled = saveState !is TransactionSaveState.Saving && amountCents > 0 && title.isNotBlank(),
            modifier = Modifier.fillMaxWidth().height(52.dp),
            colors = ButtonDefaults.buttonColors(containerColor = typeColor),
            shape = RoundedCornerShape(8.dp)
        ) {
            if (saveState is TransactionSaveState.Saving) {
                CircularProgressIndicator(color = GranaXPColors.White, modifier = Modifier.size(20.dp))
            } else {
                Text("Salvar Transação", color = GranaXPColors.White, fontSize = 16.sp, fontWeight = FontWeight.SemiBold)
            }
        }
    }
}

// ---------- Toggle animado ----------

@Composable
private fun SegmentedTypeToggle(
    selected: TransactionType,
    onSelect: (TransactionType) -> Unit
) {
    BoxWithConstraints(
        modifier = Modifier
            .fillMaxWidth()
            .height(44.dp)
            .background(GranaXPColors.Gray100, RoundedCornerShape(50))
            .padding(4.dp)
    ) {
        val segmentWidth = maxWidth / 2
        val offset by animateDpAsState(
            targetValue = if (selected == TransactionType.DESPESA) 0.dp else segmentWidth,
            animationSpec = tween(durationMillis = 200),
            label = "toggle_offset"
        )
        val slideColor = if (selected == TransactionType.DESPESA) GranaXPColors.Red500 else GranaXPColors.Primary

        Box(
            modifier = Modifier
                .offset(x = offset)
                .width(segmentWidth)
                .fillMaxHeight()
                .background(slideColor, RoundedCornerShape(50))
        )

        Row(modifier = Modifier.fillMaxSize()) {
            listOf(TransactionType.DESPESA, TransactionType.RECEITA).forEach { t ->
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxHeight()
                        .clickable(
                            indication = null,
                            interactionSource = remember { MutableInteractionSource() }
                        ) { onSelect(t) },
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        if (t == TransactionType.DESPESA) "Despesa" else "Receita",
                        color = if (selected == t) GranaXPColors.White else GranaXPColors.Gray600,
                        fontSize = 14.sp,
                        fontWeight = FontWeight.SemiBold
                    )
                }
            }
        }
    }
}

// ---------- Hero Input com máscara de moeda ----------

@Composable
private fun HeroAmountInput(
    amountCents: Long,
    onAmountChange: (Long) -> Unit,
    color: Color
) {
    val focusRequester = remember { FocusRequester() }

    LaunchedEffect(Unit) {
        focusRequester.requestFocus() // auto-focus ao abrir o sheet
    }

    Column(
        modifier = Modifier.fillMaxWidth(),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text("Valor", fontSize = 12.sp, color = GranaXPColors.Gray500)
        Spacer(Modifier.height(4.dp))
        BasicTextField(
            value = TextFieldValue(
                text = amountCents.toString(),
                selection = TextRange(amountCents.toString().length)
            ),
            onValueChange = { newValue ->
                val digitsOnly = newValue.text.filter { it.isDigit() }
                val cents = digitsOnly.toLongOrNull() ?: 0L
                onAmountChange(cents.coerceAtMost(999_999_999L)) // limite de sanidade
            },
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
            textStyle = androidx.compose.ui.text.TextStyle(
                fontSize = 40.sp,
                fontWeight = FontWeight.Bold,
                color = color,
                textAlign = androidx.compose.ui.text.style.TextAlign.Center
            ),
            visualTransformation = CurrencyVisualTransformation(),
            modifier = Modifier
                .fillMaxWidth()
                .focusRequester(focusRequester)
        )
    }
}

// Formata centavos (Long) como "R$ 10,05" enquanto o usuário digita
private class CurrencyVisualTransformation : VisualTransformation {
    private val formatter = NumberFormat.getCurrencyInstance(Locale("pt", "BR"))

    override fun filter(text: androidx.compose.ui.text.AnnotatedString): TransformedText {
        val cents = text.text.toLongOrNull() ?: 0L
        val formatted = formatter.format(cents / 100.0)

        return TransformedText(
            androidx.compose.ui.text.AnnotatedString(formatted),
            object : OffsetMapping {
                override fun originalToTransformed(offset: Int) = formatted.length
                override fun transformedToOriginal(offset: Int) = text.text.length
            }
        )
    }
}