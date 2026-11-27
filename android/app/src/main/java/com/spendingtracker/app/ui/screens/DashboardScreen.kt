package com.spendingtracker.app.ui.screens

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ExitToApp
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowLeft
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.automirrored.filled.TrendingUp
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.CameraAlt
import androidx.compose.material.icons.filled.DarkMode
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.Fingerprint
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Image
import androidx.compose.material.icons.filled.ReceiptLong
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.spendingtracker.app.data.BudgetRecord
import com.spendingtracker.app.data.CategorySpending
import com.spendingtracker.app.data.MonthlyTrendPoint
import com.spendingtracker.app.data.TransactionRecord
import com.spendingtracker.app.ui.UiState
import com.spendingtracker.app.ui.theme.categoryAccentColor
import java.math.BigDecimal
import java.text.NumberFormat
import java.time.LocalDate
import java.time.Month
import java.time.YearMonth
import java.time.format.DateTimeFormatter
import java.time.format.TextStyle
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DashboardScreen(
    state: UiState,
    onRefresh: () -> Unit,
    onPreviousMonth: () -> Unit,
    onNextMonth: () -> Unit,
    onScanBill: () -> Unit,
    onPickScreenshot: () -> Unit,
    onPickStatement: () -> Unit,
    onAddTransactionClick: () -> Unit,
    onSetBudgetClick: () -> Unit,
    onNewCategoryClick: () -> Unit,
    onAddGoalClick: () -> Unit,
    onAddIouClick: () -> Unit,
    onSettleIouClick: (String) -> Unit,
    onGenerateInsightsClick: () -> Unit,
    onZeroBalanceClick: () -> Unit,
    onUseTemplateClick: (String) -> Unit,
    onAddTemplateClick: () -> Unit,
    onAddWalletClick: () -> Unit,
    onTransferWalletClick: () -> Unit,
    onAddRuleClick: () -> Unit,
    onDeleteRuleClick: (String) -> Unit,
    onSetRangeClick: (String, String) -> Unit,
    onClearRangeClick: () -> Unit,
    onExportBackupClick: () -> Unit,
    onImportBackupClick: () -> Unit,
    onCycleThemeClick: () -> Unit,
    onTogglePrivacy: () -> Unit,
    onReviewPendingClick: () -> Unit,
    onFlushOfflineClick: () -> Unit,
    onToggleBiometric: () -> Unit,
    onTransactionClick: (TransactionRecord) -> Unit,
    onLogout: () -> Unit,
    onClearMessages: () -> Unit
) {
    val snackbarHostState = remember { SnackbarHostState() }
    val monthFormatter = remember { DateTimeFormatter.ofPattern("MMMM yyyy", Locale.getDefault()) }

    var selectedTab by remember { mutableStateOf(0) } // 0=Home, 1=Ledger, 2=Plan, 3=More
    var searchQuery by remember { mutableStateOf("") }
    var typeFilter by remember { mutableStateOf("all") } // all | debit | credit
    var categoryFilter by remember { mutableStateOf<String?>(null) }
    var tagFilter by remember { mutableStateOf<String?>(null) }

    fun maskAmt(raw: String): String = if (state.privacyMode) "••••••" else raw

    LaunchedEffect(state.errorMessage, state.statusMessage) {
        val msg = state.errorMessage ?: state.statusMessage
        if (!msg.isNullOrBlank()) {
            snackbarHostState.showSnackbar(msg)
            onClearMessages()
        }
    }

    val baseCurrency = state.monthlySpending?.baseCurrency ?: "VND"
    val activeCategories = remember(state.transactions) {
        state.transactions.map { it.categoryName ?: "Uncategorized" }.distinct().sorted()
    }
    val filteredTxs = remember(state.transactions, searchQuery, typeFilter, categoryFilter, tagFilter) {
        val q = searchQuery.trim().lowercase()
        state.transactions.filter { tx ->
            val typeOk = typeFilter == "all" || tx.txType.equals(typeFilter, ignoreCase = true)
            val catOk = categoryFilter == null || (tx.categoryName ?: "Uncategorized") == categoryFilter
            val tagOk = tagFilter == null || tx.tags.contains(tagFilter)
            val queryOk = q.isEmpty() ||
                tx.vendor.lowercase().contains(q) ||
                (tx.categoryName ?: "").lowercase().contains(q) ||
                (tx.walletName ?: "").lowercase().contains(q) ||
                tx.tags.any { it.lowercase().contains(q.removePrefix("#")) } ||
                (tx.description ?: "").lowercase().contains(q)
            typeOk && catOk && tagOk && queryOk
        }
    }
    val groupedByDate = remember(filteredTxs) {
        filteredTxs.groupBy { it.txDate }.entries.toList()
    }

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            Column {
                TopAppBar(
                    colors = TopAppBarDefaults.topAppBarColors(
                        containerColor = MaterialTheme.colorScheme.surface
                    ),
                    title = {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(28.dp)
                                    .clip(RoundedCornerShape(8.dp))
                                    .background(MaterialTheme.colorScheme.primary),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    imageVector = Icons.AutoMirrored.Filled.TrendingUp,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.onPrimary,
                                    modifier = Modifier.size(16.dp)
                                )
                            }
                            Column {
                                Text(
                                    text = "Spending Tracker",
                                    style = MaterialTheme.typography.titleMedium
                                )
                                if (!state.userEmail.isNullOrBlank()) {
                                    Text(
                                        text = state.userEmail,
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                            }
                        }
                    },
                    actions = {
                        IconButton(onClick = onTogglePrivacy) {
                            Icon(
                                imageVector = if (state.privacyMode) Icons.Default.VisibilityOff else Icons.Default.Visibility,
                                contentDescription = if (state.privacyMode) "Show Amounts" else "Hide Amounts",
                                tint = if (state.privacyMode) {
                                    MaterialTheme.colorScheme.primary
                                } else {
                                    MaterialTheme.colorScheme.onSurfaceVariant
                                }
                            )
                        }
                        IconButton(onClick = onCycleThemeClick) {
                            Icon(
                                imageVector = Icons.Default.DarkMode,
                                contentDescription = "Switch Theme (${state.themeMode})",
                                tint = if (state.themeMode != "system") {
                                    MaterialTheme.colorScheme.primary
                                } else {
                                    MaterialTheme.colorScheme.onSurfaceVariant
                                }
                            )
                        }
                        IconButton(onClick = onToggleBiometric) {
                            Icon(
                                imageVector = Icons.Default.Fingerprint,
                                contentDescription = "Toggle Biometric Lock",
                                tint = if (state.biometricEnabled) {
                                    MaterialTheme.colorScheme.primary
                                } else {
                                    MaterialTheme.colorScheme.onSurfaceVariant
                                }
                            )
                        }
                        IconButton(onClick = onLogout) {
                            Icon(
                                imageVector = Icons.AutoMirrored.Filled.ExitToApp,
                                contentDescription = "Sign Out",
                                tint = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                )
                HorizontalDivider(color = MaterialTheme.colorScheme.outline)
            }
        },
        floatingActionButton = {
            FloatingActionButton(
                onClick = onAddTransactionClick,
                containerColor = MaterialTheme.colorScheme.primary,
                contentColor = MaterialTheme.colorScheme.onPrimary,
                shape = RoundedCornerShape(16.dp)
            ) {
                Icon(Icons.Default.Add, contentDescription = "Add Transaction")
            }
        },
        bottomBar = {
            NavigationBar(
                containerColor = MaterialTheme.colorScheme.surface,
                tonalElevation = 0.dp
            ) {
                NavigationBarItem(
                    selected = selectedTab == 0,
                    onClick = { selectedTab = 0 },
                    icon = { Icon(Icons.Default.Home, contentDescription = "Home") },
                    label = { Text("Home") }
                )
                NavigationBarItem(
                    selected = selectedTab == 1,
                    onClick = { selectedTab = 1 },
                    icon = { Icon(Icons.Default.ReceiptLong, contentDescription = "Ledger") },
                    label = { Text("Ledger") }
                )
                NavigationBarItem(
                    selected = selectedTab == 2,
                    onClick = { selectedTab = 2 },
                    icon = { Icon(Icons.AutoMirrored.Filled.TrendingUp, contentDescription = "Plan") },
                    label = { Text("Plan") }
                )
                NavigationBarItem(
                    selected = selectedTab == 3,
                    onClick = { selectedTab = 3 },
                    icon = { Icon(Icons.Default.Tune, contentDescription = "More") },
                    label = { Text("More") }
                )
            }
        },
        snackbarHost = { SnackbarHost(snackbarHostState) }
    ) { padding ->
        PullToRefreshBox(
            isRefreshing = state.isLoading,
            onRefresh = onRefresh,
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
        ) {
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(horizontal = 16.dp, vertical = 16.dp),
                verticalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                if (state.isUploading) {
                    item {
                        Column(modifier = Modifier.fillMaxWidth()) {
                            LinearProgressIndicator(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clip(RoundedCornerShape(99.dp))
                            )
                            Spacer(modifier = Modifier.height(6.dp))
                            Text(
                                text = "Extracting transactions with Gemini AI...",
                                style = MaterialTheme.typography.labelMedium,
                                color = MaterialTheme.colorScheme.primary
                            )
                        }
                    }
                }

                // Offline Queue Banner
                if (state.offlineQueueCount > 0) {
                    item {
                        Surface(
                            onClick = onFlushOfflineClick,
                            color = MaterialTheme.colorScheme.primaryContainer,
                            shape = RoundedCornerShape(12.dp),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    text = "${state.offlineQueueCount} offline receipt(s) queued — tap to upload",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onPrimaryContainer,
                                    fontWeight = FontWeight.Medium
                                )
                            }
                        }
                    }
                }

                // Pending Drafts Banner
                val pendingCount = state.monthlySpending?.pendingDraftCount ?: 0L
                if (pendingCount > 0L) {
                    item {
                        Surface(
                            onClick = onReviewPendingClick,
                            color = Color(0xFFFFFBEB),
                            shape = RoundedCornerShape(12.dp),
                            border = BorderStroke(1.dp, Color(0xFFFDE68A)),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    text = "$pendingCount unreviewed draft(s) — tap to review",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = Color(0xFFB45309),
                                    fontWeight = FontWeight.SemiBold
                                )
                            }
                        }
                    }
                }

                // Running Balance Card (with Carryover) — shown on Home tab
                if (selectedTab == 0) {
                item {
                    Card(
                        shape = RoundedCornerShape(16.dp),
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(20.dp)
                        ) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                val runningVal = state.monthlySpending?.cumulativeBalance?.toBigDecimalOrNull() ?: BigDecimal.ZERO
                                val statusText = when {
                                    runningVal < BigDecimal.ZERO -> "IN DEBT"
                                    runningVal > BigDecimal.ZERO -> "SURPLUS"
                                    else -> "RUNNING BALANCE"
                                }
                                val statusColor = when {
                                    runningVal < BigDecimal.ZERO -> MaterialTheme.colorScheme.error
                                    runningVal > BigDecimal.ZERO -> MaterialTheme.colorScheme.primary
                                    else -> MaterialTheme.colorScheme.onSurfaceVariant
                                }
                                Text(
                                    text = statusText,
                                    style = MaterialTheme.typography.labelMedium,
                                    color = statusColor
                                )
                                Surface(
                                    shape = RoundedCornerShape(99.dp),
                                    color = MaterialTheme.colorScheme.surfaceVariant,
                                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline)
                                ) {
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        IconButton(
                                            onClick = onPreviousMonth,
                                            modifier = Modifier.size(32.dp)
                                        ) {
                                            Icon(
                                                Icons.AutoMirrored.Filled.KeyboardArrowLeft,
                                                contentDescription = "Previous Month",
                                                modifier = Modifier.size(18.dp)
                                            )
                                        }
                                        Text(
                                            text = state.selectedMonth.format(monthFormatter),
                                            style = MaterialTheme.typography.labelLarge,
                                            modifier = Modifier.padding(horizontal = 4.dp)
                                        )
                                        IconButton(
                                            onClick = onNextMonth,
                                            modifier = Modifier.size(32.dp)
                                        ) {
                                            Icon(
                                                Icons.AutoMirrored.Filled.KeyboardArrowRight,
                                                contentDescription = "Next Month",
                                                modifier = Modifier.size(18.dp)
                                            )
                                        }
                                    }
                                }
                            }

                            Spacer(modifier = Modifier.height(10.dp))
                            val runningVal = state.monthlySpending?.cumulativeBalance?.toBigDecimalOrNull() ?: BigDecimal.ZERO
                            val runningPrefix = if (runningVal > BigDecimal.ZERO) "+" else ""
                            val runningColor = when {
                                runningVal < BigDecimal.ZERO -> MaterialTheme.colorScheme.error
                                runningVal > BigDecimal.ZERO -> MaterialTheme.colorScheme.primary
                                else -> MaterialTheme.colorScheme.onSurface
                            }
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    text = maskAmt("$runningPrefix${formatAmount(state.monthlySpending?.cumulativeBalance ?: "0")}"),
                                    style = MaterialTheme.typography.displaySmall,
                                    color = runningColor,
                                    modifier = Modifier.weight(1f)
                                )
                                val canZero = runningVal.compareTo(BigDecimal.ZERO) != 0
                                Surface(
                                    onClick = { if (canZero) onZeroBalanceClick() },
                                    enabled = canZero,
                                    shape = RoundedCornerShape(99.dp),
                                    color = when {
                                        !canZero -> MaterialTheme.colorScheme.surfaceVariant
                                        runningVal < BigDecimal.ZERO -> MaterialTheme.colorScheme.errorContainer
                                        else -> MaterialTheme.colorScheme.primaryContainer
                                    },
                                    border = BorderStroke(
                                        1.5.dp,
                                        when {
                                            !canZero -> MaterialTheme.colorScheme.outline
                                            runningVal < BigDecimal.ZERO -> MaterialTheme.colorScheme.error
                                            else -> MaterialTheme.colorScheme.primary
                                        }
                                    )
                                ) {
                                    Text(
                                        text = "Zero Out",
                                        style = MaterialTheme.typography.labelLarge,
                                        fontWeight = FontWeight.SemiBold,
                                        color = when {
                                            !canZero -> MaterialTheme.colorScheme.onSurfaceVariant
                                            runningVal < BigDecimal.ZERO -> MaterialTheme.colorScheme.onErrorContainer
                                            else -> MaterialTheme.colorScheme.onPrimaryContainer
                                        },
                                        modifier = Modifier.padding(horizontal = 14.dp, vertical = 7.dp)
                                    )
                                }
                            }
                            Spacer(modifier = Modifier.height(12.dp))
                            val carryVal = state.monthlySpending?.carryForwardBalance?.toBigDecimalOrNull() ?: BigDecimal.ZERO
                            val carryPrefix = if (carryVal > BigDecimal.ZERO) "+" else ""
                            val carryColor = when {
                                carryVal < BigDecimal.ZERO -> MaterialTheme.colorScheme.error
                                carryVal > BigDecimal.ZERO -> MaterialTheme.colorScheme.primary
                                else -> MaterialTheme.colorScheme.onSurface
                            }
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(16.dp)
                            ) {
                                Column {
                                    Text("Carryover", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                    Text(
                                        text = maskAmt("$carryPrefix${formatAmount(state.monthlySpending?.carryForwardBalance ?: "0")}"),
                                        style = MaterialTheme.typography.bodyMedium.copy(fontFeatureSettings = "tnum"),
                                        fontWeight = FontWeight.SemiBold,
                                        color = carryColor
                                    )
                                }
                                Column {
                                    Text("Debit", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                    Text(
                                        text = maskAmt("-${formatAmount(state.monthlySpending?.totalSpent ?: "0")}"),
                                        style = MaterialTheme.typography.bodyMedium.copy(fontFeatureSettings = "tnum"),
                                        fontWeight = FontWeight.SemiBold
                                    )
                                }
                                Column {
                                    Text("Credit", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                    Text(
                                        text = maskAmt("+${formatAmount(state.monthlySpending?.totalCredit ?: "0")}"),
                                        style = MaterialTheme.typography.bodyMedium.copy(fontFeatureSettings = "tnum"),
                                        fontWeight = FontWeight.SemiBold,
                                        color = MaterialTheme.colorScheme.primary
                                    )
                                }
                            }
                        }
                    }
                }

                // Capture Action Tiles
                item {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        CaptureTile(
                            icon = Icons.Default.CameraAlt,
                            label = "Scan Bill",
                            enabled = !state.isUploading,
                            onClick = onScanBill,
                            modifier = Modifier.weight(1f)
                        )
                        CaptureTile(
                            icon = Icons.Default.Image,
                            label = "Screenshot",
                            enabled = !state.isUploading,
                            onClick = onPickScreenshot,
                            modifier = Modifier.weight(1f)
                        )
                        CaptureTile(
                            icon = Icons.Default.Description,
                            label = "PDF / CSV",
                            enabled = !state.isUploading,
                            onClick = onPickStatement,
                            modifier = Modifier.weight(1f)
                        )
                    }
                }

                }

                // 6-Month Cash Flow Trend Chart — Plan tab
                if (selectedTab == 2) {
                if (state.trend.isNotEmpty()) {
                    item {
                        CashFlowTrendCard(points = state.trend)
                    }
                }

                // Monthly Budgets Card
                item {
                    BudgetsCard(
                        budgets = state.budgets,
                        selectedMonth = state.selectedMonth,
                        onSetBudgetClick = onSetBudgetClick,
                        onNewCategoryClick = onNewCategoryClick
                    )
                }

                // Recurring Bills Radar
                val recurringItems = state.recurring?.items.orEmpty()
                if (recurringItems.isNotEmpty()) {
                    item {
                        Card(
                            shape = RoundedCornerShape(16.dp),
                            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Column(
                                modifier = Modifier.padding(18.dp),
                                verticalArrangement = Arrangement.spacedBy(10.dp)
                            ) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Text(
                                        text = "RECURRING BILLS",
                                        style = MaterialTheme.typography.labelMedium,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                    Text(
                                        text = "${formatAmount(state.recurring?.monthlyFixedTotal ?: "0")} $baseCurrency/mo",
                                        style = MaterialTheme.typography.labelMedium.copy(fontFeatureSettings = "tnum"),
                                        color = MaterialTheme.colorScheme.primary
                                    )
                                }
                                recurringItems.take(5).forEach { bill ->
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.SpaceBetween
                                    ) {
                                        Column(modifier = Modifier.weight(1f)) {
                                            Text(bill.vendor, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Medium)
                                            Text(
                                                "${bill.categoryName} · Next: ${bill.nextExpectedDate}",
                                                style = MaterialTheme.typography.labelSmall,
                                                color = MaterialTheme.colorScheme.onSurfaceVariant
                                            )
                                        }
                                        Text(
                                            text = formatAmount(bill.avgAmount),
                                            style = MaterialTheme.typography.bodyMedium.copy(fontFeatureSettings = "tnum"),
                                            fontWeight = FontWeight.SemiBold
                                        )
                                    }
                                }
                            }
                        }
                    }
                }

                // AI Monthly Spending Coach
                item {
                    Card(
                        shape = RoundedCornerShape(16.dp),
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(
                            modifier = Modifier.padding(18.dp),
                            verticalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    text = "AI MONTHLY COACH",
                                    style = MaterialTheme.typography.labelMedium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                                TextButton(
                                    onClick = onGenerateInsightsClick,
                                    enabled = !state.isLoadingInsights
                                ) {
                                    Text(if (state.isLoadingInsights) "Thinking..." else "Generate")
                                }
                            }
                            if (state.insightsTips.isEmpty()) {
                                Text(
                                    text = "Tap Generate for 3 AI tips on your spending and debt payoff pace (1 free Flash-Lite request).",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            } else {
                                state.insightsTips.forEachIndexed { idx, tip ->
                                    Surface(
                                        color = MaterialTheme.colorScheme.primaryContainer,
                                        shape = RoundedCornerShape(10.dp),
                                        modifier = Modifier.fillMaxWidth()
                                    ) {
                                        Text(
                                            text = "${idx + 1}. $tip",
                                            style = MaterialTheme.typography.bodySmall,
                                            color = MaterialTheme.colorScheme.onPrimaryContainer,
                                            modifier = Modifier.padding(10.dp)
                                        )
                                    }
                                }
                            }
                        }
                    }
                }

                // Goals & Debt Payoff Card
                item {
                    Card(
                        shape = RoundedCornerShape(16.dp),
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(
                            modifier = Modifier.padding(18.dp),
                            verticalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    text = "GOALS & DEBT PAYOFF",
                                    style = MaterialTheme.typography.labelMedium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                                TextButton(onClick = onAddGoalClick) { Text("+ Goal") }
                            }
                            if (state.goals.isEmpty()) {
                                Text(
                                    text = "No goals yet. Tap + Goal to track debt payoff or savings targets.",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            } else {
                                state.goals.forEach { g ->
                                    val cur = g.currentAmount.toBigDecimalOrNull() ?: BigDecimal.ZERO
                                    val tgt = g.targetAmount.toBigDecimalOrNull()?.takeIf { it > BigDecimal.ZERO } ?: BigDecimal.ONE
                                    val ratio = (cur.toFloat() / tgt.toFloat()).coerceIn(0f, 1f)
                                    val pct = (ratio * 100).toInt()
                                    val color = if (g.goalType == "debt_payoff") Color(0xFFD97706) else MaterialTheme.colorScheme.primary
                                    Column {
                                        Row(
                                            modifier = Modifier.fillMaxWidth(),
                                            horizontalArrangement = Arrangement.SpaceBetween
                                        ) {
                                            Text(g.name, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Medium)
                                            Text(
                                                "${formatAmount(g.currentAmount)} / ${formatAmount(g.targetAmount)} ($pct%)",
                                                style = MaterialTheme.typography.bodySmall.copy(fontFeatureSettings = "tnum"),
                                                fontWeight = FontWeight.SemiBold
                                            )
                                        }
                                        Spacer(modifier = Modifier.height(4.dp))
                                        LinearProgressIndicator(
                                            progress = { ratio },
                                            color = color,
                                            trackColor = MaterialTheme.colorScheme.surfaceVariant,
                                            strokeCap = StrokeCap.Round,
                                            modifier = Modifier.fillMaxWidth().height(6.dp)
                                        )
                                    }
                                }
                            }
                        }
                    }
                }

                // Split / IOUs ("Who Owes Me") Card
                item {
                    Card(
                        shape = RoundedCornerShape(16.dp),
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(
                            modifier = Modifier.padding(18.dp),
                            verticalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    text = "SPLIT & IOUS",
                                    style = MaterialTheme.typography.labelMedium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                                TextButton(onClick = onAddIouClick) { Text("+ IOU") }
                            }
                            if (state.ious.isEmpty()) {
                                Text(
                                    text = "No IOUs yet. Track money lent to friends or owed to others.",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            } else {
                                state.ious.forEach { iou ->
                                    val isOwed = iou.direction == "owed_to_me"
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Column(modifier = Modifier.weight(1f)) {
                                            Text(
                                                text = if (iou.settled) "${iou.personName} (Settled)" else iou.personName,
                                                style = MaterialTheme.typography.bodyMedium,
                                                fontWeight = FontWeight.Medium
                                            )
                                            Text(
                                                text = if (isOwed) "Owes me" else "I owe",
                                                style = MaterialTheme.typography.labelSmall,
                                                color = MaterialTheme.colorScheme.onSurfaceVariant
                                            )
                                        }
                                        Row(verticalAlignment = Alignment.CenterVertically) {
                                            Text(
                                                text = "${if (isOwed) "+" else "-"}${formatAmount(iou.amount)} ${iou.currency}",
                                                style = MaterialTheme.typography.bodyMedium.copy(fontFeatureSettings = "tnum"),
                                                fontWeight = FontWeight.SemiBold,
                                                color = if (isOwed) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.error
                                            )
                                            TextButton(onClick = { onSettleIouClick(iou.id) }) {
                                                Text(if (iou.settled) "Undo" else "Settle")
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }
                }

                }

                // Quick-Add Templates Strip (#1) — Home tab
                if (selectedTab == 0) {
                item {
                    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = "QUICK ADD",
                                style = MaterialTheme.typography.labelMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.padding(horizontal = 4.dp)
                            )
                            TextButton(onClick = onAddTemplateClick) { Text("+ Template") }
                        }
                        if (state.templates.isNotEmpty()) {
                            LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                items(state.templates, key = { it.id }) { tpl ->
                                    FilterChip(
                                        selected = false,
                                        onClick = { onUseTemplateClick(tpl.id) },
                                        label = { Text("${tpl.label} · ${formatAmount(tpl.amount)}") }
                                    )
                                }
                            }
                        }
                    }
                }

                // Wallets & Accounts Card (#2)
                item {
                    Card(
                        shape = RoundedCornerShape(16.dp),
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(
                            modifier = Modifier.padding(18.dp),
                            verticalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    text = "WALLETS & ACCOUNTS",
                                    style = MaterialTheme.typography.labelMedium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                                Row {
                                    if (state.wallets.size >= 2) {
                                        TextButton(onClick = onTransferWalletClick) { Text("Transfer") }
                                    }
                                    TextButton(onClick = onAddWalletClick) { Text("+ Wallet") }
                                }
                            }
                            if (state.wallets.isEmpty()) {
                                Text(
                                    text = "Add Cash, Bank, or E-Wallet accounts to track individual balances and internal transfers.",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            } else {
                                state.wallets.forEach { w ->
                                    val bal = w.currentBalance.toBigDecimalOrNull() ?: BigDecimal.ZERO
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Column {
                                            Text(w.name, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Medium)
                                            Text(
                                                "${w.walletType} · ${w.transactionCount} tx",
                                                style = MaterialTheme.typography.labelSmall,
                                                color = MaterialTheme.colorScheme.onSurfaceVariant
                                            )
                                        }
                                        Text(
                                            text = "${formatAmount(w.currentBalance)} ${w.currency}",
                                            style = MaterialTheme.typography.bodyMedium.copy(fontFeatureSettings = "tnum"),
                                            fontWeight = FontWeight.SemiBold,
                                            color = if (bal < BigDecimal.ZERO) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary
                                        )
                                    }
                                }
                            }
                        }
                    }
                }

                }

                // Merchant Auto-Categorization Rules & JSON Backup Card (#6 & #7) — More tab
                if (selectedTab == 3) {
                item {
                    Card(
                        shape = RoundedCornerShape(16.dp),
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(
                            modifier = Modifier.padding(18.dp),
                            verticalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    text = "AUTO-RULES & BACKUP",
                                    style = MaterialTheme.typography.labelMedium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                                TextButton(onClick = onAddRuleClick) { Text("+ Rule") }
                            }
                            if (state.rules.isNotEmpty()) {
                                state.rules.take(5).forEach { r ->
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Text(
                                            "${r.merchantPattern} → ${r.categoryName}",
                                            style = MaterialTheme.typography.bodySmall,
                                            modifier = Modifier.weight(1f)
                                        )
                                        TextButton(onClick = { onDeleteRuleClick(r.id) }) { Text("Remove") }
                                    }
                                }
                            }
                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                TextButton(onClick = onExportBackupClick) { Text("Export JSON Backup") }
                                TextButton(onClick = onImportBackupClick) { Text("Import JSON") }
                            }
                        }
                    }
                }

                }

                // Category Breakdown Card — Home tab
                val categories = state.monthlySpending?.byCategory.orEmpty()
                if (selectedTab == 0 && categories.isNotEmpty()) {
                    item {
                        val totalDecimal = state.monthlySpending?.totalSpent?.toBigDecimalOrNull()
                            ?.takeIf { it > BigDecimal.ZERO } ?: BigDecimal.ONE

                        Card(
                            shape = RoundedCornerShape(16.dp),
                            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Column(
                                modifier = Modifier.padding(18.dp),
                                verticalArrangement = Arrangement.spacedBy(14.dp)
                            ) {
                                Text(
                                    text = "CATEGORY BREAKDOWN",
                                    style = MaterialTheme.typography.labelMedium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                                categories.forEach { cat ->
                                    Box(
                                        modifier = Modifier.clickable {
                                            categoryFilter = if (categoryFilter == cat.categoryName) null else cat.categoryName
                                            selectedTab = 1
                                        }
                                    ) {
                                        CategoryBreakdownRow(category = cat, totalSpent = totalDecimal)
                                    }
                                }
                            }
                        }
                    }
                }

                // Ledger Header + Search + Debit/Credit Filter Chips — Ledger tab
                if (selectedTab == 1) {
                item {
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text(
                            text = "LEDGER",
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(horizontal = 4.dp)
                        )
                        OutlinedTextField(
                            value = searchQuery,
                            onValueChange = { searchQuery = it },
                            placeholder = { Text("Search merchant or category...") },
                            leadingIcon = { Icon(Icons.Default.Search, contentDescription = null) },
                            singleLine = true,
                            shape = RoundedCornerShape(12.dp),
                            modifier = Modifier.fillMaxWidth()
                        )
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            listOf("all" to "All", "debit" to "Debits", "credit" to "Credits").forEach { (key, label) ->
                                FilterChip(
                                    selected = typeFilter == key,
                                    onClick = { typeFilter = key },
                                    label = { Text(label) }
                                )
                            }
                        }
                        // Date Range / YTD Presets (#5)
                        LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            item {
                                FilterChip(
                                    selected = state.rangeStart == null,
                                    onClick = onClearRangeClick,
                                    label = { Text("This Month") }
                                )
                            }
                            item {
                                val today = LocalDate.now()
                                val s30 = today.minusDays(30).toString()
                                val e30 = today.toString()
                                FilterChip(
                                    selected = state.rangeStart == s30 && state.rangeEnd == e30,
                                    onClick = { onSetRangeClick(s30, e30) },
                                    label = { Text("Last 30d") }
                                )
                            }
                            item {
                                val today = LocalDate.now()
                                val ytdStart = LocalDate.of(today.year, 1, 1).toString()
                                val ytdEnd = today.toString()
                                FilterChip(
                                    selected = state.rangeStart == ytdStart && state.rangeEnd == ytdEnd,
                                    onClick = { onSetRangeClick(ytdStart, ytdEnd) },
                                    label = { Text("YTD ${today.year}") }
                                )
                            }
                        }
                        state.rangeSummary?.let { rs ->
                            Text(
                                text = "Range (${rs.startDate} → ${rs.endDate}): Spent -${formatAmount(rs.totalSpent)} · Credit +${formatAmount(rs.totalCredit)} ${rs.baseCurrency}",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.primary
                            )
                        }
                        // Tag Filter Chips (#5)
                        if (state.tags.isNotEmpty()) {
                            LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                item {
                                    FilterChip(
                                        selected = tagFilter == null,
                                        onClick = { tagFilter = null },
                                        label = { Text("All Tags") }
                                    )
                                }
                                items(state.tags, key = { it.tag }) { tg ->
                                    FilterChip(
                                        selected = tagFilter == tg.tag,
                                        onClick = { tagFilter = if (tagFilter == tg.tag) null else tg.tag },
                                        label = { Text("#${tg.tag} (${tg.transactionCount})") }
                                    )
                                }
                            }
                        }
                        if (activeCategories.isNotEmpty()) {
                            LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                item {
                                    FilterChip(
                                        selected = categoryFilter == null,
                                        onClick = { categoryFilter = null },
                                        label = { Text("All Categories") }
                                    )
                                }
                                items(activeCategories) { catName ->
                                    FilterChip(
                                        selected = categoryFilter == catName,
                                        onClick = {
                                            categoryFilter = if (categoryFilter == catName) null else catName
                                        },
                                        label = { Text(catName) }
                                    )
                                }
                            }
                        }
                    }
                }

                if (groupedByDate.isEmpty() && !state.isLoading) {
                    item {
                        Card(
                            shape = RoundedCornerShape(16.dp),
                            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text(
                                text = "No transactions match this filter. Tap + to add manually, scan a bill, or share from your banking app.",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.padding(24.dp)
                            )
                        }
                    }
                } else {
                    groupedByDate.forEach { (dateStr, dayTxs) ->
                        val dayNet = dayTxs.fold(BigDecimal.ZERO) { acc, tx ->
                            val v = (tx.baseAmount ?: tx.amount).toBigDecimalOrNull() ?: BigDecimal.ZERO
                            if (tx.txType.equals("credit", ignoreCase = true)) acc + v else acc - v
                        }
                        item(key = "hdr_$dateStr") {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 4.dp, vertical = 2.dp),
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Text(
                                    text = formatDateHeading(dateStr),
                                    style = MaterialTheme.typography.labelMedium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                                val prefix = if (dayNet > BigDecimal.ZERO) "+" else ""
                                Text(
                                    text = maskAmt("$prefix${formatAmount(dayNet.toPlainString())} $baseCurrency"),
                                    style = MaterialTheme.typography.labelMedium.copy(fontFeatureSettings = "tnum"),
                                    color = if (dayNet < BigDecimal.ZERO) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary
                                )
                            }
                        }
                        items(dayTxs, key = { it.id }) { tx ->
                            Surface(
                                onClick = { onTransactionClick(tx) },
                                color = MaterialTheme.colorScheme.surface,
                                shape = RoundedCornerShape(14.dp),
                                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline)
                            ) {
                                TransactionRow(tx = tx, baseCurrency = baseCurrency, privacyMode = state.privacyMode)
                            }
                        }
                    }
                }
                }

                item { Spacer(modifier = Modifier.height(72.dp)) }
            }
        }
    }
}

@Composable
private fun CashFlowTrendCard(points: List<MonthlyTrendPoint>) {
    val maxVal = points
        .flatMap { listOf(it.totalCredit.toFloatOrNull() ?: 0f, it.totalSpent.toFloatOrNull() ?: 0f) }
        .maxOrNull()
        ?.takeIf { it > 0f } ?: 1f

    Card(
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(modifier = Modifier.padding(18.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(
                    text = "6-MONTH CASH FLOW",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Text(
                    text = "Credit vs Debit",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            Spacer(modifier = Modifier.height(14.dp))
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(110.dp),
                horizontalArrangement = Arrangement.SpaceEvenly,
                verticalAlignment = Alignment.Bottom
            ) {
                points.forEach { p ->
                    val creditH = (((p.totalCredit.toFloatOrNull() ?: 0f) / maxVal) * 80f).coerceAtLeast(3f)
                    val debitH = (((p.totalSpent.toFloatOrNull() ?: 0f) / maxVal) * 80f).coerceAtLeast(3f)
                    val monthName = Month.of(p.month.coerceIn(1, 12))
                        .getDisplayName(TextStyle.SHORT, Locale.getDefault())
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Row(
                            verticalAlignment = Alignment.Bottom,
                            horizontalArrangement = Arrangement.spacedBy(3.dp),
                            modifier = Modifier.height(84.dp)
                        ) {
                            Box(
                                modifier = Modifier
                                    .width(10.dp)
                                    .height(creditH.dp)
                                    .clip(RoundedCornerShape(topStart = 3.dp, topEnd = 3.dp))
                                    .background(MaterialTheme.colorScheme.primary)
                            )
                            Box(
                                modifier = Modifier
                                    .width(10.dp)
                                    .height(debitH.dp)
                                    .clip(RoundedCornerShape(topStart = 3.dp, topEnd = 3.dp))
                                    .background(MaterialTheme.colorScheme.outlineVariant)
                            )
                        }
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = monthName,
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun BudgetsCard(
    budgets: List<BudgetRecord>,
    selectedMonth: YearMonth,
    onSetBudgetClick: () -> Unit,
    onNewCategoryClick: () -> Unit
) {
    val today = LocalDate.now()
    val isCurrentMonth = selectedMonth.year == today.year && selectedMonth.monthValue == today.monthValue
    val daysLeft = if (isCurrentMonth) (selectedMonth.lengthOfMonth() - today.dayOfMonth).coerceAtLeast(0) else null

    Card(
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(
            modifier = Modifier.padding(18.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column {
                    Text(
                        text = "MONTHLY BUDGETS",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    if (daysLeft != null) {
                        Text(
                            text = "${daysLeft}d left in month",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
                Row {
                    TextButton(onClick = onNewCategoryClick) { Text("+ Category") }
                    TextButton(onClick = onSetBudgetClick) { Text("Set Budget") }
                }
            }

            if (budgets.isEmpty()) {
                Text(
                    text = "No budgets set yet. Tap Set Budget to track monthly category limits.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            } else {
                budgets.forEach { b ->
                    val limit = b.monthlyLimit.toBigDecimalOrNull()?.takeIf { it > BigDecimal.ZERO } ?: BigDecimal.ONE
                    val spent = b.spent.toBigDecimalOrNull() ?: BigDecimal.ZERO
                    val ratio = (spent.toFloat() / limit.toFloat()).coerceIn(0f, 1f)
                    val pct = ((spent.toFloat() / limit.toFloat()) * 100f).toInt()
                    val barColor = when {
                        pct >= 100 -> MaterialTheme.colorScheme.error
                        pct >= 75 -> Color(0xFFD97706)
                        else -> MaterialTheme.colorScheme.primary
                    }
                    Column {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text(b.categoryName, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Medium)
                            Text(
                                text = "${formatAmount(b.spent)} / ${formatAmount(b.monthlyLimit)} ($pct%)",
                                style = MaterialTheme.typography.bodySmall.copy(fontFeatureSettings = "tnum"),
                                fontWeight = FontWeight.SemiBold,
                                color = barColor
                            )
                        }
                        Spacer(modifier = Modifier.height(5.dp))
                        LinearProgressIndicator(
                            progress = { ratio },
                            color = barColor,
                            trackColor = MaterialTheme.colorScheme.surfaceVariant,
                            strokeCap = StrokeCap.Round,
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(6.dp)
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun CaptureTile(
    icon: ImageVector,
    label: String,
    enabled: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Surface(
        onClick = onClick,
        enabled = enabled,
        modifier = modifier,
        shape = RoundedCornerShape(14.dp),
        color = MaterialTheme.colorScheme.surface,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline)
    ) {
        Column(
            modifier = Modifier.padding(vertical = 14.dp, horizontal = 8.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Box(
                modifier = Modifier
                    .size(36.dp)
                    .clip(RoundedCornerShape(10.dp))
                    .background(MaterialTheme.colorScheme.primaryContainer),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = icon,
                    contentDescription = label,
                    tint = MaterialTheme.colorScheme.onPrimaryContainer,
                    modifier = Modifier.size(18.dp)
                )
            }
            Text(
                text = label,
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.onSurface
            )
        }
    }
}

@Composable
private fun CategoryBreakdownRow(
    category: CategorySpending,
    totalSpent: BigDecimal
) {
    val catVal = category.totalSpent.toBigDecimalOrNull() ?: BigDecimal.ZERO
    val progress = (catVal.toFloat() / totalSpent.toFloat()).coerceIn(0f, 1f)
    val pct = (progress * 100).toInt()
    val accent = categoryAccentColor(category.categoryName)

    Column(modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Box(
                    modifier = Modifier
                        .size(8.dp)
                        .clip(CircleShape)
                        .background(accent)
                )
                Text(
                    text = category.categoryName,
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.Medium
                )
            }
            Text(
                text = "${formatAmount(category.totalSpent)}  ($pct%)",
                style = MaterialTheme.typography.bodyMedium.copy(fontFeatureSettings = "tnum"),
                fontWeight = FontWeight.SemiBold
            )
        }
        Spacer(modifier = Modifier.height(6.dp))
        LinearProgressIndicator(
            progress = { progress },
            color = accent,
            trackColor = MaterialTheme.colorScheme.surfaceVariant,
            strokeCap = StrokeCap.Round,
            modifier = Modifier
                .fillMaxWidth()
                .height(6.dp)
        )
    }
}

@Composable
private fun TransactionRow(tx: TransactionRecord, baseCurrency: String, privacyMode: Boolean = false) {
    val accent = categoryAccentColor(tx.categoryName)
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 14.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Row(
            modifier = Modifier.weight(1f),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Box(
                modifier = Modifier
                    .size(10.dp)
                    .clip(CircleShape)
                    .background(accent)
            )
            Column {
                Text(
                    text = buildString {
                        append(tx.vendor)
                        if (tx.isRecurring) append(" ↻")
                        if (tx.isTransfer) append(" ⇄")
                    },
                    style = MaterialTheme.typography.bodyLarge,
                    fontWeight = FontWeight.Medium
                )
                val subtitle = buildList {
                    add(tx.categoryName ?: "Uncategorized")
                    if (!tx.walletName.isNullOrBlank()) add(tx.walletName)
                    if (tx.tags.isNotEmpty()) add(tx.tags.joinToString(" ") { "#$it" })
                }.joinToString(" · ")
                Text(
                    text = subtitle,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
        val isCredit = tx.txType.equals("credit", ignoreCase = true)
        val sign = if (isCredit) "+" else "-"
        Column(horizontalAlignment = Alignment.End) {
            Text(
                text = if (privacyMode) "••••••" else "$sign${formatAmount(tx.amount)} ${tx.currency}",
                style = MaterialTheme.typography.bodyLarge.copy(fontFeatureSettings = "tnum"),
                fontWeight = FontWeight.SemiBold,
                color = if (isCredit) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface
            )
            if (!privacyMode && !tx.baseAmount.isNullOrBlank() && !tx.currency.equals(baseCurrency, ignoreCase = true)) {
                Text(
                    text = "≈ ${formatAmount(tx.baseAmount)} $baseCurrency",
                    style = MaterialTheme.typography.labelSmall.copy(fontFeatureSettings = "tnum"),
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

private fun formatDateHeading(isoDate: String): String {
    val today = LocalDate.now().toString()
    val yesterday = LocalDate.now().minusDays(1).toString()
    return when (isoDate) {
        today -> "TODAY · $isoDate"
        yesterday -> "YESTERDAY · $isoDate"
        else -> isoDate
    }
}

private fun formatAmount(raw: String): String {
    val num = raw.toBigDecimalOrNull() ?: return raw
    return NumberFormat.getNumberInstance(Locale.getDefault()).format(num)
}
