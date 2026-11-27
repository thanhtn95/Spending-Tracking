package com.spendingtracker.app.widget

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.widget.RemoteViews
import com.spendingtracker.app.MainActivity
import com.spendingtracker.app.R
import com.spendingtracker.app.data.TokenStore
import java.math.BigDecimal
import java.text.NumberFormat
import java.util.Locale

/**
 * Glanceable Home Screen Widget ($0/month — reads cached balance snapshot from TokenStore).
 * Shows Running Balance, This Month's Spend, and 1-tap Scan Receipt / + Add shortcuts.
 */
class SpendingWidgetProvider : AppWidgetProvider() {

    override fun onUpdate(
        context: Context,
        appWidgetManager: AppWidgetManager,
        appWidgetIds: IntArray
    ) {
        for (appWidgetId in appWidgetIds) {
            updateWidget(context, appWidgetManager, appWidgetId)
        }
    }

    companion object {
        fun notifyDataChanged(context: Context) {
            val mgr = AppWidgetManager.getInstance(context)
            val ids = mgr.getAppWidgetIds(ComponentName(context, SpendingWidgetProvider::class.java))
            for (id in ids) {
                updateWidget(context, mgr, id)
            }
        }

        private fun formatCompact(raw: String): String {
            val num = raw.toBigDecimalOrNull() ?: return raw
            return NumberFormat.getNumberInstance(Locale.getDefault()).apply {
                maximumFractionDigits = if (num.scale() > 0 && num.stripTrailingZeros().scale() > 0) 2 else 0
            }.format(num)
        }

        private fun updateWidget(context: Context, mgr: AppWidgetManager, widgetId: Int) {
            val store = TokenStore(context)
            val balRaw = store.getWidgetBalance()
            val spentRaw = store.getWidgetMonthSpent()
            val cur = store.getWidgetCurrency()
            val balNum = balRaw.toBigDecimalOrNull() ?: BigDecimal.ZERO
            val balPrefix = if (balNum > BigDecimal.ZERO) "+" else ""

            val views = RemoteViews(context.packageName, R.layout.widget_spending)
            views.setTextViewText(R.id.widget_balance, "$balPrefix${formatCompact(balRaw)} $cur")
            views.setTextViewText(R.id.widget_spent, "Month spent: ${formatCompact(spentRaw)} $cur")

            val openIntent = Intent(context, MainActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
            }
            views.setOnClickPendingIntent(
                R.id.widget_root,
                PendingIntent.getActivity(
                    context,
                    0,
                    openIntent,
                    PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
                )
            )

            val scanIntent = Intent(context, MainActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
                putExtra(MainActivity.EXTRA_WIDGET_ACTION, "scan")
            }
            views.setOnClickPendingIntent(
                R.id.widget_btn_scan,
                PendingIntent.getActivity(
                    context,
                    1,
                    scanIntent,
                    PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
                )
            )

            val addIntent = Intent(context, MainActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
                putExtra(MainActivity.EXTRA_WIDGET_ACTION, "add")
            }
            views.setOnClickPendingIntent(
                R.id.widget_btn_add,
                PendingIntent.getActivity(
                    context,
                    2,
                    addIntent,
                    PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
                )
            )

            mgr.updateAppWidget(widgetId, views)
        }
    }
}
