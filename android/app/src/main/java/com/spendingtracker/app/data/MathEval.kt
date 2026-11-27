package com.spendingtracker.app.data

import kotlin.math.roundToLong

/**
 * Safe inline arithmetic evaluator for Amount fields ($0, 100% local).
 * Supports +, -, *, /, parentheses, and k/m/tr suffixes (e.g. "35000+15000", "240k/3").
 */
object MathEval {
    private val kRegex = Regex("(\\d+(?:\\.\\d+)?)k", RegexOption.IGNORE_CASE)
    private val mRegex = Regex("(\\d+(?:\\.\\d+)?)(?:m|tr)", RegexOption.IGNORE_CASE)
    private val tokenRegex = Regex("\\d+(?:\\.\\d+)?|[+\\-*/()]")

    fun evaluate(raw: String): Double? {
        var s = raw.replace(",", "").replace("×", "*").replace("÷", "/").replace(" ", "")
        if (s.isBlank()) return null
        s = kRegex.replace(s) { m -> ((m.groupValues[1].toDoubleOrNull() ?: 0.0) * 1000.0).toString() }
        s = mRegex.replace(s) { m -> ((m.groupValues[1].toDoubleOrNull() ?: 0.0) * 1_000_000.0).toString() }
        if (!s.matches(Regex("^[0-9.+\\-*/()]+$"))) return null

        val tokens = tokenRegex.findAll(s).map { it.value }.toList()
        if (tokens.isEmpty()) return null

        val vals = ArrayDeque<Double>()
        val ops = ArrayDeque<String>()

        fun prec(op: String) = when (op) {
            "+", "-" -> 1
            "*", "/" -> 2
            else -> 0
        }

        fun applyOp(): Boolean {
            if (ops.isEmpty() || vals.size < 2) return false
            val op = ops.removeLast()
            val b = vals.removeLast()
            val a = vals.removeLast()
            val res = when (op) {
                "+" -> a + b
                "-" -> a - b
                "*" -> a * b
                "/" -> if (b == 0.0) Double.NaN else a / b
                else -> Double.NaN
            }
            if (res.isNaN() || res.isInfinite()) return false
            vals.addLast(res)
            return true
        }

        for (tok in tokens) {
            when {
                tok[0].isDigit() -> vals.addLast(tok.toDoubleOrNull() ?: return null)
                tok == "(" -> ops.addLast(tok)
                tok == ")" -> {
                    while (ops.isNotEmpty() && ops.last() != "(") {
                        if (!applyOp()) return null
                    }
                    if (ops.isNotEmpty() && ops.last() == "(") ops.removeLast()
                }
                else -> {
                    while (ops.isNotEmpty() && prec(ops.last()) >= prec(tok)) {
                        if (!applyOp()) return null
                    }
                    ops.addLast(tok)
                }
            }
        }
        while (ops.isNotEmpty()) {
            if (!applyOp()) return null
        }
        val res = vals.lastOrNull() ?: return null
        if (res.isNaN() || res.isInfinite() || res <= 0.0) return null
        return (res * 100.0).roundToLong() / 100.0
    }

    fun formatForApi(value: Double): String {
        return if (value == value.toLong().toDouble()) {
            value.toLong().toString()
        } else {
            value.toString()
        }
    }
}
