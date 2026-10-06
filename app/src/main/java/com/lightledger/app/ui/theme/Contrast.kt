package com.lightledger.app.ui.theme

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance

/**
 * 在给定背景色上挑一个可读的前景色（深或浅二选一）。
 *
 * 用于「用户自定义颜色」的场景：分类色、账本色、成员色都由用户自选，
 * 明度不可控。若直接拿原色当文字色，用户选到浅色时文字几乎看不见。
 *
 * 阈值取 0.45：比 [MemberChip] 早先手写的 0.5 略低，因为淡色底
 * （同色 10%~14% 透明度）叠在主题底色上后会被整体抬高，需要更保守。
 *
 * @param bg 背景色（通常是实色或已与主题底色混合后的近似色）
 * @param dark 背景偏亮时使用的深色前景
 * @param light 背景偏暗时使用的浅色前景
 */
fun readableOn(
    bg: Color,
    dark: Color = Color(0xFF10161F),
    light: Color = Color.White,
): Color = if (bg.luminance() > 0.45f) dark else light

/**
 * 自定义色「同色淡底 + 同色文字」场景的前景色选择。
 *
 * 例如分类格选中态：底是 `categoryColor.copy(alpha = 0.10f)`，
 * 文字原本也用 `categoryColor`，用户选浅色分类时文字就糊了。
 */
fun Color.onTint(): Color = readableOn(this)
