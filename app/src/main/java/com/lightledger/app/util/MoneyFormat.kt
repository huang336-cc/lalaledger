package com.lightledger.app.util

import java.text.DecimalFormat

/**
 * 金额工具：内部一律使用"分"（Long），显示时转元。
 */
object MoneyFormat {

    private val df = DecimalFormat("#,##0.00")
    private val dfPlain = DecimalFormat("0.##")

    /** 分 -> "1,234.56" */
    fun fenToString(fen: Long): String = df.format(fen / 100.0)

    /** 分 -> "1234.56"（无千分位，用于 CSV） */
    fun fenToPlain(fen: Long): String = dfPlain.format(fen / 100.0)

    /**
     * 分 -> 紧凑显示（无符号）：小于 1000 元原样，否则 "1.22k" / "1.22m"。
     * 用于日历格等窄空间，避免长数字撑破布局。
     */
    fun fenToCompact(fen: Long): String = compactYuan(fen / 100.0)

    /**
     * 分 -> 带正负号的紧凑显示：
     * 支出 "-1.22k"、收入 "+35.5"（negative = true 时取负号）。
     */
    fun fenToCompactSigned(fen: Long, negative: Boolean): String =
        (if (negative) "-" else "+") + compactYuan(kotlin.math.abs(fen) / 100.0)

    private fun compactYuan(yuan: Double): String = when {
        yuan >= 1_000_000.0 -> dfPlain.format(yuan / 1_000_000.0) + "m"
        yuan >= 1_000.0 -> dfPlain.format(yuan / 1_000.0) + "k"
        else -> dfPlain.format(yuan)
    }

    /** 元字符串 -> 分；非法输入返回 null */
    fun yuanToFen(text: String): Long? {
        val cleaned = text.trim().removePrefix("¥").replace(",", "")
        if (cleaned.isEmpty()) return null
        val value = cleaned.toDoubleOrNull() ?: return null
        if (value <= 0 || value > 99_999_999) return null
        return Math.round(value * 100)
    }

    /** 校验输入中的小数位不超过 2 位 */
    fun isValidInput(text: String): Boolean {
        if (text.isEmpty()) return true
        val dot = text.indexOf('.')
        if (text.count { it == '.' } > 1) return false
        if (dot >= 0 && text.length - dot - 1 > 2) return false
        return text.all { it.isDigit() || it == '.' }
    }
}
