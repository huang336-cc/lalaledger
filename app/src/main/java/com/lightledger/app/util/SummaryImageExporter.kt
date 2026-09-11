package com.lightledger.app.util

import android.content.ContentValues
import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Path
import android.graphics.Rect
import android.graphics.RectF
import android.net.Uri
import android.provider.MediaStore
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlin.math.max
import kotlin.math.min

/**
 * 账单汇总长图导出：
 * - android.graphics Canvas 手绘 1080px 宽 PNG 长图（汇总卡 + 账单清单 + 小票缩略图）
 * - 配色跟随深浅主题（isDark）
 * - 通过 MediaStore 保存到相册 Pictures/拉了记账（Android 10+ 免存储权限）
 * - 使用 RGB_565 与 100 条上限控制长图内存占用
 */
object SummaryImageExporter {

    /** 单行账单的渲染数据 */
    data class Row(
        val title: String,
        val categoryName: String,
        val categoryColor: Int,
        val amountText: String,
        val isExpense: Boolean,
        val timeText: String,
        val thumbnailPath: String?,
        /** 旅行账本成员归属；null = 本人（不显示胶囊） */
        val memberName: String? = null,
        val memberColor: Int = 0xFF5BB3A2.toInt(),
        /** 垫付成员；与归属同名或为 null 时不重复显示 */
        val payerName: String? = null,
        val payerColor: Int = 0xFF5BB3A2.toInt(),
    )

    /** 单条账单导出的渲染数据 */
    data class SingleBill(
        val bookName: String,
        val categoryName: String,
        val categoryColor: Int,
        val amountText: String,
        val isExpense: Boolean,
        val memberName: String?,
        val memberColor: Int,
        val payerName: String? = null,
        val payerColor: Int = 0xFF5BB3A2.toInt(),
        val timeText: String,
        val locationText: String?,
        val noteText: String?,
        val thumbnailPath: String?,
    )

    /** 汇总数据 */
    data class Summary(
        val bookName: String,
        val count: Int,
        val expenseText: String,
        val incomeText: String,
        val netText: String,
    )

    internal data class Palette(
        val background: Int,
        val card: Int,
        val title: Int,
        val body: Int,
        val sub: Int,
        val divider: Int,
        val accent: Int,
        val expense: Int,
        val income: Int,
        /** 成员胶囊底色透明度（深色主题需要更高才看得清） */
        val chipAlpha: Float,
    )

    internal val lightPalette = Palette(
        background = 0xFFF7F5F0.toInt(),
        card = 0xFFFFFFFF.toInt(),
        title = 0xFF2B2B2B.toInt(),
        body = 0xFF3A3A3A.toInt(),
        sub = 0xFF9A968D.toInt(),
        divider = 0xFFE8E4DC.toInt(),
        accent = 0xFF5BB3A2.toInt(),
        expense = 0xFFD95E5E.toInt(),
        income = 0xFF3E9B74.toInt(),
        chipAlpha = 0.14f,
    )

    internal val darkPalette = Palette(
        background = 0xFF151A23.toInt(),
        card = 0xFF1F2733.toInt(),
        title = 0xFFF2F4F7.toInt(),
        body = 0xFFDDE2EA.toInt(),
        sub = 0xFF8B94A3.toInt(),
        divider = 0xFF2C3542.toInt(),
        accent = 0xFF7BC8B8.toInt(),
        expense = 0xFFE38181.toInt(),
        income = 0xFF63BE97.toInt(),
        chipAlpha = 0.28f,
    )

    private const val WIDTH = 1080
    private const val PAD = 60
    private const val ROW_H = 150
    private const val MAX_ROWS = 100
    private const val SAVE_DIR = "Pictures/拉了记账"

    /**
     * 生成并保存汇总长图，成功返回相册 Uri，失败返回 null。
     */
    suspend fun export(
        context: Context,
        summary: Summary,
        rows: List<Row>,
        isDark: Boolean,
    ): Uri? = withContext(Dispatchers.IO) {
        runCatching {
            val palette = if (isDark) darkPalette else lightPalette
            val displayRows = rows.take(MAX_ROWS)
            val bitmap = drawBitmap(summary, displayRows, palette, rows.size > MAX_ROWS)
            saveToGallery(context, bitmap)
        }.getOrElse { it.printStackTrace(); null }
    }

    /**
     * 生成并保存单条账单分享图，成功返回相册 Uri，失败返回 null。
     */
    suspend fun exportSingle(
        context: Context,
        bill: SingleBill,
        isDark: Boolean,
    ): Uri? = withContext(Dispatchers.IO) {
        runCatching {
            val palette = if (isDark) darkPalette else lightPalette
            val bitmap = drawSingleBitmap(bill, palette)
            saveToGallery(context, bitmap, prefix = "账单")
        }.getOrElse { it.printStackTrace(); null }
    }

    // ---------- 绘制 ----------

    private fun drawBitmap(
        summary: Summary,
        rows: List<Row>,
        p: Palette,
        truncated: Boolean,
    ): Bitmap {
        val headH = PAD + 210 + 360 + 110
        val listH = rows.size * ROW_H
        val footH = 150 + PAD
        val height = headH + listH + footH

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
        canvas.drawText("账单汇总", PAD.toFloat(), PAD + 72f, paint)

        paint.isFakeBoldText = false
        paint.color = p.sub
        paint.textSize = 38f
        val now = SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.CHINA).format(Date())
        canvas.drawText("${summary.bookName} · 生成于 $now", PAD.toFloat(), PAD + 140f, paint)

        // ---------- 汇总卡（2 x 2） ----------
        val cardTop = PAD + 200f
        val cardLeft = PAD.toFloat()
        val cardRight = (WIDTH - PAD).toFloat()
        val cardH = 330f
        paint.color = p.card
        canvas.drawRoundRect(cardLeft, cardTop, cardRight, cardTop + cardH, 40f, 40f, paint)

        val cellW = (cardRight - cardLeft - PAD) / 2f
        drawStatCell(canvas, paint, p, cardLeft + 40, cardTop + 78, "账单条目", "${summary.count} 笔", p.title)
        drawStatCell(canvas, paint, p, cardLeft + 40 + cellW + 20, cardTop + 78, "总支出", "¥${summary.expenseText}", p.expense)
        drawStatCell(canvas, paint, p, cardLeft + 40, cardTop + 208, "总收入", "¥${summary.incomeText}", p.income)
        drawStatCell(canvas, paint, p, cardLeft + 40 + cellW + 20, cardTop + 208, "净花费", "¥${summary.netText}", p.accent)

        // ---------- 清单标题 ----------
        val listTitleY = cardTop + cardH + 90f
        paint.color = p.title
        paint.textSize = 44f
        paint.isFakeBoldText = true
        val listTitle = if (truncated) {
            "账单清单（前 $MAX_ROWS 条，共 ${summary.count} 条）"
        } else {
            "账单清单（${summary.count}）"
        }
        canvas.drawText(listTitle, PAD.toFloat(), listTitleY, paint)
        paint.isFakeBoldText = false

        // ---------- 账单行 ----------
        var y = listTitleY + 60f
        rows.forEach { row ->
            drawRow(canvas, paint, p, row, y)
            y += ROW_H
        }

        // ---------- 底部 ----------
        paint.color = p.sub
        paint.textSize = 34f
        paint.textAlign = Paint.Align.CENTER
        canvas.drawText("由 拉了记账 生成 · 纯本地离线", WIDTH / 2f, height - PAD / 1.5f, paint)
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

    private fun drawRow(canvas: Canvas, paint: Paint, p: Palette, row: Row, top: Float) {
        val centerX = (PAD + 44).toFloat()
        val centerY = top + ROW_H / 2f

        // 缩略图在右侧（金额左），先画文字区宽度预留
        val thumbSize = 110f
        val hasThumb = row.thumbnailPath != null
        val amountRight = (WIDTH - PAD).toFloat()

        // 分类圆 + 白色首字
        paint.color = row.categoryColor
        canvas.drawCircle(centerX, centerY, 44f, paint)
        paint.color = Color.WHITE
        paint.textSize = 40f
        paint.isFakeBoldText = true
        paint.textAlign = Paint.Align.CENTER
        val initial = row.categoryName.firstOrNull()?.toString() ?: "记"
        val baseline = centerY - (paint.descent() + paint.ascent()) / 2f
        canvas.drawText(initial, centerX, baseline, paint)
        paint.textAlign = Paint.Align.LEFT
        paint.isFakeBoldText = false

        // 标题 + 时间
        val textLeft = (PAD + 110).toFloat()
        val textRight = if (hasThumb) amountRight - thumbSize - 30f else amountRight
        paint.color = p.body
        paint.textSize = 40f
        canvas.drawText(
            ellipsize(paint, row.title, textRight - textLeft - 170f),
            textLeft, top + 62f, paint,
        )
        paint.color = p.sub
        paint.textSize = 32f
        canvas.drawText(row.timeText, textLeft, top + 112f, paint)
        // 成员胶囊（时间右侧）；垫付人与归属不同时并排再画一颗
        var chipLeft = -1f
        row.memberName?.takeIf { it.isNotBlank() }?.let { name ->
            val timeW = paint.measureText(row.timeText)
            chipLeft = textLeft + timeW + 26f
            drawMemberChip(canvas, paint, p, name, row.memberColor, chipLeft, top + 112f)
        }
        row.payerName?.takeIf { it.isNotBlank() && it != row.memberName }?.let { name ->
            val base = if (chipLeft >= 0f) chipLeft + chipWidth(paint, row.memberName!!) + 10f
            else textLeft
            drawMemberChip(canvas, paint, p, name, row.payerColor, base, top + 112f)
        }

        // 缩略图（圆角）
        if (hasThumb) {
            val thumb = decodeThumbnail(row.thumbnailPath!!, 220)
            if (thumb != null) {
                val tLeft = amountRight - 150f - thumbSize
                val tTop = centerY - thumbSize / 2f
                val rect = RectF(tLeft, tTop, tLeft + thumbSize, tTop + thumbSize)
                val path = Path().apply { addRoundRect(rect, 20f, 20f, Path.Direction.CW) }
                canvas.save()
                canvas.clipPath(path)
                val src = scaleCropRect(thumb)
                canvas.drawBitmap(thumb, src, rect, paint)
                canvas.restore()
                thumb.recycle()
            }
        }

        // 金额
        paint.color = if (row.isExpense) p.expense else p.income
        paint.textSize = 44f
        paint.isFakeBoldText = true
        paint.textAlign = Paint.Align.RIGHT
        val amountBaseline = centerY - (paint.descent() + paint.ascent()) / 2f
        canvas.drawText(row.amountText, amountRight, amountBaseline, paint)
        paint.textAlign = Paint.Align.LEFT
        paint.isFakeBoldText = false

        // 分隔线
        paint.color = p.divider
        paint.strokeWidth = 2f
        canvas.drawLine(
            textLeft.toFloat(), top + ROW_H.toFloat(),
            (WIDTH - PAD).toFloat(), top + ROW_H.toFloat(), paint,
        )
    }

    private fun ellipsize(paint: Paint, text: String, maxWidth: Float): String {
        if (paint.measureText(text) <= maxWidth) return text
        var out = text
        while (out.isNotEmpty() && paint.measureText("$out…") > maxWidth) {
            out = out.dropLast(1)
        }
        return "$out…"
    }

    /** 与 drawMemberChip 相同布局的胶囊宽度（用于并排排版） */
    private fun chipWidth(paint: Paint, name: String): Float {
        paint.textSize = 30f
        return 18f + 14f + 10f + paint.measureText(name) + 18f
    }

    /** 汇总长图行内的成员小胶囊（色点 + 名字），baseline 对齐时间文字 */
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

        // 胶囊底（主题适配透明度）
        paint.color = color
        paint.alpha = (p.chipAlpha * 255).toInt()
        canvas.drawRoundRect(left, top, left + chipW, top + chipH, chipH / 2f, chipH / 2f, paint)
        paint.alpha = 255
        // 色点
        canvas.drawCircle(left + padH + dotD / 2f, cy, dotD / 2f, paint)
        // 名字
        canvas.drawText(name, left + padH + dotD + 10f, cy - (paint.descent() + paint.ascent()) / 2f, paint)
    }

    /** 单条账单分享图：金额主卡 + 信息行 + 可选小票图片 */
    private fun drawSingleBitmap(bill: SingleBill, p: Palette): Bitmap {
        val headH = PAD + 72 + 68 + 36

        // 信息行
        val infoRows = mutableListOf<Triple<String, String, Int?>>()
        infoRows.add(Triple("分类", bill.categoryName, bill.categoryColor))
        bill.memberName?.takeIf { it.isNotBlank() }?.let { infoRows.add(Triple("成员", it, bill.memberColor)) }
        bill.payerName?.takeIf { it.isNotBlank() && it != bill.memberName }
            ?.let { infoRows.add(Triple("垫付", it, bill.payerColor)) }
        infoRows.add(Triple("时间", bill.timeText, null))
        bill.locationText?.takeIf { it.isNotBlank() }?.let { infoRows.add(Triple("地点", it, null)) }
        bill.noteText?.takeIf { it.isNotBlank() }?.let { infoRows.add(Triple("备注", it, null)) }

        val thumb = bill.thumbnailPath?.let { decodeThumbnail(it, 900) }
        val imgAreaW = (WIDTH - PAD * 2 - 80).toFloat()
        val imgH = thumb?.let {
            val scale = min(imgAreaW / it.width, 900f / it.height)
            it.height * scale + 46f
        } ?: 0f

        val amountH = 250f
        val rowH = 84f
        val cardTop = headH.toFloat()
        val cardH = 46f + amountH + 26f + infoRows.size * rowH + 24f + imgH + 40f
        val height = (cardTop + cardH + 150 + PAD).toInt()

        val bitmap = Bitmap.createBitmap(WIDTH, height, Bitmap.Config.RGB_565)
        val canvas = Canvas(bitmap)
        val paint = Paint(Paint.ANTI_ALIAS_FLAG)

        // 背景
        paint.color = p.background
        canvas.drawRect(0f, 0f, WIDTH.toFloat(), height.toFloat(), paint)

        // 头部
        paint.color = p.title
        paint.textSize = 72f
        paint.isFakeBoldText = true
        canvas.drawText("账单", PAD.toFloat(), PAD + 72f, paint)
        paint.isFakeBoldText = false
        paint.color = p.sub
        paint.textSize = 38f
        val now = SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.CHINA).format(Date())
        canvas.drawText("${bill.bookName} · 生成于 $now", PAD.toFloat(), PAD + 140f, paint)

        // 主卡片
        val cardLeft = PAD.toFloat()
        val cardRight = (WIDTH - PAD).toFloat()
        paint.color = p.card
        canvas.drawRoundRect(cardLeft, cardTop, cardRight, cardTop + cardH, 40f, 40f, paint)

        // 金额（居中大字）
        paint.color = if (bill.isExpense) p.expense else p.income
        paint.textSize = 100f
        paint.isFakeBoldText = true
        paint.textAlign = Paint.Align.CENTER
        canvas.drawText(bill.amountText, WIDTH / 2f, cardTop + 60f + 100f, paint)
        paint.isFakeBoldText = false
        paint.color = p.sub
        paint.textSize = 36f
        canvas.drawText(
            if (bill.isExpense) "支出" else "收入",
            WIDTH / 2f, cardTop + 60f + 100f + 62f, paint,
        )
        paint.textAlign = Paint.Align.LEFT

        // 信息行
        var y = cardTop + 46f + amountH + 26f + rowH - 20f
        infoRows.forEach { (label, value, valueColor) ->
            paint.color = p.sub
            paint.textSize = 34f
            canvas.drawText(label, cardLeft + 44f, y, paint)
            paint.color = valueColor ?: p.body
            paint.textSize = 36f
            canvas.drawText(
                ellipsize(paint, value, cardRight - (cardLeft + 200f) - 44f),
                cardLeft + 200f, y, paint,
            )
            y += rowH
        }

        // 小票图片（圆角，居中，等比缩放）
        if (thumb != null) {
            val scale = min(imgAreaW / thumb.width, 900f / thumb.height)
            val dw = thumb.width * scale
            val dh = thumb.height * scale
            val dLeft = (WIDTH - dw) / 2f
            val dTop = y - rowH + 20f
            val rect = RectF(dLeft, dTop, dLeft + dw, dTop + dh)
            val path = Path().apply { addRoundRect(rect, 24f, 24f, Path.Direction.CW) }
            canvas.save()
            canvas.clipPath(path)
            canvas.drawBitmap(thumb, null, rect, paint)
            canvas.restore()
            thumb.recycle()
        }

        // 底部
        paint.color = p.sub
        paint.textSize = 34f
        paint.textAlign = Paint.Align.CENTER
        canvas.drawText("由 拉了记账 生成 · 纯本地离线", WIDTH / 2f, height - PAD / 1.5f, paint)
        paint.textAlign = Paint.Align.LEFT

        return bitmap
    }

    private fun scaleCropRect(bitmap: Bitmap): Rect {
        // 源图按中心裁剪成正方形，避免拉伸
        val w = bitmap.width
        val h = bitmap.height
        val side = min(w, h)
        val left = (w - side) / 2
        val top = (h - side) / 2
        return Rect(left, top, left + side, top + side)
    }

    private fun decodeThumbnail(path: String, targetPx: Int): Bitmap? {
        if (path.isBlank()) return null
        return runCatching {
            val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
            BitmapFactory.decodeFile(path, bounds)
            var sample = 1
            while (max(bounds.outWidth, bounds.outHeight) / sample > targetPx * 2) {
                sample *= 2
            }
            BitmapFactory.decodeFile(
                path,
                BitmapFactory.Options().apply {
                    inSampleSize = sample
                    inPreferredConfig = Bitmap.Config.RGB_565
                },
            )
        }.getOrNull()
    }

    // ---------- 保存到相册 ----------

    internal fun saveToGallery(context: Context, bitmap: Bitmap, prefix: String = "汇总"): Uri? {
        val resolver = context.contentResolver
        val name = "拉了记账_${prefix}_${SimpleDateFormat("yyyyMMdd_HHmmss", Locale.CHINA).format(Date())}.png"
        val values = ContentValues().apply {
            put(MediaStore.Images.Media.DISPLAY_NAME, name)
            put(MediaStore.Images.Media.MIME_TYPE, "image/png")
            put(MediaStore.Images.Media.RELATIVE_PATH, SAVE_DIR)
            put(MediaStore.Images.Media.IS_PENDING, 1)
        }
        val uri = resolver.insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, values)
            ?: return null
        try {
            resolver.openOutputStream(uri)?.use { out ->
                if (!bitmap.compress(Bitmap.CompressFormat.PNG, 100, out)) {
                    error("PNG compress failed")
                }
            } ?: error("openOutputStream null")
            values.clear()
            values.put(MediaStore.Images.Media.IS_PENDING, 0)
            resolver.update(uri, values, null, null)
            return uri
        } catch (e: Exception) {
            e.printStackTrace()
            runCatching { resolver.delete(uri, null, null) }
            return null
        } finally {
            bitmap.recycle()
        }
    }
}
