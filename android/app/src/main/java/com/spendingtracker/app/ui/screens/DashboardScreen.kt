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
import androidx.compose.foundation.layout.fillMaxHeight
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
import androidx.compose.material.icons.automirrored.filled.ReceiptLong
import androidx.compose.material.icons.automirrored.filled.TrendingUp
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.CameraAlt
import androidx.compose.material.icons.filled.DarkMode
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.Fingerprint
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Image
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material3.Button
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.SnackbarResult
import androidx.compose.material3.SwipeToDismissBox
import androidx.compose.material3.SwipeToDismissBoxValue
import androidx.compose.material3.rememberSwipeToDismissBoxState
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import kotlinx.coroutines.launch
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
    onQuickLogSubmit: (String) -> Unit,
    onVoiceQuickLogClick: () -> Unit,
    onLogRecurringClick: (com.spendingtracker.app.data.RecurringBillItem) -> Unit,
    onBulkDeleteClick: (List<String>) -> Unit,
    onBulkCategoryClick: (List<String>, String) -> Unit,
    onSavePreferences: (Int?, String?, String?) -> Unit,
    onEndEventClick: () -> Unit,
    onExportPdfClick: () -> Unit,
    onChangePasswordClick: () -> Unit,
    onDebtCalcClick: () -> Unit,
    onSwipeDeleteTx: (TransactionRecord) -> Unit,
    onUndoDeleteTx: (TransactionRecord) -> Unit,
    onGlobalSearchClick: (String, String?) -> Unit,
    onClearGlobalSearchClick: () -> Unit,
    onYearlyWrappedYearChange: (Int) -> Unit,
    onInspectMerchant: (String) -> Unit,
    onSuggestBudgetsClick: () -> Unit,
    onApplyBudgetSuggestion: (String, String) -> Unit,
    onToggleBankAlerts: () -> Unit,
    onReviewPendingClick: () -> Unit,
    onFlushOfflineClick: () -> Unit,
    onToggleBiometric: () -> Unit,
    onTransactionClick: (TransactionRecord) -> Unit,
    onLogout: () -> Unit,
    onClearMessages: () -> Unit
) {
    val snackbarHostState = remember { SnackbarHostState() }
    val haptic = LocalHapticFeedback.current
    val scope = rememberCoroutineScope()
    val monthFormatter = remember { DateTimeFormatter.ofPattern("MMMM yyyy", Locale.getDefault()) }

    var selectedTab by remember { mutableStateOf(0) } // 0=Home, 1=Ledger, 2=Plan, 3=More
    var quickInputText by remember { mutableStateOf("") }
    var selectionMode by remember { mutableStateOf(false) }
    var selectedIds by remember { mutableStateOf(setOf<String>()) }
    var bulkCatMenuExpanded by remember { mutableStateOf(false) }
    var searchQuery by remember { mutableStateOf("") }
    var minAmountFilter by remember { mutableStateOf<Double?>(null) }
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
    val filteredTxs = remember(state.transactions, searchQuery, typeFilter, categoryFilter, tagFilter, minAmountFilter) {
        val q = searchQuery.trim().lowercase()
        state.transactions.filter { tx ->
            val typeOk = typeFilter == "all" || tx.txType.equals(typeFilter, ignoreCase = true)
            val catOk = categoryFilter == null || (tx.categoryName ?: "Uncategorized") == categoryFilter
            val tagOk = tagFilter == null || tx.tags.contains(tagFilter)
            val amtNum = (tx.baseAmount ?: tx.amount).toDoubleOrNull() ?: 0.0
            val minOk = minAmountFilter == null || amtNum >= minAmountFilter!!
            val queryOk = q.isEmpty() ||
                tx.vendor.lowercase().contains(q) ||
                tx.txDate.lowercase().contains(q) ||
                (tx.categoryName ?: "").lowercase().contains(q) ||
                (tx.walletName ?: "").lowercase().contains(q) ||
                tx.tags.any { it.lowercase().contains(q.removePrefix("#")) } ||
                (tx.description ?: "").lowercase().contains(q)
            typeOk && catOk && tagOk && minOk && queryOk
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
                    icon = { Icon(Icons.AutoMirrored.Filled.ReceiptLong, contentDescription = "Ledger") },
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

                // Active Travel / Event Mode Banner (#6)
                val activeEventTag = state.preferences?.activeEventTag
                if (!activeEventTag.isNullOrBlank()) {
                    item {
                        Surface(
                            color = MaterialTheme.colorScheme.primaryContainer,
                            shape = RoundedCornerShape(12.dp),
                            border = BorderStroke(1.dp, MaterialTheme.colorScheme.primary),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 14.dp, vertical = 8.dp),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    text = "✈ #$activeEventTag · ${maskAmt("${formatAmount(state.preferences?.activeEventSpent ?: "0")} $baseCurrency")}",
                                    style = MaterialTheme.typography.labelLarge,
                                    color = MaterialTheme.colorScheme.onPrimaryContainer,
                                    modifier = Modifier.weight(1f)
                                )
                                TextButton(onClick = onEndEventClick) { Text("End") }
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
                                    onClick = {
                                        if (canZero) {
                                            haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                            onZeroBalanceClick()
                                        }
                                    },
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
                            // Safe to Spend Today Pacing Gauge (#3)
                            val isCurrentMonth = state.selectedMonth == YearMonth.now()
                            if (isCurrentMonth && state.budgets.isNotEmpty()) {
                                val totalLimit = state.budgets.fold(BigDecimal.ZERO) { a, b ->
                                    a + (b.monthlyLimit.toBigDecimalOrNull() ?: BigDecimal.ZERO)
                                }
                                val totalBudgetSpent = state.budgets.fold(BigDecimal.ZERO) { a, b ->
                                    a + (b.spent.toBigDecimalOrNull() ?: BigDecimal.ZERO)
                                }
                                val daysLeft = (state.selectedMonth.lengthOfMonth() - LocalDate.now().dayOfMonth + 1).coerceAtLeast(1)
                                val perDay = (totalLimit - totalBudgetSpent).divideToIntegralValue(BigDecimal(daysLeft))
                                val ok = perDay >= BigDecimal.ZERO
                                Spacer(modifier = Modifier.height(10.dp))
                                Surface(
                                    color = if (ok) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.errorContainer,
                                    shape = RoundedCornerShape(10.dp),
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    Text(
                                        text = if (ok) {
                                            "Safe to spend today: ${maskAmt("${formatAmount(perDay.toPlainString())} $baseCurrency/day")} ($daysLeft d left)"
                                        } else {
                                            "Over budget pace by ${maskAmt("${formatAmount(perDay.abs().toPlainString())} $baseCurrency/day")}"
                                        },
                                        style = MaterialTheme.typography.labelMedium,
                                        color = if (ok) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onErrorContainer,
                                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp)
                                    )
                                }
                            }
                        }
                    }
                }

                // Today's Spending Card (Home tab)
                item {
                    val todayIso = remember { LocalDate.now().toString() }
                    val todayTxs = remember(state.transactions, todayIso) {
                        state.transactions.filter { tx ->
                            tx.txDate == todayIso && !tx.isTransfer && tx.txType.equals("debit", ignoreCase = true)
                        }
                    }
                    val todayTotal = remember(todayTxs) {
                        todayTxs.fold(BigDecimal.ZERO) { acc, tx ->
                            acc + ((tx.baseAmount ?: tx.amount).toBigDecimalOrNull() ?: BigDecimal.ZERO)
                        }
                    }
                    val isZeroToday = todayTotal.compareTo(BigDecimal.ZERO) == 0
                    Card(
                        onClick = {
                            searchQuery = todayIso
                            selectedTab = 1
                        },
                        shape = RoundedCornerShape(16.dp),
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 18.dp, vertical = 14.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column {
                                Text(
                                    text = "TODAY'S SPENDING",
                                    style = MaterialTheme.typography.labelMedium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                                Text(
                                    text = if (isZeroToday) "No spend today" else "${todayTxs.size} transaction${if (todayTxs.size == 1) "" else "s"} · tap to view",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                            Text(
                                text = maskAmt(
                                    if (isZeroToday) "0 $baseCurrency"
                                    else "-${formatAmount(todayTotal.stripTrailingZeros().toPlainString())} $baseCurrency"
                                ),
                                style = MaterialTheme.typography.titleMedium.copy(fontFeatureSettings = "tnum"),
                                fontWeight = FontWeight.Bold,
                                color = if (isZeroToday) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.error
                            )
                        }
                    }
                }

                // Natural-Language Quick Input + Voice Mic (#1 & #2, 100% local, 0 AI calls)
                item {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        OutlinedTextField(
                            value = quickInputText,
                            onValueChange = { quickInputText = it },
                            placeholder = { Text("50k coffee cash #work", style = MaterialTheme.typography.bodySmall) },
                            trailingIcon = {
                                IconButton(onClick = onVoiceQuickLogClick) {
                                    Icon(
                                        imageVector = Icons.Default.Mic,
                                        contentDescription = "Voice Quick Log",
                                        tint = MaterialTheme.colorScheme.primary
                                    )
                                }
                            },
                            singleLine = true,
                            shape = RoundedCornerShape(12.dp),
                            modifier = Modifier.weight(1f)
                        )
                        Surface(
                            onClick = {
                                if (quickInputText.isNotBlank()) {
                                    onQuickLogSubmit(quickInputText)
                                    quickInputText = ""
                                }
                            },
                            color = MaterialTheme.colorScheme.primary,
                            shape = RoundedCornerShape(12.dp)
                        ) {
                            Text(
                                text = "Log",
                                style = MaterialTheme.typography.labelLarge,
                                color = MaterialTheme.colorScheme.onPrimary,
                                modifier = Modifier.padding(horizontal = 16.dp, vertical = 14.dp)
                            )
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
                // End-of-Month Forecast, Net Worth Canvas Line Chart & Anomalies (#2, #4, #5)
                state.forecast?.let { f ->
                    item {
                        ForecastAndNetWorthCard(
                            forecast = f,
                            privacyMode = state.privacyMode
                        )
                    }
                }
                // Day-of-Week Spending Breakdown (#2)
                state.weekdayBreakdown?.let { wd ->
                    if (wd.days.any { (it.totalSpent.toDoubleOrNull() ?: 0.0) > 0.0 }) {
                        item {
                            WeekdaySpendingCard(weekday = wd, privacyMode = state.privacyMode)
                        }
                    }
                }
                // Monthly Highlights: Savings Rate, No-Spend Streak, MoM & Top Merchants (#2 & #3)
                state.comparison?.let { comp ->
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
                                Text(
                                    text = "HIGHLIGHTS & VS. LAST MONTH",
                                    style = MaterialTheme.typography.labelMedium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                    Surface(
                                        color = MaterialTheme.colorScheme.primaryContainer,
                                        shape = RoundedCornerShape(10.dp),
                                        modifier = Modifier.weight(1f)
                                    ) {
                                        Column(modifier = Modifier.padding(10.dp)) {
                                            Text("Savings Rate", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onPrimaryContainer)
                                            Text(
                                                text = comp.savingsRatePct?.let { "$it%" } ?: "—",
                                                style = MaterialTheme.typography.titleMedium,
                                                color = MaterialTheme.colorScheme.onPrimaryContainer
                                            )
                                        }
                                    }
                                    Surface(
                                        color = MaterialTheme.colorScheme.surfaceVariant,
                                        shape = RoundedCornerShape(10.dp),
                                        modifier = Modifier.weight(1f)
                                    ) {
                                        Column(modifier = Modifier.padding(10.dp)) {
                                            Text("No-Spend Days", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                            Text(
                                                text = "${comp.noSpendDays}d (${comp.currentNoSpendStreak}d streak)",
                                                style = MaterialTheme.typography.titleMedium
                                            )
                                        }
                                    }
                                }
                                if (comp.categoryChanges.isNotEmpty()) {
                                    Text("Biggest Category Changes", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                    comp.categoryChanges.take(5).forEach { c ->
                                        val diffNum = c.diff.toBigDecimalOrNull() ?: BigDecimal.ZERO
                                        val pctStr = c.pctChange?.let { " (${if (it > 0) "+" else ""}$it%)" } ?: ""
                                        Row(
                                            modifier = Modifier.fillMaxWidth(),
                                            horizontalArrangement = Arrangement.SpaceBetween
                                        ) {
                                            Text(c.categoryName, style = MaterialTheme.typography.bodySmall)
                                            Text(
                                                text = maskAmt("${if (diffNum > BigDecimal.ZERO) "+" else ""}${formatAmount(c.diff)}$pctStr"),
                                                style = MaterialTheme.typography.bodySmall.copy(fontFeatureSettings = "tnum"),
                                                fontWeight = FontWeight.SemiBold,
                                                color = if (diffNum > BigDecimal.ZERO) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary
                                            )
                                        }
                                    }
                                }
                                if (comp.topMerchants.isNotEmpty()) {
                                    HorizontalDivider(color = MaterialTheme.colorScheme.outline)
                                    Text("Top Merchants (tap to inspect)", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                    comp.topMerchants.forEach { m ->
                                        Row(
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .clickable { onInspectMerchant(m.vendor) }
                                                .padding(vertical = 2.dp),
                                            horizontalArrangement = Arrangement.SpaceBetween
                                        ) {
                                            Text("${m.vendor} (${m.transactionCount}x)", style = MaterialTheme.typography.bodySmall)
                                            Text(
                                                text = maskAmt("${formatAmount(m.totalSpent)} ${comp.baseCurrency}"),
                                                style = MaterialTheme.typography.bodySmall.copy(fontFeatureSettings = "tnum"),
                                                fontWeight = FontWeight.SemiBold
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }
                }

                // v1.8.0: 50/30/20 Rule Card
                state.rule503020?.let { rule ->
                    item {
                        Rule503020Card(rule = rule, privacyMode = state.privacyMode)
                    }
                }

                // v1.8.0: Merchant Lifetime Profile Card
                item {
                    MerchantProfileCard(
                        profile = state.merchantProfile,
                        privacyMode = state.privacyMode,
                        onInspect = onInspectMerchant
                    )
                }

                // v1.8.0: Annual Year-in-Review ("Wrapped") Card
                state.yearlyWrapped?.let { wrapped ->
                    item {
                        YearlyWrappedCard(
                            wrapped = wrapped,
                            privacyMode = state.privacyMode,
                            onPrevYear = { onYearlyWrappedYearChange(wrapped.year - 1) },
                            onNextYear = { onYearlyWrappedYearChange(wrapped.year + 1) },
                            onInspectMerchant = onInspectMerchant
                        )
                    }
                }

                if (state.trend.isNotEmpty()) {
                    item {
                        CashFlowTrendCard(points = state.trend)
                    }
                }

                // Monthly Budgets Card + v1.8.0 Smart Budget Auto-Suggest
                item {
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        BudgetsCard(
                            budgets = state.budgets,
                            selectedMonth = state.selectedMonth,
                            onSetBudgetClick = onSetBudgetClick,
                            onNewCategoryClick = onNewCategoryClick
                        )
                        Card(
                            shape = RoundedCornerShape(16.dp),
                            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Column(
                                modifier = Modifier.padding(16.dp),
                                verticalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Text(
                                        text = "SMART BUDGET SUGGESTIONS",
                                        style = MaterialTheme.typography.labelMedium,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                    TextButton(onClick = onSuggestBudgetsClick) {
                                        Text("3M Avg")
                                    }
                                }
                                val suggs = state.budgetSuggestions?.suggestions.orEmpty()
                                val suggCur = state.budgetSuggestions?.baseCurrency ?: baseCurrency
                                if (suggs.isEmpty()) {
                                    Text(
                                        text = "Tap 3M Avg to auto-suggest category budgets from your trailing 3-month spending.",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                } else {
                                    suggs.take(6).forEach { s ->
                                        Row(
                                            modifier = Modifier.fillMaxWidth(),
                                            horizontalArrangement = Arrangement.SpaceBetween,
                                            verticalAlignment = Alignment.CenterVertically
                                        ) {
                                            Column(modifier = Modifier.weight(1f)) {
                                                Text(s.categoryName, style = MaterialTheme.typography.bodySmall, fontWeight = FontWeight.Medium)
                                                Text(
                                                    "Avg: ${maskAmt(formatAmount(s.avgMonthlySpent))} → ${maskAmt(formatAmount(s.suggestedLimit))} $suggCur",
                                                    style = MaterialTheme.typography.labelSmall,
                                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                                )
                                            }
                                            TextButton(onClick = { onApplyBudgetSuggestion(s.categoryName, s.suggestedLimit) }) {
                                                Text("Apply")
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }
                }

                // v1.8.0: Subscription Annual Cost Audit Card
                state.subscriptionAudit?.let { audit ->
                    if (audit.items.isNotEmpty()) {
                        item {
                            Card(
                                shape = RoundedCornerShape(16.dp),
                                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Column(
                                    modifier = Modifier.padding(18.dp),
                                    verticalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Text(
                                            text = "SUBSCRIPTION COST AUDIT",
                                            style = MaterialTheme.typography.labelMedium,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                        Text(
                                            text = maskAmt("${formatAmount(audit.annualTotal)} ${audit.baseCurrency}/yr"),
                                            style = MaterialTheme.typography.labelLarge.copy(fontFeatureSettings = "tnum"),
                                            fontWeight = FontWeight.SemiBold,
                                            color = MaterialTheme.colorScheme.error
                                        )
                                    }
                                    Text(
                                        text = "Monthly burn: ${maskAmt(formatAmount(audit.monthlyTotal))} ${audit.baseCurrency}/mo" +
                                            (audit.pctOfAvgIncome?.let { " · $it% of avg income" } ?: ""),
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                    audit.items.take(6).forEach { it ->
                                        Row(
                                            modifier = Modifier.fillMaxWidth(),
                                            horizontalArrangement = Arrangement.SpaceBetween
                                        ) {
                                            Text("${it.vendor} (${it.occurrences}x)", style = MaterialTheme.typography.bodySmall)
                                            Text(
                                                text = maskAmt("${formatAmount(it.annualCost)}/yr"),
                                                style = MaterialTheme.typography.bodySmall.copy(fontFeatureSettings = "tnum"),
                                                fontWeight = FontWeight.SemiBold
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }
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
                                        Row(verticalAlignment = Alignment.CenterVertically) {
                                            Text(
                                                text = maskAmt(formatAmount(bill.avgAmount)),
                                                style = MaterialTheme.typography.bodyMedium.copy(fontFeatureSettings = "tnum"),
                                                fontWeight = FontWeight.SemiBold
                                            )
                                            TextButton(onClick = { onLogRecurringClick(bill) }) {
                                                Text("Log")
                                            }
                                        }
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
                                Row {
                                    TextButton(onClick = onDebtCalcClick) { Text("Payoff Calc") }
                                    TextButton(onClick = onAddGoalClick) { Text("+ Goal") }
                                }
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

                // Bank Notification Auto-Capture Card (#1) — More tab
                item {
                    Card(
                        shape = RoundedCornerShape(16.dp),
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(
                            modifier = Modifier.padding(18.dp),
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    text = "BANK ALERT AUTO-CAPTURE",
                                    style = MaterialTheme.typography.labelMedium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                                TextButton(onClick = onToggleBankAlerts) {
                                    Text(if (state.bankAlertsEnabled) "ON (Tap to disable)" else "Enable")
                                }
                            }
                            Text(
                                text = "100% on-device regex parser for VCB, TCB, MB, ACB, MoMo, ZaloPay, Timo, Cake notifications. Drops alerts into Pending Drafts with 0 AI calls.",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }

                // Payday Cycle & Travel / Event Mode Settings (#6 & #7) — More tab
                item {
                    PreferencesCard(
                        prefs = state.preferences,
                        onSave = onSavePreferences
                    )
                }

                // Reports & Account Security (#2 & #3) — More tab
                item {
                    Card(
                        shape = RoundedCornerShape(16.dp),
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(
                            modifier = Modifier.padding(18.dp),
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Text(
                                text = "REPORTS & SECURITY",
                                style = MaterialTheme.typography.labelMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                TextButton(onClick = onExportPdfClick) { Text("Save Monthly PDF") }
                                TextButton(onClick = onChangePasswordClick) { Text("Change Password") }
                            }
                        }
                    }
                }

                // Quick Currency Converter (#3) — More tab
                state.fxRates?.let { fx ->
                    if (fx.ratesVsUsd.isNotEmpty()) {
                        item { CurrencyConverterCard(fx = fx) }
                    }
                }

                }

                // Monthly Spending Calendar Heatmap (#4) — Home tab
                if (selectedTab == 0) {
                    item {
                        SpendingCalendarHeatmapCard(
                            yearMonth = state.selectedMonth,
                            transactions = state.transactions,
                            onDayClick = { isoDate ->
                                searchQuery = isoDate
                                selectedTab = 1
                            }
                        )
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
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = "LEDGER",
                                style = MaterialTheme.typography.labelMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.padding(horizontal = 4.dp)
                            )
                            TextButton(
                                onClick = {
                                    selectionMode = !selectionMode
                                    if (!selectionMode) selectedIds = emptySet()
                                }
                            ) {
                                Text(if (selectionMode) "Done (${selectedIds.size})" else "Select")
                            }
                        }
                        if (selectionMode && selectedIds.isNotEmpty()) {
                            Surface(
                                color = MaterialTheme.colorScheme.primaryContainer,
                                shape = RoundedCornerShape(12.dp),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Row(
                                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Text(
                                        "${selectedIds.size} selected",
                                        style = MaterialTheme.typography.labelMedium,
                                        color = MaterialTheme.colorScheme.onPrimaryContainer
                                    )
                                    Row {
                                        Box {
                                            TextButton(onClick = { bulkCatMenuExpanded = true }) { Text("Category") }
                                            androidx.compose.material3.DropdownMenu(
                                                expanded = bulkCatMenuExpanded,
                                                onDismissRequest = { bulkCatMenuExpanded = false }
                                            ) {
                                                state.categories.forEach { c ->
                                                    androidx.compose.material3.DropdownMenuItem(
                                                        text = { Text(c) },
                                                        onClick = {
                                                            bulkCatMenuExpanded = false
                                                            onBulkCategoryClick(selectedIds.toList(), c)
                                                            selectedIds = emptySet()
                                                            selectionMode = false
                                                        }
                                                    )
                                                }
                                            }
                                        }
                                        TextButton(
                                            onClick = {
                                                onBulkDeleteClick(selectedIds.toList())
                                                selectedIds = emptySet()
                                                selectionMode = false
                                            }
                                        ) {
                                            Text("Delete", color = MaterialTheme.colorScheme.error)
                                        }
                                    }
                                }
                            }
                        }
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            OutlinedTextField(
                                value = searchQuery,
                                onValueChange = { searchQuery = it },
                                placeholder = { Text("Search merchant, #tag...") },
                                leadingIcon = { Icon(Icons.Default.Search, contentDescription = null) },
                                singleLine = true,
                                shape = RoundedCornerShape(12.dp),
                                modifier = Modifier.weight(1f)
                            )
                            TextButton(
                                onClick = {
                                    onGlobalSearchClick(searchQuery, minAmountFilter?.toLong()?.toString())
                                }
                            ) {
                                Text("All-Time")
                            }
                        }
                        state.globalSearch?.let { gs ->
                            Surface(
                                color = MaterialTheme.colorScheme.primaryContainer,
                                shape = RoundedCornerShape(10.dp),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Row(
                                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Text(
                                        text = "All-Time (${gs.count}): -${maskAmt(formatAmount(gs.totalSpent))} ${gs.baseCurrency}",
                                        style = MaterialTheme.typography.labelMedium,
                                        color = MaterialTheme.colorScheme.onPrimaryContainer,
                                        modifier = Modifier.weight(1f)
                                    )
                                    TextButton(onClick = {
                                        searchQuery = ""
                                        minAmountFilter = null
                                        onClearGlobalSearchClick()
                                    }) { Text("Reset") }
                                }
                            }
                        }
                        LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            items(listOf("all" to "All", "debit" to "Debits", "credit" to "Credits")) { (key, label) ->
                                FilterChip(
                                    selected = typeFilter == key,
                                    onClick = { typeFilter = key },
                                    label = { Text(label) }
                                )
                            }
                            item {
                                FilterChip(
                                    selected = minAmountFilter == 500_000.0,
                                    onClick = { minAmountFilter = if (minAmountFilter == 500_000.0) null else 500_000.0 },
                                    label = { Text("≥ 500k") }
                                )
                            }
                            item {
                                FilterChip(
                                    selected = minAmountFilter == 1_000_000.0,
                                    onClick = { minAmountFilter = if (minAmountFilter == 1_000_000.0) null else 1_000_000.0 },
                                    label = { Text("≥ 1M") }
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
                            val isSelected = selectedIds.contains(tx.id)
                            val dismissState = rememberSwipeToDismissBoxState(
                                confirmValueChange = { value ->
                                    if (value == SwipeToDismissBoxValue.EndToStart && !selectionMode) {
                                        haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                        onSwipeDeleteTx(tx)
                                        scope.launch {
                                            val res = snackbarHostState.showSnackbar(
                                                message = "Deleted ${tx.vendor}",
                                                actionLabel = "Undo"
                                            )
                                            if (res == SnackbarResult.ActionPerformed) {
                                                onUndoDeleteTx(tx)
                                            }
                                        }
                                        true
                                    } else {
                                        false
                                    }
                                }
                            )
                            SwipeToDismissBox(
                                state = dismissState,
                                enableDismissFromStartToEnd = false,
                                backgroundContent = {
                                    Box(
                                        modifier = Modifier
                                            .fillMaxSize()
                                            .clip(RoundedCornerShape(14.dp))
                                            .background(MaterialTheme.colorScheme.errorContainer)
                                            .padding(horizontal = 20.dp),
                                        contentAlignment = Alignment.CenterEnd
                                    ) {
                                        Icon(
                                            imageVector = Icons.Default.Delete,
                                            contentDescription = "Delete",
                                            tint = MaterialTheme.colorScheme.onErrorContainer
                                        )
                                    }
                                }
                            ) {
                                Surface(
                                    onClick = {
                                        if (selectionMode) {
                                            selectedIds = if (isSelected) selectedIds - tx.id else selectedIds + tx.id
                                        } else {
                                            onTransactionClick(tx)
                                        }
                                    },
                                    color = if (isSelected) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surface,
                                    shape = RoundedCornerShape(14.dp),
                                    border = BorderStroke(
                                        if (isSelected) 1.5.dp else 1.dp,
                                        if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outline
                                    )
                                ) {
                                    TransactionRow(tx = tx, baseCurrency = baseCurrency, privacyMode = state.privacyMode)
                                }
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

@Composable
private fun ForecastAndNetWorthCard(
    forecast: com.spendingtracker.app.data.ForecastAndHealthResponse,
    privacyMode: Boolean
) {
    val projVal = forecast.projectedMonthEndBalance.toBigDecimalOrNull() ?: BigDecimal.ZERO
    val projColor = if (projVal < BigDecimal.ZERO) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary
    val accentColor = MaterialTheme.colorScheme.primary

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
                    text = "FORECAST & NET WORTH",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Text(
                    text = if (privacyMode) "••••••" else "End: ${if (projVal > BigDecimal.ZERO) "+" else ""}${formatAmount(forecast.projectedMonthEndBalance)} ${forecast.baseCurrency}",
                    style = MaterialTheme.typography.labelLarge.copy(fontFeatureSettings = "tnum"),
                    fontWeight = FontWeight.SemiBold,
                    color = projColor
                )
            }
            Text(
                text = "Burn rate: ${if (privacyMode) "••••" else formatAmount(forecast.dailyBurnRate)}/day · ${forecast.remainingDays}d left · Upcoming bills: ${if (privacyMode) "••••" else formatAmount(forecast.upcomingRecurringTotal)}",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            // Net Worth Trajectory Line Chart (Compose Canvas)
            if (forecast.netWorthPoints.size >= 2) {
                val vals = forecast.netWorthPoints.map { it.netWorth.toFloatOrNull() ?: 0f }
                val minV = vals.minOrNull() ?: 0f
                val maxV = vals.maxOrNull() ?: 1f
                val span = (maxV - minV).coerceAtLeast(1f)
                androidx.compose.foundation.Canvas(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(86.dp)
                        .padding(horizontal = 8.dp, vertical = 6.dp)
                ) {
                    val stepX = size.width / (vals.size - 1).coerceAtLeast(1)
                    val path = androidx.compose.ui.graphics.Path()
                    vals.forEachIndexed { idx, v ->
                        val x = idx * stepX
                        val y = size.height - ((v - minV) / span) * size.height
                        if (idx == 0) path.moveTo(x, y) else path.lineTo(x, y)
                    }
                    drawPath(
                        path = path,
                        color = accentColor,
                        style = androidx.compose.ui.graphics.drawscope.Stroke(width = 6f)
                    )
                    vals.forEachIndexed { idx, v ->
                        val x = idx * stepX
                        val y = size.height - ((v - minV) / span) * size.height
                        drawCircle(color = accentColor, radius = 9f, center = androidx.compose.ui.geometry.Offset(x, y))
                    }
                }
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    forecast.netWorthPoints.forEach { pt ->
                        Text(
                            text = Month.of(pt.month).getDisplayName(TextStyle.SHORT, Locale.getDefault()),
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }

            // Anomalies & Subscription Price-Hike Alerts
            if (forecast.alerts.isNotEmpty()) {
                HorizontalDivider(color = MaterialTheme.colorScheme.outline)
                forecast.alerts.forEach { alert ->
                    Surface(
                        color = MaterialTheme.colorScheme.errorContainer,
                        shape = RoundedCornerShape(10.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(modifier = Modifier.padding(10.dp)) {
                            Text(
                                text = alert.title,
                                style = MaterialTheme.typography.labelMedium,
                                color = MaterialTheme.colorScheme.onErrorContainer,
                                fontWeight = FontWeight.SemiBold
                            )
                            Text(
                                text = alert.detail,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onErrorContainer
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun PreferencesCard(
    prefs: com.spendingtracker.app.data.UserPreferencesResponse?,
    onSave: (Int?, String?, String?) -> Unit
) {
    var cycleDay by remember(prefs?.cycleStartDay) { mutableStateOf((prefs?.cycleStartDay ?: 1).toString()) }
    var eventTag by remember(prefs?.activeEventTag) { mutableStateOf(prefs?.activeEventTag.orEmpty()) }
    var eventCur by remember(prefs?.activeEventCurrency) { mutableStateOf(prefs?.activeEventCurrency.orEmpty()) }

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
            Text(
                text = "PAYDAY CYCLE & TRAVEL MODE",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(
                    value = cycleDay,
                    onValueChange = { cycleDay = it.filter { ch -> ch.isDigit() }.take(2) },
                    label = { Text("Payday (1-28)") },
                    singleLine = true,
                    shape = RoundedCornerShape(10.dp),
                    modifier = Modifier.weight(1f)
                )
                OutlinedTextField(
                    value = eventCur,
                    onValueChange = { eventCur = it.uppercase().take(3) },
                    label = { Text("Trip Curr") },
                    singleLine = true,
                    shape = RoundedCornerShape(10.dp),
                    modifier = Modifier.weight(1f)
                )
            }
            OutlinedTextField(
                value = eventTag,
                onValueChange = { eventTag = it },
                label = { Text("Active Trip/Event Tag (e.g. dalat-trip)") },
                singleLine = true,
                shape = RoundedCornerShape(10.dp),
                modifier = Modifier.fillMaxWidth()
            )
            TextButton(
                onClick = {
                    val d = cycleDay.toIntOrNull()?.coerceIn(1, 28) ?: 1
                    onSave(d, eventTag.trim(), eventCur.trim())
                }
            ) {
                Text("Save Payday & Travel Settings")
            }
        }
    }
}

@Composable
private fun WeekdaySpendingCard(
    weekday: com.spendingtracker.app.data.WeekdayBreakdownResponse,
    privacyMode: Boolean
) {
    val maxV = weekday.days.maxOfOrNull { it.totalSpent.toFloatOrNull() ?: 0f }?.coerceAtLeast(1f) ?: 1f
    Card(
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(modifier = Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Text(
                    text = "DAY-OF-WEEK SPENDING (${weekday.months}M)",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                weekday.busiestDay?.let {
                    Text("Peak: $it", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.primary)
                }
            }
            Row(
                modifier = Modifier.fillMaxWidth().height(90.dp),
                horizontalArrangement = Arrangement.SpaceEvenly,
                verticalAlignment = Alignment.Bottom
            ) {
                weekday.days.forEach { d ->
                    val amt = d.totalSpent.toFloatOrNull() ?: 0f
                    val h = ((amt / maxV) * 62f).coerceAtLeast(4f).dp
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Box(
                            modifier = Modifier
                                .width(18.dp)
                                .height(h)
                                .clip(RoundedCornerShape(topStart = 4.dp, topEnd = 4.dp))
                                .background(
                                    if (d.label == weekday.busiestDay) MaterialTheme.colorScheme.error
                                    else MaterialTheme.colorScheme.primary
                                )
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(d.label, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            }
        }
    }
}

@Composable
private fun CurrencyConverterCard(fx: com.spendingtracker.app.data.FxRatesResponse) {
    var amt by remember { mutableStateOf("100") }
    var fromCur by remember { mutableStateOf("USD") }
    var toCur by remember { mutableStateOf(fx.baseCurrency) }
    val currencies = listOf("USD", "VND", "EUR", "JPY", "THB", "SGD", "GBP", "KRW")
    val result = remember(amt, fromCur, toCur, fx.ratesVsUsd) {
        val v = amt.toDoubleOrNull() ?: return@remember "—"
        val rf = fx.ratesVsUsd[fromCur]?.toDoubleOrNull() ?: return@remember "—"
        val rt = fx.ratesVsUsd[toCur]?.toDoubleOrNull() ?: return@remember "—"
        if (rf <= 0.0) return@remember "—"
        val out = (v / rf) * rt
        val fmt = NumberFormat.getNumberInstance(Locale.getDefault()).apply {
            maximumFractionDigits = if (toCur in listOf("VND", "JPY", "KRW")) 0 else 2
        }.format(out)
        "= $fmt $toCur"
    }

    Card(
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(modifier = Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(
                text = "CURRENCY CONVERTER (CACHED FX)",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            OutlinedTextField(
                value = amt,
                onValueChange = { amt = it },
                label = { Text("Amount") },
                singleLine = true,
                shape = RoundedCornerShape(10.dp),
                modifier = Modifier.fillMaxWidth()
            )
            LazyRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                items(currencies) { code ->
                    FilterChip(
                        selected = fromCur == code,
                        onClick = { fromCur = code },
                        label = { Text(code) }
                    )
                }
            }
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "$amt $fromCur $result",
                    style = MaterialTheme.typography.titleMedium.copy(fontFeatureSettings = "tnum"),
                    color = MaterialTheme.colorScheme.primary
                )
                TextButton(onClick = { val tmp = fromCur; fromCur = toCur; toCur = tmp }) {
                    Text("Swap")
                }
            }
        }
    }
}

@Composable
private fun SpendingCalendarHeatmapCard(
    yearMonth: YearMonth,
    transactions: List<TransactionRecord>,
    onDayClick: (String) -> Unit
) {
    val daysInMonth = yearMonth.lengthOfMonth()
    val firstDow = (yearMonth.atDay(1).dayOfWeek.value - 1) // Mon=0..Sun=6
    val dayTotals = remember(yearMonth, transactions) {
        val map = mutableMapOf<Int, Double>()
        transactions.forEach { tx ->
            if (!tx.txType.equals("debit", ignoreCase = true) || tx.isTransfer) return@forEach
            val day = tx.txDate.takeLast(2).toIntOrNull() ?: return@forEach
            if (day in 1..daysInMonth) {
                map[day] = (map[day] ?: 0.0) + ((tx.baseAmount ?: tx.amount).toDoubleOrNull() ?: 0.0)
            }
        }
        map
    }
    val maxDay = (dayTotals.values.maxOrNull() ?: 1.0).coerceAtLeast(1.0)
    val today = LocalDate.now()

    Card(
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(
                    text = "SPENDING CALENDAR",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Text(
                    text = "Green = no spend",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                listOf("M", "T", "W", "T", "F", "S", "S").forEach { wd ->
                    Box(modifier = Modifier.weight(1f), contentAlignment = Alignment.Center) {
                        Text(wd, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            }
            val totalSlots = firstDow + daysInMonth
            val rowsCount = (totalSlots + 6) / 7
            for (r in 0 until rowsCount) {
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    for (c in 0 until 7) {
                        val day = r * 7 + c - firstDow + 1
                        if (day !in 1..daysInMonth) {
                            Spacer(modifier = Modifier.weight(1f).height(34.dp))
                        } else {
                            val date = yearMonth.atDay(day)
                            val amt = dayTotals[day] ?: 0.0
                            val bgColor = when {
                                amt == 0.0 && !date.isAfter(today) -> MaterialTheme.colorScheme.primaryContainer
                                amt > 0.0 -> {
                                    val ratio = amt / maxDay
                                    when {
                                        ratio > 0.66 -> Color(0x88EF4444)
                                        ratio > 0.33 -> Color(0x77F59E0B)
                                        else -> Color(0x44FBBF24)
                                    }
                                }
                                else -> MaterialTheme.colorScheme.surfaceVariant
                            }
                            Surface(
                                onClick = { onDayClick(date.toString()) },
                                color = bgColor,
                                shape = RoundedCornerShape(8.dp),
                                modifier = Modifier.weight(1f).height(34.dp)
                            ) {
                                Box(contentAlignment = Alignment.Center) {
                                    Text(
                                        text = "$day",
                                        style = MaterialTheme.typography.labelSmall,
                                        fontWeight = FontWeight.SemiBold
                                    )
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
private fun Rule503020Card(
    rule: com.spendingtracker.app.data.Rule503020Response,
    privacyMode: Boolean
) {
    val totalPct = (rule.needsPct + rule.wantsPct + rule.savingsPct).coerceAtLeast(0.0)
    fun mask(s: String) = if (privacyMode) "••••••" else s

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
                    text = "50 / 30 / 20 RULE",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Text(
                    text = "${rule.needsPct.toInt()}% / ${rule.wantsPct.toInt()}% / ${rule.savingsPct.toInt()}%",
                    style = MaterialTheme.typography.labelMedium.copy(fontFeatureSettings = "tnum"),
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.primary
                )
            }
            if (totalPct <= 0.0) {
                Text(
                    text = "No spending or income recorded for this month yet.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            } else {
                val nWeight = (rule.needsPct / totalPct).toFloat().coerceAtLeast(0.001f)
                val wWeight = (rule.wantsPct / totalPct).toFloat().coerceAtLeast(0.001f)
                val sWeight = (rule.savingsPct / totalPct).toFloat().coerceAtLeast(0.001f)
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(12.dp)
                        .clip(RoundedCornerShape(6.dp))
                ) {
                    if (rule.needsPct > 0) Box(modifier = Modifier.weight(nWeight).fillMaxHeight().background(Color(0xFF2563EB)))
                    if (rule.wantsPct > 0) Box(modifier = Modifier.weight(wWeight).fillMaxHeight().background(Color(0xFFD97706)))
                    if (rule.savingsPct > 0) Box(modifier = Modifier.weight(sWeight).fillMaxHeight().background(Color(0xFF059669)))
                }
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    Text("Needs ${rule.needsPct}% (target 50%)", style = MaterialTheme.typography.bodySmall, color = Color(0xFF2563EB))
                    Text(mask("${formatAmount(rule.needsAmount)} ${rule.baseCurrency}"), style = MaterialTheme.typography.bodySmall.copy(fontFeatureSettings = "tnum"), fontWeight = FontWeight.SemiBold)
                }
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    Text("Wants ${rule.wantsPct}% (target 30%)", style = MaterialTheme.typography.bodySmall, color = Color(0xFFD97706))
                    Text(mask("${formatAmount(rule.wantsAmount)} ${rule.baseCurrency}"), style = MaterialTheme.typography.bodySmall.copy(fontFeatureSettings = "tnum"), fontWeight = FontWeight.SemiBold)
                }
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    Text("Savings ${rule.savingsPct}% (target 20%)", style = MaterialTheme.typography.bodySmall, color = Color(0xFF059669))
                    Text(mask("${formatAmount(rule.savingsAmount)} ${rule.baseCurrency}"), style = MaterialTheme.typography.bodySmall.copy(fontFeatureSettings = "tnum"), fontWeight = FontWeight.SemiBold)
                }
            }
        }
    }
}

@Composable
private fun MerchantProfileCard(
    profile: com.spendingtracker.app.data.MerchantProfileResponse?,
    privacyMode: Boolean,
    onInspect: (String) -> Unit
) {
    var query by remember { mutableStateOf(profile?.vendor ?: "") }
    fun mask(s: String) = if (privacyMode) "••••••" else s

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
            Text(
                text = "MERCHANT LIFETIME PROFILE",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                OutlinedTextField(
                    value = query,
                    onValueChange = { query = it },
                    placeholder = { Text("Merchant name (e.g. Grab)", style = MaterialTheme.typography.bodySmall) },
                    singleLine = true,
                    modifier = Modifier.weight(1f)
                )
                Button(
                    onClick = { if (query.isNotBlank()) onInspect(query) },
                    enabled = query.isNotBlank()
                ) {
                    Text("Inspect")
                }
            }
            if (profile == null) {
                Text(
                    text = "Tap any Top Merchant or search a vendor above to inspect lifetime spend, visits, and average ticket.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            } else if (profile.visitCount <= 0) {
                Text(
                    text = "No spending found matching \"${profile.vendor}\".",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            } else {
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Surface(color = MaterialTheme.colorScheme.surfaceVariant, shape = RoundedCornerShape(10.dp), modifier = Modifier.weight(1f)) {
                        Column(modifier = Modifier.padding(10.dp)) {
                            Text("Lifetime", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            Text(mask(formatAmount(profile.totalSpent)), style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Bold)
                        }
                    }
                    Surface(color = MaterialTheme.colorScheme.surfaceVariant, shape = RoundedCornerShape(10.dp), modifier = Modifier.weight(1f)) {
                        Column(modifier = Modifier.padding(10.dp)) {
                            Text("Visits", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            Text("${profile.visitCount}x", style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Bold)
                        }
                    }
                    Surface(color = MaterialTheme.colorScheme.surfaceVariant, shape = RoundedCornerShape(10.dp), modifier = Modifier.weight(1f)) {
                        Column(modifier = Modifier.padding(10.dp)) {
                            Text("Avg Ticket", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            Text(mask(formatAmount(profile.avgTicket)), style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Bold)
                        }
                    }
                }
                Text(
                    text = "Range: ${mask(formatAmount(profile.minAmount))} – ${mask(formatAmount(profile.maxAmount))} ${profile.baseCurrency} · ${profile.firstSeen ?: ""} → ${profile.lastSeen ?: ""}",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                profile.recentTransactions.take(4).forEach { tx ->
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        Text("${tx.txDate} · ${tx.vendor}", style = MaterialTheme.typography.bodySmall)
                        Text(mask("${formatAmount(tx.amount)} ${tx.currency}"), style = MaterialTheme.typography.bodySmall.copy(fontFeatureSettings = "tnum"), fontWeight = FontWeight.SemiBold)
                    }
                }
            }
        }
    }
}

@Composable
private fun YearlyWrappedCard(
    wrapped: com.spendingtracker.app.data.YearlyWrappedResponse,
    privacyMode: Boolean,
    onPrevYear: () -> Unit,
    onNextYear: () -> Unit,
    onInspectMerchant: (String) -> Unit
) {
    fun mask(s: String) = if (privacyMode) "••••••" else s

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
                    text = "YEAR IN REVIEW (${wrapped.year})",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Row(verticalAlignment = Alignment.CenterVertically) {
                    TextButton(onClick = onPrevYear) { Text("‹") }
                    Text("${wrapped.year}", style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.SemiBold)
                    TextButton(onClick = onNextYear) { Text("›") }
                }
            }
            if (wrapped.transactionCount <= 0) {
                Text(
                    text = "No confirmed transactions recorded in ${wrapped.year}.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            } else {
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Surface(color = MaterialTheme.colorScheme.primaryContainer, shape = RoundedCornerShape(10.dp), modifier = Modifier.weight(1f)) {
                        Column(modifier = Modifier.padding(10.dp)) {
                            Text("Income", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onPrimaryContainer)
                            Text(mask("+${formatAmount(wrapped.totalIncome)}"), style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onPrimaryContainer)
                        }
                    }
                    Surface(color = MaterialTheme.colorScheme.surfaceVariant, shape = RoundedCornerShape(10.dp), modifier = Modifier.weight(1f)) {
                        Column(modifier = Modifier.padding(10.dp)) {
                            Text("Spent", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            Text(mask("-${formatAmount(wrapped.totalSpent)}"), style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.error)
                        }
                    }
                }
                Text(
                    text = "${wrapped.transactionCount} tx · Savings rate: ${wrapped.savingsRatePct?.let { "$it%" } ?: "—"} · Lowest: ${wrapped.bestMonth ?: "—"} · Highest: ${wrapped.busiestMonth ?: "—"}",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                wrapped.biggestExpense?.let { big ->
                    Text(
                        text = "Biggest expense: ${big.vendor} (${mask("${formatAmount(big.amount)} ${wrapped.baseCurrency}")} on ${big.txDate})",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                // 12-Month Bar Chart
                val maxMonth = wrapped.months.maxOfOrNull { it.totalSpent.toDoubleOrNull() ?: 0.0 }?.coerceAtLeast(1.0) ?: 1.0
                Row(
                    modifier = Modifier.fillMaxWidth().height(72.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.Bottom
                ) {
                    wrapped.months.forEach { m ->
                        val ratio = ((m.totalSpent.toDoubleOrNull() ?: 0.0) / maxMonth).toFloat().coerceIn(0f, 1f)
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.Bottom,
                            modifier = Modifier.weight(1f).fillMaxHeight()
                        ) {
                            Box(
                                modifier = Modifier
                                    .width(8.dp)
                                    .height((ratio * 48f).coerceAtLeast(3f).dp)
                                    .clip(RoundedCornerShape(topStart = 3.dp, topEnd = 3.dp))
                                    .background(MaterialTheme.colorScheme.primary)
                            )
                            Spacer(modifier = Modifier.height(3.dp))
                            Text(m.label.take(1), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                }
                if (wrapped.topMerchants.isNotEmpty()) {
                    HorizontalDivider(color = MaterialTheme.colorScheme.outline)
                    Text("Top Merchants (${wrapped.year})", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    wrapped.topMerchants.take(3).forEach { m ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { onInspectMerchant(m.vendor) }
                                .padding(vertical = 2.dp),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text("${m.vendor} (${m.transactionCount}x)", style = MaterialTheme.typography.bodySmall)
                            Text(mask("${formatAmount(m.totalSpent)} ${wrapped.baseCurrency}"), style = MaterialTheme.typography.bodySmall.copy(fontFeatureSettings = "tnum"), fontWeight = FontWeight.SemiBold)
                        }
                    }
                }
            }
        }
    }
}
