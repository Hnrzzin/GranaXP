package com.hnrzzin.granaxp.ui.theme.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.Lightbulb
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.hnrzzin.granaxp.ui.theme.GranaXPColors

@Composable
fun InsightCard(
    title: String,
    description: String,
    actionText: String,
    onLearnMoreClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = GranaXPColors.Blue50,
            contentColor = GranaXPColors.Blue900
        ),
        border = BorderStroke(1.dp, GranaXPColors.Blue100),
        shape = RoundedCornerShape(16.dp)
    ) {
        Column(
            modifier = Modifier.padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            // Header: icon box + título
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Box(
                    modifier = Modifier
                        .size(28.dp)
                        .background(GranaXPColors.Blue100, RoundedCornerShape(8.dp)),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        Icons.Filled.Lightbulb,
                        contentDescription = null,
                        tint = GranaXPColors.Blue600,
                        modifier = Modifier.size(18.dp)
                    )
                }
                Text(
                    text = title,
                    fontSize = 16.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = GranaXPColors.Blue900
                )
            }

            Spacer(Modifier.height(8.dp))

            Text(
                text = description,
                fontSize = 14.sp,
                lineHeight = 20.sp,
                color = GranaXPColors.Blue900
            )

            Spacer(Modifier.height(8.dp))

            // Link "Aprender mais sobre isso" + chevron
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable(onClick = onLearnMoreClick),
                horizontalArrangement = Arrangement.Start,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = actionText,
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Medium,
                    color = GranaXPColors.Blue700
                )
                Icon(
                    Icons.AutoMirrored.Filled.KeyboardArrowRight,
                    contentDescription = null,
                    tint = GranaXPColors.Blue700,
                    modifier = Modifier.size(16.dp)
                )
            }
        }
    }
}

@Preview(showBackground = true)
@Composable
fun InsightCardPreview() {
    MaterialTheme {
        InsightCard(
            title = "Reserva de Emergência",
            description = "Guardar uma parte da sua mesada ou salário do estágio garante tranquilidade em imprevistos.",
            actionText = "Aprender mais",
            onLearnMoreClick = {}
        )
    }
}
