package com.spendingtracker.app.ui

import android.app.Application
import android.net.Uri
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.spendingtracker.app.data.ApiClient
import com.spendingtracker.app.data.AskLedgerRequest
import com.spendingtracker.app.data.BudgetRecord
import com.spendingtracker.app.data.BudgetSuggestionsResponse
import com.spendingtracker.app.data.BulkTransactionsRequest
import com.spendingtracker.app.data.CachedDashboardSnapshot
import com.spendingtracker.app.data.CategoryHistoryResponse
import com.spendingtracker.app.data.CategorySpending
import com.spendingtracker.app.data.ChangePasswordRequest
import com.spendingtracker.app.data.ConfirmTransactionItem
import com.spendingtracker.app.data.ConfirmTransactionsRequest
import com.spendingtracker.app.data.CreateCategoryRequest
import com.spendingtracker.app.data.CreateGoalRequest
import com.spendingtracker.app.data.CreateIouRequest
import com.spendingtracker.app.data.CreateTemplateRequest
import com.spendingtracker.app.data.CreateTransactionRequest
import com.spendingtracker.app.data.CreateWalletRequest
import com.spendingtracker.app.data.DashboardCache
import com.spendingtracker.app.data.DraftTransactionPreview
import com.spendingtracker.app.data.DuplicateScanResponse
import com.spendingtracker.app.data.EmergencyRunway
import com.spendingtracker.app.data.FinancialHealthScore
import com.spendingtracker.app.data.ForecastAndHealthResponse
import com.spendingtracker.app.data.FxRatesResponse
import com.spendingtracker.app.data.GlobalSearchResponse
import com.spendingtracker.app.data.GoalRecord
import com.spendingtracker.app.data.IngestResponse
import com.spendingtracker.app.data.IouRecord
import com.spendingtracker.app.data.LoginRequest
import com.spendingtracker.app.data.MediaUtils
import com.spendingtracker.app.data.MerchantProfileResponse
import com.spendingtracker.app.data.MerchantRuleRecord
import com.spendingtracker.app.data.MonthlyComparisonResponse
import com.spendingtracker.app.data.MonthlySpendingResponse
import com.spendingtracker.app.data.MonthlyTrendPoint
import com.spendingtracker.app.data.OfflineQueue
import com.spendingtracker.app.data.PaceComparison
import com.spendingtracker.app.data.QuickInputParser
import com.spendingtracker.app.data.QuickTemplateRecord
import com.spendingtracker.app.data.RangeSpendingResponse
import com.spendingtracker.app.data.RecurringBillItem
import com.spendingtracker.app.data.RecurringSummaryResponse
import com.spendingtracker.app.data.RegisterRequest
import com.spendingtracker.app.data.Rule503020Response
import com.spendingtracker.app.data.SpendingChallenge
import com.spendingtracker.app.data.SplitItemRequest
import com.spendingtracker.app.data.SplitTransactionRequest
import com.spendingtracker.app.data.SubscriptionAuditResponse
import com.spendingtracker.app.data.TagSpending
import com.spendingtracker.app.data.TokenStore
import com.spendingtracker.app.data.TransactionRecord
import com.spendingtracker.app.data.TransferWalletRequest
import com.spendingtracker.app.data.UpdateGoalRequest
import com.spendingtracker.app.data.UpdatePreferencesRequest
import com.spendingtracker.app.data.UpdateTransactionRequest
import com.spendingtracker.app.data.UpsertBudgetRequest
import com.spendingtracker.app.data.UpsertMerchantRuleRequest
import com.spendingtracker.app.data.UserPreferencesResponse
import com.spendingtracker.app.data.WalletRecord
import com.spendingtracker.app.data.WeekdayBreakdownResponse
import com.spendingtracker.app.data.YearlyWrappedResponse
import com.spendingtracker.app.data.ZeroBalanceRequest
import com.spendingtracker.app.widget.SpendingWidgetProvider
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.async
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.supervisorScope
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.RequestBody.Companion.toRequestBody
import java.io.IOException
import java.time.YearMonth

data class PendingMediaUpload(
    val uris: List<Uri>,
    val sourceType: String?
)

private data class Stage1Result(
    val summary: retrofit2.Response<MonthlySpendingResponse>,
    val txs: retrofit2.Response<List<TransactionRecord>>,
    val range: retrofit2.Response<RangeSpendingResponse>?,
    val budgets: retrofit2.Response<List<BudgetRecord>>?,
    val wallets: retrofit2.Response<List<WalletRecord>>?,
    val templates: retrofit2.Response<List<QuickTemplateRecord>>?,
    val prefs: retrofit2.Response<UserPreferencesResponse>?
)

data class UiState(
    val isAuthenticated: Boolean = false,
    val biometricEnabled: Boolean = false,
    val themeMode: String = "system",
    val accentPalette: String = "forest",
    val privacyMode: Boolean = false,
    val bankAlertsEnabled: Boolean = false,
    val userEmail: String? = null,
    val isLoading: Boolean = false,
    val isUploading: Boolean = false,
    val selectedMonth: YearMonth = YearMonth.now(),
    val rangeStart: String? = null,
    val rangeEnd: String? = null,
    val rangeSummary: RangeSpendingResponse? = null,
    val monthlySpending: MonthlySpendingResponse? = null,
    val comparison: MonthlyComparisonResponse? = null,
    val forecast: ForecastAndHealthResponse? = null,
    val preferences: UserPreferencesResponse? = null,
    val weekdayBreakdown: WeekdayBreakdownResponse? = null,
    val fxRates: FxRatesResponse? = null,
    val globalSearch: GlobalSearchResponse? = null,
    val rule503020: Rule503020Response? = null,
    val yearlyWrapped: YearlyWrappedResponse? = null,
    val merchantProfile: MerchantProfileResponse? = null,
    val budgetSuggestions: BudgetSuggestionsResponse? = null,
    val subscriptionAudit: SubscriptionAuditResponse? = null,
    val incomeByCategory: List<CategorySpending> = emptyList(),
    val paceVsLastMonth: PaceComparison? = null,
    val duplicates: DuplicateScanResponse? = null,
    val healthScore: FinancialHealthScore? = null,
    val emergencyRunway: EmergencyRunway? = null,
    val categoryHistory: CategoryHistoryResponse? = null,
    val challenges: List<SpendingChallenge> = emptyList(),
    val askLedgerAnswer: String? = null,
    val isAskingLedger: Boolean = false,
    val transactions: List<TransactionRecord> = emptyList(),
    val wallets: List<WalletRecord> = emptyList(),
    val templates: List<QuickTemplateRecord> = emptyList(),
    val rules: List<MerchantRuleRecord> = emptyList(),
    val tags: List<TagSpending> = emptyList(),
    val budgets: List<BudgetRecord> = emptyList(),
    val trend: List<MonthlyTrendPoint> = emptyList(),
    val recurring: RecurringSummaryResponse? = null,
    val goals: List<GoalRecord> = emptyList(),
    val ious: List<IouRecord> = emptyList(),
    val insightsTips: List<String> = emptyList(),
    val isLoadingInsights: Boolean = false,
    val categories: List<String> = DEFAULT_CATEGORY_NAMES,
    val offlineQueueCount: Int = 0,
    val pendingDraft: IngestResponse? = null,
    val pendingPasswordPrompt: PendingMediaUpload? = null,
    val errorMessage: String? = null,
    val statusMessage: String? = null
)

val DEFAULT_CATEGORY_NAMES = listOf(
    "Debt Repayment", "Dining", "Education", "Entertainment", "Family & Kids",
    "Fees & Interest", "Gifts & Donations", "Groceries", "Health", "Housing",
    "Income", "Insurance", "Investments", "Personal Care", "Refund", "Salary",
    "Shopping", "Subscriptions", "Transfer", "Transport", "Travel", "Utilities",
    "Uncategorized"
)

class SpendingViewModel(application: Application) : AndroidViewModel(application) {

    private val tokenStore = TokenStore(application.applicationContext)
    private val offlineQueue = OfflineQueue(application.applicationContext)
    private val dashboardCache = DashboardCache(application.applicationContext)
    private val api = ApiClient.create(tokenStore)

    private val _uiState = MutableStateFlow(
        UiState(
            isAuthenticated = !tokenStore.getToken().isNullOrBlank(),
            biometricEnabled = tokenStore.isBiometricEnabled(),
            themeMode = tokenStore.getThemeMode(),
            accentPalette = tokenStore.getAccentPalette(),
            privacyMode = tokenStore.isPrivacyMode(),
            bankAlertsEnabled = tokenStore.isBankAlertsEnabled(),
            userEmail = tokenStore.getEmail(),
            offlineQueueCount = offlineQueue.count()
        )
    )
    val uiState: StateFlow<UiState> = _uiState.asStateFlow()

    private var queuedShareUpload: PendingMediaUpload? = null
    private var refreshJob: Job? = null

    init {
        if (_uiState.value.isAuthenticated) {
            dashboardCache.load()?.let { cached ->
                _uiState.update {
                    it.copy(
                        monthlySpending = cached.monthlySpending,
                        transactions = cached.transactions,
                        wallets = cached.wallets,
                        templates = cached.templates,
                        budgets = cached.budgets,
                        goals = cached.goals,
                        ious = cached.ious,
                        comparison = cached.comparison,
                        forecast = cached.forecast,
                        preferences = cached.preferences
                    )
                }
            }
            refreshDashboard()
        }
    }

    fun clearMessages() {
        _uiState.update { it.copy(errorMessage = null, statusMessage = null) }
    }

    fun setBiometricEnabled(enabled: Boolean) {
        tokenStore.setBiometricEnabled(enabled)
        _uiState.update {
            it.copy(
                biometricEnabled = enabled,
                statusMessage = if (enabled) "Biometric lock enabled" else "Biometric lock disabled"
            )
        }
    }

    fun cycleThemeMode() {
        val next = when (_uiState.value.themeMode) {
            "system" -> "light"
            "light" -> "dark"
            else -> "system"
        }
        tokenStore.setThemeMode(next)
        _uiState.update {
            it.copy(themeMode = next, statusMessage = "Theme: ${next.replaceFirstChar { c -> c.uppercase() }}")
        }
    }

    fun togglePrivacyMode() {
        val next = !_uiState.value.privacyMode
        tokenStore.setPrivacyMode(next)
        _uiState.update {
            it.copy(privacyMode = next, statusMessage = if (next) "Amounts hidden" else "Amounts visible")
        }
    }

    fun setBankAlertsEnabled(enabled: Boolean) {
        tokenStore.setBankAlertsEnabled(enabled)
        _uiState.update {
            it.copy(
                bankAlertsEnabled = enabled,
                statusMessage = if (enabled) "Bank notification auto-capture ON" else "Bank notification auto-capture OFF"
            )
        }
    }

    fun logNaturalQuickInput(raw: String) {
        val parsed = QuickInputParser.parse(raw, _uiState.value.wallets)
        if (parsed == null) {
            _uiState.update { it.copy(errorMessage = "Try: '50k coffee cash #work' or '+15m salary'") }
            return
        }
        val prefs = _uiState.value.preferences
        val cur = prefs?.activeEventCurrency?.takeIf { it.isNotBlank() }
            ?: _uiState.value.monthlySpending?.baseCurrency ?: "VND"
        val mergedTags = buildList {
            addAll(parsed.tags)
            prefs?.activeEventTag?.takeIf { it.isNotBlank() && !contains(it) }?.let { add(it) }
        }
        createManualTransaction(
            txDate = java.time.LocalDate.now().toString(),
            vendor = parsed.vendor,
            amount = parsed.amount,
            txType = parsed.txType,
            currency = cur,
            categoryName = parsed.categoryName,
            description = null,
            isRecurring = false,
            walletId = parsed.walletId,
            tags = mergedTags
        )
    }

    fun updatePreferences(cycleStartDay: Int?, activeEventTag: String?, activeEventCurrency: String?) {
        viewModelScope.launch {
            try {
                val res = api.updatePreferences(
                    UpdatePreferencesRequest(
                        cycleStartDay = cycleStartDay,
                        activeEventTag = activeEventTag,
                        activeEventCurrency = activeEventCurrency
                    )
                )
                if (res.isSuccessful && res.body() != null) {
                    _uiState.update { it.copy(preferences = res.body(), statusMessage = "Preferences saved") }
                    refreshDashboard()
                } else {
                    _uiState.update { it.copy(errorMessage = ApiClient.parseErrorMessage(res)) }
                }
            } catch (e: kotlinx.coroutines.CancellationException) {
                throw e
            } catch (e: Exception) {
                _uiState.update { it.copy(errorMessage = e.localizedMessage ?: "Failed to save preferences") }
            }
        }
    }

    fun splitTransaction(id: String, items: List<SplitItemRequest>) {
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true, errorMessage = null) }
            try {
                val res = api.splitTransaction(id, SplitTransactionRequest(items))
                if (res.isSuccessful) {
                    _uiState.update { it.copy(isLoading = false, statusMessage = "Split into ${items.size} categories") }
                    refreshDashboard()
                } else {
                    _uiState.update { it.copy(isLoading = false, errorMessage = ApiClient.parseErrorMessage(res)) }
                }
            } catch (e: kotlinx.coroutines.CancellationException) {
                throw e
            } catch (e: Exception) {
                _uiState.update { it.copy(isLoading = false, errorMessage = e.localizedMessage ?: "Split failed") }
            }
        }
    }

    fun bulkDeleteTransactions(ids: List<String>) {
        if (ids.isEmpty()) return
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true, errorMessage = null) }
            try {
                val res = api.bulkTransactions(BulkTransactionsRequest(ids = ids, action = "delete"))
                if (res.isSuccessful) {
                    _uiState.update { it.copy(isLoading = false, statusMessage = "Deleted ${res.body()?.affectedCount ?: ids.size} transaction(s)") }
                    refreshDashboard()
                } else {
                    _uiState.update { it.copy(isLoading = false, errorMessage = ApiClient.parseErrorMessage(res)) }
                }
            } catch (e: kotlinx.coroutines.CancellationException) {
                throw e
            } catch (e: Exception) {
                _uiState.update { it.copy(isLoading = false, errorMessage = e.localizedMessage ?: "Bulk delete failed") }
            }
        }
    }

    fun bulkUpdateCategory(ids: List<String>, categoryName: String) {
        if (ids.isEmpty()) return
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true, errorMessage = null) }
            try {
                val res = api.bulkTransactions(
                    BulkTransactionsRequest(ids = ids, action = "update", categoryName = categoryName)
                )
                if (res.isSuccessful) {
                    _uiState.update { it.copy(isLoading = false, statusMessage = "Updated ${res.body()?.affectedCount ?: ids.size} transaction(s) → $categoryName") }
                    refreshDashboard()
                } else {
                    _uiState.update { it.copy(isLoading = false, errorMessage = ApiClient.parseErrorMessage(res)) }
                }
            } catch (e: kotlinx.coroutines.CancellationException) {
                throw e
            } catch (e: Exception) {
                _uiState.update { it.copy(isLoading = false, errorMessage = e.localizedMessage ?: "Bulk update failed") }
            }
        }
    }

    fun logRecurringBillNow(bill: RecurringBillItem) {
        createManualTransaction(
            txDate = java.time.LocalDate.now().toString(),
            vendor = bill.vendor,
            amount = bill.avgAmount,
            txType = "debit",
            currency = bill.currency,
            categoryName = bill.categoryName,
            description = "Recurring bill",
            isRecurring = true
        )
    }

    fun login(email: String, password: String) {
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true, errorMessage = null) }
            try {
                val res = api.login(LoginRequest(email.trim(), password))
                if (res.isSuccessful && res.body() != null) {
                    val body = res.body()!!
                    tokenStore.saveSession(body.accessToken, body.user.email)
                    _uiState.update {
                        it.copy(
                            isAuthenticated = true,
                            userEmail = body.user.email,
                            isLoading = false
                        )
                    }
                    refreshDashboard()
                    flushQueuedUpload()
                } else {
                    _uiState.update {
                        it.copy(isLoading = false, errorMessage = ApiClient.parseErrorMessage(res))
                    }
                }
            } catch (e: kotlinx.coroutines.CancellationException) {
                throw e
            } catch (e: Exception) {
                _uiState.update {
                    it.copy(isLoading = false, errorMessage = e.localizedMessage ?: "Network error")
                }
            }
        }
    }

    fun register(email: String, password: String, displayName: String?) {
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true, errorMessage = null) }
            try {
                val res = api.register(
                    RegisterRequest(
                        email = email.trim(),
                        password = password,
                        displayName = displayName?.trim()?.takeIf { it.isNotEmpty() }
                    )
                )
                if (res.isSuccessful && res.body() != null) {
                    val body = res.body()!!
                    tokenStore.saveSession(body.accessToken, body.user.email)
                    _uiState.update {
                        it.copy(
                            isAuthenticated = true,
                            userEmail = body.user.email,
                            isLoading = false
                        )
                    }
                    refreshDashboard()
                    flushQueuedUpload()
                } else {
                    _uiState.update {
                        it.copy(isLoading = false, errorMessage = ApiClient.parseErrorMessage(res))
                    }
                }
            } catch (e: kotlinx.coroutines.CancellationException) {
                throw e
            } catch (e: Exception) {
                _uiState.update {
                    it.copy(isLoading = false, errorMessage = e.localizedMessage ?: "Network error")
                }
            }
        }
    }

    fun logout() {
        tokenStore.clear()
        dashboardCache.clear()
        queuedShareUpload = null
        _uiState.value = UiState(isAuthenticated = false, offlineQueueCount = offlineQueue.count())
    }

    fun changePassword(currentPassword: String, newPassword: String) {
        viewModelScope.launch {
            try {
                val res = api.changePassword(ChangePasswordRequest(currentPassword, newPassword))
                if (res.isSuccessful) {
                    _uiState.update { it.copy(statusMessage = "Password updated successfully") }
                } else {
                    _uiState.update { it.copy(errorMessage = ApiClient.parseErrorMessage(res)) }
                }
            } catch (e: kotlinx.coroutines.CancellationException) {
                throw e
            } catch (e: Exception) {
                _uiState.update { it.copy(errorMessage = e.localizedMessage ?: "Password change failed") }
            }
        }
    }

    fun restoreDeletedTransaction(tx: TransactionRecord) {
        createManualTransaction(
            txDate = tx.txDate,
            vendor = tx.vendor,
            amount = tx.amount,
            txType = tx.txType,
            currency = tx.currency,
            categoryName = tx.categoryName ?: "Uncategorized",
            description = tx.description,
            isRecurring = tx.isRecurring,
            walletId = tx.walletId,
            tags = tx.tags
        )
    }

    fun previousMonth() {
        _uiState.update { it.copy(selectedMonth = it.selectedMonth.minusMonths(1)) }
        refreshDashboard()
    }

    fun nextMonth() {
        _uiState.update { it.copy(selectedMonth = it.selectedMonth.plusMonths(1)) }
        refreshDashboard()
    }

    fun refreshDashboard() {
        if (!_uiState.value.isAuthenticated) return
        val ym = _uiState.value.selectedMonth
        refreshJob?.cancel()
        refreshJob = viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true, errorMessage = null) }
            try {
                val rStart = _uiState.value.rangeStart
                val rEnd = _uiState.value.rangeEnd
                val wrappedYear = _uiState.value.yearlyWrapped?.year ?: ym.year

                // Stage 1 (v1.9.0): Single-request gzipped dashboard bootstrap
                val bootRes = api.getDashboardBootstrap(
                    year = ym.year,
                    month = ym.monthValue,
                    startDate = rStart,
                    endDate = rEnd,
                    status = "confirmed"
                )
                val rangeRes = if (rStart != null && rEnd != null) {
                    runCatching { api.getRangeSpending(rStart, rEnd) }.getOrNull()
                } else null

                if (bootRes.code() == 401) {
                    logout()
                    _uiState.update { it.copy(errorMessage = "Session expired. Please sign in again.") }
                    return@launch
                }

                if (bootRes.isSuccessful && bootRes.body() != null) {
                    val boot = bootRes.body()!!
                    val summaryBody = boot.monthlySpending
                    tokenStore.saveWidgetSnapshot(
                        balance = summaryBody.cumulativeBalance,
                        monthSpent = summaryBody.totalSpent,
                        currency = summaryBody.baseCurrency
                    )
                    SpendingWidgetProvider.notifyDataChanged(getApplication<Application>().applicationContext)

                    // Immediately paint Home & Ledger and clear loading spinner
                    _uiState.update {
                        it.copy(
                            isLoading = false,
                            monthlySpending = summaryBody,
                            preferences = boot.preferences ?: it.preferences,
                            incomeByCategory = boot.incomeByCategory,
                            paceVsLastMonth = boot.paceVsLastMonth,
                            globalSearch = null,
                            rangeSummary = if (rangeRes?.isSuccessful == true) rangeRes.body() else null,
                            transactions = boot.transactions,
                            wallets = boot.wallets,
                            templates = boot.templates,
                            budgets = boot.budgets,
                            offlineQueueCount = offlineQueue.count()
                        )
                    }

                    // Stage 2 (v2.0.0): Single-request gzipped analytics bundle (replaces 14 calls with 1!)
                    val anRes = runCatching { api.getDashboardAnalytics(ym.year, ym.monthValue, wrappedYear) }.getOrNull()
                    if (anRes?.isSuccessful == true && anRes.body() != null) {
                        val an = anRes.body()!!
                        _uiState.update {
                            it.copy(
                                comparison = an.comparison ?: it.comparison,
                                forecast = an.forecast ?: it.forecast,
                                weekdayBreakdown = an.weekday ?: it.weekdayBreakdown,
                                fxRates = an.fxRates ?: it.fxRates,
                                rule503020 = an.rule503020 ?: it.rule503020,
                                yearlyWrapped = an.yearlyWrapped ?: it.yearlyWrapped,
                                subscriptionAudit = an.subscriptionAudit ?: it.subscriptionAudit,
                                healthScore = an.healthScore ?: it.healthScore,
                                emergencyRunway = an.emergencyRunway ?: it.emergencyRunway,
                                challenges = an.challenges,
                                rules = an.rules,
                                tags = an.tags,
                                trend = an.trend,
                                recurring = an.recurring ?: it.recurring,
                                goals = an.goals,
                                ious = an.ious,
                                categories = if (an.categories.isNotEmpty()) an.categories.map { c -> c.name } else it.categories
                            )
                        }
                    }

                    // Persist offline read-only cache snapshot
                    val snap = _uiState.value
                    dashboardCache.save(
                        CachedDashboardSnapshot(
                            monthlySpending = snap.monthlySpending,
                            transactions = snap.transactions,
                            wallets = snap.wallets,
                            templates = snap.templates,
                            budgets = snap.budgets,
                            goals = snap.goals,
                            ious = snap.ious,
                            comparison = snap.comparison,
                            forecast = snap.forecast,
                            preferences = snap.preferences
                        )
                    )
                    // Try flushing offline receipts quietly if any are queued
                    if (offlineQueue.count() > 0) {
                        flushOfflineQueue()
                    }
                } else {
                    _uiState.update { it.copy(isLoading = false, errorMessage = ApiClient.parseErrorMessage(bootRes)) }
                }
            } catch (e: kotlinx.coroutines.CancellationException) {
                throw e
            } catch (e: Exception) {
                val hasCache = _uiState.value.monthlySpending != null
                _uiState.update {
                    it.copy(
                        isLoading = false,
                        offlineQueueCount = offlineQueue.count(),
                        statusMessage = if (hasCache) "Offline — showing cached ledger" else it.statusMessage,
                        errorMessage = if (hasCache) null else (e.localizedMessage ?: "Failed to load data")
                    )
                }
            }
        }
    }

    fun uploadMedia(uris: List<Uri>, sourceType: String? = null, pdfPassword: String? = null) {
        if (uris.isEmpty()) return

        if (!_uiState.value.isAuthenticated) {
            queuedShareUpload = PendingMediaUpload(uris, sourceType)
            _uiState.update {
                it.copy(statusMessage = "Sign in to finish importing your shared file(s).")
            }
            return
        }

        viewModelScope.launch {
            _uiState.update {
                it.copy(
                    isUploading = true,
                    errorMessage = null,
                    pendingPasswordPrompt = null
                )
            }
            try {
                val ctx = getApplication<Application>().applicationContext
                val parts = withContext(Dispatchers.IO) {
                    MediaUtils.urisToMultipartParts(ctx, uris)
                }
                if (parts.isEmpty()) {
                    _uiState.update {
                        it.copy(isUploading = false, errorMessage = "Could not read selected file(s).")
                    }
                    return@launch
                }

                val textMedia = "text/plain".toMediaType()
                val pwdBody = pdfPassword?.takeIf { it.isNotBlank() }?.toRequestBody(textMedia)
                val sourceBody = sourceType?.takeIf { it.isNotBlank() }?.toRequestBody(textMedia)

                val res = api.ingestMedia(parts, pwdBody, sourceBody)
                if (res.code() == 401) {
                    logout()
                    _uiState.update { it.copy(errorMessage = "Session expired. Please sign in again.") }
                    return@launch
                }

                if (res.isSuccessful && res.body() != null) {
                    _uiState.update {
                        it.copy(isUploading = false, pendingDraft = res.body())
                    }
                } else {
                    val errMsg = ApiClient.parseErrorMessage(res)
                    val needsPassword = res.code() == 422 &&
                        errMsg.contains("password", ignoreCase = true)
                    if (needsPassword) {
                        _uiState.update {
                            it.copy(
                                isUploading = false,
                                pendingPasswordPrompt = PendingMediaUpload(uris, sourceType),
                                errorMessage = errMsg
                            )
                        }
                    } else {
                        _uiState.update {
                            it.copy(isUploading = false, errorMessage = errMsg)
                        }
                    }
                }
            } catch (_: IOException) {
                // Offline: persist in app-private offline queue for later upload
                val saved = withContext(Dispatchers.IO) {
                    offlineQueue.enqueue(uris, sourceType)
                }
                _uiState.update {
                    it.copy(
                        isUploading = false,
                        offlineQueueCount = offlineQueue.count(),
                        statusMessage = if (saved) {
                            "Offline — receipt saved to queue and will upload when back online."
                        } else {
                            "Network error during upload."
                        }
                    )
                }
            } catch (e: kotlinx.coroutines.CancellationException) {
                throw e
            } catch (e: Exception) {
                _uiState.update {
                    it.copy(isUploading = false, errorMessage = e.localizedMessage ?: "Upload failed")
                }
            }
        }
    }

    fun flushOfflineQueue() {
        if (!_uiState.value.isAuthenticated || _uiState.value.isUploading) return
        viewModelScope.launch {
            val batches = withContext(Dispatchers.IO) { offlineQueue.listBatches() }
            val manuals = withContext(Dispatchers.IO) { offlineQueue.listManualTransactions() }
            if (batches.isEmpty() && manuals.isEmpty()) return@launch
            var uploaded = 0
            var syncedManuals = 0
            val textMedia = "text/plain".toMediaType()
            for (item in manuals) {
                try {
                    val res = api.createTransaction(item.request)
                    if (res.isSuccessful) {
                        offlineQueue.removeManualTransaction(item.id)
                        syncedManuals++
                    }
                } catch (_: Exception) {
                    break
                }
            }
            for (batch in batches) {
                try {
                    val parts = withContext(Dispatchers.IO) { offlineQueue.toMultipartParts(batch) }
                    if (parts.isEmpty()) {
                        offlineQueue.remove(batch.id)
                        continue
                    }
                    val sourceBody = batch.sourceType?.toRequestBody(textMedia)
                    val res = api.ingestMedia(parts, null, sourceBody)
                    if (res.isSuccessful) {
                        offlineQueue.remove(batch.id)
                        uploaded++
                    }
                } catch (_: Exception) {
                    break
                }
            }
            val msg = buildList {
                if (syncedManuals > 0) add("Synced $syncedManuals offline transaction(s)")
                if (uploaded > 0) add("Uploaded $uploaded queued receipt(s)")
            }.joinToString(" · ").takeIf { it.isNotEmpty() }
            _uiState.update {
                it.copy(
                    offlineQueueCount = offlineQueue.count(),
                    statusMessage = msg ?: it.statusMessage
                )
            }
            if (uploaded > 0 || syncedManuals > 0) refreshDashboard()
        }
    }

    fun openPendingDrafts() {
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true, errorMessage = null) }
            try {
                val res = api.getPendingTransactions()
                if (res.isSuccessful && res.body() != null) {
                    val pending = res.body()!!.map { t ->
                        DraftTransactionPreview(
                            id = t.id,
                            txDate = t.txDate,
                            vendor = t.vendor,
                            description = t.description,
                            categoryId = t.categoryId ?: "",
                            categoryName = t.categoryName ?: "Uncategorized",
                            amount = t.amount,
                            txType = t.txType,
                            currency = t.currency,
                            source = t.source,
                            status = t.status,
                            confidence = 1.0,
                            potentialDuplicateIds = emptyList()
                        )
                    }
                    _uiState.update {
                        it.copy(
                            isLoading = false,
                            pendingDraft = IngestResponse(
                                documentId = "pending",
                                sourceType = "pending_drafts",
                                modelUsed = "saved drafts",
                                openingBalance = null,
                                closingBalance = null,
                                reconciled = false,
                                reconciliationDiff = null,
                                transactions = pending,
                                skippedExactDuplicates = 0
                            )
                        )
                    }
                } else {
                    _uiState.update {
                        it.copy(isLoading = false, errorMessage = ApiClient.parseErrorMessage(res))
                    }
                }
            } catch (e: kotlinx.coroutines.CancellationException) {
                throw e
            } catch (e: Exception) {
                _uiState.update {
                    it.copy(isLoading = false, errorMessage = e.localizedMessage ?: "Failed to load drafts")
                }
            }
        }
    }

    fun dismissPasswordPrompt() {
        _uiState.update { it.copy(pendingPasswordPrompt = null) }
    }

    fun dismissDraftDialog() {
        _uiState.update { it.copy(pendingDraft = null) }
    }

    fun confirmDrafts(
        confirmed: List<ConfirmTransactionItem>,
        discardIds: List<String>
    ) {
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true, errorMessage = null) }
            try {
                val res = api.confirmTransactions(
                    ConfirmTransactionsRequest(transactions = confirmed, discardIds = discardIds)
                )
                if (res.isSuccessful && res.body() != null) {
                    val body = res.body()!!
                    _uiState.update {
                        it.copy(
                            isLoading = false,
                            pendingDraft = null,
                            statusMessage = "Saved ${body.confirmedCount} transaction(s)"
                        )
                    }
                    refreshDashboard()
                } else {
                    _uiState.update {
                        it.copy(isLoading = false, errorMessage = ApiClient.parseErrorMessage(res))
                    }
                }
            } catch (e: kotlinx.coroutines.CancellationException) {
                throw e
            } catch (e: Exception) {
                _uiState.update {
                    it.copy(isLoading = false, errorMessage = e.localizedMessage ?: "Confirm failed")
                }
            }
        }
    }

    fun createManualTransaction(
        txDate: String,
        vendor: String,
        amount: String,
        txType: String,
        currency: String,
        categoryName: String,
        description: String?,
        isRecurring: Boolean,
        walletId: String? = null,
        tags: List<String> = emptyList()
    ) {
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true, errorMessage = null) }
            val req = CreateTransactionRequest(
                txDate = txDate,
                vendor = vendor.trim(),
                description = description?.trim()?.takeIf { it.isNotEmpty() },
                categoryName = categoryName,
                walletId = walletId,
                amount = amount.trim(),
                txType = txType,
                currency = currency,
                isRecurring = isRecurring,
                tags = tags
            )
            try {
                val res = api.createTransaction(req)
                if (res.isSuccessful) {
                    _uiState.update { it.copy(isLoading = false, statusMessage = "Transaction added") }
                    refreshDashboard()
                } else {
                    _uiState.update {
                        it.copy(isLoading = false, errorMessage = ApiClient.parseErrorMessage(res))
                    }
                }
            } catch (e: kotlinx.coroutines.CancellationException) {
                throw e
            } catch (e: IOException) {
                val queued = withContext(Dispatchers.IO) { offlineQueue.enqueueManualTransaction(req) }
                _uiState.update {
                    it.copy(
                        isLoading = false,
                        offlineQueueCount = offlineQueue.count(),
                        statusMessage = if (queued) "Offline — queued \"${req.vendor}\" to auto-sync" else null,
                        errorMessage = if (queued) null else (e.localizedMessage ?: "Network error")
                    )
                }
            } catch (e: Exception) {
                _uiState.update {
                    it.copy(isLoading = false, errorMessage = e.localizedMessage ?: "Failed to add transaction")
                }
            }
        }
    }

    fun updateTransaction(
        id: String,
        txDate: String,
        vendor: String,
        amount: String,
        txType: String,
        currency: String,
        categoryName: String,
        description: String?,
        isRecurring: Boolean,
        walletId: String? = null,
        tags: List<String>? = null
    ) {
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true, errorMessage = null) }
            try {
                val res = api.updateTransaction(
                    id = id,
                    request = UpdateTransactionRequest(
                        txDate = txDate,
                        vendor = vendor.trim(),
                        description = description?.trim(),
                        categoryName = categoryName,
                        walletId = walletId,
                        amount = amount.trim(),
                        txType = txType,
                        currency = currency,
                        isRecurring = isRecurring,
                        tags = tags
                    )
                )
                if (res.isSuccessful) {
                    _uiState.update { it.copy(isLoading = false, statusMessage = "Transaction updated") }
                    refreshDashboard()
                } else {
                    _uiState.update {
                        it.copy(isLoading = false, errorMessage = ApiClient.parseErrorMessage(res))
                    }
                }
            } catch (e: kotlinx.coroutines.CancellationException) {
                throw e
            } catch (e: Exception) {
                _uiState.update {
                    it.copy(isLoading = false, errorMessage = e.localizedMessage ?: "Update failed")
                }
            }
        }
    }

    fun deleteTransaction(id: String) {
        val prevList = _uiState.value.transactions
        // Optimistic UI removal in 0ms
        _uiState.update {
            it.copy(
                transactions = it.transactions.filterNot { tx -> tx.id == id },
                errorMessage = null
            )
        }
        viewModelScope.launch {
            try {
                val res = api.deleteTransaction(id)
                if (res.isSuccessful) {
                    _uiState.update { it.copy(statusMessage = "Transaction deleted") }
                    refreshDashboard()
                } else {
                    _uiState.update {
                        it.copy(transactions = prevList, errorMessage = ApiClient.parseErrorMessage(res))
                    }
                }
            } catch (e: kotlinx.coroutines.CancellationException) {
                throw e
            } catch (e: Exception) {
                _uiState.update {
                    it.copy(transactions = prevList, errorMessage = e.localizedMessage ?: "Delete failed")
                }
            }
        }
    }

    fun upsertBudget(categoryName: String, monthlyLimit: String, rollover: Boolean = false) {
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true, errorMessage = null) }
            try {
                val res = api.upsertBudget(UpsertBudgetRequest(categoryName, monthlyLimit.trim(), rollover))
                if (res.isSuccessful) {
                    _uiState.update { it.copy(isLoading = false, statusMessage = "Budget saved for $categoryName") }
                    refreshDashboard()
                } else {
                    _uiState.update {
                        it.copy(isLoading = false, errorMessage = ApiClient.parseErrorMessage(res))
                    }
                }
            } catch (e: kotlinx.coroutines.CancellationException) {
                throw e
            } catch (e: Exception) {
                _uiState.update {
                    it.copy(isLoading = false, errorMessage = e.localizedMessage ?: "Failed to save budget")
                }
            }
        }
    }

    fun createCategory(name: String) {
        val clean = name.trim()
        if (clean.isEmpty()) return
        viewModelScope.launch {
            try {
                val res = api.createCategory(CreateCategoryRequest(clean))
                if (res.isSuccessful) {
                    _uiState.update { it.copy(statusMessage = "Category \"$clean\" added") }
                    refreshDashboard()
                } else {
                    _uiState.update { it.copy(errorMessage = ApiClient.parseErrorMessage(res)) }
                }
            } catch (e: kotlinx.coroutines.CancellationException) {
                throw e
            } catch (e: Exception) {
                _uiState.update { it.copy(errorMessage = e.localizedMessage ?: "Failed to add category") }
            }
        }
    }

    fun generateMonthlyInsights() {
        val ym = _uiState.value.selectedMonth
        viewModelScope.launch {
            _uiState.update { it.copy(isLoadingInsights = true, errorMessage = null) }
            try {
                val res = api.getMonthlyInsights(ym.year, ym.monthValue)
                if (res.isSuccessful && res.body() != null) {
                    _uiState.update {
                        it.copy(isLoadingInsights = false, insightsTips = res.body()!!.tips)
                    }
                } else {
                    _uiState.update {
                        it.copy(isLoadingInsights = false, errorMessage = ApiClient.parseErrorMessage(res))
                    }
                }
            } catch (e: kotlinx.coroutines.CancellationException) {
                throw e
            } catch (e: Exception) {
                _uiState.update {
                    it.copy(isLoadingInsights = false, errorMessage = e.localizedMessage ?: "Insights failed")
                }
            }
        }
    }

    fun askLedger(question: String) {
        val clean = question.trim()
        if (clean.isEmpty()) return
        val ym = _uiState.value.selectedMonth
        viewModelScope.launch {
            _uiState.update { it.copy(isAskingLedger = true, errorMessage = null) }
            try {
                val res = api.askLedger(AskLedgerRequest(clean, ym.year, ym.monthValue))
                if (res.isSuccessful && res.body() != null) {
                    val body = res.body()!!
                    _uiState.update {
                        it.copy(isAskingLedger = false, askLedgerAnswer = "Q: ${body.question}\nA: ${body.answer}")
                    }
                } else {
                    _uiState.update {
                        it.copy(isAskingLedger = false, errorMessage = ApiClient.parseErrorMessage(res))
                    }
                }
            } catch (e: kotlinx.coroutines.CancellationException) {
                throw e
            } catch (e: Exception) {
                _uiState.update {
                    it.copy(isAskingLedger = false, errorMessage = e.localizedMessage ?: "Ask failed")
                }
            }
        }
    }

    fun createGoal(name: String, goalType: String, targetAmount: String) {
        viewModelScope.launch {
            try {
                val res = api.createGoal(CreateGoalRequest(name.trim(), goalType, targetAmount.trim()))
                if (res.isSuccessful) {
                    _uiState.update { it.copy(statusMessage = "Goal added") }
                    refreshDashboard()
                } else {
                    _uiState.update { it.copy(errorMessage = ApiClient.parseErrorMessage(res)) }
                }
            } catch (e: kotlinx.coroutines.CancellationException) {
                throw e
            } catch (e: Exception) {
                _uiState.update { it.copy(errorMessage = e.localizedMessage ?: "Failed to add goal") }
            }
        }
    }

    fun updateGoalProgress(id: String, newAmount: String) {
        viewModelScope.launch {
            try {
                val res = api.updateGoal(id, UpdateGoalRequest(newAmount.trim()))
                if (res.isSuccessful) refreshDashboard()
            } catch (_: Exception) {}
        }
    }

    fun createIou(personName: String, direction: String, amount: String) {
        viewModelScope.launch {
            try {
                val res = api.createIou(CreateIouRequest(personName.trim(), direction, amount.trim()))
                if (res.isSuccessful) {
                    _uiState.update { it.copy(statusMessage = "IOU added") }
                    refreshDashboard()
                } else {
                    _uiState.update { it.copy(errorMessage = ApiClient.parseErrorMessage(res)) }
                }
            } catch (e: kotlinx.coroutines.CancellationException) {
                throw e
            } catch (e: Exception) {
                _uiState.update { it.copy(errorMessage = e.localizedMessage ?: "Failed to add IOU") }
            }
        }
    }

    fun toggleSettleIou(id: String) {
        viewModelScope.launch {
            try {
                val res = api.settleIou(id)
                if (res.isSuccessful) refreshDashboard()
            } catch (_: Exception) {}
        }
    }

    fun zeroOutBalance() {
        val ym = _uiState.value.selectedMonth
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true, errorMessage = null) }
            try {
                val res = api.zeroBalance(ZeroBalanceRequest(ym.year, ym.monthValue))
                if (res.isSuccessful && res.body() != null) {
                    val msg = if (res.body()!!.alreadyZero) "Balance is already 0" else "Balance zeroed out to 0"
                    _uiState.update { it.copy(isLoading = false, statusMessage = msg) }
                    refreshDashboard()
                } else {
                    _uiState.update {
                        it.copy(isLoading = false, errorMessage = ApiClient.parseErrorMessage(res))
                    }
                }
            } catch (e: kotlinx.coroutines.CancellationException) {
                throw e
            } catch (e: Exception) {
                _uiState.update {
                    it.copy(isLoading = false, errorMessage = e.localizedMessage ?: "Failed to zero balance")
                }
            }
        }
    }

    // --- Quick-Add Templates (#1) ---
    fun useTemplate(id: String) {
        viewModelScope.launch {
            try {
                val res = api.useTemplate(id)
                if (res.isSuccessful && res.body() != null) {
                    val tx = res.body()!!
                    _uiState.update { it.copy(statusMessage = "Logged ${tx.vendor} (${tx.amount} ${tx.currency})") }
                    refreshDashboard()
                } else {
                    _uiState.update { it.copy(errorMessage = ApiClient.parseErrorMessage(res)) }
                }
            } catch (e: kotlinx.coroutines.CancellationException) {
                throw e
            } catch (e: Exception) {
                _uiState.update { it.copy(errorMessage = e.localizedMessage ?: "Template failed") }
            }
        }
    }

    fun createTemplate(label: String, amount: String, categoryName: String) {
        viewModelScope.launch {
            try {
                val cur = _uiState.value.monthlySpending?.baseCurrency ?: "VND"
                val res = api.createTemplate(
                    CreateTemplateRequest(
                        label = label.trim(),
                        vendor = label.trim(),
                        amount = amount.trim(),
                        categoryName = categoryName,
                        currency = cur
                    )
                )
                if (res.isSuccessful) {
                    _uiState.update { it.copy(statusMessage = "Template saved") }
                    refreshDashboard()
                } else {
                    _uiState.update { it.copy(errorMessage = ApiClient.parseErrorMessage(res)) }
                }
            } catch (e: kotlinx.coroutines.CancellationException) {
                throw e
            } catch (e: Exception) {
                _uiState.update { it.copy(errorMessage = e.localizedMessage ?: "Failed to save template") }
            }
        }
    }

    fun deleteTemplate(id: String) {
        viewModelScope.launch {
            try {
                val res = api.deleteTemplate(id)
                if (res.isSuccessful) refreshDashboard()
            } catch (_: Exception) {}
        }
    }

    // --- Wallets & Internal Transfers (#2) ---
    fun createWallet(name: String, walletType: String, initialBalance: String) {
        viewModelScope.launch {
            try {
                val cur = _uiState.value.monthlySpending?.baseCurrency ?: "VND"
                val res = api.createWallet(
                    CreateWalletRequest(
                        name = name.trim(),
                        walletType = walletType,
                        initialBalance = initialBalance.trim().ifEmpty { "0" },
                        currency = cur
                    )
                )
                if (res.isSuccessful) {
                    _uiState.update { it.copy(statusMessage = "Wallet added") }
                    refreshDashboard()
                } else {
                    _uiState.update { it.copy(errorMessage = ApiClient.parseErrorMessage(res)) }
                }
            } catch (e: kotlinx.coroutines.CancellationException) {
                throw e
            } catch (e: Exception) {
                _uiState.update { it.copy(errorMessage = e.localizedMessage ?: "Failed to add wallet") }
            }
        }
    }

    fun transferWallet(fromId: String, toId: String, amount: String) {
        viewModelScope.launch {
            try {
                val res = api.transferWallet(TransferWalletRequest(fromId, toId, amount.trim()))
                if (res.isSuccessful) {
                    _uiState.update { it.copy(statusMessage = "Internal transfer recorded") }
                    refreshDashboard()
                } else {
                    _uiState.update { it.copy(errorMessage = ApiClient.parseErrorMessage(res)) }
                }
            } catch (e: kotlinx.coroutines.CancellationException) {
                throw e
            } catch (e: Exception) {
                _uiState.update { it.copy(errorMessage = e.localizedMessage ?: "Transfer failed") }
            }
        }
    }

    // --- Date Range / YTD (#5) ---
    fun setDateRange(startDate: String, endDate: String) {
        _uiState.update { it.copy(rangeStart = startDate, rangeEnd = endDate) }
        refreshDashboard()
    }

    fun clearDateRange() {
        _uiState.update { it.copy(rangeStart = null, rangeEnd = null, rangeSummary = null) }
        refreshDashboard()
    }

    fun runGlobalSearch(query: String, minAmount: String? = null) {
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true, errorMessage = null) }
            try {
                val res = api.searchAllTransactions(
                    query = query.trim().takeIf { it.isNotEmpty() },
                    minAmount = minAmount?.trim()?.takeIf { it.isNotEmpty() }
                )
                if (res.isSuccessful && res.body() != null) {
                    val body = res.body()!!
                    _uiState.update {
                        it.copy(
                            isLoading = false,
                            globalSearch = body,
                            transactions = body.transactions
                        )
                    }
                } else {
                    _uiState.update { it.copy(isLoading = false, errorMessage = ApiClient.parseErrorMessage(res)) }
                }
            } catch (e: kotlinx.coroutines.CancellationException) {
                throw e
            } catch (e: Exception) {
                _uiState.update { it.copy(isLoading = false, errorMessage = e.localizedMessage ?: "Search failed") }
            }
        }
    }

    fun clearGlobalSearch() {
        _uiState.update { it.copy(globalSearch = null) }
        refreshDashboard()
    }

    // --- v1.8.0: Yearly Wrapped, Merchant Profile & Smart Budget Suggestions ---
    fun loadYearlyWrapped(year: Int) {
        viewModelScope.launch {
            try {
                val res = api.getYearlyWrapped(year)
                if (res.isSuccessful && res.body() != null) {
                    _uiState.update { it.copy(yearlyWrapped = res.body()) }
                }
            } catch (_: Exception) {}
        }
    }

    fun loadMerchantProfile(vendor: String) {
        val clean = vendor.trim()
        if (clean.isEmpty()) return
        viewModelScope.launch {
            try {
                val res = api.getMerchantProfile(clean)
                if (res.isSuccessful && res.body() != null) {
                    _uiState.update { it.copy(merchantProfile = res.body()) }
                } else {
                    _uiState.update { it.copy(errorMessage = ApiClient.parseErrorMessage(res)) }
                }
            } catch (e: kotlinx.coroutines.CancellationException) {
                throw e
            } catch (e: Exception) {
                _uiState.update { it.copy(errorMessage = e.localizedMessage ?: "Merchant lookup failed") }
            }
        }
    }

    fun loadBudgetSuggestions() {
        viewModelScope.launch {
            try {
                val res = api.getBudgetSuggestions()
                if (res.isSuccessful && res.body() != null) {
                    _uiState.update { it.copy(budgetSuggestions = res.body()) }
                } else {
                    _uiState.update { it.copy(errorMessage = ApiClient.parseErrorMessage(res)) }
                }
            } catch (e: kotlinx.coroutines.CancellationException) {
                throw e
            } catch (e: Exception) {
                _uiState.update { it.copy(errorMessage = e.localizedMessage ?: "Suggestions failed") }
            }
        }
    }

    fun scanDuplicates() {
        viewModelScope.launch {
            try {
                val res = api.scanDuplicates()
                if (res.isSuccessful && res.body() != null) {
                    val body = res.body()!!
                    _uiState.update {
                        it.copy(
                            duplicates = body,
                            statusMessage = if (body.count == 0) "No duplicates found" else "Found ${body.count} likely duplicate(s)"
                        )
                    }
                } else {
                    _uiState.update { it.copy(errorMessage = ApiClient.parseErrorMessage(res)) }
                }
            } catch (e: kotlinx.coroutines.CancellationException) {
                throw e
            } catch (e: Exception) {
                _uiState.update { it.copy(errorMessage = e.localizedMessage ?: "Duplicate scan failed") }
            }
        }
    }

    fun deleteDuplicate(id: String) {
        viewModelScope.launch {
            try {
                val res = api.deleteTransaction(id)
                if (res.isSuccessful) {
                    _uiState.update { st ->
                        val rem = st.duplicates?.pairs?.filterNot { it.duplicate.id == id }.orEmpty()
                        st.copy(
                            duplicates = st.duplicates?.copy(count = rem.size, pairs = rem),
                            statusMessage = "Duplicate removed"
                        )
                    }
                    refreshDashboard()
                }
            } catch (e: kotlinx.coroutines.CancellationException) {
                throw e
            } catch (_: Exception) {}
        }
    }

    // --- v2.0.0: Category 6-Month History, Bill-Split Auto-IOU & Ledger CSV Export ---
    fun loadCategoryHistory(categoryName: String) {
        val clean = categoryName.trim()
        if (clean.isEmpty()) return
        viewModelScope.launch {
            try {
                val res = api.getCategoryHistory(clean, 6)
                if (res.isSuccessful && res.body() != null) {
                    _uiState.update { it.copy(categoryHistory = res.body()) }
                }
            } catch (e: kotlinx.coroutines.CancellationException) {
                throw e
            } catch (_: Exception) {}
        }
    }

    fun logBillSplit(friendName: String, myShare: String, owedToMe: String, people: Int) {
        viewModelScope.launch {
            val cur = _uiState.value.monthlySpending?.baseCurrency ?: "VND"
            val label = friendName.trim().ifEmpty { "Group (${people - 1} friends)" }
            try {
                val txRes = api.createTransaction(
                    CreateTransactionRequest(
                        txDate = java.time.LocalDate.now().toString(),
                        vendor = "Split Bill ($label)",
                        categoryName = "Dining",
                        amount = myShare,
                        txType = "debit",
                        currency = cur
                    )
                )
                val iouRes = api.createIou(
                    CreateIouRequest(
                        personName = label,
                        direction = "owed_to_me",
                        amount = owedToMe
                    )
                )
                if (txRes.isSuccessful && iouRes.isSuccessful) {
                    _uiState.update { it.copy(statusMessage = "Logged your share & created IOU for $label") }
                    refreshDashboard()
                } else {
                    _uiState.update { it.copy(errorMessage = "Bill split partially failed") }
                }
            } catch (e: kotlinx.coroutines.CancellationException) {
                throw e
            } catch (e: Exception) {
                _uiState.update { it.copy(errorMessage = e.localizedMessage ?: "Bill split failed") }
            }
        }
    }

    fun buildLedgerCsv(): String {
        val header = "Date,Vendor,Category,Wallet,Type,Amount,Currency,Tags,Description\n"
        fun esc(s: String?) = "\"${(s ?: "").replace("\"", "\"\"")}\""
        val rows = _uiState.value.transactions.joinToString("\n") { t ->
            listOf(
                esc(t.txDate),
                esc(t.vendor),
                esc(t.categoryName ?: "Uncategorized"),
                esc(t.walletName ?: ""),
                esc(t.txType),
                esc(t.amount),
                esc(t.currency),
                esc(t.tags.joinToString(" ")),
                esc(t.description ?: "")
            ).joinToString(",")
        }
        return header + rows + "\n"
    }

    // --- Merchant Rules (#6) ---
    fun upsertRule(pattern: String, categoryName: String) {
        viewModelScope.launch {
            try {
                val res = api.upsertRule(UpsertMerchantRuleRequest(pattern.trim(), categoryName))
                if (res.isSuccessful) {
                    _uiState.update { it.copy(statusMessage = "Rule saved") }
                    refreshDashboard()
                } else {
                    _uiState.update { it.copy(errorMessage = ApiClient.parseErrorMessage(res)) }
                }
            } catch (e: kotlinx.coroutines.CancellationException) {
                throw e
            } catch (e: Exception) {
                _uiState.update { it.copy(errorMessage = e.localizedMessage ?: "Failed to save rule") }
            }
        }
    }

    fun deleteRule(id: String) {
        viewModelScope.launch {
            try {
                val res = api.deleteRule(id)
                if (res.isSuccessful) refreshDashboard()
            } catch (_: Exception) {}
        }
    }

    // --- Full JSON Backup & Restore (#7) ---
    fun exportBackupJson(onReady: (String) -> Unit) {
        viewModelScope.launch {
            try {
                val res = api.exportBackup()
                if (res.isSuccessful && res.body() != null) {
                    val json = withContext(Dispatchers.IO) { res.body()!!.string() }
                    onReady(json)
                } else {
                    _uiState.update { it.copy(errorMessage = ApiClient.parseErrorMessage(res)) }
                }
            } catch (e: kotlinx.coroutines.CancellationException) {
                throw e
            } catch (e: Exception) {
                _uiState.update { it.copy(errorMessage = e.localizedMessage ?: "Export failed") }
            }
        }
    }

    fun importBackupJson(json: String) {
        viewModelScope.launch {
            try {
                val body = json.toRequestBody("application/json".toMediaType())
                val res = api.importBackup(body)
                if (res.isSuccessful && res.body() != null) {
                    val r = res.body()!!
                    _uiState.update {
                        it.copy(statusMessage = "Restored ${r.transactionsAdded} tx (${r.transactionsSkipped} skipped), ${r.walletsAdded} wallets")
                    }
                    refreshDashboard()
                } else {
                    _uiState.update { it.copy(errorMessage = ApiClient.parseErrorMessage(res)) }
                }
            } catch (e: kotlinx.coroutines.CancellationException) {
                throw e
            } catch (e: Exception) {
                _uiState.update { it.copy(errorMessage = e.localizedMessage ?: "Import failed") }
            }
        }
    }

    fun setAccentPalette(palette: String) {
        tokenStore.setAccentPalette(palette)
        _uiState.update { it.copy(accentPalette = palette) }
    }

    private fun flushQueuedUpload() {
        val pending = queuedShareUpload ?: return
        queuedShareUpload = null
        uploadMedia(pending.uris, pending.sourceType)
    }
}
