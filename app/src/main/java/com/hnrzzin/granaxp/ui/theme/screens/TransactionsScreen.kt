package com.hnrzzin.granaxp.ui.theme.screens

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ArrowDownward
import androidx.compose.material.icons.filled.ArrowUpward
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material3.*
import androidx.compose.material3.TabRowDefaults.tabIndicatorOffset
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.hnrzzin.granaxp.model.TransactionModel
import com.hnrzzin.granaxp.model.TransactionType
import com.hnrzzin.granaxp.ui.theme.GranaXPColors
import com.hnrzzin.granaxp.ui.theme.components.AddTransactionSheet
import com.hnrzzin.granaxp.ui.theme.components.AppBottomNavigationBar
import com.hnrzzin.granaxp.ui.theme.components.AppModalBottomSheet
import com.hnrzzin.granaxp.ui.theme.components.AppModalHeader
import com.hnrzzin.granaxp.ui.theme.components.AppTab
import com.hnrzzin.granaxp.ui.theme.components.HomeTopBar
import com.hnrzzin.granaxp.utils.CurrencyVisualTransformation
import com.hnrzzin.granaxp.utils.rawDigitsToAmount
import com.hnrzzin.granaxp.viewmodel.*
import kotlinx.coroutines.launch

enum class FinanceTab(val label: String) {
    HISTORICO("Histórico"), PLANILHA("Planilha"), METAS("Metas"), LEMBRETES("Lembretes")
}

@Composable
fun TransactionsScreen(
    transactionViewModel: TransactionViewModel,
    budgetViewModel: BudgetViewModel,
    goalViewModel: GoalViewModel,
    reminderViewModel: ReminderViewModel,
    userViewModel: UserViewModel,
    onNavigateToHome: () -> Unit,
    onNavigateToLearn: () -> Unit,
    onNavigateToProfile: () -> Unit,
) {
    val pagerState = rememberPagerState(pageCount = { FinanceTab.entries.size })
    val coroutineScope = rememberCoroutineScope()
    val selectedTab = FinanceTab.entries[pagerState.currentPage]
    val userUiState by userViewModel.uiState.collectAsStateWithLifecycle()

    var showAddTransactionSheet by remember { mutableStateOf(false) }
    var showAddBudgetDialog by remember { mutableStateOf(false) }
    var showAddReminderDialog by remember { mutableStateOf(false) }
    var showAddGoalDialog by remember { mutableStateOf(false) }

    val historyListState = rememberLazyListState()
    val fabVisible by remember {
        derivedStateOf {
            val prevIndex = historyListState.firstVisibleItemIndex
            val prevOffset = historyListState.firstVisibleItemScrollOffset
            prevIndex == 0 && prevOffset < 10 || !historyListState.isScrollInProgress || prevOffset < 10
        }
    }

    Scaffold(
        containerColor = GranaXPColors.Background,
        topBar = {
            val level = (userUiState as? UserUiState.Success)?.user?.level ?: 1
            val xp = (userUiState as? UserUiState.Success)?.user?.xp ?: 0
            val nextLevelXp = (userUiState as? UserUiState.Success)?.user?.nextLevelXp ?: 100
            HomeTopBar(
                level = level,
                xp = xp,
                nextLevelXp = nextLevelXp,
                onProfileClick = onNavigateToProfile
            )
        },
        bottomBar = {
            AppBottomNavigationBar(
                selectedTab = AppTab.TRANSACTIONS,
                onNavigateToHome = onNavigateToHome,
                onNavigateToTransactions = { /* já está aqui */ },
                onNavigateToLearn = onNavigateToLearn,
                onNavigateToProfile = onNavigateToProfile
            )
        },
        floatingActionButton = {
            val (fabColor, fabAction, fabText) = when (selectedTab) {
                FinanceTab.HISTORICO -> Triple(GranaXPColors.Primary, { showAddTransactionSheet = true }, "Nova transação")
                FinanceTab.PLANILHA -> Triple(GranaXPColors.Blue600, { showAddBudgetDialog = true }, "Novo gasto")
                FinanceTab.METAS -> Triple(GranaXPColors.Purple600, { showAddGoalDialog = true }, "Nova meta")
                FinanceTab.LEMBRETES -> Triple(GranaXPColors.Rose600, { showAddReminderDialog = true }, "Novo lembrete")
            }

            val shouldShow = selectedTab != FinanceTab.HISTORICO || fabVisible

            AnimatedVisibility(
                visible = shouldShow,
                enter = fadeIn(tween(200)) + scaleIn(tween(200)),
                exit = fadeOut(tween(200)) + scaleOut(tween(200))
            ) {
                ExtendedFloatingActionButton(
                    onClick = fabAction,
                    containerColor = fabColor,
                    contentColor = GranaXPColors.White,
                    elevation = FloatingActionButtonDefaults.elevation(defaultElevation = 6.dp, pressedElevation = 12.dp)
                ) {
                    Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.size(20.dp))
                    Spacer(Modifier.width(8.dp))
                    Text(fabText, fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
                }
            }
        }
    ) { paddingValues ->
        Column(modifier = Modifier.fillMaxSize().padding(paddingValues)) {
            // Header sticky com título + tabs
            Surface(color = GranaXPColors.White, shadowElevation = 2.dp) {
                Column {
                    Text(
                        "Gestão Financeira",
                        fontWeight = FontWeight.Bold,
                        fontSize = 21.sp,
                        color = GranaXPColors.Gray800,
                        modifier = Modifier.padding(16.dp, 16.dp, 16.dp, 16.dp)
                    )

                    TabRow(
                        selectedTabIndex = pagerState.currentPage,
                        containerColor = GranaXPColors.White,
                        contentColor = GranaXPColors.Primary,
                        indicator = { tabPositions ->
                            TabRowDefaults.SecondaryIndicator(
                                modifier = Modifier.tabIndicatorOffset(tabPositions[pagerState.currentPage]),
                                height = 3.dp,
                                color = GranaXPColors.Primary
                            )
                        }
                    ) {
                        FinanceTab.entries.forEachIndexed { index, tab ->
                            Tab(
                                selected = pagerState.currentPage == index,
                                onClick = {
                                    coroutineScope.launch { pagerState.animateScrollToPage(index) }
                                },
                                text = {
                                    Text(
                                        tab.label,
                                        fontSize = 14.sp,
                                        fontWeight = FontWeight.Medium,
                                        color = if (pagerState.currentPage == index) GranaXPColors.Primary else GranaXPColors.Gray500
                                    )
                                }
                            )
                        }
                    }
                }
            }

            // Conteúdo com swipe 1:1
            HorizontalPager(
                state = pagerState,
                modifier = Modifier.weight(1f)
            ) { page ->
                when (FinanceTab.entries[page]) {
                    FinanceTab.HISTORICO -> TransactionHistoryContent(transactionViewModel, historyListState)
                    FinanceTab.PLANILHA -> BudgetContent(budgetViewModel)
                    FinanceTab.METAS -> GoalContent(goalViewModel)
                    FinanceTab.LEMBRETES -> ReminderContent(reminderViewModel)
                }
            }
        }
    }

    if (showAddBudgetDialog) {
        AddBudgetSheet(viewModel = budgetViewModel, onDismiss = { showAddBudgetDialog = false })
    }
    if (showAddReminderDialog) {
        AddReminderDialog(viewModel = reminderViewModel, onDismiss = { showAddReminderDialog = false })
    }
    if (showAddGoalDialog) {
        AddGoalSheet(viewModel = goalViewModel, onDismiss = { showAddGoalDialog = false })
    }
    if (showAddTransactionSheet) {
        AddTransactionSheet(
            viewModel = transactionViewModel,
            onDismiss = { showAddTransactionSheet = false }
        )
    }
}

// ---------- Histórico com Sticky Headers por data ----------

@Composable
private fun TransactionHistoryContent(
    viewModel: TransactionViewModel,
    listState: LazyListState
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    var transactionToEdit by remember { mutableStateOf<TransactionModel?>(null) }

    when (val state = uiState) {
        is TransactionUiState.Loading -> {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                CircularProgressIndicator(color = GranaXPColors.Primary)
            }
        }
        is TransactionUiState.Error -> {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Text(state.message, color = GranaXPColors.Error, modifier = Modifier.padding(16.dp))
            }
        }
        is TransactionUiState.Success -> {
            if (state.transactions.isEmpty()) {
                EmptyStateCard("Nenhuma transação registrada", Modifier.padding(16.dp))
            } else {
                val grouped = state.transactions.groupBy { formatDateLabel(it.date) }

                LazyColumn(
                    state = listState,
                    modifier = Modifier.fillMaxSize().background(GranaXPColors.Background),
                    contentPadding = PaddingValues(16.dp, 8.dp, 16.dp, 96.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    grouped.forEach { (dateLabel, transactions) ->
                        stickyHeader(key = dateLabel) {
                            Surface(color = GranaXPColors.Background, modifier = Modifier.fillMaxWidth()) {
                                Text(
                                    dateLabel,
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.SemiBold,
                                    color = GranaXPColors.Gray500,
                                    modifier = Modifier.padding(vertical = 6.dp)
                                )
                            }
                        }
                        items(transactions, key = { it.id }) { transaction ->
                            TransactionListItem(
                                transaction = transaction,
                                onEdit = { transactionToEdit = transaction },
                                onDelete = { viewModel.deleteTransaction(transaction) }
                            )
                        }
                    }
                }
            }
        }
    }

    transactionToEdit?.let { transaction ->
        EditTransactionSheet(
            transaction = transaction,
            viewModel = viewModel,
            onDismiss = { transactionToEdit = null }
        )
    }
}

// Ajuste conforme o campo real de data em TransactionModel (assumindo com.google.firebase.Timestamp)
private fun formatDateLabel(timestamp: com.google.firebase.Timestamp): String {
    val date = timestamp.toDate()
    val today = java.util.Calendar.getInstance()
    val cal = java.util.Calendar.getInstance().apply { time = date }

    val isToday = today.get(java.util.Calendar.DAY_OF_YEAR) == cal.get(java.util.Calendar.DAY_OF_YEAR) &&
            today.get(java.util.Calendar.YEAR) == cal.get(java.util.Calendar.YEAR)

    today.add(java.util.Calendar.DAY_OF_YEAR, -1)
    val isYesterday = today.get(java.util.Calendar.DAY_OF_YEAR) == cal.get(java.util.Calendar.DAY_OF_YEAR) &&
            today.get(java.util.Calendar.YEAR) == cal.get(java.util.Calendar.YEAR)

    return when {
        isToday -> "Hoje"
        isYesterday -> "Ontem"
        else -> {
            val sdf = java.text.SimpleDateFormat("d 'de' MMMM", java.util.Locale("pt", "BR"))
            sdf.format(date)
        }
    }
}

@Composable
fun TransactionListItem(
    transaction: TransactionModel,
    onEdit: () -> Unit,
    onDelete: () -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(containerColor = GranaXPColors.White),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
        border = BorderStroke(1.dp, GranaXPColors.Gray100)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(16.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.weight(1f)) {
                val isReceita = transaction.type == TransactionType.RECEITA
                Box(
                    modifier = Modifier
                        .size(40.dp)
                        .background(
                            if (isReceita) GranaXPColors.Emerald100 else GranaXPColors.Red100,
                            CircleShape
                        ),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        if (isReceita) Icons.Default.ArrowUpward else Icons.Default.ArrowDownward,
                        contentDescription = null,
                        tint = if (isReceita) GranaXPColors.Emerald600 else GranaXPColors.Red600,
                        modifier = Modifier.size(18.dp)
                    )
                }
                Spacer(Modifier.width(12.dp))
                Column {
                    Text(transaction.title, fontSize = 14.sp, fontWeight = FontWeight.Medium, color = GranaXPColors.Gray800)
                    Text(transaction.category, fontSize = 12.sp, color = GranaXPColors.Gray500)
                }
            }
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    "${if (transaction.type == TransactionType.RECEITA) "+" else "-"} R$ %.2f".format(transaction.amount),
                    fontSize = 14.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = if (transaction.type == TransactionType.RECEITA) GranaXPColors.Emerald600 else GranaXPColors.Gray800
                )
                IconButton(onClick = onEdit, modifier = Modifier.size(32.dp)) {
                    Icon(Icons.Default.Edit, contentDescription = "Editar", tint = GranaXPColors.Gray500, modifier = Modifier.size(16.dp))
                }
                IconButton(onClick = onDelete, modifier = Modifier.size(32.dp)) {
                    Icon(Icons.Default.Delete, contentDescription = "Deletar", tint = GranaXPColors.Red600, modifier = Modifier.size(16.dp))
                }
            }
        }
    }
}

@Composable
fun EmptyStateCard(text: String, modifier: Modifier = Modifier) {
    Card(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(containerColor = GranaXPColors.Background),
        border = BorderStroke(1.dp, GranaXPColors.Gray300)
    ) {
        Box(Modifier.fillMaxWidth().padding(vertical = 40.dp), contentAlignment = Alignment.Center) {
            Text(text, fontSize = 14.sp, color = GranaXPColors.Gray500)
        }
    }
}

@Composable
fun EditTransactionSheet(
    transaction: TransactionModel,
    viewModel: TransactionViewModel,
    onDismiss: () -> Unit
) {
    val saveState by viewModel.saveState.collectAsState()
    var amount by remember { mutableStateOf((transaction.amount * 100).toLong().toString()) }
    var title by remember { mutableStateOf(transaction.title) }
    var category by remember { mutableStateOf(transaction.category) }

    val typeColor = if (transaction.type == TransactionType.DESPESA) GranaXPColors.Red500 else GranaXPColors.Primary

    LaunchedEffect(saveState) {
        if (saveState is TransactionSaveState.Success) onDismiss()
    }

    AppModalBottomSheet(onDismiss = onDismiss) {
        AppModalHeader(title = "Editar Transação", onClose = onDismiss)

        Text(
            if (transaction.type == TransactionType.RECEITA) "Receita" else "Despesa",
            fontSize = 12.sp,
            fontWeight = FontWeight.Medium,
            color = typeColor
        )
        Spacer(Modifier.height(4.dp))
        Text(
            "Para mudar o tipo, exclua e crie uma nova transação.",
            fontSize = 11.sp,
            color = GranaXPColors.Gray500
        )

        Spacer(Modifier.height(16.dp))

        OutlinedTextField(
            value = amount,
            onValueChange = { input -> amount = input.filter { it.isDigit() } },
            label = { Text("Valor (R$)") },
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
            visualTransformation = CurrencyVisualTransformation(),
            modifier = Modifier.fillMaxWidth(),
            colors = OutlinedTextFieldDefaults.colors(focusedBorderColor = typeColor)
        )

        Spacer(Modifier.height(12.dp))

        OutlinedTextField(
            value = title,
            onValueChange = { title = it },
            label = { Text("Título") },
            modifier = Modifier.fillMaxWidth(),
            colors = OutlinedTextFieldDefaults.colors(focusedBorderColor = typeColor)
        )

        Spacer(Modifier.height(12.dp))

        OutlinedTextField(
            value = category,
            onValueChange = { category = it },
            label = { Text("Categoria") },
            modifier = Modifier.fillMaxWidth(),
            colors = OutlinedTextFieldDefaults.colors(focusedBorderColor = typeColor)
        )

        if (saveState is TransactionSaveState.Error) {
            Spacer(Modifier.height(8.dp))
            Text((saveState as TransactionSaveState.Error).message, color = GranaXPColors.Error, fontSize = 12.sp)
        }

        Spacer(Modifier.height(24.dp))

        Button(
            onClick = {
                viewModel.updateTransaction(
                    transaction.copy(
                        title = title,
                        amount = rawDigitsToAmount(amount),
                        category = category
                    )
                )
            },
            enabled = saveState !is TransactionSaveState.Saving && amount.isNotBlank() && title.isNotBlank(),
            modifier = Modifier.fillMaxWidth().height(52.dp),
            colors = ButtonDefaults.buttonColors(containerColor = typeColor),
            shape = RoundedCornerShape(8.dp)
        ) {
            if (saveState is TransactionSaveState.Saving) {
                CircularProgressIndicator(color = GranaXPColors.White, modifier = Modifier.size(20.dp))
            } else {
                Text("Salvar Alterações", color = GranaXPColors.White, fontSize = 16.sp, fontWeight = FontWeight.SemiBold)
            }
        }
    }
}