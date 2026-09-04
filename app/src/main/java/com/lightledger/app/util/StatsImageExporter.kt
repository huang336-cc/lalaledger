package com.lightledger.app.util

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import android.net.Uri
import com.lightledger.app.util.SummaryImageExporter.Palette
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * 统计页长图导出：
 * - android.graphics Canvas 手绘 1080px 宽 PNG 长图（汇总卡 + 分类排行比例条 + 成员明细 + AA 结算）
 * - 配色复用 SummaryImageExporter 的双主题 Palette，保存复用其相册写入逻辑
 * - 保存到相册 Pictures/拉拉记账（Android 10+ 免存储权限）
 */
object StatsImageExporter {

    /** 分类排行行：名称 + 金额 + 百分比 + 比例条 */
    data class CategoryLine(
        val name: String,
        val color: Int,
        val amountText: String,
        val percentText: String,
        val ratio: Float,
    )

    /** 成员明细行：成员 + 消费 + 垫付 */
    data class MemberLine(
        val name: String,
        val color: Int,
        val consumeText: String,
        val paidText: String,
    )

    /** AA 结算行：成员 + 净额文案（直接带"应收/应付/已平"与语义色） */
    data class AaLine(
        val name: String,
        val color: Int,
        val netText: String,
        /** 0 = 已平（灰），1 = 应收（绿），2 = 应付（红） */
        val netType: Int,
    )

    /** 简化转账方案行 */
    data class TransferLine(val text: String)

    data class StatsData(
        val titleText: String,
        val bookName: String,
        val periodText: String,
        val expenseLabel: String,
        val expenseText: String,
        val incomeLabel: String,
        val incomeText: String,
        val netLabel: String,
        val netText: String,
        val dailyLabel: String,
        val dailyText: String,
        val rankTitle: String,
        val rankEmptyText: String,
        val categories: List<CategoryLine>,
        /** 以下旅行账本字段：null = 不显示该节 */
        val memberTitle: String? = null,
        val memberNote: String? = null,
        val memberConsumeLabel: String = "",
        val memberPaidLabel: String = "",
        val members: List<MemberLine> = emptyList(),
        val aaTitle: String? = null,
        val aaEmptyText: String? = null,
        val aaSettledText: String? = null,
        val aaLines: List<AaLine> = emptyList(),
        val transfers: List<TransferLine> = emptyList(),
    )

    private const val WIDTH = 1080
    private const val PAD = 60
    private const val SAVE_DIR = "Pictures/拉拉记账"

    /** 生成并保存统计长图，成功返回相册 Uri，失败返回 null。 */
    suspend fun export(
        context: Context,
        data: StatsData,
        isDark: Boolean,
    ): Uri? = withContext(Dispatchers.IO) {
        runCatching {
            val palette = if (isDark) SummaryImageExporter.darkPalette else SummaryImageExporter.lightPalette
            val bitmap = drawBitmap(data, palette)
            SummaryImageExporter.saveToGallery(context, bitmap, prefix = "统计")
        }.getOrElse { it.printStackTrace(); null }
    }

    // ---------- 绘制 ----------

    private fun drawBitmap(data: StatsData, p: Palette): Bitmap {
        // 预计算各区高度（略放宽余量，宁多勿截断）
        val headH = PAD + 200
        val cardH = 330 + 80
        val rankH = 90 + if (data.categories.isEmpty()) 110 else data.categories.size * 118
        val memberH = if (data.memberTitle != null && data.members.isNotEmpty()) {
            130 + data.members.size * 96
        } else 0
        val aaH = if (data.aaTitle != null) {
            if (data.aaLines.isEmpty()) {
                150 + 110
            } else {
                150 + data.aaLines.size * 88 +
                    (if (data.transfers.isNotEmpty()) 70 + data.transfers.size * 62 else 110)
            }
        } else 0
        val height = headH + cardH + rankH + memberH + aaH + 150 + PAD

        val bitmap = Bitmap.createBitmap(WIDTH, height, Bitmap.Config.RGB_565)
        val canvas = Canvas(bitmap)
        val paint = Paint(Paint.ANTI_ALIAS_FLAG)

        // 背景
        paint.color = p.background
        canvas.drawRect(0f, 0f, WIDTH.toFloat(), height.toFloat(), paint)

        // ---------- 头部 ----------
        paint.color = p.title
        paint.textSize = 72f
        paint.isFakeBoldText = true
        canvas.drawText(data.titleText, PAD.toFloat(), PAD + 72f, paint)

        paint.isFakeBoldText = false
        paint.color = p.sub
        paint.textSize = 38f
        val now = SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.CHINA).format(Date())
        canvas.drawText("${data.bookName} · ${data.periodText} · 生成于 $now", PAD.toFloat(), PAD + 140f, paint)

        // ---------- 汇总卡（2 x 2） ----------
        val cardTop = PAD + 200f
        val cardLeft = PAD.toFloat()
        val cardRight = (WIDTH - PAD).toFloat()
        val cardBoxH = 330f
        paint.color = p.card
        canvas.drawRoundRect(cardLeft, cardTop, cardRight, cardTop + cardBoxH, 40f, 40f, paint)

        val cellW = (cardRight - cardLeft - PAD) / 2f
        drawStatCell(canvas, paint, p, cardLeft + 40, cardTop + 78, data.expenseLabel, data.expenseText, p.expense)
        drawStatCell(canvas, paint, p, cardLeft + 40 + cellW + 20, cardTop + 78, data.incomeLabel, data.incomeText, p.income)
        drawStatCell(canvas, paint, p, cardLeft + 40, cardTop + 208, data.netLabel, data.netText, p.accent)
        drawStatCell(canvas, paint, p, cardLeft + 40 + cellW + 20, cardTop + 208, data.dailyLabel, data.dailyText, p.title)

        var y = cardTop + cardBoxH + 80f

        // ---------- 分类排行 ----------
        paint.color = p.title
        paint.textSize = 44f
        paint.isFakeBoldText = true
        canvas.drawText(data.rankTitle, PAD.toFloat(), y, paint)
        paint.isFakeBoldText = false
        y += 50f

        if (data.categories.isEmpty()) {
            paint.color = p.sub
            paint.textSize = 36f
            canvas.drawText(data.rankEmptyText, PAD.toFloat(), y + 40f, paint)
            y += 110f
        } else {
            data.categories.forEach { line ->
                // 名称（左）+ 金额 · 百分比（右）
                paint.color = line.color
                canvas.drawCircle((PAD + 36).toFloat(), y + 8f, 36f, paint)
                paint.color = COLOR_WHITE
                paint.textSize = 36f
                paint.isFakeBoldText = true
                paint.textAlign = Paint.Align.CENTER
                val initial = line.name.firstOrNull()?.toString() ?: "记"
                canvas.drawText(initial, (PAD + 36).toFloat(), y + 20f, paint)
                paint.textAlign = Paint.Align.LEFT
                paint.isFakeBoldText = false

                paint.color = p.body
                paint.textSize = 40f
                canvas.drawText(
                    ellipsize(paint, line.name, WIDTH - PAD * 2 - 560f),
                    (PAD + 96).toFloat(), y + 12f, paint,
                )
                paint.color = p.expense
                paint.textSize = 40f
                paint.isFakeBoldText = true
                paint.textAlign = Paint.Align.RIGHT
                canvas.drawText("${line.amountText} · ${line.percentText}", (WIDTH - PAD).toFloat(), y + 12f, paint)
                paint.textAlign = Paint.Align.LEFT
                paint.isFakeBoldText = false

                // 比例条
                val barLeft = (PAD + 96).toFloat()
                val barRight = (WIDTH - PAD).toFloat()
                val barTop = y + 44f
                paint.color = p.divider
                canvas.drawRoundRect(barLeft, barTop, barRight, barTop + 14f, 7f, 7f, paint)
                val fillW = (barRight - barLeft) * line.ratio.coerceIn(0f, 1f)
                if (fillW > 14f) {
                    paint.color = line.color
                    canvas.drawRoundRect(barLeft, barTop, barLeft + fillW, barTop + 14f, 7f, 7f, paint)
                }
                y += 118f
            }
        }

        // ---------- 成员明细（旅行账本） ----------
        if (data.memberTitle != null && data.members.isNotEmpty()) {
            y += 40f
            paint.color = p.title
            paint.textSize = 44f
            paint.isFakeBoldText = true
            canvas.drawText(data.memberTitle!!, PAD.toFloat(), y, paint)
            paint.isFakeBoldText = false
            // 列头：消费（右二列） / 垫付（右一列）
            paint.color = p.sub
            paint.textSize = 32f
            paint.textAlign = Paint.Align.RIGHT
            canvas.drawText(data.memberConsumeLabel, (WIDTH - PAD - 330).toFloat(), y, paint)
            canvas.drawText(data.memberPaidLabel, (WIDTH - PAD).toFloat(), y, paint)
            paint.textAlign = Paint.Align.LEFT
            data.memberNote?.let { note ->
                paint.color = p.sub
                paint.textSize = 32f
                canvas.drawText(note, PAD.toFloat(), y + 46f, paint)
            }
            y += 80f

            data.members.forEach { line ->
                drawMemberChip(canvas, paint, p, line.name, line.color, PAD.toFloat(), y + 6f)
                paint.textAlign = Paint.Align.RIGHT
                paint.color = p.body
                paint.textSize = 38f
                paint.isFakeBoldText = true
                canvas.drawText(line.consumeText, (WIDTH - PAD - 330).toFloat(), y + 12f, paint)
                canvas.drawText(line.paidText, (WIDTH - PAD).toFloat(), y + 12f, paint)
                paint.textAlign = Paint.Align.LEFT
                paint.isFakeBoldText = false
                y += 96f
            }
        }

        // ---------- AA 结算（旅行账本） ----------
        if (data.aaTitle != null) {
            y += 40f
            paint.color = p.title
            paint.textSize = 44f
            paint.isFakeBoldText = true
            canvas.drawText(data.aaTitle!!, PAD.toFloat(), y, paint)
            paint.isFakeBoldText = false
            y += 60f

            if (data.aaLines.isEmpty()) {
                paint.color = p.sub
                paint.textSize = 36f
                canvas.drawText(data.aaEmptyText ?: "", PAD.toFloat(), y + 20f, paint)
                y += 90f
            } else {
                data.aaLines.forEach { line ->
                    drawMemberChip(canvas, paint, p, line.name, line.color, PAD.toFloat(), y + 6f)
                    val netColor = when (line.netType) {
                        1 -> p.income
                        2 -> p.expense
                        else -> p.sub
                    }
                    paint.color = netColor
                    paint.textSize = 40f
                    paint.isFakeBoldText = true
                    paint.textAlign = Paint.Align.RIGHT
                    canvas.drawText(line.netText, (WIDTH - PAD).toFloat(), y + 12f, paint)
                    paint.textAlign = Paint.Align.LEFT
                    paint.isFakeBoldText = false
                    y += 88f
                }

                if (data.transfers.isNotEmpty()) {
                    y += 20f
                    val boxTop = y
                    val boxH = 30f + data.transfers.size * 62f
                    paint.color = p.card
                    canvas.drawRoundRect(
                        PAD.toFloat(), boxTop, (WIDTH - PAD).toFloat(), boxTop + boxH, 24f, 24f, paint,
                    )
                    var ty = boxTop + 52f
                    data.transfers.forEach { t ->
                        paint.color = p.body
                        paint.textSize = 36f
                        canvas.drawText(t.text, (PAD + 40).toFloat(), ty, paint)
                        ty += 62f
                    }
                    y = boxTop + boxH + 20f
                } else {
                    paint.color = p.sub
                    paint.textSize = 36f
                    canvas.drawText(data.aaSettledText ?: data.aaEmptyText ?: "", PAD.toFloat(), y + 20f, paint)
                    y += 90f
                }
            }
        }

        // ---------- 底部 ----------
        paint.color = p.sub
        paint.textSize = 34f
        paint.textAlign = Paint.Align.CENTER
        canvas.drawText("由 拉拉记账 生成 · 纯本地离线", WIDTH / 2f, height - PAD / 1.5f, paint)
        paint.textAlign = Paint.Align.LEFT

        return bitmap
    }

    private fun drawStatCell(
        canvas: Canvas,
        paint: Paint,
        p: Palette,
        x: Float,
        y: Float,
        label: String,
        value: String,
        valueColor: Int,
    ) {
        paint.color = p.sub
        paint.textSize = 36f
        paint.isFakeBoldText = false
        canvas.drawText(label, x, y, paint)

        paint.color = valueColor
        paint.textSize = 56f
        paint.isFakeBoldText = true
        canvas.drawText(value, x, y + 74f, paint)
        paint.isFakeBoldText = false
    }

    /** 汇总图同款成员小胶囊（色点 + 名字） */
    private fun drawMemberChip(
        canvas: Canvas,
        paint: Paint,
        p: Palette,
        name: String,
        color: Int,
        left: Float,
        textBaseline: Float,
    ) {
        val chipH = 46f
        paint.textSize = 30f
        val textW = paint.measureText(name)
        val dotD = 14f
        val padH = 18f
        val chipW = padH + dotD + 10f + textW + padH
        val top = textBaseline - 33f
        val cy = top + chipH / 2f

        paint.color = color
        paint.alpha = (p.chipAlpha * 255).toInt()
        canvas.drawRoundRect(left, top, left + chipW, top + chipH, chipH / 2f, chipH / 2f, paint)
        paint.alpha = 255
        canvas.drawCircle(left + padH + dotD / 2f, cy, dotD / 2f, paint)
        canvas.drawText(name, left + padH + dotD + 10f, cy - (paint.descent() + paint.ascent()) / 2f, paint)
    }

    private fun ellipsize(paint: Paint, text: String, maxWidth: Float): String {
        if (paint.measureText(text) <= maxWidth) return text
        var out = text
        while (out.isNotEmpty() && paint.measureText("$out…") > maxWidth) {
            out = out.dropLast(1)
        }
        return "$out…"
    }

    private const val COLOR_WHITE = 0xFFFFFFFF.toInt()
}
