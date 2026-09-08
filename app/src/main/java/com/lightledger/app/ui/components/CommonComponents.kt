package com.lightledger.app.ui.components

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Check
import androidx.compose.material.icons.outlined.ReceiptLong
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import coil.compose.AsyncImage
import com.lightledger.app.domain.model.IconLibrary
import com.lightledger.app.domain.model.TransactionType
import com.lightledger.app.ui.theme.SemanticTheme
import com.lightledger.app.util.DateUtils
import com.lightledger.app.util.MoneyFormat

/** 金额文本：自动带正负号与语义色 */
@Composable
fun MoneyText(
    fen: Long,
    type: TransactionType,
    style: androidx.compose.ui.text.TextStyle = MaterialTheme.typography.titleMedium,
    signed: Boolean = true,
    modifier: Modifier = Modifier,
) {
    val color = if (type == TransactionType.EXPENSE) {
        SemanticTheme.colors.expense
    } else {
        SemanticTheme.colors.income
    }
    val sign = when {
        !signed -> ""
        type == TransactionType.EXPENSE -> "-"
        else -> "+"
    }
    Text(
        text = "$sign¥${MoneyFormat.fenToString(fen)}",
        style = style,
        color = color,
        modifier = modifier,
    )
}

/** 圆形分类图标 */
@Composable
fun CategoryIcon(
    iconKey: String,
    color: Color,
    size: Int = 44,
    modifier: Modifier = Modifier,
    tint: Color = Color.White,
) {
    Box(
        modifier = modifier
            .size(size.dp)
            .background(color, CircleShape),
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            imageVector = IconLibrary.of(iconKey),
            contentDescription = null,
            tint = tint,
            modifier = Modifier.size((size * 0.52).dp),
        )
    }
}

/**
 * 成员标签胶囊：色点 + 名字，圆角 50、深浅色自适应。
 * - 展示态（默认）：浅色底 = 成员色 12% 透明度背景，深色主题自动降透明度
 * - 选择态（selected=true）：成员色实心底 + 白字，用于记账页成员选择
 */
@Composable
fun MemberChip(
    name: String,
    color: Color,
    modifier: Modifier = Modifier,
    selected: Boolean = false,
    onClick: (() -> Unit)? = null,
) {
    val bg = if (selected) color
    else color.copy(alpha = if (MaterialTheme.colorScheme.surface.luminance() > 0.5f) 0.13f else 0.22f)
    val contentColor = if (selected) Color.White else color
    Row(
        modifier = modifier
            .clip(RoundedCornerShape(50))
            .background(bg)
            .then(
                if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier
            )
            .padding(horizontal = 9.dp, vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            modifier = Modifier
                .size(6.dp)
                .background(
                    if (selected) Color.White.copy(alpha = 0.9f) else color,
                    CircleShape,
                ),
        )
        Spacer(Modifier.width(4.dp))
        Text(
            text = name,
            style = MaterialTheme.typography.labelMedium,
            color = contentColor,
            maxLines = 1,
        )
    }
}

/** 账单列表行：分类图标 + 名称/时间 + 金额；可选小票缩略图与多选勾选框 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun BillRow(
    categoryName: String,
    categoryIcon: String,
    categoryColor: Color,
    amountFen: Long,
    type: TransactionType,
    time: Long,
    note: String?,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    /** 本地缩略图路径；非空时在金额前显示，点击可预览大图 */
    thumbnailPath: String? = null,
    onThumbnailClick: (() -> Unit)? = null,
    /** 多选模式：行首显示圆形勾选框 */
    selectionMode: Boolean = false,
    selected: Boolean = false,
    /** 长按回调（首页长按快速进入多选模式）；null = 无长按行为 */
    onLongPress: (() -> Unit)? = null,
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .combinedClickable(onClick = onClick, onLongClick = onLongPress)
            .padding(horizontal = 4.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (selectionMode) {
            SelectCircle(selected = selected)
            Spacer(Modifier.width(10.dp))
        }
        CategoryIcon(iconKey = categoryIcon, color = categoryColor)
        Spacer(Modifier.width(14.dp))
        Column(Modifier.weight(1f)) {
            // 分类名固定作大标题；备注以小字列在时间旁
            Text(
                text = categoryName,
                style = MaterialTheme.typography.bodyLarge,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            val noteText = note?.trim()?.takeIf { it.isNotBlank() }
            val timeText = DateUtils.formatBillTime(time)
            Text(
                text = if (noteText != null) "$noteText · $timeText" else timeText,
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
        if (thumbnailPath != null) {
            Spacer(Modifier.width(10.dp))
            AsyncImage(
                model = thumbnailPath,
                contentDescription = "小票缩略图",
                contentScale = ContentScale.Crop,
                modifier = Modifier
                    .size(width = 44.dp, height = 36.dp)
                    .clip(MaterialTheme.shapes.small)
                    .clickable(enabled = onThumbnailClick != null) { onThumbnailClick?.invoke() },
            )
        }
        Spacer(Modifier.width(10.dp))
        MoneyText(fen = amountFen, type = type)
    }
}

/** 多选模式的圆形勾选框：选中实心打勾，未选中空心圆圈 */
@Composable
fun SelectCircle(
    selected: Boolean,
    modifier: Modifier = Modifier,
) {
    Box(
        modifier = modifier
            .size(22.dp)
            .clip(CircleShape)
            .background(
                if (selected) MaterialTheme.colorScheme.primary
                else Color.Transparent
            )
            .border(
                width = 2.dp,
                color = if (selected) MaterialTheme.colorScheme.primary
                else MaterialTheme.colorScheme.outline,
                shape = CircleShape,
            ),
        contentAlignment = Alignment.Center,
    ) {
        if (selected) {
            Icon(
                imageVector = Icons.Outlined.Check,
                contentDescription = "已选中",
                tint = Color.White,
                modifier = Modifier.size(14.dp),
            )
        }
    }
}

/**
 * 轻量图片预览弹窗：全屏黑底 + 左右翻页 + 页码，点击任意处关闭。
 */
@Composable
fun ImagePreviewDialog(
    images: List<String>,
    initialIndex: Int,
    onDismiss: () -> Unit,
) {
    if (images.isEmpty()) return
    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false),
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Color.Black.copy(alpha = 0.92f))
                .clickable(onClick = onDismiss),
            contentAlignment = Alignment.Center,
        ) {
            val pagerState = rememberPagerState(
                initialPage = initialIndex.coerceIn(0, images.lastIndex),
                pageCount = { images.size },
            )
            HorizontalPager(state = pagerState) { page ->
                AsyncImage(
                    model = images.getOrNull(page),
                    contentDescription = "大图预览",
                    contentScale = ContentScale.Fit,
                    modifier = Modifier
                        .fillMaxSize()
                        .clickable(enabled = false) { },
                )
            }
            Text(
                text = "${pagerState.currentPage + 1} / ${images.size}",
                style = MaterialTheme.typography.labelMedium,
                color = Color.White,
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .padding(24.dp),
            )
        }
    }
}

/** 区块标题 */
@Composable
fun SectionTitle(
    text: String,
    modifier: Modifier = Modifier,
    trailing: @Composable (() -> Unit)? = null,
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 4.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = text,
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.onBackground,
        )
        trailing?.invoke()
    }
}

/** 通用卡片容器：大圆角 + 轻微阴影 */
@Composable
fun AppCard(
    modifier: Modifier = Modifier,
    content: @Composable androidx.compose.foundation.layout.ColumnScope.() -> Unit,
) {
    Card(
        modifier = modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.medium,
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
        content = content,
    )
}

/** 空态占位 */
@Composable
fun EmptyState(
    text: String,
    modifier: Modifier = Modifier,
    icon: ImageVector = Icons.Outlined.ReceiptLong,
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(vertical = 36.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.size(40.dp),
        )
        Text(
            text = text,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
        )
    }
}

/** 二次确认弹窗（删除账单/账本等危险操作） */
@Composable
fun ConfirmDialog(
    title: String,
    message: String,
    confirmText: String = stringResource(com.lightledger.app.R.string.delete),
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title, style = MaterialTheme.typography.titleMedium) },
        text = {
            Text(message, style = MaterialTheme.typography.bodyMedium)
        },
        confirmButton = {
            TextButton(
                onClick = {
                    onConfirm()
                    onDismiss()
                }
            ) {
                Text(confirmText, color = MaterialTheme.colorScheme.error)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(com.lightledger.app.R.string.cancel))
            }
        },
        shape = MaterialTheme.shapes.large,
    )
}

/** 底部导航占位高度 */
@Composable
fun BottomSpacer(modifier: Modifier = Modifier) {
    Spacer(modifier = modifier.height(8.dp))
}
