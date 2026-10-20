package com.hnrzzin.granaxp.ui.theme.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AccountCircle
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.hnrzzin.granaxp.ui.theme.GranaXPColors

@Composable
fun HomeTopBar(
    level: Int,
    xp: Int,
    nextLevelXp: Int,
    currentStreak: Int = 0,
    onProfileClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(GranaXPColors.Emerald600)
            .padding(horizontal = 16.dp, vertical = 16.dp)
            .statusBarsPadding(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        // Esquerda: nome do app
        Column(Modifier.weight(1f).padding(end = 8.dp)) {
            Text(
                "GranaXP",
                color = GranaXPColors.White,
                fontWeight = FontWeight.Bold,
                fontSize = 18.sp,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                "Gestão & Aprendizado",
                color = GranaXPColors.Emerald100,
                fontSize = 12.sp,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }

        // Direita: nível + XP bar + botão de perfil
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            Column(horizontalAlignment = Alignment.End) {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(
                        "🔥 ${currentStreak.coerceAtLeast(0)}",
                        color = GranaXPColors.White,
                        fontSize = 12.sp,
                        maxLines = 1,
                        modifier = Modifier.semantics {
                            contentDescription = "Sequência diária: ${currentStreak.coerceAtLeast(0)}"
                        },
                    )
                    Text(
                        "Nível $level",
                        color = GranaXPColors.White,
                        fontWeight = FontWeight.SemiBold,
                        fontSize = 12.sp,
                    )
                }
                Spacer(Modifier.height(4.dp))
                LinearProgressIndicator(
                    progress = { if (nextLevelXp > 0) (xp.toFloat() / nextLevelXp.toFloat()).coerceIn(0f, 1f) else 0f },
                    modifier = Modifier
                        .width(80.dp)
                        .height(8.dp)
                        .clip(CircleShape),
                    color = GranaXPColors.Yellow400,
                    trackColor = GranaXPColors.Emerald800
                )
            }

            Box(
                modifier = Modifier
                    .size(34.dp)
                    .clip(CircleShape)
                    .background(GranaXPColors.Emerald500)
                    .padding(1.dp),
                contentAlignment = Alignment.Center
            ) {
                IconButton(onClick = onProfileClick, modifier = Modifier.size(34.dp)) {
                    Icon(
                        Icons.Default.AccountCircle,
                        contentDescription = "Perfil",
                        tint = GranaXPColors.White,
                        modifier = Modifier.size(18.dp)
                    )
                }
            }
        }
    }
}
