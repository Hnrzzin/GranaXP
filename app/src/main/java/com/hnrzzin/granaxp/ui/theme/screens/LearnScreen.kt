package com.hnrzzin.granaxp.ui.theme.screens

import androidx.compose.foundation.background
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.progressSemantics
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items as gridItems
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.PlayCircle
import androidx.compose.material.icons.filled.AccountBalanceWallet
import androidx.compose.material.icons.automirrored.filled.TrendingUp
import androidx.compose.material.icons.filled.EmojiEvents
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.key
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
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
import com.hnrzzin.granaxp.viewmodel.headerStreak
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
    var showingDailyMission by rememberSaveable { mutableStateOf(false) }
    var selectedModuleId by rememberSaveable { mutableStateOf<String?>(null) }

    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) { dailyMissionViewModel.refresh() }
    BackHandler(enabled = showingDailyMission || selection != null || selectedModuleId != null) {
        when {
            showingDailyMission -> showingDailyMission = false
            selection != null -> {
                selection = null
                viewModel.clearLessonContent()
            }
            else -> selectedModuleId = null
        }
    }

    val currentLesson = selection?.let { selected ->
        (uiState as? LessonUiState.Success)
            ?.lessons
            ?.find { it.lesson.id == selected.lesson.lesson.id }
            ?: selected.lesson
    }

    if (selection != null && currentLesson != null) {
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
            selectedModuleId = selectedModuleId,
            onModuleClick = { selectedModuleId = it },
            onModulesBack = { selectedModuleId = null },
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

    if (showingDailyMission) {
        DailyMissionDialog(
            state = dailyMissionState,
            onBack = { showingDailyMission = false },
            onSelect = dailyMissionViewModel::selectAnswer,
            onSubmit = dailyMissionViewModel::submitAnswer,
            onRefresh = dailyMissionViewModel::refresh,
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

    LaunchedEffect(dailyMissionState) {
        if (((dailyMissionState as? DailyMissionUiState.Completed)?.xpEarned ?: 0) > 0) {
            userViewModel.fetchUser()
        }
    }
}

@Composable
private fun LearningTrail(
    uiState: LessonUiState,
    dailyMissionState: DailyMissionUiState,
    userUiState: UserUiState,
    selectedModuleId: String?,
    onModuleClick: (String) -> Unit,
    onModulesBack: () -> Unit,
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
                currentStreak = dailyMissionState.headerStreak(),
                onProfileClick = onNavigateToProfile,
            )
        },
        bottomBar = {
            AppBottomNavigationBar(
                selectedTab = AppTab.LEARN,
                onNavigateToHome = onNavigateToHome,
                onNavigateToTransactions = onNavigateToTransactions,
                onNavigateToLearn = onModulesBack,
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
                selectedModuleId = selectedModuleId,
                onModuleClick = onModuleClick,
                onModulesBack = onModulesBack,
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
    selectedModuleId: String?,
    onModuleClick: (String) -> Unit,
    onModulesBack: () -> Unit,
) {
    val selectedModule = state.modules.find { it.module.idModule == selectedModuleId }
    if (selectedModule != null) {
        LazyColumn(
            modifier = Modifier.fillMaxSize().padding(paddingValues)
                .background(GranaXPColors.Background),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            item {
                Row(
                    modifier = Modifier.fillMaxWidth().clickable(onClick = onModulesBack)
                        .padding(vertical = 12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = null,
                        tint = GranaXPColors.Primary)
                    Spacer(Modifier.width(8.dp))
                    Text("Voltar aos módulos", color = GranaXPColors.Primary,
                        fontWeight = FontWeight.SemiBold)
                }
            }
            item { ModuleSection(module = selectedModule, onLessonClick = onLessonClick) }
        }
        return
    }
    LazyVerticalGrid(
        columns = GridCells.Fixed(2),
        modifier = Modifier
            .fillMaxSize()
            .padding(paddingValues)
            .background(GranaXPColors.Background),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        item(span = { GridItemSpan(maxLineSpan) }) { LearnHeaderCard(state) }

        item(span = { GridItemSpan(maxLineSpan) }) {
            DailyMissionEntry(
                state = dailyMissionState,
                onOpen = onDailyMissionClick,
                onRefresh = onDailyMissionRefresh,
            )
        }

        gridItems(state.modules, key = { it.module.idModule }) { module ->
            ModuleCatalogCard(module = module, onClick = { onModuleClick(module.module.idModule) })
        }

        if (state.legacyLessons.isNotEmpty()) {
            item(span = { GridItemSpan(maxLineSpan) }) {
                LegacyLessonsSection(
                    state = state,
                    showHeading = state.modules.isNotEmpty(),
                    onLessonClick = onLessonClick,
                )
            }
        }

        if (state.modules.isEmpty() && state.legacyLessons.isEmpty()) {
            item(span = { GridItemSpan(maxLineSpan) }) { EmptyLearningState() }
        }
    }
}

@Composable
private fun LearnHeaderCard(state: LessonUiState.Success) {
    val total = state.lessons.size
    val completed = state.lessons.count { it.isCompleted }
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(18.dp),
        elevation = CardDefaults.cardElevation(defaultElevation = 3.dp),
    ) {
        Column(Modifier.fillMaxWidth().background(Brush.horizontalGradient(
            listOf(GranaXPColors.Primary, Color(0xFF169C90))
        )).padding(20.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Default.EmojiEvents, contentDescription = null,
                    tint = GranaXPColors.Yellow300, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(6.dp))
                Text("TRILHA DE APRENDIZADO", color = Color.White.copy(alpha = 0.9f),
                    fontWeight = FontWeight.SemiBold, fontSize = 12.sp, letterSpacing = 0.7.sp)
            }
            Spacer(Modifier.height(6.dp))
            Text("Escolha seu Caminho", color = Color.White,
                fontWeight = FontWeight.Bold, fontSize = 22.sp)
            Spacer(Modifier.height(18.dp))
            Row(verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text("$completed", color = Color.White, fontSize = 24.sp, fontWeight = FontWeight.Bold)
                    Text("concluídas", color = Color.White, fontSize = 12.sp)
                }
                Text("/", color = GranaXPColors.PrimaryLight, fontSize = 24.sp)
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text("$total", color = Color.White, fontSize = 24.sp, fontWeight = FontWeight.Bold)
                    Text("lições", color = Color.White, fontSize = 12.sp)
                }
                LinearProgressIndicator(
                    progress = { if (total == 0) 0f else completed.toFloat() / total },
                    modifier = Modifier.weight(1f).height(8.dp).clip(CircleShape),
                    color = GranaXPColors.Yellow300,
                    trackColor = GranaXPColors.PrimaryDark,
                )
            }
        }
    }
}

@Composable
private fun ModuleCatalogCard(module: ModuleWithLessons, onClick: () -> Unit) {
    val investments = module.module.idModule == "investimentos"
    val colors = if (investments) listOf(Color(0xFF619BF8), Color(0xFF5145EB))
        else listOf(Color(0xFF2BCE99), GranaXPColors.Primary)
    Card(
        modifier = Modifier.fillMaxWidth()
            .semantics { contentDescription = "Abrir módulo: ${module.module.title}" }
            .clickable(onClick = onClick),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = GranaXPColors.Surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
    ) {
        Box(
            modifier = Modifier.fillMaxWidth().height(86.dp)
                .background(Brush.horizontalGradient(colors)).padding(16.dp),
            contentAlignment = Alignment.BottomStart,
        ) {
            Icon(
                if (investments) Icons.AutoMirrored.Filled.TrendingUp else Icons.Default.AccountBalanceWallet,
                contentDescription = null, tint = Color.White,
                modifier = Modifier.size(36.dp),
            )
        }
        Column(Modifier.padding(12.dp)) {
            Text(module.module.title, fontSize = 14.sp, lineHeight = 18.sp,
                fontWeight = FontWeight.Bold, color = GranaXPColors.OnSurface,
                minLines = 2, maxLines = 2, overflow = TextOverflow.Ellipsis)
            Text(module.module.description, fontSize = 12.sp, lineHeight = 16.sp,
                color = GranaXPColors.Gray500, minLines = 2, maxLines = 2,
                overflow = TextOverflow.Ellipsis)
            Spacer(Modifier.height(10.dp))
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Text("${module.completedLessons}/${module.totalLessons} lições",
                    fontSize = 11.sp, color = GranaXPColors.Gray500)
                Text("★ ${module.lessons.sumOf { it.lesson.xpReward }} XP",
                    fontSize = 11.sp, color = Color(0xFFD38700), fontWeight = FontWeight.SemiBold)
            }
            Spacer(Modifier.height(6.dp))
            LinearProgressIndicator(progress = { module.progress },
                modifier = Modifier.fillMaxWidth().height(4.dp).clip(CircleShape),
                color = if (investments) GranaXPColors.Indigo700 else GranaXPColors.Primary,
                trackColor = GranaXPColors.Gray100)
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
internal fun LessonContentPage(
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
    val content = (contentUiState as? LessonContentUiState.Success)
        ?.takeIf { it.lessonId == lesson.id }
    // Presentation only: reopening starts at the first block; confirmations stay server-side.
    var stepIndex by rememberSaveable(lesson.id) { mutableIntStateOf(0) }
    val blocks = content?.blocks.orEmpty()
    val pendingActivity = if (alreadyCompleted) -1 else blocks.indexOfFirst {
        it.type.equals("ACTIVITY", ignoreCase = true) && it.id !in content?.completedActivityIds.orEmpty()
    }
    val lastReachableIndex = if (pendingActivity >= 0) pendingActivity else blocks.lastIndex
    val currentIndex = stepIndex.coerceIn(0, lastReachableIndex.coerceAtLeast(0))
    LaunchedEffect(currentIndex) { stepIndex = currentIndex }
    val block = blocks.getOrNull(currentIndex)
    val isActivity = block?.type.equals("ACTIVITY", ignoreCase = true)
    val canContinue = content != null && block != null &&
        (!isActivity || alreadyCompleted || block.id in content.completedActivityIds)
    val isLastStep = content != null && (blocks.isEmpty() || currentIndex == blocks.lastIndex)
    val goBack = {
        if (currentIndex > 0) stepIndex = currentIndex - 1 else onBack()
    }
    BackHandler(onBack = goBack)

    Scaffold(
        containerColor = GranaXPColors.Background,
        topBar = {
            Column {
                TopAppBar(
                    title = {
                        Column {
                            Text(lesson.title, maxLines = 2, overflow = TextOverflow.Ellipsis,
                                fontSize = 18.sp, fontWeight = FontWeight.Bold)
                            Text("+${lesson.xpReward} XP · ${lesson.duration} min",
                                fontSize = 12.sp, color = GranaXPColors.Primary)
                        }
                    },
                    navigationIcon = {
                        IconButton(onClick = goBack) {
                            Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Voltar")
                        }
                    },
                )
                if (blocks.isNotEmpty()) {
                    Column(Modifier.padding(horizontal = 20.dp, vertical = 10.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth()
                                .progressSemantics((currentIndex + 1).toFloat() / blocks.size),
                            horizontalArrangement = Arrangement.spacedBy(4.dp),
                        ) {
                            blocks.forEachIndexed { index, _ ->
                                Box(Modifier.weight(1f).height(5.dp).clip(CircleShape)
                                    .background(if (index <= currentIndex) GranaXPColors.Primary
                                        else GranaXPColors.Gray200))
                            }
                        }
                        Spacer(Modifier.height(8.dp))
                        Text("Etapa ${currentIndex + 1} de ${blocks.size}",
                            fontSize = 12.sp, color = GranaXPColors.Gray600)
                    }
                }
            }
        },
        bottomBar = {
            if (content != null) {
                Surface(modifier = Modifier.navigationBarsPadding(),
                    color = GranaXPColors.Surface, shadowElevation = 4.dp) {
                    Column(Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 12.dp)) {
                        if (isActivity && !canContinue) {
                            Text("Responda corretamente à atividade para continuar.",
                                fontSize = 12.sp, color = GranaXPColors.Gray700)
                            Spacer(Modifier.height(8.dp))
                        }
                        if (isLastStep && !alreadyCompleted && !content.canComplete) {
                            Text("Conclua todas as atividades para finalizar a lição.",
                                fontSize = 12.sp, color = GranaXPColors.Gray700)
                            Spacer(Modifier.height(8.dp))
                        }
                        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                            OutlinedButton(
                                modifier = Modifier.weight(1f),
                                enabled = currentIndex > 0,
                                onClick = { stepIndex = currentIndex - 1 },
                            ) { Text("Anterior") }
                            when {
                                !isLastStep -> Button(
                                    modifier = Modifier.weight(1.5f),
                                    enabled = canContinue,
                                    onClick = { if (canContinue) stepIndex = currentIndex + 1 },
                                ) { Text("Continuar") }
                                alreadyCompleted -> Button(
                                    modifier = Modifier.weight(1.5f),
                                    onClick = onBack,
                                ) { Text("Concluir revisão") }
                                else -> Button(
                                    modifier = Modifier.weight(1.5f),
                                    enabled = content.canComplete,
                                    onClick = onComplete,
                                ) { Text("Concluir lição") }
                            }
                        }
                    }
                }
            }
        },
    ) { paddingValues ->
        key(lesson.id, block?.id) {
            Column(
                modifier = Modifier.fillMaxSize().padding(paddingValues)
                    .verticalScroll(rememberScrollState()).padding(20.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp),
            ) {
                if (content == null) {
                    val error = (contentUiState as? LessonContentUiState.Error)
                        ?.takeIf { it.lessonId == lesson.id }
                    if (error != null) Text(error.message, color = GranaXPColors.Error)
                    else {
                        CircularProgressIndicator()
                        Text("Preparando a lição...", color = GranaXPColors.Gray600)
                    }
                } else {
                    if (currentIndex == 0) {
                        LessonContentHeader(
                            lessonNumber = lessonNumber,
                            duration = lesson.duration,
                            xpReward = lesson.xpReward,
                            description = lesson.description,
                            alreadyCompleted = alreadyCompleted,
                        )
                    }
                    if (block == null) {
                        Text("Nenhum bloco de conteúdo disponível.", color = GranaXPColors.Gray600)
                    } else if (isActivity) {
                        LessonActivityCard(
                            block = block,
                            answerState = content.activityAnswers[block.id] ?: ActivityAnswerState(),
                            isPersistedCompleted = block.id in content.completedActivityIds,
                            onAnswerSelected = { answerId -> onAnswerSelected(block.id, answerId) },
                            onSubmit = { onSubmitAnswer(block.id) },
                        )
                    } else {
                        LessonBlockCard(block)
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
                text = when (block.activityType) {
                    ActivityType.MULTIPLE_CHOICE -> "Múltipla escolha"
                    ActivityType.TRUE_FALSE -> "Verdadeiro ou falso"
                    ActivityType.CLASSIFICATION -> "Classificação"
                    ActivityType.FINANCIAL_SCENARIO -> "Cenário financeiro"
                    ActivityType.CHART_ANALYSIS -> "Análise de gráfico"
                    null -> "Atividade"
                },
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
