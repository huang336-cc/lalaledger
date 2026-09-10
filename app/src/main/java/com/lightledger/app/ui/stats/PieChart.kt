package com.lightledger.app.ui.stats

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import java.util.Locale
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.min
import kotlin.math.sin
import kotlin.math.sqrt

/** 环形描边宽度 */
private val PieStroke = 20.dp

/** 环中径（固定值，给两侧引导线标签留出空间） */
private val PieRadius = 74.dp

/** 同侧标签的最小垂直间距（两行小字高 + 余量） */
private val LabelMinGap = 27.dp

/** 引导线标签：分类名（主，稍大且加粗） */
private val LabelNameSize = 10.sp

/** 引导线标签：百分比（次，明显小一号 + 常规字重 + 更淡，与分类名拉开主次） */
private val LabelPctSize = 8.sp

/** 引导线标签行高（容纳小一号的百分比后仍能对齐） */
private val LabelLineHeight = 13.sp

/** 百分比相对分类名的淡化程度（叠加在标签整体弱化之上） */
private const val LabelPctAlpha = 0.62f

/**
 * 最小扇区角度：占比极小的分类（如 0.5% ≈ 1.8°）在环上只有几个 dp 宽，
 * 既看不见也点不中。绘制与点击共用同一套"布局角度"，小于该值的扇区按该角度
 * 呈现（总额超过 360° 时整体等比缩放），保证"看到什么就能点到什么"。
 */
private const val MinSliceDeg = 6f

/** 扇区之间的留缝角度（轻盈感） */
private const val SliceGapDeg = 1.5f

/** 点击命中的径向容差（手指友好） */
private val HitTolerance = 12.dp

/**
 * 分类占比环形图（交互增强版）。
 * - 环心显示总额；选中扇区时由外部切换为该分类的名称/占比/金额
 * - 每个可见扇区外侧画折线引导线 + 「分类名 + 百分比」两行小字，
 *   左右分侧、垂直防重叠（下压 + 上收两遍扫描）；引导线从环外缘
 *   沿径向出发（绝不穿环），颜色跟随所属分类，标签可点击选中扇区
 * - 选中扇区外扩加粗高亮，其余扇区与标签弱化；进场有展开动画
 * - 点击扇区或标签回调 [onSliceClick]（"其他"合并段下标为 [OTHER_INDEX]）；
 *   点击环心回调 [onCenterClick]（用于清除选中）
 */
@Composable
fun CategoryPieChart(
    slices: List<PieSlice>,
    centerTitle: String,
    centerValue: String,
    modifier: Modifier = Modifier,
    height: Dp = 240.dp,
    maxSlices: Int = 6,
    otherLabel: String = "其他",
    selectedSlice: Int? = null,
    onSliceClick: ((Int) -> Unit)? = null,
    onCenterClick: (() -> Unit)? = null,
) {
    val trackColor = MaterialTheme.colorScheme.surfaceVariant
    val labelColor = MaterialTheme.colorScheme.onSurfaceVariant
    val textMeasurer = rememberTextMeasurer()

    // ---------- 扇区段（绘制 + 点击命中共用，含最小角度保障） ----------
    val segments = remember(slices, maxSlices, trackColor) {
        val shown = slices.take(maxSlices)
        val restRatio = slices.drop(maxSlices).sumOf { it.ratio.toDouble() }.toFloat()
        val entries = mutableListOf<Pair<Int, Float>>() // 下标 to 占比
        shown.forEachIndexed { i, s -> entries += i to s.ratio }
        if (restRatio > 0f) entries += OTHER_INDEX to restRatio

        // 真实角度 -> 布局角度：过小扇区抬到 [MinSliceDeg]，总量超 360° 时等比缩放
        val raw = entries.map { (_, ratio) -> ratio * 360f }
        val ensured = raw.map { maxOf(it, MinSliceDeg) }
        val sum = ensured.sum()
        val scale = if (sum > 360f) 360f / sum else 1f

        var start = -90f
        entries.mapIndexed { i, (index, ratio) ->
            val sweep = ensured[i] * scale
            val seg = PieSegment(
                index = index,
                startAngle = start,
                sweep = sweep,
                color = if (index == OTHER_INDEX) trackColor else shown[index].color,
                ratio = ratio,
            )
            start += sweep
            seg
        }
    }

    // ---------- 进场展开动画（仅数据变化时重放） ----------
    val progress = remember { Animatable(0f) }
    LaunchedEffect(segments) {
        progress.snapTo(0f)
        progress.animateTo(1f, tween(550, easing = FastOutSlowInEasing))
    }

    // ---------- 引导线标签：每个可见扇区「分类名 + 百分比」两行，左右分侧防重叠 ----------
    val labels = remember(segments, slices, otherLabel, labelColor, textMeasurer) {
        val raws = segments.map { seg ->
            val name = when {
                seg.index == OTHER_INDEX -> otherLabel
                else -> slices.getOrNull(seg.index)?.label ?: "—"
            }.let { if (it.length > 6) it.take(5) + "…" else it }
            LabelSource(seg, name, String.format(Locale.US, "%.1f%%", seg.ratio * 100))
        }
        val list = raws.map { raw ->
            // 分类名与百分比拆成两段样式：名称主、百分比次（小一号 + 常规字重 + 更淡），
            // 一眼分主次，不再像两行等大的小字。
            val text = buildAnnotatedString {
                withStyle(
                    SpanStyle(fontSize = LabelNameSize, fontWeight = FontWeight.Medium)
                ) { append(raw.name) }
                append("\n")
                withStyle(
                    SpanStyle(
                        fontSize = LabelPctSize,
                        fontWeight = FontWeight.Normal,
                        color = labelColor.copy(alpha = LabelPctAlpha),
                    )
                ) { append(raw.pct) }
            }
            val layout = textMeasurer.measure(
                text,
                TextStyle(
                    fontSize = LabelNameSize,
                    lineHeight = LabelLineHeight,
                    textAlign = TextAlign.Left,
                ),
            )
            // 扇区中线的 y 分量决定标签在左/右侧及纵向初始位置
            val mid = raw.seg.startAngle + raw.seg.sweep / 2f
            PieLabel(
                seg = raw.seg,
                layout = layout,
                side = if (cos(Math.toRadians(mid.toDouble())) >= 0) 1 else -1,
                relY = (sin(Math.toRadians(mid.toDouble())) * (PieRadius.value + 24f)).toFloat(),
            )
        }
        // 每侧两遍扫描：先下压避免重叠，再上收避免底部溢出，最后夹进画布
        val gap = LabelMinGap.value
        val maxAbs = height.value / 2f - 16f
        listOf(1, -1).forEach { side ->
            val group = list.filter { it.side == side }.sortedBy { it.relY }
            var prev = -Float.MAX_VALUE
            group.forEach { l ->
                l.relY = maxOf(l.relY, prev + gap)
                prev = l.relY
            }
            var next = Float.MAX_VALUE
            group.asReversed().forEach { l ->
                l.relY = minOf(l.relY, next - gap)
                next = l.relY
            }
            group.forEach { l -> l.relY = l.relY.coerceIn(-maxAbs, maxAbs) }
        }
        list
    }

    Box(modifier = modifier.height(height), contentAlignment = Alignment.Center) {
        Canvas(
            Modifier
                .fillMaxSize()
                .then(
                    if (onSliceClick != null) {
                        Modifier.pointerInput(segments, labels) {
                            detectTapGestures { pos ->
                                val w = this.size.width.toFloat()
                                val h = this.size.height.toFloat()
                                val cx = w / 2f
                                val cy = h / 2f
                                // 1) 标签命中：文字包围盒 + 少量外扩（标签在环外，优先判定）
                                val padX = 6.dp.toPx()
                                val padY = 6.dp.toPx()
                                for (l in labels) {
                                    val textW = l.layout.size.width.toFloat()
                                    val textH = l.layout.size.height.toFloat()
                                    val labelY = cy + l.relY.dp.toPx()
                                    val left = if (l.side > 0) w - 4.dp.toPx() - textW else 4.dp.toPx()
                                    if (pos.x >= left - padX && pos.x <= left + textW + padX &&
                                        pos.y >= labelY - textH / 2f - padY &&
                                        pos.y <= labelY + textH / 2f + padY
                                    ) {
                                        onSliceClick(l.seg.index)
                                        return@detectTapGestures
                                    }
                                }
                                // 2) 环带命中
                                val strokePx = PieStroke.toPx()
                                val midR = PieRadius.toPx()
                                val dx = pos.x - cx
                                val dy = pos.y - cy
                                val dist = sqrt(dx * dx + dy * dy)
                                val tol = HitTolerance.toPx()
                                if (dist < midR - strokePx / 2f - tol || dist > midR + strokePx / 2f + tol) {
                                    return@detectTapGestures
                                }
                                // 与 drawArc 同坐标系：y 向下、顺时针为正
                                var angle = Math.toDegrees(atan2(dy.toDouble(), dx.toDouble()))
                                if (angle < 0) angle += 360.0
                                for (seg in segments) {
                                    val rel = ((angle - seg.startAngle) % 360.0 + 360.0) % 360.0
                                    if (rel <= seg.sweep) {
                                        onSliceClick(seg.index)
                                        return@detectTapGestures
                                    }
                                }
                            }
                        }
                    } else {
                        Modifier
                    }
                ),
        ) {
            val stroke = PieStroke.toPx()
            val cx = this.size.width / 2f
            val cy = this.size.height / 2f
            val midR = PieRadius.toPx()
            val sweepScale = progress.value
            val outer = midR + stroke / 2f

            if (segments.isEmpty()) {
                drawArc(
                    color = trackColor,
                    startAngle = 0f,
                    sweepAngle = 360f,
                    useCenter = false,
                    topLeft = Offset(cx - midR, cy - midR),
                    size = Size(midR * 2f, midR * 2f),
                    style = Stroke(width = stroke),
                )
                return@Canvas
            }

            // 非选中扇区先画（选中态时弱化），选中段最后画在最上层
            val ordered = segments.sortedBy { it.index == selectedSlice }
            ordered.forEach { seg ->
                val isSel = seg.index == selectedSlice
                val r = if (isSel) midR + 3.dp.toPx() else midR
                val sw = if (isSel) stroke + 3.dp.toPx() else stroke
                val alpha = if (selectedSlice == null || isSel) 1f else 0.4f
                drawArc(
                    color = seg.color.copy(alpha = alpha),
                    startAngle = seg.startAngle,
                    // 留缝隙更轻盈；最小角度保障下至少保留 3° 可见
                    sweepAngle = (seg.sweep - SliceGapDeg).coerceAtLeast(3f) * sweepScale,
                    useCenter = false,
                    topLeft = Offset(cx - r, cy - r),
                    size = Size(r * 2f, r * 2f),
                    style = Stroke(width = sw),
                )
            }

            // 引导线：p0→p1 沿扇区中线纯径向（绝不穿环），p1→elbow 斜线到文字边缘；
            // 线色跟随分类颜色，标签↔扇区一眼对应
            labels.forEach { l ->
                val isDimmed = selectedSlice != null && l.seg.index != selectedSlice
                val alpha = if (isDimmed) 0.4f else 1f
                val theta = Math.toRadians((l.seg.startAngle + l.seg.sweep / 2f).toDouble())
                val dirX = cos(theta).toFloat()
                val dirY = sin(theta).toFloat()
                val p0 = Offset(cx + dirX * (outer + 2.dp.toPx()), cy + dirY * (outer + 2.dp.toPx()))
                val p1 = Offset(cx + dirX * (outer + 12.dp.toPx()), cy + dirY * (outer + 12.dp.toPx()))
                val textW = l.layout.size.width.toFloat()
                val textH = l.layout.size.height.toFloat()
                val labelY = cy + l.relY.dp.toPx()
                val edgeX = if (l.side > 0) this.size.width - 4.dp.toPx() else 4.dp.toPx()
                val labelX = if (l.side > 0) edgeX - textW else edgeX
                val elbow = Offset(
                    if (l.side > 0) labelX - 6.dp.toPx() else labelX + textW + 6.dp.toPx(),
                    labelY,
                )
                val segLine = l.seg.color.copy(alpha = alpha)
                drawLine(segLine, p0, p1, strokeWidth = 1.dp.toPx())
                drawLine(segLine, p1, elbow, strokeWidth = 1.dp.toPx())
                drawText(
                    l.layout,
                    // 弱化走 alpha：选中他人时整体变淡，百分比自身的淡色逐级叠加仍保持更次一级
                    color = labelColor,
                    alpha = if (isDimmed) 0.45f else 1f,
                    topLeft = Offset(labelX, labelY - textH / 2f),
                )
            }
        }

        // 环心汇总 / 选中分类详情（点击清除选中）
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = if (onCenterClick != null) {
                Modifier.clickable { onCenterClick.invoke() }
            } else {
                Modifier
            },
        ) {
            Text(
                text = centerTitle,
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
            )
            Text(
                text = centerValue,
                style = MaterialTheme.typography.titleMedium,
                textAlign = TextAlign.Center,
            )
        }
    }
}

data class PieSlice(
    val label: String,
    val ratio: Float,
    val color: Color,
)

/** "其他"合并段的下标标记（不对应任何具体分类） */
const val OTHER_INDEX = Int.MAX_VALUE

/** 内部扇区段：绘制与点击命中检测共用 */
private data class PieSegment(
    val index: Int,
    val startAngle: Float,
    val sweep: Float,
    val color: Color,
    val ratio: Float,
)

/** 标签文案来源（测量前的中间结构） */
private class LabelSource(
    val seg: PieSegment,
    val name: String,
    val pct: String,
)

/** 引导线标签：测量布局 + 侧别 + 防重叠后的纵向位置（相对圆心，单位 dp 值） */
private class PieLabel(
    val seg: PieSegment,
    val layout: TextLayoutResult,
    val side: Int,
    relY: Float,
) {
    var relY: Float = relY
}
