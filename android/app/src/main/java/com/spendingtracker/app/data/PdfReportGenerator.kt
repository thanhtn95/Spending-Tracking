package com.spendingtracker.app.data

import android.graphics.Color
import android.graphics.Paint
import android.graphics.Typeface
import android.graphics.pdf.PdfDocument
import com.spendingtracker.app.ui.UiState
import java.io.OutputStream
import java.text.NumberFormat
import java.time.format.DateTimeFormatter
import java.util.Locale

/**
 * Generates a clean 1-page Monthly Financial Summary PDF using Android's native PdfDocument ($0, 100% offline).
 */
object PdfReportGenerator {

    private fun fmt(raw: String?): String {
        val n = raw?.toBigDecimalOrNull() ?: return raw ?: "0"
        return NumberFormat.getNumberInstance(Locale.getDefault()).format(n)
    }

    fun writeMonthlyReport(state: UiState, out: OutputStream) {
        val doc = PdfDocument()
        val pageInfo = PdfDocument.PageInfo.Builder(595, 842, 1).create() // A4
        val page = doc.startPage(pageInfo)
        val c = page.canvas

        val titlePaint = Paint().apply {
            color = Color.parseColor("#0D5C4B")
            textSize = 20f
            typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
        }
        val headingPaint = Paint().apply {
            color = Color.parseColor("#141513")
            textSize = 13f
            typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
        }
        val bodyPaint = Paint().apply {
            color = Color.parseColor("#222222")
            textSize = 11f
        }
        val mutedPaint = Paint().apply {
            color = Color.parseColor("#6B6D67")
            textSize = 10f
        }
        val linePaint = Paint().apply {
            color = Color.parseColor("#DDDDDD")
            strokeWidth = 1f
        }

        val monthLbl = state.selectedMonth.format(DateTimeFormatter.ofPattern("MMMM yyyy", Locale.getDefault()))
        val cur = state.monthlySpending?.baseCurrency ?: "VND"
        var y = 48f

        c.drawText("Spending Tracker — Monthly Report", 40f, y, titlePaint)
        y += 20f
        c.drawText("Period: $monthLbl   ·   Account: ${state.userEmail.orEmpty()}", 40f, y, mutedPaint)
        y += 14f
        c.drawLine(40f, y, 555f, y, linePaint)
        y += 24f

        // Summary KPIs
        c.drawText("Cash Flow Summary ($cur)", 40f, y, headingPaint)
        y += 18f
        val s = state.monthlySpending
        c.drawText("Running Balance (incl. carryover): ${fmt(s?.cumulativeBalance)} $cur", 40f, y, bodyPaint); y += 16f
        c.drawText("From Previous Months: ${fmt(s?.carryForwardBalance)} $cur", 40f, y, bodyPaint); y += 16f
        c.drawText("This Month Debit (Spent): -${fmt(s?.totalSpent)} $cur   |   Credit (Inflow): +${fmt(s?.totalCredit)} $cur", 40f, y, bodyPaint); y += 16f
        state.comparison?.let { comp ->
            val sr = comp.savingsRatePct?.let { "$it%" } ?: "—"
            c.drawText("Savings Rate: $sr   |   No-Spend Days: ${comp.noSpendDays}d (Streak: ${comp.currentNoSpendStreak}d)", 40f, y, bodyPaint)
            y += 16f
        }
        y += 10f

        // Category Breakdown
        val cats = s?.byCategory.orEmpty().take(10)
        if (cats.isNotEmpty()) {
            c.drawText("Top Spending Categories", 40f, y, headingPaint)
            y += 18f
            cats.forEach { cat ->
                c.drawText("• ${cat.categoryName} (${cat.transactionCount} tx)", 48f, y, bodyPaint)
                c.drawText("${fmt(cat.totalSpent)} $cur", 410f, y, bodyPaint)
                y += 15f
            }
            y += 10f
        }

        // Budgets
        if (state.budgets.isNotEmpty() && y < 680f) {
            c.drawText("Monthly Budgets", 40f, y, headingPaint)
            y += 18f
            state.budgets.take(8).forEach { b ->
                val lim = b.effectiveLimit ?: b.monthlyLimit
                c.drawText("• ${b.categoryName}${if (b.rollover) " (Rollover)" else ""}", 48f, y, bodyPaint)
                c.drawText("${fmt(b.spent)} / ${fmt(lim)} ${b.currency}", 370f, y, bodyPaint)
                y += 15f
            }
            y += 10f
        }

        // Top Merchants
        val merchants = state.comparison?.topMerchants.orEmpty()
        if (merchants.isNotEmpty() && y < 740f) {
            c.drawText("Top 5 Merchants", 40f, y, headingPaint)
            y += 18f
            merchants.forEach { m ->
                c.drawText("• ${m.vendor} (${m.transactionCount}x)", 48f, y, bodyPaint)
                c.drawText("${fmt(m.totalSpent)} $cur", 410f, y, bodyPaint)
                y += 15f
            }
        }

        doc.finishPage(page)
        doc.writeTo(out)
        doc.close()
    }
}
