package com.hnrzzin.granaxp.ui.theme.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.hnrzzin.granaxp.ui.theme.GranaXPColors

@Composable
fun AppModalHeader(title: String, onClose: () -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(bottom = 16.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(title, fontSize = 18.sp, fontWeight = FontWeight.Bold, color = GranaXPColors.Gray800)
        Box(
            modifier = Modifier
                .size(28.dp)
                .background(GranaXPColors.Gray100, CircleShape),
            contentAlignment = Alignment.Center
        ) {
            IconButton(onClick = onClose, modifier = Modifier.size(28.dp)) {
                Icon(Icons.Default.Close, contentDescription = "Fechar", tint = GranaXPColors.Gray600, modifier = Modifier.size(16.dp))
            }
        }
    }
}