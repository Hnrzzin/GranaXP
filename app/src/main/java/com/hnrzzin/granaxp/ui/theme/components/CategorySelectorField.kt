package com.hnrzzin.granaxp.ui.theme.components

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp

val TransactionCategories = listOf(
    "Alimentação",
    "Transporte",
    "Moradia",
    "Lazer",
    "Saúde",
    "Educação",
    "Roupas",
    "Assinaturas",
    "Compras"
)

private const val OUTRA_OPTION = "Outra..."

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CategorySelectorField(
    selectedCategory: String,
    onCategoryChange: (String) -> Unit,
    focusColor: Color,
    modifier: Modifier = Modifier
) {
    var expanded by remember { mutableStateOf(false) }
    val startsAsCustom = selectedCategory.isNotBlank() && selectedCategory !in TransactionCategories
    var isCustomMode by remember { mutableStateOf(startsAsCustom) }
    var customText by remember { mutableStateOf(if (startsAsCustom) selectedCategory else "") }

    Column(modifier = modifier) {
        ExposedDropdownMenuBox(
            expanded = expanded,
            onExpandedChange = { expanded = !expanded }
        ) {
            OutlinedTextField(
                value = if (isCustomMode) OUTRA_OPTION else selectedCategory,
                onValueChange = {},
                readOnly = true,
                label = { Text("Categoria") },
                trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expanded) },
                modifier = Modifier.menuAnchor().fillMaxWidth(),
                colors = OutlinedTextFieldDefaults.colors(focusedBorderColor = focusColor)
            )
            ExposedDropdownMenu(
                expanded = expanded,
                onDismissRequest = { expanded = false }
            ) {
                TransactionCategories.forEach { option ->
                    DropdownMenuItem(
                        text = { Text(option) },
                        onClick = {
                            isCustomMode = false
                            onCategoryChange(option)
                            expanded = false
                        }
                    )
                }
                DropdownMenuItem(
                    text = { Text(OUTRA_OPTION) },
                    onClick = {
                        isCustomMode = true
                        onCategoryChange(customText)
                        expanded = false
                    }
                )
            }
        }

        if (isCustomMode) {
            Spacer(Modifier.height(8.dp))
            OutlinedTextField(
                value = customText,
                onValueChange = {
                    customText = it
                    onCategoryChange(it)
                },
                label = { Text("Digite a categoria") },
                placeholder = { Text("Ex: Pets") },
                modifier = Modifier.fillMaxWidth(),
                colors = OutlinedTextFieldDefaults.colors(focusedBorderColor = focusColor)
            )
        }
    }
}