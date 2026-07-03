package com.hnrzzin.granaxp.ui.theme.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.PlayCircle
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.hnrzzin.granaxp.ui.theme.GranaXPColors
import com.hnrzzin.granaxp.ui.theme.components.AppBottomNavigationBar
import com.hnrzzin.granaxp.ui.theme.components.AppTab
import com.hnrzzin.granaxp.viewmodel.*

@Composable
fun LearnScreen(
    viewModel: LessonViewModel,
    onNavigateToHome: () -> Unit,
    onNavigateToTransactions: () -> Unit,
    onNavigateToProfile: () -> Unit
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val completionEvent by viewModel.completionEvent.collectAsStateWithLifecycle()

    var selectedLesson by remember { mutableStateOf<LessonWithProgress?>(null) }

    Scaffold(
        bottomBar = {
            AppBottomNavigationBar(
                selectedTab = AppTab.LEARN,
                onNavigateToHome = onNavigateToHome,
                onNavigateToTransactions = onNavigateToTransactions,
                onNavigateToLearn = { /* já está em Aprender */ },
                onNavigateToProfile = onNavigateToProfile
            )
        }
    ) { paddingValues ->
        when (val state = uiState) {
            is LessonUiState.Loading -> {
                Box(Modifier.fillMaxSize().padding(paddingValues), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator()
                }
            }
            is LessonUiState.Error -> {
                Box(Modifier.fillMaxSize().padding(paddingValues), contentAlignment = Alignment.Center) {
                    Text(state.message, color = GranaXPColors.Error)
                }
            }
            is LessonUiState.Success -> {
                LazyColumn(
                    modifier = Modifier.fillMaxSize().padding(paddingValues),
                    contentPadding = PaddingValues(16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    item {
                        LearnHeaderCard()
                    }

                    items(state.lessons.size) { index ->
                        val item = state.lessons[index]
                        val unlocked = viewModel.isLessonUnlocked(index, state.lessons)
                        val completed = item.progress?.isCompleted == true

                        LessonListItem(
                            lessonWithProgress = item,
                            index = index,
                            unlocked = unlocked,
                            completed = completed,
                            onClick = {
                                if (unlocked) {
                                    if (item.progress == null) viewModel.startLesson(item.lesson.id)
                                    selectedLesson = item
                                }
                            }
                        )
                    }
                }
            }
        }
    }

    selectedLesson?.let { item ->
        LessonDetailDialog(
            lessonWithProgress = item,
            index = (uiState as? LessonUiState.Success)?.lessons?.indexOf(item) ?: 0,
            alreadyCompleted = item.progress?.isCompleted == true,
            onDismiss = { selectedLesson = null },
            onComplete = {
                viewModel.completeLesson(item.lesson, item.progress?.id)
            }
        )
    }

    // Fecha o modal automaticamente após concluir com sucesso
    LaunchedEffect(completionEvent) {
        if (completionEvent is LessonCompletionEvent.Completed) {
            selectedLesson = null
            viewModel.resetCompletionEvent()
        }
    }
}

@Composable
private fun LearnHeaderCard() {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.large,
        colors = CardDefaults.cardColors(containerColor = GranaXPColors.Info)
    ) {
        Column(Modifier.padding(20.dp)) {
            Text(
                "Trilha de Aprendizado",
                color = Color.White,
                fontWeight = FontWeight.Bold,
                fontSize = 20.sp
            )
            Spacer(Modifier.height(4.dp))
            Text(
                "Complete as lições para ganhar XP, subir de nível e desbloquear novas conquistas!",
                color = Color.White.copy(alpha = 0.9f),
                fontSize = 13.sp
            )
        }
    }
}

@Composable
private fun LessonListItem(
    lessonWithProgress: LessonWithProgress,
    index: Int,
    unlocked: Boolean,
    completed: Boolean,
    onClick: () -> Unit
) {
    val lesson = lessonWithProgress.lesson

    Card(
        modifier = Modifier.fillMaxWidth().clickable(enabled = unlocked, onClick = onClick),
        shape = MaterialTheme.shapes.large,
        colors = CardDefaults.cardColors(
            containerColor = if (unlocked) GranaXPColors.Surface else GranaXPColors.Gray100
        )
    ) {
        Row(
            modifier = Modifier.padding(16.dp).fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            val (icon, iconColor, bgColor) = when {
                completed -> Triple(Icons.Default.CheckCircle, GranaXPColors.Success, GranaXPColors.SurfaceVariant)
                unlocked -> Triple(Icons.Default.PlayCircle, GranaXPColors.Info, GranaXPColors.SurfaceVariant)
                else -> Triple(Icons.Default.Lock, GranaXPColors.Gray400, GranaXPColors.Gray200)
            }

            Box(
                modifier = Modifier.size(40.dp).clip(CircleShape).background(bgColor),
                contentAlignment = Alignment.Center
            ) {
                Icon(icon, contentDescription = null, tint = iconColor)
            }

            Spacer(Modifier.width(12.dp))

            Column(Modifier.weight(1f)) {
                Text(
                    lesson.title,
                    fontWeight = FontWeight.Medium,
                    color = if (unlocked) GranaXPColors.OnSurface else GranaXPColors.Gray500
                )
                Row(verticalAlignment = Alignment.CenterVertically) {
                    lesson.duration?.let {
                        Text("$it min", fontSize = 12.sp, color = GranaXPColors.Gray500)
                        Spacer(Modifier.width(8.dp))
                    }
                    Text("+${lesson.xpReward} XP", fontSize = 12.sp, color = GranaXPColors.Secondary)
                }
            }
        }
    }
}

@Composable
private fun LessonDetailDialog(
    lessonWithProgress: LessonWithProgress,
    index: Int,
    alreadyCompleted: Boolean,
    onDismiss: () -> Unit,
    onComplete: () -> Unit
) {
    val lesson = lessonWithProgress.lesson

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Column {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Surface(
                        shape = MaterialTheme.shapes.small,
                        color = GranaXPColors.SurfaceVariant
                    ) {
                        Text(
                            "Aula ${index + 1}",
                            modifier = Modifier.padding(8.dp, 4.dp),
                            fontSize = 12.sp,
                            color = GranaXPColors.Info
                        )
                    }
                    Text("+${lesson.xpReward} XP", color = GranaXPColors.Secondary, fontWeight = FontWeight.Bold)
                }
                Spacer(Modifier.height(8.dp))
                Text(lesson.title, fontWeight = FontWeight.Bold, fontSize = 18.sp)
            }
        },
        text = {
            Text(lesson.description, fontSize = 14.sp, color = GranaXPColors.OnSurface)
        },
        confirmButton = {
            if (!alreadyCompleted) {
                Button(onClick = onComplete) {
                    Icon(Icons.Default.CheckCircle, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(6.dp))
                    Text("Concluir Aula")
                }
            } else {
                TextButton(onClick = onDismiss) { Text("Fechar") }
            }
        },
        dismissButton = {
            if (!alreadyCompleted) {
                TextButton(onClick = onDismiss) { Text("Voltar") }
            }
        }
    )
}