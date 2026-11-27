package com.spendingtracker.app.data

import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import com.spendingtracker.app.R
import java.math.BigDecimal
import java.time.LocalDate
import java.time.YearMonth
import java.util.concurrent.TimeUnit

/**
 * Local on-device daily reminder worker ($0/month, no Firebase needed).
 * Alerts when any category budget crosses 80%/100% or a recurring bill is due within 2 days.
 */
class ReminderWorker(
    appContext: Context,
    params: WorkerParameters
) : CoroutineWorker(appContext, params) {

    override suspend fun doWork(): Result {
        val tokenStore = TokenStore(applicationContext)
        if (tokenStore.getToken().isNullOrBlank()) return Result.success()

        val api = ApiClient.create(tokenStore)
        val ym = YearMonth.now()
        val today = LocalDate.now()

        try {
            val bootRes = api.getDashboardBootstrap(ym.year, ym.monthValue)
            if (bootRes.isSuccessful && bootRes.body() != null) {
                val boot = bootRes.body()!!
                val s = boot.monthlySpending
                tokenStore.saveWidgetSnapshot(s.cumulativeBalance, s.totalSpent, s.baseCurrency)
                com.spendingtracker.app.widget.SpendingWidgetProvider.notifyDataChanged(applicationContext)

                boot.budgets.forEachIndexed { idx, b ->
                    val limit = b.monthlyLimit.toBigDecimalOrNull() ?: return@forEachIndexed
                    val spent = b.spent.toBigDecimalOrNull() ?: BigDecimal.ZERO
                    if (limit > BigDecimal.ZERO) {
                        val pct = ((spent.toFloat() / limit.toFloat()) * 100f).toInt()
                        if (pct >= 80) {
                            val title = if (pct >= 100) "Over Budget: ${b.categoryName}" else "Budget Warning: ${b.categoryName}"
                            val body = "Spent ${b.spent} of ${b.monthlyLimit} ${b.currency} ($pct%)."
                            sendNotification(100 + idx, title, body)
                        }
                    }
                }
            }

            val recRes = api.getRecurring()
            if (recRes.isSuccessful) {
                val cutoff = today.plusDays(2)
                recRes.body()?.items.orEmpty().forEachIndexed { idx, item ->
                    val due = runCatching { LocalDate.parse(item.nextExpectedDate) }.getOrNull()
                    if (due != null && !due.isBefore(today) && !due.isAfter(cutoff)) {
                        sendNotification(
                            200 + idx,
                            "Upcoming Bill: ${item.vendor}",
                            "Expected ~${item.avgAmount} ${item.currency} on ${item.nextExpectedDate}."
                        )
                    }
                }
            }
        } catch (_: Exception) {
            return Result.retry()
        }

        return Result.success()
    }

    private fun sendNotification(id: Int, title: String, message: String) {
        val mgr = applicationContext.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                "Spending Alerts & Bill Reminders",
                NotificationManager.IMPORTANCE_DEFAULT
            )
            mgr.createNotificationChannel(channel)
        }

        val notif = NotificationCompat.Builder(applicationContext, CHANNEL_ID)
            .setSmallIcon(R.mipmap.ic_launcher)
            .setContentTitle(title)
            .setContentText(message)
            .setAutoCancel(true)
            .build()

        runCatching { mgr.notify(id, notif) }
    }

    companion object {
        private const val CHANNEL_ID = "spending_alerts"
        private const val WORK_NAME = "spending_daily_reminders"

        fun scheduleDaily(context: Context) {
            val request = PeriodicWorkRequestBuilder<ReminderWorker>(24, TimeUnit.HOURS).build()
            WorkManager.getInstance(context).enqueueUniquePeriodicWork(
                WORK_NAME,
                ExistingPeriodicWorkPolicy.KEEP,
                request
            )
        }
    }
}
