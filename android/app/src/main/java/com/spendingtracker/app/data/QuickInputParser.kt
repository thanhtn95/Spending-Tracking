package com.spendingtracker.app.data

data class ParsedQuickInput(
    val amount: String,
    val vendor: String,
    val txType: String,
    val categoryName: String,
    val walletId: String?,
    val tags: List<String>
)

/**
 * 100% local on-device Natural-Language Quick Input parser (0 AI calls).
 * Examples:
 *   "50k coffee cash #work"  -> debit 50000, vendor="Coffee", wallet=Cash, tags=[work], cat=Dining
 *   "+15m salary vcb"        -> credit 15000000, vendor="Salary", wallet=VCB, cat=Salary
 */
object QuickInputParser {
    private val amtRegex = Regex("^([+-])?(\\d+(?:[.,]\\d+)?)(k|m|tr)?$", RegexOption.IGNORE_CASE)

    fun parse(raw: String, wallets: List<WalletRecord>): ParsedQuickInput? {
        val tokens = raw.trim().split(Regex("\\s+")).filter { it.isNotEmpty() }
        if (tokens.isEmpty()) return null

        var txType = "debit"
        var amount: Double? = null
        val tags = mutableListOf<String>()
        var walletId: String? = null
        val words = mutableListOf<String>()

        for (tok in tokens) {
            if (tok.startsWith("#") && tok.length > 1) {
                tags.add(tok.substring(1).lowercase())
                continue
            }
            val match = amtRegex.matchEntire(tok)
            if (match != null && amount == null) {
                if (match.groupValues[1] == "+") txType = "credit"
                var num = match.groupValues[2].replace(",", ".").toDoubleOrNull() ?: 0.0
                when (match.groupValues[3].lowercase()) {
                    "k" -> num *= 1_000.0
                    "m", "tr" -> num *= 1_000_000.0
                }
                if (num > 0.0) {
                    amount = num
                    continue
                }
            }
            val wallet = wallets.firstOrNull { it.name.equals(tok, ignoreCase = true) }
            if (wallet != null && walletId == null) {
                walletId = wallet.id
                continue
            }
            words.add(tok)
        }

        val finalAmt = amount ?: return null
        if (words.isEmpty()) return null

        val vendor = words.joinToString(" ") { w ->
            w.replaceFirstChar { c -> c.uppercase() }
        }
        val lower = vendor.lowercase()
        val category = when {
            txType == "credit" && Regex("salary|lương|bonus|payroll").containsMatchIn(lower) -> "Salary"
            txType == "credit" -> "Income"
            Regex("coffee|cafe|cà phê|lunch|dinner|breakfast|pho|phở|food|eat|tea|trà").containsMatchIn(lower) -> "Dining"
            Regex("grab|taxi|uber|gas|xăng|parking|bus|metro|train").containsMatchIn(lower) -> "Transport"
            Regex("mart|grocery|supermarket|siêu thị|market|coop|winmart|bhx").containsMatchIn(lower) -> "Groceries"
            Regex("electric|water|internet|wifi|phone|bill|điện|nước").containsMatchIn(lower) -> "Utilities"
            Regex("netflix|spotify|icloud|youtube|gym").containsMatchIn(lower) -> "Subscriptions"
            else -> "Uncategorized"
        }

        val amtStr = if (finalAmt == finalAmt.toLong().toDouble()) {
            finalAmt.toLong().toString()
        } else {
            finalAmt.toString()
        }
        return ParsedQuickInput(
            amount = amtStr,
            vendor = vendor,
            txType = txType,
            categoryName = category,
            walletId = walletId,
            tags = tags.distinct()
        )
    }
}
