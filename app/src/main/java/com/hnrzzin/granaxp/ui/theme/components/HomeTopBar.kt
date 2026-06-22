package com.hnrzzin.granaxp.ui.theme.components

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AccountCircle
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.hnrzzin.granaxp.ui.theme.GranaXPTheme

/**
 * Componente de progresso de experiência (XP) do usuário de forma horizontal.
 *
 * @param progress Valor de 0.0 a 1.0 representando o progresso do nível atual.
 */
@Composable
fun XPProgressBar(
    progress: Float,
    modifier: Modifier = Modifier
) {
    LinearProgressIndicator(
        progress = { progress.coerceIn(0f, 1f) },
        modifier = modifier
            .height(6.dp)
            .clip(RoundedCornerShape(3.dp)),
        color = MaterialTheme.colorScheme.secondary, // Cor Ouro/Laranja de Gamificação
        trackColor = Color.White.copy(alpha = 0.3f)  // Fundo contrastante sutil sobre o verde
    )
}

/**
 * Bloco que agrupa a identificação do nível textual e a barra de progresso do XP.
 *
 * @param level Nível atual obtido do modelo do usuário.
 * @param progress Float do progresso de preenchimento do nível.
 */
@Composable
fun UserLevelBadge(
    level: Int,
    progress: Float,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier,
        horizontalAlignment = Alignment.End,
        verticalArrangement = Arrangement.Center
    ) {
        Text(
            text = "Nível $level",
            style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold),
            color = MaterialTheme.colorScheme.onPrimary
        )
        Spacer(modifier = Modifier.height(4.dp))
        XPProgressBar(
            progress = progress,
            modifier = Modifier.width(80.dp) // Largura fixa proporcional ao protótipo
        )
    }
}

/**
 * Componente principal da barra superior (TopBar) customizada para a HomeScreen.
 * Alinha a identidade visual do app à esquerda e o status de gamificação com avatar à direita.
 *
 * @param level Nível atual do usuário.
 * @param xp Pontuação de experiência atual no nível.
 * @param nextLevelXp Total de XP necessário para subir de nível.
 * @param onProfileClick Callback acionado ao clicar no avatar do usuário.
 * @param title Nome principal ou saudação exibida no topo esquerdo.
 * @param subtitle Mensagem complementar logo abaixo do título.
 */
@Composable
fun HomeTopBar(
    level: Int,
    xp: Int,
    nextLevelXp: Int,
    onProfileClick: () -> Unit,
    modifier: Modifier = Modifier,
    title: String = "FinEdu",
    subtitle: String = "Gestão & Aprendizado"
) {
    // Cálculo seguro do percentual de progresso
    val progress = if (nextLevelXp > 0) xp.toFloat() / nextLevelXp else 0f

    Surface(
        color = MaterialTheme.colorScheme.primary, // Fundo Verde Esmeralda do Projeto
        contentColor = MaterialTheme.colorScheme.onPrimary,
        modifier = modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .statusBarsPadding() // Garante espaçamento correto sob a barra de status do sistema
                .padding(horizontal = 16.dp, vertical = 16.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            // Lado Esquerdo: Identidade / Título da Tela
            Column(
                verticalArrangement = Arrangement.Center,
                modifier = Modifier.weight(1f)
            ) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold)
                )
                Text(
                    text = subtitle,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onPrimary.copy(alpha = 0.8f)
                )
            }

            // Lado Direito: Badge de nível e Botão do perfil
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                UserLevelBadge(
                    level = level,
                    progress = progress
                )

                IconButton(
                    onClick = onProfileClick,
                    modifier = Modifier
                        .size(40.dp)
                        .clip(CircleShape)
                ) {
                    Icon(
                        imageVector = Icons.Default.AccountCircle,
                        contentDescription = "Acessar Perfil do Usuário",
                        modifier = Modifier.fillMaxSize(),
                        tint = MaterialTheme.colorScheme.onPrimary
                    )
                }
            }
        }
    }
}
