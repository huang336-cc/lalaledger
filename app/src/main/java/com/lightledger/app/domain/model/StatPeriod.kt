package com.lightledger.app.domain.model

import java.time.LocalDate
import java.time.ZoneId

/**
 * 统计面板的时间筛选维度。
 * [custom] 需要额外传入起止日期。
 */
sealed class StatPeriod {
    data object Today : StatPeriod()
    data object ThisWeek : StatPeriod()
    data object ThisMonth : StatPeriod()
    data object All : StatPeriod()
    data class Custom(val start: LocalDate, val end: LocalDate) : StatPeriod()

    /** 转换为毫秒时间戳区间 [startMillis, endMillis)，全部时段返回 null */
    fun toRange(zone: ZoneId = ZoneId.systemDefault()): Pair<Long, Long>? {
        fun LocalDate.startMillis(): Long =
            atStartOfDay(zone).toInstant().toEpochMilli()
        fun LocalDate.endMillis(): Long =
            plusDays(1).atStartOfDay(zone).toInstant().toEpochMilli() - 1

        val today = LocalDate.now(zone)
        return when (this) {
            Today -> today.startMillis() to today.endMillis()
            ThisWeek -> today.with(java.time.DayOfWeek.MONDAY).startMillis() to today.endMillis()
            ThisMonth -> today.withDayOfMonth(1).startMillis() to today.endMillis()
            All -> null
            is Custom -> start.startMillis() to end.endMillis()
        }
    }
}
