package com.lightledger.app.util

import java.text.SimpleDateFormat
import java.time.Instant
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Date
import java.util.Locale

/**
 * 日期工具：统一使用系统时区，格式化风格轻量简洁。
 */
object DateUtils {

    private val zone: ZoneId get() = ZoneId.systemDefault()

    private val fmtTime = SimpleDateFormat("HH:mm", Locale.CHINA)
    private val fmtMonthDay = DateTimeFormatter.ofPattern("M月d日")
    private val fmtFullDate = DateTimeFormatter.ofPattern("yyyy年M月d日")
    private val fmtYearMonth = DateTimeFormatter.ofPattern("yyyy年M月")
    private val fmtFull = DateTimeFormatter.ofPattern("yyyy年M月d日 HH:mm")
    private val fmtCsv = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss")

    fun startOfDayMillis(date: LocalDate = LocalDate.now()): Long =
        date.atStartOfDay(zone).toInstant().toEpochMilli()

    fun endOfDayMillis(date: LocalDate = LocalDate.now()): Long =
        date.plusDays(1).atStartOfDay(zone).toInstant().toEpochMilli() - 1

    fun startOfWeekMillis(): Long =
        startOfDayMillis(LocalDate.now().with(java.time.DayOfWeek.MONDAY))

    fun startOfMonthMillis(): Long =
        startOfDayMillis(LocalDate.now().withDayOfMonth(1))

    /** 账单时间 -> 显示文案：今天/昨天带日期词，今年显示 M月d日，往年带年份 */
    fun formatBillTime(millis: Long): String {
        val dt = LocalDateTime.ofInstant(Instant.ofEpochMilli(millis), zone)
        val today = LocalDate.now()
        val date = dt.toLocalDate()
        val time = fmtTime.format(Date(millis))
        return when {
            date == today -> "今天 $time"
            date == today.minusDays(1) -> "昨天 $time"
            date.year == today.year -> "${date.format(fmtMonthDay)} $time"
            else -> dt.format(fmtFull)
        }
    }

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
        val today = LocalDate.now()
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
}
