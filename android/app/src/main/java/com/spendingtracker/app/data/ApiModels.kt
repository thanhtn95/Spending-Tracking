package com.spendingtracker.app.data

import com.google.gson.annotations.SerializedName

// --- Auth ---

data class RegisterRequest(
    val email: String,
    val password: String,
    @SerializedName("display_name") val displayName: String? = null
)

data class LoginRequest(
    val email: String,
    val password: String
)

data class ChangePasswordRequest(
    @SerializedName("current_password") val currentPassword: String,
    @SerializedName("new_password") val newPassword: String
)

data class UserProfile(
    val id: String,
    val email: String,
    @SerializedName("display_name") val displayName: String?,
    @SerializedName("created_at") val createdAt: String
)

data class AuthResponse(
    @SerializedName("access_token") val accessToken: String,
    @SerializedName("token_type") val tokenType: String,
    @SerializedName("expires_in") val expiresIn: Long,
    val user: UserProfile
)

// --- Ingest ---

data class IngestResponse(
    @SerializedName("document_id") val documentId: String,
    @SerializedName("source_type") val sourceType: String,
    @SerializedName("model_used") val modelUsed: String,
    @SerializedName("opening_balance") val openingBalance: String?,
    @SerializedName("closing_balance") val closingBalance: String?,
    val reconciled: Boolean,
    @SerializedName("reconciliation_diff") val reconciliationDiff: String?,
    val transactions: List<DraftTransactionPreview>,
    @SerializedName("skipped_exact_duplicates") val skippedExactDuplicates: Int
)

data class DraftTransactionPreview(
    val id: String,
    @SerializedName("tx_date") val txDate: String,
    val vendor: String,
    val description: String?,
    @SerializedName("category_id") val categoryId: String,
    @SerializedName("category_name") val categoryName: String,
    val amount: String,
    @SerializedName("tx_type") val txType: String = "debit",
    val currency: String,
    val source: String,
    val status: String,
    val confidence: Double,
    @SerializedName("potential_duplicate_ids") val potentialDuplicateIds: List<String>
)

// --- Confirm ---

data class ConfirmTransactionsRequest(
    val transactions: List<ConfirmTransactionItem>,
    @SerializedName("discard_ids") val discardIds: List<String> = emptyList()
)

data class ConfirmTransactionItem(
    val id: String,
    @SerializedName("tx_date") val txDate: String? = null,
    val vendor: String? = null,
    val description: String? = null,
    @SerializedName("category_name") val categoryName: String? = null,
    val amount: String? = null,
    @SerializedName("tx_type") val txType: String? = null,
    val currency: String? = null
)

data class ConfirmTransactionsResponse(
    @SerializedName("confirmed_count") val confirmedCount: Int,
    @SerializedName("discarded_count") val discardedCount: Int,
    val transactions: List<TransactionRecord>
)

// --- Analytics & Listing ---

data class MonthlySpendingResponse(
    val year: Int,
    val month: Int,
    @SerializedName("base_currency") val baseCurrency: String = "VND",
    @SerializedName("carry_forward_balance") val carryForwardBalance: String = "0",
    @SerializedName("total_spent") val totalSpent: String,
    @SerializedName("total_credit") val totalCredit: String = "0",
    @SerializedName("net_balance") val netBalance: String = "0",
    @SerializedName("cumulative_balance") val cumulativeBalance: String = "0",
    @SerializedName("debit_count") val debitCount: Long = 0,
    @SerializedName("credit_count") val creditCount: Long = 0,
    @SerializedName("transaction_count") val transactionCount: Long,
    @SerializedName("pending_draft_count") val pendingDraftCount: Long = 0,
    @SerializedName("by_category") val byCategory: List<CategorySpending>
)

data class CategorySpending(
    @SerializedName("category_id") val categoryId: String?,
    @SerializedName("category_name") val categoryName: String,
    @SerializedName("total_spent") val totalSpent: String,
    @SerializedName("transaction_count") val transactionCount: Long
)

data class TransactionRecord(
    val id: String,
    @SerializedName("document_id") val documentId: String?,
    @SerializedName("tx_date") val txDate: String,
    val vendor: String,
    val description: String?,
    @SerializedName("category_id") val categoryId: String?,
    @SerializedName("category_name") val categoryName: String?,
    @SerializedName("wallet_id") val walletId: String? = null,
    @SerializedName("wallet_name") val walletName: String? = null,
    val amount: String,
    @SerializedName("base_amount") val baseAmount: String? = null,
    @SerializedName("tx_type") val txType: String = "debit",
    val currency: String,
    val source: String,
    val status: String,
    @SerializedName("is_recurring") val isRecurring: Boolean = false,
    @SerializedName("is_transfer") val isTransfer: Boolean = false,
    val tags: List<String> = emptyList(),
    @SerializedName("created_at") val createdAt: String
)

// --- Manual Entry, Budgets, Trend, Categories, Recurring ---

data class CreateTransactionRequest(
    @SerializedName("tx_date") val txDate: String,
    val vendor: String,
    val description: String? = null,
    @SerializedName("category_name") val categoryName: String = "Uncategorized",
    @SerializedName("wallet_id") val walletId: String? = null,
    val amount: String,
    @SerializedName("tx_type") val txType: String = "debit",
    val currency: String = "VND",
    @SerializedName("is_recurring") val isRecurring: Boolean = false,
    val tags: List<String> = emptyList()
)

data class UpdateTransactionRequest(
    @SerializedName("tx_date") val txDate: String? = null,
    val vendor: String? = null,
    val description: String? = null,
    @SerializedName("category_name") val categoryName: String? = null,
    @SerializedName("wallet_id") val walletId: String? = null,
    val amount: String? = null,
    @SerializedName("tx_type") val txType: String? = null,
    val currency: String? = null,
    @SerializedName("is_recurring") val isRecurring: Boolean? = null,
    val tags: List<String>? = null
)

data class CategoryRecord(
    val id: String,
    val name: String
)

data class CreateCategoryRequest(
    val name: String
)

data class RecurringBillItem(
    val vendor: String,
    @SerializedName("category_name") val categoryName: String,
    @SerializedName("avg_amount") val avgAmount: String,
    val currency: String,
    @SerializedName("last_date") val lastDate: String,
    @SerializedName("next_expected_date") val nextExpectedDate: String,
    val occurrences: Long,
    @SerializedName("is_marked_recurring") val isMarkedRecurring: Boolean
)

data class RecurringSummaryResponse(
    @SerializedName("monthly_fixed_total") val monthlyFixedTotal: String,
    @SerializedName("base_currency") val baseCurrency: String = "VND",
    val items: List<RecurringBillItem>
)

// --- Goals, IOUs, AI Coach ---

data class GoalRecord(
    val id: String,
    val name: String,
    @SerializedName("goal_type") val goalType: String,
    @SerializedName("target_amount") val targetAmount: String,
    @SerializedName("current_amount") val currentAmount: String,
    val currency: String,
    @SerializedName("target_date") val targetDate: String?
)

data class CreateGoalRequest(
    val name: String,
    @SerializedName("goal_type") val goalType: String = "debt_payoff",
    @SerializedName("target_amount") val targetAmount: String,
    @SerializedName("target_date") val targetDate: String? = null
)

data class UpdateGoalRequest(
    @SerializedName("current_amount") val currentAmount: String
)

data class IouRecord(
    val id: String,
    @SerializedName("person_name") val personName: String,
    val direction: String,
    val amount: String,
    val currency: String,
    val note: String?,
    val settled: Boolean
)

data class CreateIouRequest(
    @SerializedName("person_name") val personName: String,
    val direction: String = "owed_to_me",
    val amount: String
)

data class MonthlyInsightsResponse(
    val year: Int,
    val month: Int,
    @SerializedName("model_used") val modelUsed: String,
    val tips: List<String>
)

data class ZeroBalanceRequest(
    val year: Int,
    val month: Int,
    val note: String? = null
)

data class ZeroBalanceResponse(
    @SerializedName("already_zero") val alreadyZero: Boolean,
    @SerializedName("previous_balance") val previousBalance: String,
    @SerializedName("new_balance") val newBalance: String
)

data class MonthlyTrendPoint(
    val year: Int,
    val month: Int,
    @SerializedName("total_spent") val totalSpent: String,
    @SerializedName("total_credit") val totalCredit: String,
    @SerializedName("net_balance") val netBalance: String
)

data class BudgetRecord(
    val id: String,
    @SerializedName("category_id") val categoryId: String,
    @SerializedName("category_name") val categoryName: String,
    @SerializedName("monthly_limit") val monthlyLimit: String,
    @SerializedName("effective_limit") val effectiveLimit: String? = null,
    val spent: String,
    val currency: String,
    val rollover: Boolean = false
)

data class UpsertBudgetRequest(
    @SerializedName("category_name") val categoryName: String,
    @SerializedName("monthly_limit") val monthlyLimit: String,
    val rollover: Boolean = false
)

// --- Wallets, Quick Templates, Rules, Tags/Range, Backup ---

data class WalletRecord(
    val id: String,
    val name: String,
    @SerializedName("wallet_type") val walletType: String,
    @SerializedName("initial_balance") val initialBalance: String,
    @SerializedName("current_balance") val currentBalance: String,
    val currency: String,
    @SerializedName("transaction_count") val transactionCount: Long
)

data class CreateWalletRequest(
    val name: String,
    @SerializedName("wallet_type") val walletType: String = "cash",
    @SerializedName("initial_balance") val initialBalance: String = "0",
    val currency: String = "VND"
)

data class TransferWalletRequest(
    @SerializedName("from_wallet_id") val fromWalletId: String,
    @SerializedName("to_wallet_id") val toWalletId: String,
    val amount: String,
    val note: String? = null
)

data class QuickTemplateRecord(
    val id: String,
    val label: String,
    val vendor: String,
    val amount: String,
    @SerializedName("tx_type") val txType: String = "debit",
    val currency: String = "VND",
    @SerializedName("category_name") val categoryName: String? = null,
    @SerializedName("wallet_id") val walletId: String? = null,
    @SerializedName("wallet_name") val walletName: String? = null,
    val tags: List<String> = emptyList()
)

data class CreateTemplateRequest(
    val label: String,
    val vendor: String,
    val amount: String,
    @SerializedName("tx_type") val txType: String = "debit",
    val currency: String = "VND",
    @SerializedName("category_name") val categoryName: String = "Uncategorized",
    @SerializedName("wallet_id") val walletId: String? = null,
    val tags: List<String> = emptyList()
)

data class MerchantRuleRecord(
    val id: String,
    @SerializedName("merchant_pattern") val merchantPattern: String,
    @SerializedName("category_id") val categoryId: String,
    @SerializedName("category_name") val categoryName: String
)

data class UpsertMerchantRuleRequest(
    @SerializedName("merchant_pattern") val merchantPattern: String,
    @SerializedName("category_name") val categoryName: String
)

data class TagSpending(
    val tag: String,
    @SerializedName("total_spent") val totalSpent: String,
    @SerializedName("transaction_count") val transactionCount: Long
)

data class RangeSpendingResponse(
    @SerializedName("start_date") val startDate: String,
    @SerializedName("end_date") val endDate: String,
    @SerializedName("base_currency") val baseCurrency: String = "VND",
    @SerializedName("total_spent") val totalSpent: String,
    @SerializedName("total_credit") val totalCredit: String,
    @SerializedName("net_balance") val netBalance: String,
    @SerializedName("transaction_count") val transactionCount: Long
)

data class BackupImportResponse(
    @SerializedName("categories_added") val categoriesAdded: Int,
    @SerializedName("wallets_added") val walletsAdded: Int,
    @SerializedName("templates_added") val templatesAdded: Int,
    @SerializedName("rules_upserted") val rulesUpserted: Int,
    @SerializedName("budgets_upserted") val budgetsUpserted: Int,
    @SerializedName("goals_added") val goalsAdded: Int,
    @SerializedName("ious_added") val iousAdded: Int,
    @SerializedName("transactions_added") val transactionsAdded: Int,
    @SerializedName("transactions_skipped") val transactionsSkipped: Int
)

data class SplitItemRequest(
    @SerializedName("category_name") val categoryName: String,
    val amount: String,
    val description: String? = null
)

data class SplitTransactionRequest(
    val items: List<SplitItemRequest>
)

data class CreateDraftNotificationRequest(
    @SerializedName("tx_date") val txDate: String,
    val vendor: String,
    val description: String? = null,
    @SerializedName("category_name") val categoryName: String? = null,
    val amount: String,
    @SerializedName("tx_type") val txType: String = "debit",
    val currency: String = "VND"
)

data class CategoryMomChange(
    @SerializedName("category_name") val categoryName: String,
    @SerializedName("current_spent") val currentSpent: String,
    @SerializedName("previous_spent") val previousSpent: String,
    val diff: String,
    @SerializedName("pct_change") val pctChange: Double?
)

data class TopMerchantItem(
    val vendor: String,
    @SerializedName("total_spent") val totalSpent: String,
    @SerializedName("transaction_count") val transactionCount: Long
)

data class MonthlyComparisonResponse(
    val year: Int,
    val month: Int,
    @SerializedName("base_currency") val baseCurrency: String = "VND",
    @SerializedName("savings_rate_pct") val savingsRatePct: Double?,
    @SerializedName("no_spend_days") val noSpendDays: Long,
    @SerializedName("current_no_spend_streak") val currentNoSpendStreak: Long,
    @SerializedName("category_changes") val categoryChanges: List<CategoryMomChange>,
    @SerializedName("top_merchants") val topMerchants: List<TopMerchantItem>
)

data class BulkTransactionsRequest(
    val ids: List<String>,
    val action: String,
    @SerializedName("category_name") val categoryName: String? = null,
    @SerializedName("wallet_id") val walletId: String? = null,
    @SerializedName("add_tag") val addTag: String? = null
)

data class BulkTransactionsResponse(
    @SerializedName("affected_count") val affectedCount: Int
)

data class UserPreferencesResponse(
    @SerializedName("cycle_start_day") val cycleStartDay: Int = 1,
    @SerializedName("active_event_tag") val activeEventTag: String? = null,
    @SerializedName("active_event_currency") val activeEventCurrency: String? = null,
    @SerializedName("active_event_spent") val activeEventSpent: String = "0",
    @SerializedName("base_currency") val baseCurrency: String = "VND"
)

data class UpdatePreferencesRequest(
    @SerializedName("cycle_start_day") val cycleStartDay: Int? = null,
    @SerializedName("active_event_tag") val activeEventTag: String? = null,
    @SerializedName("active_event_currency") val activeEventCurrency: String? = null
)

data class NetWorthPoint(
    val year: Int,
    val month: Int,
    @SerializedName("net_worth") val netWorth: String
)

data class SpendingAlertItem(
    val kind: String,
    val title: String,
    val detail: String,
    val amount: String,
    val currency: String
)

data class ForecastAndHealthResponse(
    val year: Int,
    val month: Int,
    @SerializedName("base_currency") val baseCurrency: String = "VND",
    @SerializedName("current_running_balance") val currentRunningBalance: String = "0",
    @SerializedName("upcoming_recurring_total") val upcomingRecurringTotal: String = "0",
    @SerializedName("daily_burn_rate") val dailyBurnRate: String = "0",
    @SerializedName("remaining_days") val remainingDays: Long = 0,
    @SerializedName("projected_month_end_balance") val projectedMonthEndBalance: String = "0",
    @SerializedName("net_worth_points") val netWorthPoints: List<NetWorthPoint> = emptyList(),
    val alerts: List<SpendingAlertItem> = emptyList()
)

data class GlobalSearchResponse(
    val query: String,
    @SerializedName("base_currency") val baseCurrency: String = "VND",
    @SerializedName("total_spent") val totalSpent: String = "0",
    @SerializedName("total_credit") val totalCredit: String = "0",
    val count: Int = 0,
    val transactions: List<TransactionRecord> = emptyList()
)

data class WeekdaySpendingItem(
    val dow: Int,
    val label: String,
    @SerializedName("total_spent") val totalSpent: String,
    @SerializedName("tx_count") val txCount: Long
)

data class WeekdayBreakdownResponse(
    val months: Int = 3,
    @SerializedName("base_currency") val baseCurrency: String = "VND",
    @SerializedName("busiest_day") val busiestDay: String? = null,
    val days: List<WeekdaySpendingItem> = emptyList()
)

data class FxRatesResponse(
    @SerializedName("base_currency") val baseCurrency: String = "VND",
    @SerializedName("rates_vs_usd") val ratesVsUsd: Map<String, String> = emptyMap()
)

// --- v1.8.0: Yearly Wrapped, 50/30/20 Rule, Merchant Profile, Smart Budget Suggestions & Subscription Audit ---

data class YearlyMonthItem(
    val month: Int,
    val label: String,
    @SerializedName("total_spent") val totalSpent: String,
    @SerializedName("total_income") val totalIncome: String,
    val net: String,
    @SerializedName("tx_count") val txCount: Long
)

data class YearlyBiggestExpense(
    val vendor: String,
    val amount: String,
    @SerializedName("tx_date") val txDate: String,
    @SerializedName("category_name") val categoryName: String
)

data class YearlyWrappedResponse(
    val year: Int,
    @SerializedName("base_currency") val baseCurrency: String = "VND",
    @SerializedName("total_spent") val totalSpent: String = "0",
    @SerializedName("total_income") val totalIncome: String = "0",
    @SerializedName("net_savings") val netSavings: String = "0",
    @SerializedName("savings_rate_pct") val savingsRatePct: Double? = null,
    @SerializedName("transaction_count") val transactionCount: Long = 0,
    @SerializedName("best_month") val bestMonth: String? = null,
    @SerializedName("busiest_month") val busiestMonth: String? = null,
    @SerializedName("biggest_expense") val biggestExpense: YearlyBiggestExpense? = null,
    val months: List<YearlyMonthItem> = emptyList(),
    @SerializedName("top_categories") val topCategories: List<CategorySpending> = emptyList(),
    @SerializedName("top_merchants") val topMerchants: List<TopMerchantItem> = emptyList()
)

data class Rule503020Response(
    val year: Int,
    val month: Int,
    @SerializedName("base_currency") val baseCurrency: String = "VND",
    @SerializedName("total_income") val totalIncome: String = "0",
    @SerializedName("total_spent") val totalSpent: String = "0",
    @SerializedName("needs_amount") val needsAmount: String = "0",
    @SerializedName("wants_amount") val wantsAmount: String = "0",
    @SerializedName("savings_amount") val savingsAmount: String = "0",
    @SerializedName("needs_pct") val needsPct: Double = 0.0,
    @SerializedName("wants_pct") val wantsPct: Double = 0.0,
    @SerializedName("savings_pct") val savingsPct: Double = 0.0,
    @SerializedName("needs_categories") val needsCategories: List<String> = emptyList(),
    @SerializedName("wants_categories") val wantsCategories: List<String> = emptyList()
)

data class MerchantMonthPoint(
    val year: Int,
    val month: Int,
    @SerializedName("total_spent") val totalSpent: String,
    @SerializedName("tx_count") val txCount: Long
)

data class MerchantProfileResponse(
    val vendor: String,
    @SerializedName("base_currency") val baseCurrency: String = "VND",
    @SerializedName("total_spent") val totalSpent: String = "0",
    @SerializedName("visit_count") val visitCount: Long = 0,
    @SerializedName("avg_ticket") val avgTicket: String = "0",
    @SerializedName("min_amount") val minAmount: String = "0",
    @SerializedName("max_amount") val maxAmount: String = "0",
    @SerializedName("first_seen") val firstSeen: String? = null,
    @SerializedName("last_seen") val lastSeen: String? = null,
    @SerializedName("monthly_history") val monthlyHistory: List<MerchantMonthPoint> = emptyList(),
    @SerializedName("recent_transactions") val recentTransactions: List<TransactionRecord> = emptyList()
)

data class BudgetSuggestionItem(
    @SerializedName("category_name") val categoryName: String,
    @SerializedName("avg_monthly_spent") val avgMonthlySpent: String,
    @SerializedName("suggested_limit") val suggestedLimit: String,
    @SerializedName("current_limit") val currentLimit: String? = null
)

data class BudgetSuggestionsResponse(
    @SerializedName("months_analyzed") val monthsAnalyzed: Int = 3,
    @SerializedName("base_currency") val baseCurrency: String = "VND",
    val suggestions: List<BudgetSuggestionItem> = emptyList()
)

data class SubscriptionAuditItem(
    val vendor: String,
    @SerializedName("category_name") val categoryName: String,
    @SerializedName("monthly_cost") val monthlyCost: String,
    @SerializedName("annual_cost") val annualCost: String,
    val occurrences: Long,
    @SerializedName("last_date") val lastDate: String
)

data class SubscriptionAuditResponse(
    @SerializedName("base_currency") val baseCurrency: String = "VND",
    @SerializedName("monthly_total") val monthlyTotal: String = "0",
    @SerializedName("annual_total") val annualTotal: String = "0",
    @SerializedName("pct_of_avg_income") val pctOfAvgIncome: Double? = null,
    val items: List<SubscriptionAuditItem> = emptyList()
)

// --- Error ---

data class ApiErrorEnvelope(
    val error: ApiErrorDetail?
)

data class ApiErrorDetail(
    val code: String?,
    val message: String?
)
