package com.spendingtracker.app.data

import android.app.Notification
import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import java.time.LocalDate

/**
 * Opt-in, 100% on-device bank/e-wallet notification parser ($0/month, 0 AI calls).
 * Parses incoming banking notifications (VCB, TCB, MB, ACB, MoMo, ZaloPay, Timo, Cake, etc.)
 * and sends them to /api/v1/transactions/draft so they appear in Pending Drafts for 1-tap review.
 */
class BankNotificationListener : NotificationListenerService() {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    override fun onNotificationPosted(sbn: StatusBarNotification?) {
        if (sbn == null) return
        val store = TokenStore(applicationContext)
        if (!store.isBankAlertsEnabled() || store.getToken().isNullOrBlank()) return

        val pkg = sbn.packageName.orEmpty().lowercase()
        val extras = sbn.notification?.extras ?: return
        val title = extras.getCharSequence(Notification.EXTRA_TITLE)?.toString().orEmpty()
        val text = extras.getCharSequence(Notification.EXTRA_BIG_TEXT)?.toString()
            ?: extras.getCharSequence(Notification.EXTRA_TEXT)?.toString().orEmpty()
        val combined = "$title $text".trim()
        if (combined.isBlank()) return

        val isKnownBankPkg = BANK_PACKAGE_HINTS.any { pkg.contains(it) }
        val looksLikeBankAlert = BANK_KEYWORD_REGEX.containsMatchIn(combined)
        if (!isKnownBankPkg && !looksLikeBankAlert) return

        val parsed = parseBankText(title, text) ?: return
        val api = ApiClient.create(store)
        scope.launch {
            runCatching {
                api.createNotificationDraft(
                    CreateDraftNotificationRequest(
                        txDate = LocalDate.now().toString(),
                        vendor = parsed.vendor,
                        description = text.take(180),
                        amount = parsed.amount,
                        txType = parsed.txType,
                        currency = parsed.currency
                    )
                )
            }
        }
    }

    data class ParsedBankAlert(
        val amount: String,
        val txType: String,
        val currency: String,
        val vendor: String
    )

    companion object {
        private val BANK_PACKAGE_HINTS = listOf(
            "vietcombank", "vcb", "techcombank", "tcb", "mbbank", "mbmobile",
            "acb", "bidv", "tpb", "vpbank", "vib", "cake", "timo", "momo",
            "zalopay", "shopeepay", "sacombank", "shinhan", "ocb", "hdbank", "vietinbank"
        )

        private val BANK_KEYWORD_REGEX = Regex(
            "(VND|VNĐ|USD|\\bGD:|biến động số dư|thanh toán|chuyển tiền|nhận tiền)",
            RegexOption.IGNORE_CASE
        )

        private val AMOUNT_REGEX = Regex(
            "([+-])?\\s*(\\d{1,3}(?:[.,]\\d{3})+|\\d+(?:[.,]\\d{1,2})?)\\s*(VND|VNĐ|đ|USD|\\$)",
            RegexOption.IGNORE_CASE
        )

        fun parseBankText(title: String, text: String): ParsedBankAlert? {
            val combined = "$title $text"
            val match = AMOUNT_REGEX.find(combined) ?: return null
            val sign = match.groupValues[1]
            val rawNum = match.groupValues[2]
            val rawCur = match.groupValues[3].uppercase()
            val currency = if (rawCur == "USD" || rawCur == "$") "USD" else "VND"

            val cleanedAmount = if (currency == "VND") {
                rawNum.replace(",", "").replace(".", "")
            } else {
                rawNum.replace(",", "")
            }
            if ((cleanedAmount.toDoubleOrNull() ?: 0.0) <= 0.0) return null

            val txType = when {
                sign == "+" -> "credit"
                sign == "-" -> "debit"
                Regex("(nhận tiền|credited|received|hoàn tiền|refund)", RegexOption.IGNORE_CASE).containsMatchIn(combined) -> "credit"
                else -> "debit"
            }

            val vendor = title.trim().ifEmpty { "Bank Alert" }.take(60)
            return ParsedBankAlert(
                amount = cleanedAmount,
                txType = txType,
                currency = currency,
                vendor = vendor
            )
        }
    }
}
