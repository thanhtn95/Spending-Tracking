package com.spendingtracker.app.data

import android.content.Context
import com.google.gson.Gson
import java.io.File

data class CachedDashboardSnapshot(
    val monthlySpending: MonthlySpendingResponse? = null,
    val transactions: List<TransactionRecord> = emptyList(),
    val wallets: List<WalletRecord> = emptyList(),
    val templates: List<QuickTemplateRecord> = emptyList(),
    val budgets: List<BudgetRecord> = emptyList(),
    val goals: List<GoalRecord> = emptyList(),
    val ious: List<IouRecord> = emptyList(),
    val comparison: MonthlyComparisonResponse? = null,
    val forecast: ForecastAndHealthResponse? = null,
    val preferences: UserPreferencesResponse? = null
)

/**
 * Local offline read-only cache ($0/month) so the app opens instantly even without internet.
 */
class DashboardCache(context: Context) {
    private val file = File(context.filesDir, "dashboard_cache.json")
    private val gson = Gson()

    fun save(snapshot: CachedDashboardSnapshot) {
        runCatching {
            file.writeText(gson.toJson(snapshot))
        }
    }

    fun load(): CachedDashboardSnapshot? {
        return runCatching {
            if (!file.exists()) return null
            gson.fromJson(file.readText(), CachedDashboardSnapshot::class.java)
        }.getOrNull()
    }

    fun clear() {
        runCatching { if (file.exists()) file.delete() }
    }
}
