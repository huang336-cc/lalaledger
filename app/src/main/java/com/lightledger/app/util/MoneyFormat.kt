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
