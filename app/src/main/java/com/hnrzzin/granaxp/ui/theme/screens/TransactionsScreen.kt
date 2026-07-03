package com.hnrzzin.granaxp.ui.theme.screens

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ArrowDownward
import androidx.compose.material.icons.filled.ArrowUpward
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.*
import androidx.compose.material3.TabRowDefaults.tabIndicatorOffset
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.hnrzzin.granaxp.model.TransactionModel
import com.hnrzzin.granaxp.model.TransactionType
import com.hnrzzin.granaxp.ui.theme.GranaXPColors
import com.hnrzzin.granaxp.ui.theme.components.AppBottomNavigationBar
import com.hnrzzin.granaxp.ui.theme.components.AppTab
import com.hnrzzin.granaxp.viewmodel.*
import kotlinx.coroutines.launch
import androidx.compose.animation.core.tween
enum class FinanceTab(val label: String) {
    HISTORICO("Histórico"), PLANILHA("Planilha"), METAS("Metas"), LEMBRETES("Lembretes")
}

@Composable
fun TransactionsScreen(
    transactionViewModel: TransactionViewModel,
    budgetViewModel: BudgetViewModel,
    goalViewModel: GoalViewModel,
    reminderViewModel: ReminderViewModel,
    onNavigateToHome: () -> Unit,
    onNavigateToLearn: () -> Unit,
    onNavigateToProfile: () -> Unit,

) {
    val pagerState = rememberPagerState(pageCount = { FinanceTab.entries.size })
    val coroutineScope = rememberCoroutineScope()
    val selectedTab = FinanceTab.entries[pagerState.currentPage]

    var showAddTransactionSheet by remember { mutableStateOf(false) }
    var showAddGoalSheet by remember { mutableStateOf(false) }
    var showAddBudgetDialog by remember { mutableStateOf(false) }
    var showAddReminderDialog by remember { mutableStateOf(false) }
    var showAddGoalDialog by remember { mutableStateOf(false) }
    // Controla visibilidade do FAB conforme scroll da lista de Histórico
    val historyListState = rememberLazyListState()
    val fabVisible by remember {
        derivedStateOf {
            val prevIndex = historyListState.firstVisibleItemIndex
            val prevOffset = historyListState.firstVisibleItemScrollOffset
            // FAB some quando rola pra baixo, reaparece ao rolar pra cima ou no topo
            prevIndex == 0 && prevOffset < 10 || !historyListState.isScrollInProgress || prevOffset < 10
        }
    }

    Scaffold(
        containerColor = GranaXPColors.Background,
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

            // FAB só encolhe/some na aba Histórico (é a única com scroll longo relevante ao gesto)
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
        AddBudgetSheet (viewModel = budgetViewModel, onDismiss = { showAddBudgetDialog = false })
    }
    if (showAddReminderDialog) {
        AddReminderDialog(viewModel = reminderViewModel, onDismiss = { showAddReminderDialog = false })
    }
    if (showAddGoalDialog) {
        AddGoalDialog(viewModel = goalViewModel, onDismiss = { showAddGoalDialog = false })
    }
    if (showAddTransactionSheet) {
        AddTransactionSheet(viewModel = transactionViewModel, onDismiss = { showAddTransactionSheet = false })
    }
}


// ---------- Histórico com Sticky Headers por data ----------

@Composable
private fun TransactionHistoryContent(
    viewModel: TransactionViewModel,
    listState: LazyListState
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()

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
                                onDelete = { viewModel.deleteTransaction(transaction) }
                            )
                        }
                    }
                }
            }
        }
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
    onDelete: () -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = androidx.compose.foundation.shape.RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(containerColor = GranaXPColors.White),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
        border = androidx.compose.foundation.BorderStroke(1.dp, GranaXPColors.Gray100)
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
                            androidx.compose.foundation.shape.CircleShape
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
                    "${if (transaction.type == TransactionType.RECEITA) "+" else "-"} R$ ${transaction.amount}",
                    fontSize = 14.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = if (transaction.type == TransactionType.RECEITA) GranaXPColors.Emerald600 else GranaXPColors.Gray800
                )
                IconButton(onClick = onDelete, modifier = Modifier.size(36.dp)) {
                    Icon(Icons.Default.Delete, contentDescription = "Deletar", tint = GranaXPColors.Red600, modifier = Modifier.size(18.dp))
                }
            }
        }
    }
}

@Composable
fun EmptyStateCard(text: String, modifier: Modifier = Modifier) {
    Card(
        modifier = modifier.fillMaxWidth(),
        shape = androidx.compose.foundation.shape.RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(containerColor = GranaXPColors.Background),
        border = androidx.compose.foundation.BorderStroke(1.dp, GranaXPColors.Gray300) // dashed exigiria Modifier customizado
    ) {
        Box(Modifier.fillMaxWidth().padding(vertical = 40.dp), contentAlignment = Alignment.Center) {
            Text(text, fontSize = 14.sp, color = GranaXPColors.Gray500)
        }
    }
}