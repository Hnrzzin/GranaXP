package com.hnrzzin.granaxp.ui.theme.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.hnrzzin.granaxp.ui.theme.GranaXPColors

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AppModalBottomSheet(
    onDismiss: () -> Unit,
    content: @Composable ColumnScope.() -> Unit
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = GranaXPColors.White,
        shape = RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp),
        dragHandle = { AppDragHandle() },

    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .imePadding() // ✅ empurra o conteúdo para cima do teclado
                .navigationBarsPadding()
                .padding(horizontal = 24.dp)
                .padding(bottom = 24.dp),
            content = content
        )
    }
}

@Composable
private fun AppDragHandle() {
    Box(
        modifier = Modifier.fillMaxWidth().padding(vertical = 12.dp),
        contentAlignment = Alignment.TopCenter
    ) {
        Box(
            modifier = Modifier
                .width(32.dp)
                .height(4.dp)
                .background(GranaXPColors.Gray300, RoundedCornerShape(2.dp))
        )
    }
}