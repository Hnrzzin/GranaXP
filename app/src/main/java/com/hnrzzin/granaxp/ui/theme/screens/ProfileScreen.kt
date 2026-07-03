package com.hnrzzin.granaxp.ui.theme.screens

import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import com.hnrzzin.granaxp.ui.theme.components.AppModalBottomSheet
import com.hnrzzin.granaxp.ui.theme.components.AppModalHeader
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Logout
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.hnrzzin.granaxp.ui.theme.GranaXPColors
import com.hnrzzin.granaxp.ui.theme.components.AppBottomNavigationBar
import com.hnrzzin.granaxp.ui.theme.components.AppTab
import com.hnrzzin.granaxp.viewmodel.*

@Composable
fun ProfileScreen(
    userViewModel: UserViewModel,
    achievementViewModel: AchievementViewModel,
    onNavigateToHome: () -> Unit,
    onNavigateToTransactions: () -> Unit,
    onNavigateToLearn: () -> Unit,
    onLogout: () -> Unit,
    onAccountDeleted: () -> Unit
) {
    val userState by userViewModel.uiState.collectAsStateWithLifecycle()
    val actionState by userViewModel.actionState.collectAsStateWithLifecycle()
    val achievementState by achievementViewModel.uiState.collectAsStateWithLifecycle()

    var showEditNameDialog by remember { mutableStateOf(false) }
    var showDeleteConfirmSheet by remember { mutableStateOf(false) }
    var achievementsExpanded by remember { mutableStateOf(true) }

    LaunchedEffect(actionState) {
        if (actionState is UserActionState.Success) {
            userViewModel.resetActionState()
        }
    }

    Scaffold(
        containerColor = GranaXPColors.Background,
        bottomBar = {
            AppBottomNavigationBar(
                selectedTab = AppTab.PROFILE,
                onNavigateToHome = onNavigateToHome,
                onNavigateToTransactions = onNavigateToTransactions,
                onNavigateToLearn = onNavigateToLearn,
                onNavigateToProfile = { /* já está no Perfil */ }
            )
        }
    ) { paddingValues ->
        Column(modifier = Modifier.fillMaxSize().padding(paddingValues)) {
            when (val state = userState) {
                is UserUiState.Loading -> {
                    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        CircularProgressIndicator(color = GranaXPColors.Primary)
                    }
                }
                is UserUiState.Error -> {
                    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        Text(state.message, color = GranaXPColors.Error)
                    }
                }
                is UserUiState.Success -> {
                    val user = state.user
                    val unlockedCount = (achievementState as? AchievementUiState.Success)
                        ?.let { achievementViewModel.getUnlockedCount(it.achievements) } ?: 0
                    val totalCount = (achievementState as? AchievementUiState.Success)?.achievements?.size ?: 0

                    Column(
                        modifier = Modifier
                            .fillMaxSize()
                            .verticalScroll(rememberScrollState())
                            .padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(16.dp)
                    ) {
                        ProfileHeaderGradient(
                            name = user.name,
                            level = user.level,
                            xp = user.xp,
                            nextLevelXp = user.nextLevelXp,
                            unlockedCount = unlockedCount,
                            totalCount = totalCount,
                            onEditNameClick = { showEditNameDialog = true }
                        )

                        SettingsSection(
                            onEditName = { showEditNameDialog = true },
                            onLogout = onLogout,
                            onDeleteAccount = { showDeleteConfirmSheet = true }
                        )

                        AchievementsAccordion(
                            state = achievementState,
                            expanded = achievementsExpanded,
                            unlockedCount = unlockedCount,
                            totalCount = totalCount,
                            onToggle = { achievementsExpanded = !achievementsExpanded }
                        )

                        Spacer(Modifier.height(8.dp))
                    }
                }
            }
        }
    }

    if (showEditNameDialog) {
        EditNameDialog(
            currentName = (userState as? UserUiState.Success)?.user?.name ?: "",
            isSaving = actionState is UserActionState.Loading,
            onConfirm = { newName ->
                userViewModel.updateName(newName)
                showEditNameDialog = false
            },
            onDismiss = { showEditNameDialog = false }
        )
    }

    if (showDeleteConfirmSheet) {
        DeleteAccountSheet(
            onConfirm = {
                showDeleteConfirmSheet = false
                userViewModel.deleteAccount()
                onAccountDeleted()
            },
            onDismiss = { showDeleteConfirmSheet = false }
        )
    }
}

// ---------- Header com gradiente ----------

@Composable
private fun ProfileHeaderGradient(
    name: String,
    level: Int,
    xp: Int,
    nextLevelXp: Int,
    unlockedCount: Int,
    totalCount: Int,
    onEditNameClick: () -> Unit
) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(
                Brush.linearGradient(
                    colors = listOf(GranaXPColors.Primary, GranaXPColors.PrimaryDark)
                )
            )
    ) {
        // Detalhe decorativo: círculo translúcido no canto
        Box(
            modifier = Modifier
                .size(112.dp)
                .align(Alignment.TopEnd)
                .offset(x = 30.dp, y = (-30).dp)
                .clip(CircleShape)
                .background(GranaXPColors.White.copy(alpha = 0.10f))
        )

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            // Avatar
            Box(
                modifier = Modifier
                    .size(48.dp)
                    .clip(CircleShape)
                    .background(GranaXPColors.White)
                    .border(2.dp, GranaXPColors.White.copy(alpha = 0.30f), CircleShape),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    name.take(2).uppercase(),
                    color = GranaXPColors.Primary,
                    fontWeight = FontWeight.Bold,
                    fontSize = 16.sp
                )
            }

            // Nome + nível + XP bar
            Column(modifier = Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(name, color = GranaXPColors.White, fontWeight = FontWeight.Bold, fontSize = 14.sp)
                    Spacer(Modifier.width(6.dp))
                    Box(
                        modifier = Modifier
                            .size(20.dp)
                            .clip(CircleShape)
                            .background(GranaXPColors.White.copy(alpha = 0.20f))
                            .clickable(onClick = onEditNameClick),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            Icons.Default.Edit,
                            contentDescription = "Editar nome",
                            tint = GranaXPColors.White,
                            modifier = Modifier.size(10.dp)
                        )
                    }
                }
                Spacer(Modifier.height(4.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        Icons.Default.Star,
                        contentDescription = null,
                        tint = GranaXPColors.Yellow300,
                        modifier = Modifier.size(10.dp)
                    )
                    Spacer(Modifier.width(4.dp))
                    Text(
                        "Nível $level · $xp XP",
                        color = GranaXPColors.White.copy(alpha = 0.75f),
                        fontSize = 12.sp
                    )
                }
                Spacer(Modifier.height(6.dp))
                LinearProgressIndicator(
                    progress = { if (nextLevelXp > 0) (xp.toFloat() / nextLevelXp.toFloat()).coerceIn(0f, 1f) else 0f },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(6.dp)
                        .clip(CircleShape),
                    color = GranaXPColors.Yellow400,
                    trackColor = GranaXPColors.White.copy(alpha = 0.20f)
                )
            }

            // Badge de conquistas
            Box(
                modifier = Modifier
                    .clip(RoundedCornerShape(12.dp))
                    .background(GranaXPColors.White.copy(alpha = 0.15f))
                    .padding(horizontal = 10.dp, vertical = 6.dp)
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text("$unlockedCount/$totalCount", color = GranaXPColors.White, fontWeight = FontWeight.Bold, fontSize = 14.sp)
                    Text("conquistas", color = GranaXPColors.White.copy(alpha = 0.60f), fontSize = 9.sp)
                }
            }
        }
    }
}

// ---------- Configurações ----------

@Composable
private fun SettingsSection(
    onEditName: () -> Unit,
    onLogout: () -> Unit,
    onDeleteAccount: () -> Unit
) {
    Card(
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = GranaXPColors.White),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
    ) {
        Column {
            Text(
                "CONFIGURAÇÕES",
                modifier = Modifier.padding(16.dp, 12.dp, 16.dp, 8.dp),
                color = GranaXPColors.Gray400,
                fontSize = 11.sp,
                fontWeight = FontWeight.SemiBold,
                letterSpacing = 1.sp
            )
            SettingsItem(Icons.Default.Edit, GranaXPColors.EditIconBg, GranaXPColors.Primary, "Alterar nome", "Edite como seu nome aparece no app", GranaXPColors.Gray800, onEditName)
            HorizontalDivider(color = GranaXPColors.Gray100, modifier = Modifier.padding(horizontal = 16.dp))
            SettingsItem(Icons.Default.Shield, GranaXPColors.PrivacyIconBg, GranaXPColors.Yellow500, "Privacidade & Segurança", "Gerencie suas preferências", GranaXPColors.Gray800) { }
            HorizontalDivider(color = GranaXPColors.Gray100, modifier = Modifier.padding(horizontal = 16.dp))
            SettingsItem(Icons.AutoMirrored.Filled.Logout, GranaXPColors.Red50, GranaXPColors.Red500, "Sair da conta", "Encerre a sessão atual", GranaXPColors.Red500, onLogout)
            HorizontalDivider(color = GranaXPColors.Gray100, modifier = Modifier.padding(horizontal = 16.dp))
            SettingsItem(Icons.Default.DeleteForever, GranaXPColors.Red50, GranaXPColors.Red500, "Excluir conta", "Esta ação é irreversível", GranaXPColors.Red500, onDeleteAccount)
        }
    }
}

@Composable
private fun SettingsItem(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    iconBg: Color,
    iconTint: Color,
    title: String,
    subtitle: String,
    titleColor: Color,
    onClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 56.dp)
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier.size(32.dp).clip(CircleShape).background(iconBg),
            contentAlignment = Alignment.Center
        ) {
            Icon(icon, contentDescription = null, tint = iconTint, modifier = Modifier.size(16.dp))
        }
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text(title, color = titleColor, fontSize = 14.sp, fontWeight = FontWeight.Medium)
            Text(subtitle, color = GranaXPColors.Gray400, fontSize = 12.sp)
        }
        Icon(
            Icons.Default.ChevronRight,
            contentDescription = null,
            tint = GranaXPColors.Gray300,
            modifier = Modifier.size(14.dp)
        )
    }
}

// ---------- Acordeão de Conquistas ----------

@Composable
private fun AchievementsAccordion(
    state: AchievementUiState,
    expanded: Boolean,
    unlockedCount: Int,
    totalCount: Int,
    onToggle: () -> Unit
) {
    val rotation by animateFloatAsState(
        targetValue = if (expanded) 180f else 0f,
        animationSpec = tween(durationMillis = 300),
        label = "chevron_rotation"
    )

    Card(
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = GranaXPColors.White),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
    ) {
        Column(modifier = Modifier.animateContentSize()) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(min = 56.dp)
                    .clickable(onClick = onToggle)
                    .padding(horizontal = 16.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text("Conquistas", fontWeight = FontWeight.Bold, fontSize = 14.sp, color = GranaXPColors.Gray800, modifier = Modifier.weight(1f))
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(50))
                        .background(GranaXPColors.Primary)
                        .padding(horizontal = 8.dp, vertical = 4.dp)
                ) {
                    Text("$unlockedCount/$totalCount", color = GranaXPColors.White, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                }
                Spacer(Modifier.width(8.dp))
                Icon(
                    Icons.Default.KeyboardArrowDown,
                    contentDescription = null,
                    tint = GranaXPColors.Gray400,
                    modifier = Modifier.size(18.dp).rotate(rotation)
                )
            }

            if (expanded) {
                HorizontalDivider(color = GranaXPColors.Gray100)
                Box(modifier = Modifier.padding(12.dp)) {
                    when (state) {
                        is AchievementUiState.Loading -> {
                            Box(Modifier.fillMaxWidth().padding(24.dp), contentAlignment = Alignment.Center) {
                                CircularProgressIndicator(color = GranaXPColors.Primary)
                            }
                        }
                        is AchievementUiState.Error -> Text(state.message, color = GranaXPColors.Error)
                        is AchievementUiState.Success -> {
                            // Grid 2 colunas via Column/Row (não LazyVerticalGrid) para evitar
                            // scroll aninhado dentro do Column pai com verticalScroll — recomendação
                            // explícita da especificação técnica para grids pequenos.
                            val rows = state.achievements.chunked(2)
                            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                                rows.forEach { rowItems ->
                                    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                                        rowItems.forEach { item ->
                                            AchievementCard(item = item, modifier = Modifier.weight(1f))
                                        }
                                        if (rowItems.size == 1) Spacer(Modifier.weight(1f))
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun AchievementCard(
    item: AchievementWithProgress,
    modifier: Modifier = Modifier
) {
    val unlocked = item.progress?.isUnlocked == true

    Box(
        modifier = modifier
            .aspectRatio(1f)
            .clip(RoundedCornerShape(12.dp))
            .background(if (unlocked) GranaXPColors.Gray50 else GranaXPColors.Gray100)
            .border(
                width = 1.dp,
                color = if (unlocked) GranaXPColors.Gray100 else GranaXPColors.Gray200,
                shape = RoundedCornerShape(12.dp)
            )
            .let { if (!unlocked) it.background(GranaXPColors.White.copy(alpha = 0f)).alpha(0.55f) else it }
    ) {
        Column(
            modifier = Modifier.fillMaxSize().padding(10.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Box(
                modifier = Modifier
                    .size(36.dp)
                    .clip(CircleShape)
                    .background(
                        if (unlocked) {
                            Brush.linearGradient(listOf(GranaXPColors.AchievementGradientStart, GranaXPColors.AchievementGradientEnd))
                        } else {
                            Brush.linearGradient(listOf(GranaXPColors.Gray200, GranaXPColors.Gray200))
                        }
                    ),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    if (unlocked) Icons.Default.EmojiEvents else Icons.Default.Lock,
                    contentDescription = null,
                    tint = if (unlocked) GranaXPColors.Primary else GranaXPColors.Gray400,
                    modifier = Modifier.size(16.dp)
                )
            }
            Spacer(Modifier.height(6.dp))
            Text(
                item.achievement.title,
                fontWeight = FontWeight.Bold,
                fontSize = 11.sp,
                textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                color = GranaXPColors.Gray800,
                maxLines = 1
            )
            Text(
                item.achievement.description,
                fontSize = 10.sp,
                textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                color = GranaXPColors.Gray400,
                maxLines = 2
            )
            if (unlocked) {
                Spacer(Modifier.height(2.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Default.Bolt, contentDescription = null, tint = GranaXPColors.Yellow400, modifier = Modifier.size(8.dp))
                    Spacer(Modifier.width(2.dp))
                    Text("Desbloqueada", fontSize = 9.sp, color = GranaXPColors.Yellow500, fontWeight = FontWeight.SemiBold)
                }
            }
        }
    }
}

// ---------- Dialogs ----------

@Composable
private fun EditNameDialog(
    currentName: String,
    isSaving: Boolean,
    onConfirm: (String) -> Unit,
    onDismiss: () -> Unit
) {
    var name by remember { mutableStateOf(currentName) }

    AppModalBottomSheet(onDismiss = onDismiss) {
        AppModalHeader(title = "Alterar nome", onClose = onDismiss)

        OutlinedTextField(
            value = name,
            onValueChange = { name = it },
            singleLine = true,
            label = { Text("Nome") },
            modifier = Modifier.fillMaxWidth(),
            colors = OutlinedTextFieldDefaults.colors(focusedBorderColor = GranaXPColors.Primary)
        )

        Spacer(Modifier.height(24.dp))

        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Button(
                onClick = onDismiss,
                modifier = Modifier.weight(1f).height(44.dp),
                colors = ButtonDefaults.buttonColors(containerColor = GranaXPColors.Gray100),
                shape = RoundedCornerShape(12.dp)
            ) {
                Text("Cancelar", color = GranaXPColors.Gray700, fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
            }
            Button(
                onClick = { onConfirm(name) },
                enabled = !isSaving && name.isNotBlank(),
                modifier = Modifier.weight(1f).height(44.dp),
                colors = ButtonDefaults.buttonColors(containerColor = GranaXPColors.Primary),
                shape = RoundedCornerShape(12.dp)
            ) {
                if (isSaving) {
                    CircularProgressIndicator(color = GranaXPColors.White, modifier = Modifier.size(18.dp))
                } else {
                    Text("Salvar", color = GranaXPColors.White, fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
                }
            }
        }
    }
}


@Composable
private fun DeleteAccountSheet(
    onConfirm: () -> Unit,
    onDismiss: () -> Unit
) {
    AppModalBottomSheet(onDismiss = onDismiss) {
        Column(
            modifier = Modifier.fillMaxWidth(),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Box(
                modifier = Modifier.size(48.dp).clip(CircleShape).background(GranaXPColors.Red50),
                contentAlignment = Alignment.Center
            ) {
                Icon(Icons.Default.DeleteForever, contentDescription = null, tint = GranaXPColors.Red500, modifier = Modifier.size(22.dp))
            }
            Spacer(Modifier.height(12.dp))
            Text("Excluir conta?", fontWeight = FontWeight.Bold, fontSize = 16.sp, color = GranaXPColors.Gray900)
            Spacer(Modifier.height(8.dp))
            Text(
                "Esta ação é irreversível. Todos os seus dados serão apagados permanentemente.",
                fontSize = 14.sp,
                color = GranaXPColors.Gray500,
                textAlign = TextAlign.Center
            )
            Spacer(Modifier.height(24.dp))
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                Button(
                    onClick = onDismiss,
                    modifier = Modifier.weight(1f).height(44.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = GranaXPColors.Gray100),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Text("Cancelar", color = GranaXPColors.Gray700, fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
                }
                Button(
                    onClick = onConfirm,
                    modifier = Modifier.weight(1f).height(44.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = GranaXPColors.Red500),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Text("Excluir", color = GranaXPColors.White, fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
                }
            }
        }
    }
}