package com.spendingtracker.app.data

import okhttp3.MultipartBody
import okhttp3.RequestBody
import retrofit2.Response
import retrofit2.http.Body
import retrofit2.http.DELETE
import retrofit2.http.GET
import retrofit2.http.Multipart
import retrofit2.http.PATCH
import retrofit2.http.POST
import retrofit2.http.PUT
import retrofit2.http.Part
import retrofit2.http.Path
import retrofit2.http.Query

interface SpendingApiService {

    @POST("api/v1/auth/register")
    suspend fun register(@Body request: RegisterRequest): Response<AuthResponse>

    @POST("api/v1/auth/login")
    suspend fun login(@Body request: LoginRequest): Response<AuthResponse>

    @GET("api/v1/auth/me")
    suspend fun me(): Response<UserProfile>

    @Multipart
    @POST("api/v1/ingest/media")
    suspend fun ingestMedia(
        @Part files: List<MultipartBody.Part>,
        @Part("pdf_password") pdfPassword: RequestBody? = null,
        @Part("source_type") sourceType: RequestBody? = null
    ): Response<IngestResponse>

    @POST("api/v1/transactions/confirm")
    suspend fun confirmTransactions(
        @Body request: ConfirmTransactionsRequest
    ): Response<ConfirmTransactionsResponse>

    @GET("api/v1/spending/monthly")
    suspend fun getMonthlySpending(
        @Query("year") year: Int,
        @Query("month") month: Int
    ): Response<MonthlySpendingResponse>

    @POST("api/v1/spending/zero-balance")
    suspend fun zeroBalance(
        @Body request: ZeroBalanceRequest
    ): Response<ZeroBalanceResponse>

    @GET("api/v1/transactions")
    suspend fun getTransactions(
        @Query("year") year: Int? = null,
        @Query("month") month: Int? = null,
        @Query("start_date") startDate: String? = null,
        @Query("end_date") endDate: String? = null,
        @Query("status") status: String = "confirmed"
    ): Response<List<TransactionRecord>>

    @POST("api/v1/transactions")
    suspend fun createTransaction(
        @Body request: CreateTransactionRequest
    ): Response<TransactionRecord>

    @PATCH("api/v1/transactions/{id}")
    suspend fun updateTransaction(
        @Path("id") id: String,
        @Body request: UpdateTransactionRequest
    ): Response<TransactionRecord>

    @DELETE("api/v1/transactions/{id}")
    suspend fun deleteTransaction(
        @Path("id") id: String
    ): Response<Unit>

    @GET("api/v1/spending/trend")
    suspend fun getSpendingTrend(
        @Query("months") months: Int = 6
    ): Response<List<MonthlyTrendPoint>>

    @GET("api/v1/budgets")
    suspend fun getBudgets(
        @Query("year") year: Int,
        @Query("month") month: Int
    ): Response<List<BudgetRecord>>

    @PUT("api/v1/budgets")
    suspend fun upsertBudget(
        @Body request: UpsertBudgetRequest
    ): Response<BudgetRecord>

    @GET("api/v1/categories")
    suspend fun getCategories(): Response<List<CategoryRecord>>

    @POST("api/v1/categories")
    suspend fun createCategory(
        @Body request: CreateCategoryRequest
    ): Response<CategoryRecord>

    @GET("api/v1/recurring")
    suspend fun getRecurring(): Response<RecurringSummaryResponse>

    @GET("api/v1/transactions/pending")
    suspend fun getPendingTransactions(): Response<List<TransactionRecord>>

    @GET("api/v1/insights/monthly")
    suspend fun getMonthlyInsights(
        @Query("year") year: Int,
        @Query("month") month: Int
    ): Response<MonthlyInsightsResponse>

    @GET("api/v1/goals")
    suspend fun getGoals(): Response<List<GoalRecord>>

    @POST("api/v1/goals")
    suspend fun createGoal(@Body request: CreateGoalRequest): Response<GoalRecord>

    @PATCH("api/v1/goals/{id}")
    suspend fun updateGoal(@Path("id") id: String, @Body request: UpdateGoalRequest): Response<GoalRecord>

    @DELETE("api/v1/goals/{id}")
    suspend fun deleteGoal(@Path("id") id: String): Response<Unit>

    @GET("api/v1/ious")
    suspend fun getIous(): Response<List<IouRecord>>

    @POST("api/v1/ious")
    suspend fun createIou(@Body request: CreateIouRequest): Response<IouRecord>

    @PATCH("api/v1/ious/{id}/settle")
    suspend fun settleIou(@Path("id") id: String): Response<IouRecord>

    @DELETE("api/v1/ious/{id}")
    suspend fun deleteIou(@Path("id") id: String): Response<Unit>

    // Wallets & Transfers
    @GET("api/v1/wallets")
    suspend fun getWallets(): Response<List<WalletRecord>>

    @POST("api/v1/wallets")
    suspend fun createWallet(@Body request: CreateWalletRequest): Response<WalletRecord>

    @DELETE("api/v1/wallets/{id}")
    suspend fun deleteWallet(@Path("id") id: String): Response<Unit>

    @POST("api/v1/wallets/transfer")
    suspend fun transferWallet(@Body request: TransferWalletRequest): Response<okhttp3.ResponseBody>

    // Quick-Add Templates
    @GET("api/v1/templates")
    suspend fun getTemplates(): Response<List<QuickTemplateRecord>>

    @POST("api/v1/templates")
    suspend fun createTemplate(@Body request: CreateTemplateRequest): Response<QuickTemplateRecord>

    @DELETE("api/v1/templates/{id}")
    suspend fun deleteTemplate(@Path("id") id: String): Response<Unit>

    @POST("api/v1/templates/{id}/use")
    suspend fun useTemplate(@Path("id") id: String): Response<TransactionRecord>

    // Merchant Auto-Categorization Rules
    @GET("api/v1/rules")
    suspend fun getRules(): Response<List<MerchantRuleRecord>>

    @POST("api/v1/rules")
    suspend fun upsertRule(@Body request: UpsertMerchantRuleRequest): Response<MerchantRuleRecord>

    @DELETE("api/v1/rules/{id}")
    suspend fun deleteRule(@Path("id") id: String): Response<Unit>

    // Tags & Range Analytics
    @GET("api/v1/tags")
    suspend fun getTags(): Response<List<TagSpending>>

    @GET("api/v1/spending/range")
    suspend fun getRangeSpending(
        @Query("start_date") startDate: String,
        @Query("end_date") endDate: String
    ): Response<RangeSpendingResponse>

    // Full JSON Backup & Restore
    @GET("api/v1/backup/export")
    suspend fun exportBackup(): Response<okhttp3.ResponseBody>

    @POST("api/v1/backup/import")
    suspend fun importBackup(@Body body: RequestBody): Response<BackupImportResponse>

    // Split Transaction & On-Device Bank Alert Draft
    @POST("api/v1/transactions/{id}/split")
    suspend fun splitTransaction(
        @Path("id") id: String,
        @Body request: SplitTransactionRequest
    ): Response<List<TransactionRecord>>

    @POST("api/v1/transactions/draft")
    suspend fun createNotificationDraft(
        @Body request: CreateDraftNotificationRequest
    ): Response<TransactionRecord>
}
