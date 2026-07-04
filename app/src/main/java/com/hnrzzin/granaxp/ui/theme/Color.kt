package com.hnrzzin.granaxp.ui.theme

import androidx.compose.ui.graphics.Color

object GranaXPColors {
    // Verde — marca principal
    val Primary = Color(0xFF029E68)
    val PrimaryLight = Color(0xFF10B981)      // Emerald-500 — usado em primaryContainer/dark theme
    val PrimaryDark = Color(0xFF017A51)       // fim do gradiente do header de perfil
    val Emerald600 = Color(0xFF059669)        // header global do app, ícones de receita, nav ativa
    val Emerald500 = Color(0xFF10B981)        // hover nav, botão de perfil no header
    val Emerald100 = Color(0xFFD1FAE5)        // fundo de ícones de receita
    val Emerald800 = Color(0xFF065F46)        // trilho da barra de XP no header global

    // Neutros
    val Gray50 = Color(0xFFF9FAFB)
    val Gray100 = Color(0xFFF3F4F6)
    val Gray200 = Color(0xFFE5E7EB)
    val Gray300 = Color(0xFFD1D5DB)
    val Gray400 = Color(0xFF9CA3AF)
    val Gray500 = Color(0xFF6B7280)
    val Gray600 = Color(0xFF6B7280)
    val Gray700 = Color(0xFF374151)
    val Gray800 = Color(0xFF1F2937)
    val Gray900 = Color(0xFF111827)
    val White = Color(0xFFFFFFFF)

    // Vermelho — despesas, ações destrutivas
    val Red50 = Color(0xFFFEF2F2)
    val Red100 = Color(0xFFFEE2E2)
    val Red500 = Color(0xFFEF4444)
    val Red600 = Color(0xFFDC2626)
    val Error = Red600
    val ErrorLight = Red100

    // Rose — lembretes vencidos
    val Rose50 = Color(0xFFFFF1F2)
    val Rose300 = Color(0xFFFDA4AF)
    val Rose500 = Color(0xFFF43F5E)
    val Rose600 = Color(0xFFE11D48)

    // Azul — gastos fixos, educação
    val Blue50 = Color(0xFFEFF6FF)
    val Blue100 = Color(0xFFDBEAFE)
    val Blue600 = Color(0xFF2563EB)
    val Blue700 = Color(0xFF1D4ED8)
    val Blue900 = Color(0xFF1E3A8A)
    val Info = Blue600

    // Laranja — gastos variáveis
    val Orange50 = Color(0xFFFFF7ED)
    val Orange100 = Color(0xFFFFEDD5)
    val Orange600 = Color(0xFFEA580C)
    val Orange700 = Color(0xFFC2410C)
    val Orange900 = Color(0xFF7C2D12)

    // Roxo — metas financeiras
    val Purple50 = Color(0xFFFAF5FF)
    val Purple100 = Color(0xFFF3E8FF)
    val Purple500 = Color(0xFFA855F7)
    val Purple600 = Color(0xFF9333EA)
    val Purple700 = Color(0xFF7E22CE)
    val Secondary = Purple600
    val SecondaryLight = Purple500
    val SecondaryDark = Purple700

    // Amarelo — XP bar, estrelas, "Desbloqueada"
    val Yellow300 = Color(0xFFFCD34D)
    val Yellow400 = Color(0xFFFACC15)
    val Yellow500 = Color(0xFFEAB308)
    val Warning = Yellow500
    val Teal700 = Color(0xFF0F766E)  // fim do gradiente do card Saldo Atual
    val Indigo700 = Color(0xFF4338CA)  // fim do gradiente do header de Aprender
    // Aliases semânticos usados nas telas já construídas / no Theme.kt
    val Success = Emerald600
    val Surface = White
    val SurfaceVariant = Gray50
    val OnSurface = Gray900
    val OnPrimary = White
    val Background = Gray50
    val OnBackground = Gray800

    // Cores específicas de contexto (item de Configurações no Perfil)
    val EditIconBg = Color(0xFFE6F7F1)        // fundo do ícone "Alterar nome"
    val PrivacyIconBg = Color(0xFFFFF8E6)     // fundo do ícone "Privacidade"
    val AchievementGradientStart = Color(0xFFE6F7F1)
    val AchievementGradientEnd = Color(0xFFB3EBD6)
}