use chrono::{DateTime, NaiveDate, Utc};
use rust_decimal::Decimal;
use serde::{Deserialize, Serialize};
use uuid::Uuid;

// --- Auth DTOs ---

#[derive(Debug, Deserialize)]
pub struct RegisterRequest {
    pub email: String,
    pub password: String,
    pub display_name: Option<String>,
}

#[derive(Debug, Deserialize)]
pub struct LoginRequest {
    pub email: String,
    pub password: String,
}

#[derive(Debug, Deserialize)]
pub struct ChangePasswordRequest {
    pub current_password: String,
    pub new_password: String,
}

#[derive(Debug, Serialize, sqlx::FromRow)]
pub struct UserProfile {
    pub id: Uuid,
    pub email: String,
    pub display_name: Option<String>,
    pub created_at: DateTime<Utc>,
}

#[derive(Debug, Serialize)]
pub struct AuthResponse {
    pub access_token: String,
    pub token_type: &'static str,
    pub expires_in: i64,
    pub user: UserProfile,
}

// --- Gemini Structured Output DTOs ---

#[derive(Debug, Clone, Deserialize, Serialize)]
pub struct GeminiExtractionResult {
    #[serde(default)]
    pub transactions: Vec<ExtractedTransaction>,
    pub opening_balance: Option<Decimal>,
    pub closing_balance: Option<Decimal>,
}

#[derive(Debug, Clone, Deserialize, Serialize)]
pub struct ExtractedTransaction {
    /// ISO date string YYYY-MM-DD
    pub date: String,
    pub vendor: String,
    #[serde(default)]
    pub description: Option<String>,
    pub amount: Decimal,
    /// "debit" (outflow/spending) or "credit" (inflow/refund)
    #[serde(rename = "type")]
    pub tx_type: String,
    pub category: String,
    pub confidence: f64,
    #[serde(default)]
    pub currency: Option<String>,
}

// --- Ingest API Response ---

#[derive(Debug, Serialize)]
pub struct IngestResponse {
    pub document_id: Uuid,
    pub source_type: String,
    pub model_used: String,
    pub opening_balance: Option<Decimal>,
    pub closing_balance: Option<Decimal>,
    pub reconciled: bool,
    pub reconciliation_diff: Option<Decimal>,
    pub transactions: Vec<DraftTransactionPreview>,
    pub skipped_exact_duplicates: usize,
}

#[derive(Debug, Serialize)]
pub struct DraftTransactionPreview {
    pub id: Uuid,
    pub tx_date: NaiveDate,
    pub vendor: String,
    pub description: Option<String>,
    pub category_id: Uuid,
    pub category_name: String,
    pub amount: Decimal,
    pub tx_type: String,
    pub currency: String,
    pub source: String,
    pub status: String,
    pub confidence: f64,
    pub potential_duplicate_ids: Vec<Uuid>,
}

// --- Confirm Transactions API ---

#[derive(Debug, Deserialize)]
pub struct ConfirmTransactionsRequest {
    #[serde(default)]
    pub transactions: Vec<ConfirmTransactionItem>,
    /// Optional draft transaction IDs to discard/delete
    #[serde(default)]
    pub discard_ids: Vec<Uuid>,
}

#[derive(Debug, Deserialize)]
pub struct ConfirmTransactionItem {
    pub id: Uuid,
    pub tx_date: Option<NaiveDate>,
    pub vendor: Option<String>,
    pub description: Option<String>,
    pub category_id: Option<Uuid>,
    pub category_name: Option<String>,
    pub amount: Option<Decimal>,
    pub tx_type: Option<String>,
    pub currency: Option<String>,
}

#[derive(Debug, Serialize)]
pub struct ConfirmTransactionsResponse {
    pub confirmed_count: usize,
    pub discarded_count: usize,
    pub transactions: Vec<TransactionRecord>,
}

// --- Query / Analytics APIs ---

#[derive(Debug, Deserialize)]
pub struct MonthlyQuery {
    pub year: i32,
    pub month: u32,
}

#[derive(Debug, Deserialize)]
pub struct ListTransactionsQuery {
    pub year: Option<i32>,
    pub month: Option<u32>,
    pub start_date: Option<NaiveDate>,
    pub end_date: Option<NaiveDate>,
    pub status: Option<String>,
    pub tag: Option<String>,
    pub wallet_id: Option<Uuid>,
}

#[derive(Debug, Serialize, Deserialize, sqlx::FromRow)]
pub struct TransactionRecord {
    pub id: Uuid,
    pub document_id: Option<Uuid>,
    pub tx_date: NaiveDate,
    pub vendor: String,
    pub description: Option<String>,
    pub category_id: Option<Uuid>,
    pub category_name: Option<String>,
    pub wallet_id: Option<Uuid>,
    pub wallet_name: Option<String>,
    pub amount: Decimal,
    pub base_amount: Option<Decimal>,
    pub tx_type: String,
    pub currency: String,
    pub source: String,
    pub status: String,
    pub is_recurring: bool,
    pub is_transfer: bool,
    pub tags: Vec<String>,
    pub created_at: DateTime<Utc>,
}

#[derive(Debug, Serialize)]
pub struct MonthlySpendingResponse {
    pub year: i32,
    pub month: u32,
    pub base_currency: String,
    pub carry_forward_balance: Decimal,
    pub total_spent: Decimal,
    pub total_credit: Decimal,
    pub net_balance: Decimal,
    pub cumulative_balance: Decimal,
    pub debit_count: i64,
    pub credit_count: i64,
    pub transaction_count: i64,
    pub pending_draft_count: i64,
    pub by_category: Vec<CategorySpending>,
}

#[derive(Debug, Serialize, sqlx::FromRow)]
pub struct CategorySpending {
    pub category_id: Option<Uuid>,
    pub category_name: String,
    pub total_spent: Decimal,
    pub transaction_count: i64,
}

// --- Manual Transaction & Budgets & Cash Flow Trend ---

#[derive(Debug, Deserialize)]
pub struct CreateTransactionRequest {
    pub tx_date: NaiveDate,
    pub vendor: String,
    pub description: Option<String>,
    pub category_name: Option<String>,
    pub wallet_id: Option<Uuid>,
    pub amount: Decimal,
    pub tx_type: Option<String>,
    pub currency: Option<String>,
    pub is_recurring: Option<bool>,
    #[serde(default)]
    pub tags: Option<Vec<String>>,
}

#[derive(Debug, Deserialize)]
pub struct UpdateTransactionRequest {
    pub tx_date: Option<NaiveDate>,
    pub vendor: Option<String>,
    pub description: Option<String>,
    pub category_id: Option<Uuid>,
    pub category_name: Option<String>,
    pub wallet_id: Option<Uuid>,
    pub amount: Option<Decimal>,
    pub tx_type: Option<String>,
    pub currency: Option<String>,
    pub is_recurring: Option<bool>,
    pub tags: Option<Vec<String>>,
}

#[derive(Debug, Serialize)]
pub struct RecurringBillItem {
    pub vendor: String,
    pub category_name: String,
    pub avg_amount: Decimal,
    pub currency: String,
    pub last_date: NaiveDate,
    pub next_expected_date: NaiveDate,
    pub occurrences: i64,
    pub is_marked_recurring: bool,
}

#[derive(Debug, Serialize)]
pub struct RecurringSummaryResponse {
    pub monthly_fixed_total: Decimal,
    pub base_currency: String,
    pub items: Vec<RecurringBillItem>,
}

// --- Goals, IOUs, AI Coach Insights ---

#[derive(Debug, Deserialize)]
pub struct CreateGoalRequest {
    pub name: String,
    pub goal_type: Option<String>,
    pub target_amount: Decimal,
    pub current_amount: Option<Decimal>,
    pub currency: Option<String>,
    pub target_date: Option<NaiveDate>,
}

#[derive(Debug, Deserialize)]
pub struct UpdateGoalRequest {
    pub name: Option<String>,
    pub target_amount: Option<Decimal>,
    pub current_amount: Option<Decimal>,
    pub target_date: Option<NaiveDate>,
}

#[derive(Debug, Serialize, sqlx::FromRow)]
pub struct GoalRecord {
    pub id: Uuid,
    pub name: String,
    pub goal_type: String,
    pub target_amount: Decimal,
    pub current_amount: Decimal,
    pub currency: String,
    pub target_date: Option<NaiveDate>,
}

#[derive(Debug, Deserialize)]
pub struct CreateIouRequest {
    pub person_name: String,
    pub direction: Option<String>,
    pub amount: Decimal,
    pub currency: Option<String>,
    pub note: Option<String>,
}

#[derive(Debug, Serialize, sqlx::FromRow)]
pub struct IouRecord {
    pub id: Uuid,
    pub person_name: String,
    pub direction: String,
    pub amount: Decimal,
    pub currency: String,
    pub note: Option<String>,
    pub settled: bool,
    pub created_at: DateTime<Utc>,
}

#[derive(Debug, Serialize)]
pub struct MonthlyInsightsResponse {
    pub year: i32,
    pub month: u32,
    pub model_used: String,
    pub tips: Vec<String>,
}

#[derive(Debug, Deserialize)]
pub struct ZeroBalanceRequest {
    pub year: i32,
    pub month: u32,
    pub note: Option<String>,
}

#[derive(Debug, Serialize)]
pub struct ZeroBalanceResponse {
    pub already_zero: bool,
    pub previous_balance: Decimal,
    pub new_balance: Decimal,
    pub adjustment: Option<TransactionRecord>,
}

#[derive(Debug, Deserialize)]
pub struct TrendQuery {
    pub months: Option<u32>,
}

#[derive(Debug, Serialize)]
pub struct MonthlyTrendPoint {
    pub year: i32,
    pub month: u32,
    pub total_spent: Decimal,
    pub total_credit: Decimal,
    pub net_balance: Decimal,
}

#[derive(Debug, Deserialize)]
pub struct UpsertBudgetRequest {
    pub category_name: String,
    pub monthly_limit: Decimal,
    pub currency: Option<String>,
    pub rollover: Option<bool>,
}

#[derive(Debug, Serialize, sqlx::FromRow)]
pub struct CategoryRecord {
    pub id: Uuid,
    pub name: String,
}

#[derive(Debug, Deserialize)]
pub struct CreateCategoryRequest {
    pub name: String,
}

#[derive(Debug, Serialize, sqlx::FromRow)]
pub struct BudgetRecord {
    pub id: Uuid,
    pub category_id: Uuid,
    pub category_name: String,
    pub monthly_limit: Decimal,
    pub effective_limit: Decimal,
    pub spent: Decimal,
    pub currency: String,
    pub rollover: bool,
}

// --- Wallets & Internal Transfers ---

#[derive(Debug, Deserialize)]
pub struct CreateWalletRequest {
    pub name: String,
    pub wallet_type: Option<String>,
    pub initial_balance: Option<Decimal>,
    pub currency: Option<String>,
}

#[derive(Debug, Serialize, Deserialize, sqlx::FromRow)]
pub struct WalletRecord {
    pub id: Uuid,
    pub name: String,
    pub wallet_type: String,
    pub initial_balance: Decimal,
    pub current_balance: Decimal,
    pub currency: String,
    pub transaction_count: i64,
}

#[derive(Debug, Deserialize)]
pub struct TransferWalletRequest {
    pub from_wallet_id: Uuid,
    pub to_wallet_id: Uuid,
    pub amount: Decimal,
    pub tx_date: Option<NaiveDate>,
    pub note: Option<String>,
}

#[derive(Debug, Serialize)]
pub struct TransferWalletResponse {
    pub debit_tx: TransactionRecord,
    pub credit_tx: TransactionRecord,
}

// --- Quick-Add Templates ---

#[derive(Debug, Deserialize)]
pub struct CreateTemplateRequest {
    pub label: String,
    pub vendor: String,
    pub amount: Decimal,
    pub tx_type: Option<String>,
    pub currency: Option<String>,
    pub category_name: Option<String>,
    pub wallet_id: Option<Uuid>,
    #[serde(default)]
    pub tags: Vec<String>,
}

#[derive(Debug, Serialize, Deserialize, sqlx::FromRow)]
pub struct QuickTemplateRecord {
    pub id: Uuid,
    pub label: String,
    pub vendor: String,
    pub amount: Decimal,
    pub tx_type: String,
    pub currency: String,
    pub category_id: Option<Uuid>,
    pub category_name: Option<String>,
    pub wallet_id: Option<Uuid>,
    pub wallet_name: Option<String>,
    pub tags: Vec<String>,
}

// --- Merchant Auto-Categorization Rules ---

#[derive(Debug, Deserialize)]
pub struct UpsertMerchantRuleRequest {
    pub merchant_pattern: String,
    pub category_name: String,
}

#[derive(Debug, Serialize, Deserialize, sqlx::FromRow)]
pub struct MerchantRuleRecord {
    pub id: Uuid,
    pub merchant_pattern: String,
    pub category_id: Uuid,
    pub category_name: String,
    pub updated_at: DateTime<Utc>,
}

// --- Tags & Custom Date-Range Analytics ---

#[derive(Debug, Deserialize)]
pub struct RangeSpendingQuery {
    pub start_date: NaiveDate,
    pub end_date: NaiveDate,
    pub tag: Option<String>,
    pub wallet_id: Option<Uuid>,
}

#[derive(Debug, Serialize, sqlx::FromRow)]
pub struct TagSpending {
    pub tag: String,
    pub total_spent: Decimal,
    pub transaction_count: i64,
}

#[derive(Debug, Serialize)]
pub struct RangeSpendingResponse {
    pub start_date: NaiveDate,
    pub end_date: NaiveDate,
    pub base_currency: String,
    pub total_spent: Decimal,
    pub total_credit: Decimal,
    pub net_balance: Decimal,
    pub transaction_count: i64,
    pub by_category: Vec<CategorySpending>,
    pub by_tag: Vec<TagSpending>,
}

// --- Full Backup & Restore ---

#[derive(Debug, Serialize, Deserialize)]
pub struct BackupWallet {
    pub name: String,
    pub wallet_type: String,
    pub initial_balance: Decimal,
    pub currency: String,
}

#[derive(Debug, Serialize, Deserialize)]
pub struct BackupTemplate {
    pub label: String,
    pub vendor: String,
    pub amount: Decimal,
    pub tx_type: String,
    pub currency: String,
    pub category_name: Option<String>,
    pub wallet_name: Option<String>,
    #[serde(default)]
    pub tags: Vec<String>,
}

#[derive(Debug, Serialize, Deserialize)]
pub struct BackupRule {
    pub merchant_pattern: String,
    pub category_name: String,
}

#[derive(Debug, Serialize, Deserialize)]
pub struct BackupBudget {
    pub category_name: String,
    pub monthly_limit: Decimal,
    pub currency: String,
}

#[derive(Debug, Serialize, Deserialize)]
pub struct BackupGoal {
    pub name: String,
    pub goal_type: String,
    pub target_amount: Decimal,
    pub current_amount: Decimal,
    pub currency: String,
    pub target_date: Option<NaiveDate>,
}

#[derive(Debug, Serialize, Deserialize)]
pub struct BackupIou {
    pub person_name: String,
    pub direction: String,
    pub amount: Decimal,
    pub currency: String,
    pub note: Option<String>,
    pub settled: bool,
}

#[derive(Debug, Serialize, Deserialize)]
pub struct BackupTransaction {
    pub tx_date: NaiveDate,
    pub vendor: String,
    pub description: Option<String>,
    pub category_name: Option<String>,
    pub wallet_name: Option<String>,
    pub amount: Decimal,
    pub base_amount: Option<Decimal>,
    pub tx_type: String,
    pub currency: String,
    pub source: String,
    pub is_recurring: bool,
    #[serde(default)]
    pub is_transfer: bool,
    #[serde(default)]
    pub tags: Vec<String>,
}

#[derive(Debug, Serialize, Deserialize)]
pub struct BackupPayload {
    pub version: u32,
    pub exported_at: DateTime<Utc>,
    #[serde(default)]
    pub categories: Vec<String>,
    #[serde(default)]
    pub wallets: Vec<BackupWallet>,
    #[serde(default)]
    pub templates: Vec<BackupTemplate>,
    #[serde(default)]
    pub rules: Vec<BackupRule>,
    #[serde(default)]
    pub budgets: Vec<BackupBudget>,
    #[serde(default)]
    pub goals: Vec<BackupGoal>,
    #[serde(default)]
    pub ious: Vec<BackupIou>,
    #[serde(default)]
    pub transactions: Vec<BackupTransaction>,
}

#[derive(Debug, Serialize)]
pub struct BackupImportResponse {
    pub categories_added: usize,
    pub wallets_added: usize,
    pub templates_added: usize,
    pub rules_upserted: usize,
    pub budgets_upserted: usize,
    pub goals_added: usize,
    pub ious_added: usize,
    pub transactions_added: usize,
    pub transactions_skipped: usize,
}

// --- Split Transaction & On-Device Bank Alert Draft ---

#[derive(Debug, Deserialize)]
pub struct SplitItemRequest {
    pub category_name: String,
    pub amount: Decimal,
    pub description: Option<String>,
}

#[derive(Debug, Deserialize)]
pub struct SplitTransactionRequest {
    pub items: Vec<SplitItemRequest>,
}

#[derive(Debug, Deserialize)]
pub struct CreateDraftNotificationRequest {
    pub tx_date: NaiveDate,
    pub vendor: String,
    pub description: Option<String>,
    pub category_name: Option<String>,
    pub amount: Decimal,
    pub tx_type: Option<String>,
    pub currency: Option<String>,
}

// --- Month-over-Month Comparison, Top Merchants, Savings Rate & Bulk Actions ---

#[derive(Debug, Serialize)]
pub struct CategoryMomChange {
    pub category_name: String,
    pub current_spent: Decimal,
    pub previous_spent: Decimal,
    pub diff: Decimal,
    pub pct_change: Option<f64>,
}

#[derive(Debug, Serialize, sqlx::FromRow)]
pub struct TopMerchantItem {
    pub vendor: String,
    pub total_spent: Decimal,
    pub transaction_count: i64,
}

#[derive(Debug, Serialize)]
pub struct MonthlyComparisonResponse {
    pub year: i32,
    pub month: u32,
    pub base_currency: String,
    pub savings_rate_pct: Option<f64>,
    pub no_spend_days: i64,
    pub current_no_spend_streak: i64,
    pub category_changes: Vec<CategoryMomChange>,
    pub top_merchants: Vec<TopMerchantItem>,
}

#[derive(Debug, Deserialize)]
pub struct BulkTransactionsRequest {
    pub ids: Vec<Uuid>,
    pub action: String, // "update" or "delete"
    pub category_name: Option<String>,
    pub wallet_id: Option<Uuid>,
    pub add_tag: Option<String>,
}

#[derive(Debug, Serialize)]
pub struct BulkTransactionsResponse {
    pub affected_count: usize,
}

// --- User Preferences (Payday Cycle & Travel/Event Mode), Forecast, Net Worth & Anomalies ---

#[derive(Debug, Serialize)]
pub struct UserPreferencesResponse {
    pub cycle_start_day: i16,
    pub active_event_tag: Option<String>,
    pub active_event_currency: Option<String>,
    pub active_event_spent: Decimal,
    pub base_currency: String,
}

#[derive(Debug, Deserialize)]
pub struct UpdatePreferencesRequest {
    pub cycle_start_day: Option<i16>,
    pub active_event_tag: Option<String>,
    pub active_event_currency: Option<String>,
}

#[derive(Debug, Serialize)]
pub struct NetWorthPoint {
    pub year: i32,
    pub month: u32,
    pub net_worth: Decimal,
}

#[derive(Debug, Serialize)]
pub struct SpendingAlertItem {
    pub kind: String, // "price_hike" or "anomaly"
    pub title: String,
    pub detail: String,
    pub amount: Decimal,
    pub currency: String,
}

#[derive(Debug, Serialize)]
pub struct ForecastAndHealthResponse {
    pub year: i32,
    pub month: u32,
    pub base_currency: String,
    pub current_running_balance: Decimal,
    pub upcoming_recurring_total: Decimal,
    pub daily_burn_rate: Decimal,
    pub remaining_days: i64,
    pub projected_month_end_balance: Decimal,
    pub net_worth_points: Vec<NetWorthPoint>,
    pub alerts: Vec<SpendingAlertItem>,
}

// --- Global Search, Day-of-Week Breakdown & FX Rates ---

#[derive(Debug, Deserialize)]
pub struct GlobalSearchQuery {
    pub q: Option<String>,
    pub min_amount: Option<Decimal>,
    pub max_amount: Option<Decimal>,
}

#[derive(Debug, Serialize)]
pub struct GlobalSearchResponse {
    pub query: String,
    pub base_currency: String,
    pub total_spent: Decimal,
    pub total_credit: Decimal,
    pub count: usize,
    pub transactions: Vec<TransactionRecord>,
}

#[derive(Debug, Deserialize)]
pub struct WeekdayQuery {
    pub months: Option<u32>,
}

#[derive(Debug, Serialize)]
pub struct WeekdaySpendingItem {
    pub dow: i32, // 1=Mon .. 7=Sun
    pub label: String,
    pub total_spent: Decimal,
    pub tx_count: i64,
}

#[derive(Debug, Serialize)]
pub struct WeekdayBreakdownResponse {
    pub months: u32,
    pub base_currency: String,
    pub busiest_day: Option<String>,
    pub days: Vec<WeekdaySpendingItem>,
}

#[derive(Debug, Serialize)]
pub struct FxRatesResponse {
    pub base_currency: String,
    pub rates_vs_usd: std::collections::HashMap<String, Decimal>,
}

// --- v1.8.0: Yearly Wrapped, 50/30/20 Rule, Merchant Profile, Smart Budget Suggestions & Subscription Audit ---

#[derive(Debug, Deserialize)]
pub struct YearlyWrappedQuery {
    pub year: Option<i32>,
}

#[derive(Debug, Serialize)]
pub struct YearlyMonthItem {
    pub month: u32,
    pub label: String,
    pub total_spent: Decimal,
    pub total_income: Decimal,
    pub net: Decimal,
    pub tx_count: i64,
}

#[derive(Debug, Serialize)]
pub struct YearlyBiggestExpense {
    pub vendor: String,
    pub amount: Decimal,
    pub tx_date: NaiveDate,
    pub category_name: String,
}

#[derive(Debug, Serialize)]
pub struct YearlyWrappedResponse {
    pub year: i32,
    pub base_currency: String,
    pub total_spent: Decimal,
    pub total_income: Decimal,
    pub net_savings: Decimal,
    pub savings_rate_pct: Option<f64>,
    pub transaction_count: i64,
    pub best_month: Option<String>,
    pub busiest_month: Option<String>,
    pub biggest_expense: Option<YearlyBiggestExpense>,
    pub months: Vec<YearlyMonthItem>,
    pub top_categories: Vec<CategorySpending>,
    pub top_merchants: Vec<TopMerchantItem>,
}

#[derive(Debug, Serialize)]
pub struct Rule503020Response {
    pub year: i32,
    pub month: u32,
    pub base_currency: String,
    pub total_income: Decimal,
    pub total_spent: Decimal,
    pub needs_amount: Decimal,
    pub wants_amount: Decimal,
    pub savings_amount: Decimal,
    pub needs_pct: f64,
    pub wants_pct: f64,
    pub savings_pct: f64,
    pub needs_categories: Vec<String>,
    pub wants_categories: Vec<String>,
}

#[derive(Debug, Deserialize)]
pub struct MerchantProfileQuery {
    pub vendor: String,
}

#[derive(Debug, Serialize)]
pub struct MerchantMonthPoint {
    pub year: i32,
    pub month: u32,
    pub total_spent: Decimal,
    pub tx_count: i64,
}

#[derive(Debug, Serialize)]
pub struct MerchantProfileResponse {
    pub vendor: String,
    pub base_currency: String,
    pub total_spent: Decimal,
    pub visit_count: i64,
    pub avg_ticket: Decimal,
    pub min_amount: Decimal,
    pub max_amount: Decimal,
    pub first_seen: Option<NaiveDate>,
    pub last_seen: Option<NaiveDate>,
    pub monthly_history: Vec<MerchantMonthPoint>,
    pub recent_transactions: Vec<TransactionRecord>,
}

#[derive(Debug, Serialize)]
pub struct BudgetSuggestionItem {
    pub category_name: String,
    pub avg_monthly_spent: Decimal,
    pub suggested_limit: Decimal,
    pub current_limit: Option<Decimal>,
}

#[derive(Debug, Serialize)]
pub struct BudgetSuggestionsResponse {
    pub months_analyzed: u32,
    pub base_currency: String,
    pub suggestions: Vec<BudgetSuggestionItem>,
}

#[derive(Debug, Serialize)]
pub struct SubscriptionAuditItem {
    pub vendor: String,
    pub category_name: String,
    pub monthly_cost: Decimal,
    pub annual_cost: Decimal,
    pub occurrences: i64,
    pub last_date: NaiveDate,
}

#[derive(Debug, Serialize)]
pub struct SubscriptionAuditResponse {
    pub base_currency: String,
    pub monthly_total: Decimal,
    pub annual_total: Decimal,
    pub pct_of_avg_income: Option<f64>,
    pub items: Vec<SubscriptionAuditItem>,
}
