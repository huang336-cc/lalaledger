package com.lightledger.app.util

import java.time.Instant
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

/**
 * 日期工具：统一使用系统时区，格式化风格轻量简洁。
 *
 * 性能约定（账单列表每帧调用成百上千次）：
 * - 不使用 SimpleDateFormat：其内部 Calendar 可变且非线程安全，开销也大；
 *   时间文本改为纯整数运算拼接。
 * - DateTimeFormatter 不可变、线程安全，可安全共享静态实例。
 * - `LocalDate.now()` 每次调用都有系统时区查询开销，这里按"当天"缓存，
 *   跨天自动失效（账单列表的判断基准只需精确到天）。
 */
object DateUtils {

    private val zone: ZoneId get() = ZoneId.systemDefault()

    private val fmtMonthDay = DateTimeFormatter.ofPattern("M月d日")
    private val fmtFullDate = DateTimeFormatter.ofPattern("yyyy年M月d日")
    private val fmtYearMonth = DateTimeFormatter.ofPattern("yyyy年M月")
    private val fmtFull = DateTimeFormatter.ofPattern("yyyy年M月d日 HH:mm")
    private val fmtCsv = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss")

    // ---------- 「今天」缓存：避免每行文本都查一次系统时间 ----------

    @Volatile
    private var cachedToday: LocalDate = LocalDate.now()

    @Volatile
    private var nextDayStartMillis: Long = cachedToday.plusDays(1)
        .atStartOfDay(ZoneId.systemDefault()).toInstant().toEpochMilli()

    /** 当天日期（跨天自动刷新）。列表渲染的热点路径。 */
    private fun today(): LocalDate {
        val now = System.currentTimeMillis()
        if (now >= nextDayStartMillis) {
            // 只在真正跨天时执行一次，之后复用
            val fresh = LocalDate.now()
            cachedToday = fresh
            nextDayStartMillis = fresh.plusDays(1)
                .atStartOfDay(ZoneId.systemDefault()).toInstant().toEpochMilli()
        }
        return cachedToday
    }

    /** 两个字符的两位数字（零填充），避免 String.format 的高开销 */
    private inline fun twoDigits(value: Int, sb: StringBuilder) {
        if (value < 10) {
            sb.append('0')
            sb.append(('0'.code + value).toChar())
        } else {
            sb.append(('0'.code + value / 10).toChar())
            sb.append(('0'.code + value % 10).toChar())
        }
    }

    /** epochMillis -> "HH:mm"，纯整数运算（本地时区由 caller 保证） */
    private fun hhmm(millis: Long): String {
        val ldt = LocalDateTime.ofInstant(Instant.ofEpochMilli(millis), zone)
        val sb = StringBuilder(5)
        twoDigits(ldt.hour, sb)
        sb.append(':')
        twoDigits(ldt.minute, sb)
        return sb.toString()
    }

    fun startOfDayMillis(date: LocalDate = LocalDate.now()): Long =
        date.atStartOfDay(zone).toInstant().toEpochMilli()

    fun endOfDayMillis(date: LocalDate = LocalDate.now()): Long =
        date.plusDays(1).atStartOfDay(zone).toInstant().toEpochMilli() - 1

    // ---------- 时间文本缓存（同一毫秒值重复渲染直接命中） ----------

    private const val CACHE_MAX = 512
    private val billTimeCache = HashMap<Long, String>(CACHE_MAX)

    /** 账单时间 -> 显示文案：今天/昨天带日期词，今年显示 M月d日，往年带年份 */
    fun formatBillTime(millis: Long): String {
        billTimeCache[millis]?.let { return it }
        val dt = LocalDateTime.ofInstant(Instant.ofEpochMilli(millis), zone)
        val today = today()
        val date = dt.toLocalDate()
        val time = hhmm(millis)
        val text = when {
            date == today -> "今天 $time"
            date == today.minusDays(1) -> "昨天 $time"
            date.year == today.year -> "${date.format(fmtMonthDay)} $time"
            else -> dt.format(fmtFull)
        }
        if (billTimeCache.size >= CACHE_MAX) billTimeCache.clear()
        billTimeCache[millis] = text
        return text
    }

    fun startOfWeekMillis(): Long =
        startOfDayMillis(LocalDate.now().with(java.time.DayOfWeek.MONDAY))

    fun startOfMonthMillis(): Long =
        startOfDayMillis(LocalDate.now().withDayOfMonth(1))

    /** 详情页完整时间 */
    fun formatFullTime(millis: Long): String =
        LocalDateTime.ofInstant(Instant.ofEpochMilli(millis), zone).format(fmtFull)

    /** CSV 导出格式 */
    fun formatCsvTime(millis: Long): String =
        LocalDateTime.ofInstant(Instant.ofEpochMilli(millis), zone).format(fmtCsv)

    /** 日期 -> "yyyy-MM-dd" */
    fun formatDate(date: LocalDate): String = date.toString()

    /** "yyyy-MM-dd" -> LocalDate */
    fun parseDate(text: String): LocalDate? =
        runCatching { LocalDate.parse(text) }.getOrNull()

    /** 年月 -> "yyyy年M月"（日历页月标题） */
    fun formatYearMonth(yearMonth: java.time.YearMonth): String =
        yearMonth.format(fmtYearMonth)

    /**
     * 账单列表按日分组的组头文案：
     * 今天/昨天 → 相对词；今年 → "M月d日 周X"；往年 → "yyyy年M月d日 周X"。
     */
    fun formatGroupDate(date: LocalDate): String {
        val today = today()
        val dateText = when {
            date == today -> "今天"
            date == today.minusDays(1) -> "昨天"
            date.year == today.year -> date.format(fmtMonthDay)
            else -> date.format(fmtFullDate)
        }
        return "$dateText ${dowText(date.dayOfWeek)}"
    }

    /** 星期几中文短文案 */
    fun dowText(day: java.time.DayOfWeek): String = when (day) {
        java.time.DayOfWeek.MONDAY -> "周一"
        java.time.DayOfWeek.TUESDAY -> "周二"
        java.time.DayOfWeek.WEDNESDAY -> "周三"
        java.time.DayOfWeek.THURSDAY -> "周四"
        java.time.DayOfWeek.FRIDAY -> "周五"
        java.time.DayOfWeek.SATURDAY -> "周六"
        else -> "周日"
    }

    /** 星期几单字（日历页表头），复用 dowText 的最后一位 */
    fun dowSingle(day: java.time.DayOfWeek): String = dowText(day).substring(2)
}
