package com.hnrzzin.granaxp.ui.theme.screens

import androidx.compose.foundation.background
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.PlayCircle
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import androidx.lifecycle.Lifecycle
import coil3.compose.AsyncImage
import com.hnrzzin.granaxp.model.ActivityType
import com.hnrzzin.granaxp.model.LessonBlock
import com.hnrzzin.granaxp.model.LessonSelectionMode
import com.hnrzzin.granaxp.model.orderedAlternatives
import com.hnrzzin.granaxp.ui.theme.GranaXPColors
import com.hnrzzin.granaxp.ui.theme.components.AppBottomNavigationBar
import com.hnrzzin.granaxp.ui.theme.components.AppTab
import com.hnrzzin.granaxp.ui.theme.components.HomeTopBar
import com.hnrzzin.granaxp.viewmodel.LessonCompletionEvent
import com.hnrzzin.granaxp.viewmodel.ActivityAnswerResult
import com.hnrzzin.granaxp.viewmodel.ActivityAnswerState
import com.hnrzzin.granaxp.viewmodel.LessonContentUiState
import com.hnrzzin.granaxp.viewmodel.LessonUiState
import com.hnrzzin.granaxp.viewmodel.LessonViewModel
import com.hnrzzin.granaxp.viewmodel.DailyMissionUiState
import com.hnrzzin.granaxp.viewmodel.DailyMissionViewModel
import com.hnrzzin.granaxp.viewmodel.LessonWithProgress
import com.hnrzzin.granaxp.viewmodel.ModuleWithLessons
import com.hnrzzin.granaxp.viewmodel.UserUiState
import com.hnrzzin.granaxp.viewmodel.UserViewModel

private data class LessonSelection(
    val lesson: LessonWithProgress,
    val lessonNumber: Int,
)

@Composable
fun LearnScreen(
    viewModel: LessonViewModel,
    dailyMissionViewModel: DailyMissionViewModel,
    onNavigateToHome: () -> Unit,
    onNavigateToTransactions: () -> Unit,
    userViewModel: UserViewModel,
    onNavigateToProfile: () -> Unit,
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val contentUiState by viewModel.contentUiState.collectAsStateWithLifecycle()
    val completionEvent by viewModel.completionEvent.collectAsStateWithLifecycle()
    val userUiState by userViewModel.uiState.collectAsStateWithLifecycle()
    val dailyMissionState by dailyMissionViewModel.uiState.collectAsStateWithLifecycle()
    var selection by remember { mutableStateOf<LessonSelection?>(null) }
    var showingDailyMission by remember { mutableStateOf(false) }

    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) { dailyMissionViewModel.refresh() }
    BackHandler(enabled = showingDailyMission) { showingDailyMission = false }

    val currentLesson = selection?.let { selected ->
        (uiState as? LessonUiState.Success)
            ?.lessons
            ?.find { it.lesson.id == selected.lesson.lesson.id }
            ?: selected.lesson
    }

    if (showingDailyMission) {
        DailyMissionPage(
            state = dailyMissionState,
            onBack = { showingDailyMission = false },
            onSelect = dailyMissionViewModel::selectAnswer,
            onSubmit = dailyMissionViewModel::submitAnswer,
            onRefresh = dailyMissionViewModel::refresh,
        )
    } else if (selection != null && currentLesson != null) {
        LessonContentPage(
            lessonWithProgress = currentLesson,
            lessonNumber = selection!!.lessonNumber,
            contentUiState = contentUiState,
            onBack = {
                selection = null
                viewModel.clearLessonContent()
            },
            onComplete = {
                viewModel.completeLesson(currentLesson)
            },
            onAnswerSelected = viewModel::selectActivityAnswer,
            onSubmitAnswer = viewModel::submitActivityAnswer,
        )
    } else {
        LearningTrail(
            uiState = uiState,
            dailyMissionState = dailyMissionState,
            userUiState = userUiState,
            onNavigateToHome = onNavigateToHome,
            onNavigateToTransactions = onNavigateToTransactions,
            onNavigateToProfile = onNavigateToProfile,
            onDailyMissionClick = { showingDailyMission = true },
            onDailyMissionRefresh = dailyMissionViewModel::refresh,
            onLessonClick = { item, index ->
                viewModel.openLesson(item)
                selection = LessonSelection(
                    lesson = item,
                    lessonNumber = index + 1,
                )
            },
        )
    }

    LaunchedEffect(completionEvent) {
        if (completionEvent is LessonCompletionEvent.Completed) {
            selection = null
            viewModel.clearLessonContent()
            viewModel.resetCompletionEvent()
            userViewModel.fetchUser()
        }
    }
}

@Composable
private fun LearningTrail(
    uiState: LessonUiState,
    dailyMissionState: DailyMissionUiState,
    userUiState: UserUiState,
    onNavigateToHome: () -> Unit,
    onNavigateToTransactions: () -> Unit,
    onNavigateToProfile: () -> Unit,
    onDailyMissionClick: () -> Unit,
    onDailyMissionRefresh: () -> Unit,
    onLessonClick: (LessonWithProgress, Int) -> Unit,
) {
    Scaffold(
        topBar = {
            val user = (userUiState as? UserUiState.Success)?.user
            HomeTopBar(
                level = user?.level ?: 1,
                xp = user?.xp ?: 0,
                nextLevelXp = user?.nextLevelXp ?: 100,
                onProfileClick = onNavigateToProfile,
            )
        },
        bottomBar = {
            AppBottomNavigationBar(
                selectedTab = AppTab.LEARN,
                onNavigateToHome = onNavigateToHome,
                onNavigateToTransactions = onNavigateToTransactions,
                onNavigateToLearn = {},
                onNavigateToProfile = onNavigateToProfile,
            )
        },
    ) { paddingValues ->
        when (uiState) {
            is LessonUiState.Loading -> LoadingState(paddingValues)
            is LessonUiState.Error -> ErrorState(uiState.message, paddingValues)
            is LessonUiState.Success -> LearningContent(
                state = uiState,
                dailyMissionState = dailyMissionState,
                paddingValues = paddingValues,
                onLessonClick = onLessonClick,
                onDailyMissionClick = onDailyMissionClick,
                onDailyMissionRefresh = onDailyMissionRefresh,
            )
        }
    }
}

@Composable
private fun LoadingState(paddingValues: PaddingValues) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .padding(paddingValues),
        contentAlignment = Alignment.Center,
    ) {
        CircularProgressIndicator()
    }
}

@Composable
private fun ErrorState(message: String, paddingValues: PaddingValues) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .padding(paddingValues),
        contentAlignment = Alignment.Center,
    ) {
        Text(message, color = GranaXPColors.Error)
    }
}

@Composable
private fun LearningContent(
    state: LessonUiState.Success,
    dailyMissionState: DailyMissionUiState,
    paddingValues: PaddingValues,
    onLessonClick: (LessonWithProgress, Int) -> Unit,
    onDailyMissionClick: () -> Unit,
    onDailyMissionRefresh: () -> Unit,
) {
    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .padding(paddingValues),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        item { LearnHeaderCard() }

        if (dailyMissionState !is DailyMissionUiState.Unavailable &&
            dailyMissionState !is DailyMissionUiState.Loading) {
            item {
                DailyMissionEntry(
                    state = dailyMissionState,
                    onOpen = onDailyMissionClick,
                    onRefresh = onDailyMissionRefresh,
                )
            }
        }

        items(state.modules, key = { it.module.idModule }) { module ->
            ModuleSection(module = module, onLessonClick = onLessonClick)
        }

        if (state.legacyLessons.isNotEmpty()) {
            item {
                LegacyLessonsSection(
                    state = state,
                    showHeading = state.modules.isNotEmpty(),
                    onLessonClick = onLessonClick,
                )
            }
        }

        if (state.modules.isEmpty() && state.legacyLessons.isEmpty()) {
            item { EmptyLearningState() }
        }
    }
}

@Composable
private fun LearnHeaderCard() {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.large,
        colors = CardDefaults.cardColors(containerColor = GranaXPColors.Info),
    ) {
        Column(Modifier.padding(20.dp)) {
            Text(
                text = "Trilha de Aprendizado",
                color = Color.White,
                fontWeight = FontWeight.Bold,
                fontSize = 20.sp,
            )
            Spacer(Modifier.height(4.dp))
            Text(
                text = "Escolha um módulo e avance pelas lições no seu ritmo.",
                color = Color.White.copy(alpha = 0.9f),
                fontSize = 13.sp,
            )
        }
    }
}

@Composable
private fun ModuleSection(
    module: ModuleWithLessons,
    onLessonClick: (LessonWithProgress, Int) -> Unit,
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.large,
        colors = CardDefaults.cardColors(containerColor = GranaXPColors.Surface),
    ) {
        Column(Modifier.padding(16.dp)) {
            Text(
                text = module.module.title,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
            )
            if (module.module.description.isNotBlank()) {
                Spacer(Modifier.height(4.dp))
                Text(
                    text = module.module.description,
                    style = MaterialTheme.typography.bodyMedium,
                    color = GranaXPColors.Gray600,
                )
            }
            Spacer(Modifier.height(12.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = "${module.completedLessons}/${module.totalLessons} lições",
                    fontSize = 12.sp,
                    color = GranaXPColors.Gray600,
                )
                if (module.isCompleted) {
                    Text(
                        text = "Módulo concluído",
                        fontSize = 12.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = GranaXPColors.Success,
                    )
                }
            }
            Spacer(Modifier.height(6.dp))
            LinearProgressIndicator(
                progress = { module.progress },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(6.dp)
                    .clip(CircleShape),
                color = GranaXPColors.Primary,
                trackColor = GranaXPColors.Gray200,
            )

            if (module.lessons.isEmpty()) {
                Spacer(Modifier.height(12.dp))
                Text(
                    text = "Nenhuma lição disponível neste módulo.",
                    color = GranaXPColors.Gray500,
                    fontSize = 13.sp,
                )
            } else {
                Spacer(Modifier.height(8.dp))
                module.lessons.forEachIndexed { index, item ->
                    LessonListItem(
                        lessonWithProgress = item,
                        unlocked = module.isLessonUnlocked(index),
                        completed = item.isCompleted,
                        onClick = { onLessonClick(item, index) },
                    )
                }
            }
        }
    }
}

@Composable
private fun LegacyLessonsSection(
    state: LessonUiState.Success,
    showHeading: Boolean,
    onLessonClick: (LessonWithProgress, Int) -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        if (showHeading) {
            Text(
                text = "Outras lições",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
            )
        }
        state.legacyLessons.forEachIndexed { index, item ->
            LessonListItem(
                lessonWithProgress = item,
                unlocked = state.isLegacyLessonUnlocked(index),
                completed = item.isCompleted,
                onClick = { onLessonClick(item, index) },
            )
        }
    }
}

@Composable
private fun EmptyLearningState() {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = GranaXPColors.Surface),
    ) {
        Text(
            text = "Nenhum conteúdo de aprendizagem disponível.",
            modifier = Modifier.padding(20.dp),
            color = GranaXPColors.Gray600,
        )
    }
}

@Composable
private fun LessonListItem(
    lessonWithProgress: LessonWithProgress,
    unlocked: Boolean,
    completed: Boolean,
    onClick: () -> Unit,
) {
    val lesson = lessonWithProgress.lesson

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(enabled = unlocked, onClick = onClick)
            .padding(vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        val (icon, iconColor, backgroundColor) = when {
            completed -> Triple(Icons.Default.CheckCircle, GranaXPColors.Success, GranaXPColors.SurfaceVariant)
            unlocked -> Triple(Icons.Default.PlayCircle, GranaXPColors.Info, GranaXPColors.SurfaceVariant)
            else -> Triple(Icons.Default.Lock, GranaXPColors.Gray400, GranaXPColors.Gray200)
        }

        Box(
            modifier = Modifier
                .size(40.dp)
                .clip(CircleShape)
                .background(backgroundColor),
            contentAlignment = Alignment.Center,
        ) {
            Icon(icon, contentDescription = null, tint = iconColor)
        }

        Spacer(Modifier.width(12.dp))

        Column(Modifier.weight(1f)) {
            Text(
                text = lesson.title,
                fontWeight = FontWeight.Medium,
                color = if (unlocked) GranaXPColors.OnSurface else GranaXPColors.Gray500,
            )
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("${lesson.duration} min", fontSize = 12.sp, color = GranaXPColors.Gray500)
                Spacer(Modifier.width(8.dp))
                Text("+${lesson.xpReward} XP", fontSize = 12.sp, color = GranaXPColors.Secondary)
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun LessonContentPage(
    lessonWithProgress: LessonWithProgress,
    lessonNumber: Int,
    contentUiState: LessonContentUiState,
    onBack: () -> Unit,
    onComplete: () -> Unit,
    onAnswerSelected: (blockId: String, answerId: String) -> Unit,
    onSubmitAnswer: (blockId: String) -> Unit,
) {
    val lesson = lessonWithProgress.lesson
    val alreadyCompleted = lessonWithProgress.isCompleted
    val contentLoaded = contentUiState is LessonContentUiState.Success &&
        contentUiState.lessonId == lesson.id

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(lesson.title, maxLines = 1) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Voltar")
                    }
                },
            )
        },
    ) { paddingValues ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues),
            contentPadding = PaddingValues(20.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            item {
                LessonContentHeader(
                    lessonNumber = lessonNumber,
                    duration = lesson.duration,
                    xpReward = lesson.xpReward,
                    description = lesson.description,
                    alreadyCompleted = alreadyCompleted,
                )
            }

            when (contentUiState) {
                is LessonContentUiState.Loading -> {
                    if (contentUiState.lessonId == lesson.id) {
                        item {
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(24.dp),
                                contentAlignment = Alignment.Center,
                            ) {
                                CircularProgressIndicator()
                            }
                        }
                    }
                }

                is LessonContentUiState.Success -> {
                    if (contentUiState.lessonId == lesson.id) {
                        if (contentUiState.blocks.isEmpty()) {
                            Unit
                        } else {
                            items(contentUiState.blocks, key = { it.id }) { block ->
                                if (block.type.equals("ACTIVITY", ignoreCase = true)) {
                                    LessonActivityCard(
                                        block = block,
                                        answerState = contentUiState.activityAnswers[block.id]
                                            ?: ActivityAnswerState(),
                                        isPersistedCompleted = block.id in
                                            contentUiState.completedActivityIds,
                                        onAnswerSelected = { answerId ->
                                            onAnswerSelected(block.id, answerId)
                                        },
                                        onSubmit = { onSubmitAnswer(block.id) },
                                    )
                                } else {
                                    LessonBlockCard(block)
                                }
                            }
                        }
                    }
                }

                is LessonContentUiState.Error -> {
                    if (contentUiState.lessonId == lesson.id) {
                        item {
                            Text(contentUiState.message, color = GranaXPColors.Error)
                        }
                    }
                }

                LessonContentUiState.Idle -> Unit
            }

            item {
                HorizontalDivider(color = GranaXPColors.Gray200)
                Spacer(Modifier.height(8.dp))
                if (alreadyCompleted) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            Icons.Default.CheckCircle,
                            contentDescription = null,
                            tint = GranaXPColors.Success,
                        )
                        Spacer(Modifier.width(8.dp))
                        Text(
                            text = "Lição concluída",
                            color = GranaXPColors.Success,
                            fontWeight = FontWeight.SemiBold,
                        )
                    }
                } else if (!contentLoaded) {
                    if (contentUiState !is LessonContentUiState.Error) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            CircularProgressIndicator(modifier = Modifier.size(20.dp))
                            Spacer(Modifier.width(8.dp))
                            Text(
                                text = "Preparando a lição...",
                                color = GranaXPColors.Gray600,
                            )
                        }
                    }
                } else {
                    val completionAvailable = (
                        contentUiState as? LessonContentUiState.Success
                    )?.takeIf { it.lessonId == lesson.id }?.canComplete == true
                    if (contentLoaded && !completionAvailable) {
                        Text(
                            text = "Conclua todas as atividades para finalizar a lição.",
                            color = GranaXPColors.Gray600,
                            fontSize = 13.sp,
                        )
                        Spacer(Modifier.height(8.dp))
                    }
                    Button(
                        modifier = Modifier.fillMaxWidth(),
                        enabled = contentLoaded && completionAvailable,
                        onClick = onComplete,
                    ) {
                        Icon(
                            Icons.Default.CheckCircle,
                            contentDescription = null,
                            modifier = Modifier.size(18.dp),
                        )
                        Spacer(Modifier.width(6.dp))
                        Text("Concluir lição")
                    }
                }
            }
        }
    }
}

@Composable
private fun LessonActivityCard(
    block: LessonBlock,
    answerState: ActivityAnswerState,
    isPersistedCompleted: Boolean,
    onAnswerSelected: (String) -> Unit,
    onSubmit: () -> Unit,
) {
    val allowsMultipleAnswers = block.selectionMode == LessonSelectionMode.MULTIPLE

    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = GranaXPColors.Surface),
        shape = MaterialTheme.shapes.large,
    ) {
        Column(Modifier.padding(16.dp)) {
            Text(
                text = block.activityType?.name
                    ?.replace('_', ' ')
                    ?.lowercase()
                    ?.replaceFirstChar { it.titlecase() }
                    ?: "Atividade",
                fontSize = 11.sp,
                fontWeight = FontWeight.SemiBold,
                color = GranaXPColors.Info,
            )

            if (isPersistedCompleted) {
                Spacer(Modifier.height(6.dp))
                Text(
                    text = "Atividade concluída",
                    color = GranaXPColors.Success,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.SemiBold,
                )
            }

            if (block.title.isNotBlank()) {
                Spacer(Modifier.height(6.dp))
                Text(
                    text = block.title,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                )
            }
            if (block.content.isNotBlank()) {
                Spacer(Modifier.height(8.dp))
                Text(
                    text = block.content,
                    style = MaterialTheme.typography.bodyMedium,
                    color = GranaXPColors.OnSurface,
                )
            }

            if (
                block.activityType == ActivityType.CHART_ANALYSIS &&
                !block.chartImageUrl.isNullOrBlank()
            ) {
                Spacer(Modifier.height(12.dp))
                AsyncImage(
                    model = block.chartImageUrl,
                    contentDescription = "Gráfico da atividade ${block.title}",
                    modifier = Modifier
                        .fillMaxWidth()
                        .aspectRatio(16f / 9f)
                        .clip(MaterialTheme.shapes.medium),
                    contentScale = ContentScale.Fit,
                )
            }

            Spacer(Modifier.height(12.dp))
            block.orderedAlternatives().forEach { alternative ->
                val selected = alternative.key in answerState.selectedAnswerIds
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 4.dp)
                        .clip(MaterialTheme.shapes.medium)
                        .background(
                            if (selected) GranaXPColors.Primary.copy(alpha = 0.08f)
                            else Color.Transparent,
                        )
                        .clickable(enabled = !answerState.isSubmitting) {
                            onAnswerSelected(alternative.key)
                        }
                        .padding(horizontal = 8.dp, vertical = 6.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    if (allowsMultipleAnswers) {
                        Checkbox(
                            checked = selected,
                            onCheckedChange = null,
                        )
                    } else {
                        RadioButton(
                            selected = selected,
                            onClick = null,
                        )
                    }
                    Spacer(Modifier.width(8.dp))
                    Text(
                        text = alternative.value,
                        modifier = Modifier.weight(1f),
                        style = MaterialTheme.typography.bodyMedium,
                    )
                }
            }

            when (answerState.result) {
                ActivityAnswerResult.INCORRECT -> {
                    ActivityFeedback(
                        message = answerState.message.orEmpty(),
                        color = GranaXPColors.Error,
                    )
                }

                ActivityAnswerResult.CORRECT -> {
                    ActivityFeedback(
                        message = "Resposta correta. Atividade concluída.",
                        color = GranaXPColors.Success,
                    )
                }

                ActivityAnswerResult.ERROR -> {
                    ActivityFeedback(
                        message = answerState.message.orEmpty(),
                        color = GranaXPColors.Error,
                    )
                }

                ActivityAnswerResult.IDLE -> Unit
            }

            Spacer(Modifier.height(12.dp))
            Button(
                modifier = Modifier.fillMaxWidth(),
                enabled = answerState.selectedAnswerIds.isNotEmpty() &&
                    !answerState.isSubmitting &&
                    answerState.result != ActivityAnswerResult.CORRECT,
                onClick = onSubmit,
            ) {
                if (answerState.isSubmitting) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(18.dp),
                        strokeWidth = 2.dp,
                    )
                    Spacer(Modifier.width(8.dp))
                }
                Text(if (answerState.isSubmitting) "Salvando..." else "Responder")
            }
        }
    }
}

@Composable
private fun ActivityFeedback(message: String, color: Color) {
    if (message.isBlank()) return
    Spacer(Modifier.height(10.dp))
    Surface(
        color = color.copy(alpha = 0.08f),
        shape = MaterialTheme.shapes.medium,
        border = BorderStroke(1.dp, color.copy(alpha = 0.35f)),
    ) {
        Text(
            text = message,
            modifier = Modifier.padding(12.dp),
            color = color,
            style = MaterialTheme.typography.bodyMedium,
        )
    }
}

@Composable
private fun LessonContentHeader(
    lessonNumber: Int,
    duration: Int,
    xpReward: Int,
    description: String,
    alreadyCompleted: Boolean,
) {
    Column {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Surface(
                shape = MaterialTheme.shapes.small,
                color = GranaXPColors.SurfaceVariant,
            ) {
                Text(
                    text = "Lição $lessonNumber",
                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                    fontSize = 12.sp,
                    color = GranaXPColors.Info,
                )
            }
            Text(
                text = "+$xpReward XP",
                color = GranaXPColors.Secondary,
                fontWeight = FontWeight.Bold,
            )
        }
        Spacer(Modifier.height(12.dp))
        Text("$duration min", fontSize = 13.sp, color = GranaXPColors.Gray500)
        if (description.isNotBlank()) {
            Spacer(Modifier.height(8.dp))
            Text(
                text = description,
                style = MaterialTheme.typography.bodyLarge,
                color = GranaXPColors.OnSurface,
            )
        }
        if (alreadyCompleted) {
            Spacer(Modifier.height(8.dp))
            Text(
                text = "Você pode revisar esta lição quando quiser.",
                fontSize = 13.sp,
                color = GranaXPColors.Success,
            )
        }
    }
}

@Composable
private fun LessonBlockCard(block: LessonBlock) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = GranaXPColors.Surface),
        shape = MaterialTheme.shapes.large,
    ) {
        Column(Modifier.padding(16.dp)) {
            if (block.type.isNotBlank()) {
                Text(
                    text = block.type.replace('_', ' ').lowercase().replaceFirstChar { it.titlecase() },
                    fontSize = 11.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = GranaXPColors.Info,
                )
                Spacer(Modifier.height(6.dp))
            }
            if (block.title.isNotBlank()) {
                Text(
                    text = block.title,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                )
            }
            if (block.content.isNotBlank()) {
                if (block.title.isNotBlank()) Spacer(Modifier.height(8.dp))
                Text(
                    text = block.content,
                    style = MaterialTheme.typography.bodyMedium,
                    color = GranaXPColors.OnSurface,
                )
            }
        }
    }
}
