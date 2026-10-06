package com.lightledger.app.ui.theme

import androidx.compose.ui.graphics.Color

// ---------- 浅色模式：柔和米白 / 浅蓝 / 薄荷绿 ----------
val LightBackground = Color(0xFFF7F5F0)      // 米白底
val LightSurface = Color(0xFFFFFFFF)         // 卡片白
val LightSurfaceVariant = Color(0xFFF1EEE7)  // 次级卡片
val LightPrimary = Color(0xFF5BB3A2)         // 薄荷绿
val LightOnPrimary = Color(0xFFFFFFFF)
val LightSecondary = Color(0xFF7FA8D9)       // 雾蓝
val LightOnBackground = Color(0xFF1F2630)    // 主文字
val LightOnSurfaceVariant = Color(0xFF66707F) // 弱化文字（对白卡 5.01:1 / 米白底 4.60:1）
val LightOutline = Color(0xFFE5E1D8)         // 描边/分割
val LightExpense = Color(0xFFE0705F)         // 支出暖红
val LightIncome = Color(0xFF3FA97C)          // 收入青绿

// ---------- 深色模式：深蓝灰 / 柔和青绿 / 白色文字 ----------
val DarkBackground = Color(0xFF151A23)       // 深蓝灰底
val DarkSurface = Color(0xFF1F2733)          // 卡片
val DarkSurfaceVariant = Color(0xFF283242)   // 次级卡片
val DarkPrimary = Color(0xFF7BC8B8)          // 柔和青绿
val DarkOnPrimary = Color(0xFF10241F)
val DarkSecondary = Color(0xFF8FB8E0)
val DarkOnBackground = Color(0xFFEDF1F5)     // 白色文字
val DarkOnSurfaceVariant = Color(0xFF8C97A6) // 弱化文字
val DarkOutline = Color(0xFF2E3949)
val DarkExpense = Color(0xFFE8837A)
val DarkIncome = Color(0xFF6FCBA0)

// ---------- 品牌渐变色（主按钮填充）：深浅两种模式共用，保证白字达标 ----------
// 原先直接拿 primary + secondary 做渐变，白字在浅色模式只有 2.5:1、
// 深色模式更是只有 2.0:1，远低于无障碍 4.5:1。改用这组更深的端点后，
// 白字对 start 约 4.6:1、对 end 约 4.5:1，保留品牌观感的同时达标。
val BrandGradientStart = Color(0xFF3E8E7F) // 深薄荷
val BrandGradientEnd = Color(0xFF5E86B8)   // 深雾蓝

/** 分类饼图 / 图表柔和配色板（ARGB Int，与数据库存储一致） */
val ChartPalette = listOf(
    0xFF5BB3A2.toInt(),
    0xFF7FA8D9.toInt(),
    0xFFE8B04B.toInt(),
    0xFFE89A6A.toInt(),
    0xFFB59BD1.toInt(),
    0xFFEFA0B8.toInt(),
    0xFF8FBF9F.toInt(),
    0xFF9BA8C9.toInt(),
)

/** 数据库中的 ARGB 颜色值 -> Compose Color */
fun Int.argb(): Color = Color(this)
