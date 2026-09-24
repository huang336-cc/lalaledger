package com.lightledger.app.util

/**
 * 金额工具：内部一律使用"分"（Long），显示时转元。
 *
 * 性能约定（列表页每帧会被调用成百上千次）：
 * - 不使用 DecimalFormat 实例：它既非线程安全，调用开销也明显。
 * - 全部改为纯整数运算 + 字符串拼接，零对象分配（不产生装箱 / BigDecimal）。
 * - 结果做有界缓存，滚动时同一金额重复渲染直接命中。
 */
object MoneyFormat {

    /** 千分位分隔整数部分（只处理纯数字串） */
    private fun groupThousands(digits: String): String {
        if (digits.length <= 3) return digits
        val sb = StringBuilder(digits.length + digits.length / 3)
        val firstGroup = digits.length % 3
        if (firstGroup > 0) {
            sb.append(digits, 0, firstGroup)
        }
        var i = firstGroup
        while (i < digits.length) {
            if (sb.isNotEmpty()) sb.append(',')
            sb.append(digits, i, i + 3)
            i += 3
        }
        return sb.toString()
    }

    /** 两位小数文本：分 -> "0.05" / "1.50"（整数部分不分组） */
    private fun twoDecimals(value: Long): String {
        val yuan = value / 100
        val cents = (value % 100).toInt()
        return if (cents < 10) "$yuan.0$cents" else "$yuan.$cents"
    }

    // ---------- 结果缓存（列表滚动热点） ----------

    private const val CACHE_MAX = 1024
    private val fenCache = HashMap<Long, String>(CACHE_MAX)

    /** 分 -> "1,234.56"（带千分位，用于账单金额 / 统计展示） */
    fun fenToString(fen: Long): String {
        fenCache[fen]?.let { return it }
        val negative = fen < 0
        val abs = if (negative) -fen else fen
        // 整数部分参与千分位分组，小数部分（固定两位）必须排除在外
        val yuan = abs / 100
        val cents = (abs % 100).toInt()
        val centsText = if (cents < 10) "0$cents" else cents.toString()
        val text = groupThousands(yuan.toString()) + "." + centsText
        val result = if (negative) "-$text" else text
        if (fenCache.size >= CACHE_MAX) fenCache.clear()
        fenCache[fen] = result
        return result
    }

    /**
     * 分 -> "1234.56"（无千分位，用于 CSV / 图片导出）。
     * 与旧行为保持一致：最多两位小数，自动去掉无意义的尾随 0（如 35.00 -> 35）。
     */
    fun fenToPlain(fen: Long): String {
        val negative = fen < 0
        val abs = if (negative) -fen else fen
        val text = twoDecimalsTrimmed(abs)
        return if (negative) "-$text" else text
    }

    /**
     * 分 -> 紧凑显示（无符号）：小于 1000 元原样，否则 "1.22k" / "1.22m"。
     * 用于日历格等窄空间，避免长数字撑破布局。
     */
    fun fenToCompact(fen: Long): String = compactYuan(if (fen < 0) -fen else fen)

    /**
     * 分 -> 带正负号的紧凑显示：
     * 支出 "-1.22k"、收入 "+35.5"（negative = true 时取负号）。
     */
    fun fenToCompactSigned(fen: Long, negative: Boolean): String {
        val sign = if (negative) "-" else "+"
        val abs = if (fen < 0) -fen else fen
        val yuan = abs / 100
        return when {
            yuan >= 1_000_000 -> sign + trimZeros(abs, 1_000_000_00L) + "m"
            yuan >= 1_000 -> sign + trimZeros(abs, 1_000_00L) + "k"
            else -> sign + twoDecimalsTrimmed(abs)
        }
    }

    /** 分 -> 紧凑文本（不含符号），供无符号场景复用 */
    private fun compactYuan(fen: Long): String {
        val yuan = fen / 100
        return when {
            yuan >= 1_000_000 -> trimZeros(fen, 1_000_000_00L) + "m"
            yuan >= 1_000 -> trimZeros(fen, 1_000_00L) + "k"
            else -> twoDecimalsTrimmed(fen)
        }
    }

    /**
     * 按 divisor（单位对应的"分"值）做除法并保留两位小数，去掉无意义的尾随 0。
     * 例：fen=122000, divisor=100000 → "1.22"；fen=100000 → "1"。
     */
    private fun trimZeros(fen: Long, divisor: Long): String {
        val scaled = (fen * 100 + divisor / 2) / divisor   // 保留两位小数的四舍五入
        val intPart = scaled / 100
        var decPart = (scaled % 100).toInt()
        if (decPart == 0) return intPart.toString()
        val decText = if (decPart < 10) "0$decPart" else decPart.toString()
        val trimmed = decText.trimEnd('0')
        return if (trimmed.isEmpty()) intPart.toString() else "$intPart.$trimmed"
    }

    /** 分 -> 最多两位小数、去掉尾随 0 的元文本，如 "35.5" / "12" */
    private fun twoDecimalsTrimmed(fen: Long): String {
        val intPart = fen / 100
        val cents = (fen % 100).toInt()
        if (cents == 0) return intPart.toString()
        val decText = if (cents < 10) "0$cents" else cents.toString()
        val trimmed = decText.trimEnd('0')
        return if (trimmed.isEmpty()) intPart.toString() else "$intPart.$trimmed"
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
