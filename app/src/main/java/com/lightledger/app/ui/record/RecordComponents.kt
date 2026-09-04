@file:OptIn(ExperimentalFoundationApi::class)

package com.lightledger.app.ui.record

import androidx.compose.animation.animateColorAsState
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
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.Backspace
import androidx.compose.material.icons.outlined.Calculate
import androidx.compose.material.icons.outlined.CameraAlt
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.Edit
import androidx.compose.material.icons.outlined.LocationOn
import androidx.compose.material.icons.outlined.MoreHoriz
import androidx.compose.material.icons.outlined.PhotoLibrary
import androidx.compose.material.icons.outlined.Schedule
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.DatePickerState
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.TimePicker
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.material3.rememberTimePickerState
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.lightledger.app.R
import com.lightledger.app.data.db.entity.CategoryEntity
import com.lightledger.app.data.db.entity.MemberEntity
import com.lightledger.app.domain.model.IconLibrary
import com.lightledger.app.domain.model.TransactionType
import com.lightledger.app.ui.components.CategoryIcon
import com.lightledger.app.ui.components.MemberChip
import com.lightledger.app.ui.theme.ChartPalette
import com.lightledger.app.ui.theme.SemanticTheme
import com.lightledger.app.ui.theme.argb

import com.lightledger.app.util.MoneyFormat

/**
 * 金额显示区：大号数字 + 闪烁光标；点击弹出数字键盘浮层。
 */
@Composable
fun AmountDisplay(
    amountText: String,
    modifier: Modifier = Modifier,
    onClick: (() -> Unit)? = null,
) {
    Surface(
        modifier = modifier
            .fillMaxWidth()
            .clickable(enabled = onClick != null) { onClick?.invoke() },
        shape = MaterialTheme.shapes.medium,
        color = MaterialTheme.colorScheme.surface,
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 20.dp, vertical = 22.dp),
            verticalAlignment = Alignment.Bottom,
        ) {
            Text(
                text = "¥",
                style = MaterialTheme.typography.headlineMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(bottom = 4.dp),
            )
            Spacer(Modifier.width(6.dp))
            Text(
                text = amountText.ifEmpty { "0.00" },
                style = MaterialTheme.typography.headlineLarge.copy(fontSize = 40.sp, lineHeight = 46.sp),
                color = if (amountText.isEmpty()) {
                    MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f)
                } else {
                    MaterialTheme.colorScheme.onSurface
                },
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Spacer(Modifier.width(4.dp))
            // 光标指示
            Box(
                modifier = Modifier
                    .padding(bottom = 8.dp)
                    .size(width = 2.dp, height = 28.dp)
                    .background(MaterialTheme.colorScheme.primary),
            )
            if (onClick != null) {
                Spacer(Modifier.weight(1f))
                Row(
                    modifier = Modifier.padding(bottom = 6.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Icon(
                        imageVector = Icons.Outlined.Calculate,
                        contentDescription = "打开数字键盘",
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(16.dp),
                    )
                    Spacer(Modifier.width(4.dp))
                    Text(
                        stringResource(R.string.record_tap_hint),
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }
    }
}

/**
 * 支出 / 收入切换胶囊。
 */
@Composable
fun TypeSwitch(
    current: TransactionType,
    onSelect: (TransactionType) -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(50))
            .background(MaterialTheme.colorScheme.surfaceVariant)
            .padding(4.dp),
    ) {
        TypeTab(
            text = stringResource(R.string.record_expense),
            selected = current == TransactionType.EXPENSE,
            color = SemanticTheme.colors.expense,
            modifier = Modifier.weight(1f),
            onClick = { onSelect(TransactionType.EXPENSE) },
        )
        TypeTab(
            text = stringResource(R.string.record_income),
            selected = current == TransactionType.INCOME,
            color = SemanticTheme.colors.income,
            modifier = Modifier.weight(1f),
            onClick = { onSelect(TransactionType.INCOME) },
        )
    }
}

@Composable
private fun TypeTab(
    text: String,
    selected: Boolean,
    color: Color,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val bg by animateColorAsState(if (selected) color else Color.Transparent, label = "typeBg")
    Box(
        modifier = modifier
            .clip(RoundedCornerShape(50))
            .background(bg)
            .clickable(onClick = onClick)
            .padding(vertical = 10.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = text,
            style = MaterialTheme.typography.labelLarge,
            color = if (selected) Color.White else MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

/**
 * 地点快捷录入：当前地点按钮 + 常用地点横向胶囊 + 手动输入。
 */@Composable
fun LocationSection(
    selected: String?,
    places: List<String>,
    onSelectPlace: (String) -> Unit,
    onClearLocation: () -> Unit,
    onManualInput: () -> Unit,
    onLocate: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(modifier = modifier.fillMaxWidth()) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                text = stringResource(R.string.record_location),
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.width(10.dp))
            // 当前地点（定位）
            Surface(
                onClick = onLocate,
                shape = RoundedCornerShape(50),
                color = MaterialTheme.colorScheme.primaryContainer,
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Icon(
                        imageVector = Icons.Outlined.LocationOn,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(14.dp),
                    )
                    Spacer(Modifier.width(4.dp))
                    Text(
                        stringResource(R.string.record_current_location),
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.primary,
                    )
                }
            }
            Spacer(Modifier.width(8.dp))
            // 手动输入
            Surface(
                onClick = onManualInput,
                shape = RoundedCornerShape(50),
                color = MaterialTheme.colorScheme.surfaceVariant,
            ) {
                Text(
                    stringResource(R.string.record_manual_location),
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 7.dp),
                )
            }
            if (selected != null) {
                Spacer(Modifier.width(8.dp))
                Surface(
                    shape = RoundedCornerShape(50),
                    color = MaterialTheme.colorScheme.secondaryContainer,
                ) {
                    Row(
                        modifier = Modifier.padding(start = 12.dp, end = 4.dp, top = 4.dp, bottom = 4.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(
                            selected,
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onSurface,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.widthIn(max = 160.dp),
                        )
                        IconButton(
                            onClick = onClearLocation,
                            modifier = Modifier.size(20.dp),
                        ) {
                            Icon(
                                Icons.Outlined.Close,
                                contentDescription = stringResource(R.string.record_clear_location),
                                modifier = Modifier.size(14.dp),
                            )
                        }
                    }
                }
            }
        }

        if (places.isNotEmpty()) {
            Spacer(Modifier.height(8.dp))
            LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                items(places) { place ->
                    val isSelected = place == selected
                    Surface(
                        onClick = { onSelectPlace(place) },
                        shape = RoundedCornerShape(50),
                        color = if (isSelected) {
                            MaterialTheme.colorScheme.primaryContainer
                        } else {
                            MaterialTheme.colorScheme.surface
                        },
                        border = androidx.compose.foundation.BorderStroke(
                            width = 1.dp,
                            color = if (isSelected) {
                                MaterialTheme.colorScheme.primary
                            } else {
                                MaterialTheme.colorScheme.outline
                            },
                        ),
                    ) {
                        Text(
                            place,
                            style = MaterialTheme.typography.labelMedium,
                            color = if (isSelected) {
                                MaterialTheme.colorScheme.primary
                            } else {
                                MaterialTheme.colorScheme.onSurfaceVariant
                            },
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.padding(horizontal = 12.dp, vertical = 7.dp),
                        )
                    }
                }
            }
        }
    }
}

/**
 * 图片上传区：拍照 / 相册按钮 + 已选缩略图（可删除）。
 */
@Composable
fun PhotoSection(
    images: List<String>,
    onTakePhoto: () -> Unit,
    onPickAlbum: () -> Unit,
    onRemove: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(modifier = modifier.fillMaxWidth()) {
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            PhotoAction(
                icon = Icons.Outlined.CameraAlt,
                text = stringResource(R.string.record_photo),
                modifier = Modifier.weight(1f),
                onClick = onTakePhoto,
            )
            PhotoAction(
                icon = Icons.Outlined.PhotoLibrary,
                text = stringResource(R.string.record_album),
                modifier = Modifier.weight(1f),
                onClick = onPickAlbum,
            )
        }
        if (images.isNotEmpty()) {
            Spacer(Modifier.height(10.dp))
            LazyRow(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                items(images, key = { it }) { path ->
                    Box(modifier = Modifier.size(84.dp)) {
                        AsyncImage(
                            model = path,
                            contentDescription = "小票图片",
                            contentScale = ContentScale.Crop,
                            modifier = Modifier
                                .fillMaxSize()
                                .clip(MaterialTheme.shapes.small),
                        )
                        // 删除角标
                        Box(
                            modifier = Modifier
                                .align(Alignment.TopEnd)
                                .padding(4.dp)
                                .size(20.dp)
                                .background(Color.Black.copy(alpha = 0.55f), CircleShape)
                                .clickable { onRemove(path) },
                            contentAlignment = Alignment.Center,
                        ) {
                            Icon(
                                Icons.Outlined.Close,
                                contentDescription = "删除图片",
                                tint = Color.White,
                                modifier = Modifier.size(12.dp),
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun PhotoAction(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Surface(
        onClick = onClick,
        modifier = modifier.height(64.dp),
        shape = MaterialTheme.shapes.small,
        color = MaterialTheme.colorScheme.surface,
        border = androidx.compose.foundation.BorderStroke(
            1.dp, MaterialTheme.colorScheme.outline
        ),
    ) {
        Row(
            horizontalArrangement = Arrangement.Center,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(
                icon,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(20.dp),
            )
            Spacer(Modifier.width(6.dp))
            Text(text, style = MaterialTheme.typography.labelLarge)
        }
    }
}

/**
 * 分类平铺：5 列网格全量列出，点击选中、长按编辑；
 * 末尾固定一个「更多」格，点开全量图标选择弹窗（可替换常驻分类图标）。
 */
@Composable
fun CategorySection(
    categories: List<CategoryEntity>,
    selectedId: Long?,
    moreLabel: String,
    onSelect: (Long) -> Unit,
    onLongPress: (CategoryEntity) -> Unit,
    onMore: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(modifier = modifier.fillMaxWidth()) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                text = stringResource(R.string.record_category),
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.weight(1f))
            Text(
                text = stringResource(R.string.record_category_edit_hint),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
            )
        }
        Spacer(Modifier.height(10.dp))
        // 末尾追加 null 占位 = 「更多」格
        val cells: List<Any?> = categories + null
        val rows = cells.chunked(5)
        rows.forEach { rowItems ->
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                rowItems.forEach { item ->
                    when (item) {
                        is CategoryEntity -> CategoryCell(
                            category = item,
                            selected = item.id == selectedId,
                            onClick = { onSelect(item.id) },
                            onLongClick = { onLongPress(item) },
                            modifier = Modifier.weight(1f),
                        )
                        else -> MoreCell(
                            label = moreLabel,
                            onClick = onMore,
                            modifier = Modifier.weight(1f),
                        )
                    }
                }
                // 末行不满 5 列时空位补齐
                repeat(5 - rowItems.size) { Spacer(Modifier.weight(1f)) }
            }
            Spacer(Modifier.height(10.dp))
        }
    }
}

/** 「更多」格：与分类格同尺寸的虚位入口，点开全量图标选择 */
@Composable
private fun MoreCell(
    label: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier
            .clip(MaterialTheme.shapes.small)
            .background(Color.Transparent)
            .combinedClickable(onClick = onClick)
            .padding(vertical = 8.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Box(
            modifier = Modifier
                .size(42.dp)
                .clip(CircleShape)
                .background(MaterialTheme.colorScheme.surfaceVariant)
                .border(
                    width = 1.dp,
                    color = MaterialTheme.colorScheme.outline.copy(alpha = 0.5f),
                    shape = CircleShape,
                ),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                imageVector = Icons.Outlined.MoreHoriz,
                contentDescription = label,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(22.dp),
            )
        }
        Spacer(Modifier.height(6.dp))
        Text(
            text = label,
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 1,
        )
    }
}

/**
 * 全量图标选择弹窗：网格列出图标库全部图标，每个图标下方带名称。
 * 点击图标后回调 [onPick]，用于替换常驻分类图标等场景。
 */
@Composable
fun IconPickerDialog(
    title: String,
    useZh: Boolean,
    onDismiss: () -> Unit,
    onPick: (String) -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        shape = MaterialTheme.shapes.large,
        title = {
            Text(title, style = MaterialTheme.typography.titleMedium)
        },
        text = {
            LazyVerticalGrid(
                columns = GridCells.Fixed(4),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(max = 360.dp),
            ) {
                items(IconLibrary.keys) { key ->
                    Column(
                        modifier = Modifier
                            .clip(MaterialTheme.shapes.small)
                            .clickable { onPick(key) }
                            .padding(4.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                    ) {
                        Box(
                            modifier = Modifier
                                .size(40.dp)
                                .clip(CircleShape)
                                .background(MaterialTheme.colorScheme.surfaceVariant),
                            contentAlignment = Alignment.Center,
                        ) {
                            Icon(
                                imageVector = IconLibrary.of(key),
                                contentDescription = IconLibrary.displayName(key, useZh),
                                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.size(22.dp),
                            )
                        }
                        Spacer(Modifier.height(4.dp))
                        Text(
                            text = IconLibrary.displayName(key, useZh),
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                }
            }
        },
        confirmButton = {},
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(R.string.cancel))
            }
        },
    )
}

/**
 * 选择要替换图标的常驻分类：列出全部分类（图标 + 名称），点选即替换。
 */
@Composable
fun CategoryPickDialog(
    title: String,
    categories: List<CategoryEntity>,
    pickedIcon: String,
    useZh: Boolean,
    onDismiss: () -> Unit,
    onPickCategory: (CategoryEntity) -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        shape = MaterialTheme.shapes.large,
        title = {
            Text(title, style = MaterialTheme.typography.titleMedium)
        },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(max = 380.dp)
                    .verticalScroll(rememberScrollState()),
            ) {
                categories.forEach { category ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(MaterialTheme.shapes.small)
                            .clickable { onPickCategory(category) }
                            .padding(vertical = 8.dp, horizontal = 4.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Box(
                            modifier = Modifier
                                .size(34.dp)
                                .clip(CircleShape)
                                .background(category.color.argb()),
                            contentAlignment = Alignment.Center,
                        ) {
                            Icon(
                                imageVector = IconLibrary.of(category.icon),
                                contentDescription = category.name,
                                tint = Color.White,
                                modifier = Modifier.size(19.dp),
                            )
                        }
                        Spacer(Modifier.width(12.dp))
                        Text(
                            text = category.name,
                            style = MaterialTheme.typography.bodyLarge,
                            color = MaterialTheme.colorScheme.onSurface,
                        )
                        Spacer(Modifier.weight(1f))
                        // 预览将被替换成的新图标
                        Icon(
                            imageVector = IconLibrary.of(pickedIcon),
                            contentDescription = IconLibrary.displayName(pickedIcon, useZh),
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(20.dp),
                        )
                    }
                }
            }
        },
        confirmButton = {},
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(R.string.cancel))
            }
        },
    )
}

/**
 * 成员选择行（归属 / 垫付共用）：标题 + 提示 + 「本人 + 成员」胶囊横排。
 * 「+ 成员」按钮全局唯一：仅显示在归属行标题栏右上角（onAddMember 传入时显示），
 * 垫付行传 null 不再重复显示。
 * [publicLabel] 非空时，成员列表中的 isPublic 成员按该本地化文案显示（公共消费 = 全员 AA）。
 * 旅行账本专用；非旅行账本不渲染。
 */
@Composable
fun MemberPickerRow(
    title: String,
    hint: String,
    selfLabel: String,
    members: List<MemberEntity>,
    selectedId: Long?,
    publicLabel: String?,
    addLabel: String,
    onSelect: (Long?) -> Unit,
    onAddMember: (() -> Unit)?,
    modifier: Modifier = Modifier,
) {
    Column(modifier = modifier.fillMaxWidth()) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                text = title,
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.weight(1f))
            Text(
                text = hint,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
            )
            if (onAddMember != null) {
                Spacer(Modifier.width(8.dp))
                Surface(
                    onClick = onAddMember,
                    shape = RoundedCornerShape(50),
                    color = MaterialTheme.colorScheme.primaryContainer,
                ) {
                    Text(
                        text = addLabel,
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 3.dp),
                    )
                }
            }
        }
        Spacer(Modifier.height(8.dp))
        LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            item(key = "self") {
                MemberChip(
                    name = selfLabel,
                    color = MaterialTheme.colorScheme.primary,
                    selected = selectedId == null,
                    onClick = { onSelect(null) },
                )
            }
            items(members, key = { it.id }) { member ->
                val isPublic = member.isPublic && publicLabel != null
                if (!member.isPublic || publicLabel != null) {
                    MemberChip(
                        name = if (isPublic) publicLabel!! else member.name,
                        color = Color(member.color),
                        selected = selectedId == member.id,
                        onClick = { onSelect(member.id) },
                    )
                }
            }
        }
    }
}

/**
 * 日期时间选择入口：显示当前选择（默认"当前时间"语义），点击弹出 日期 → 时间 两步选择。
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DateTimeSection(
    selectedMillis: Long?,
    displayText: String,
    onPick: (Long) -> Unit,
    modifier: Modifier = Modifier,
) {
    var showDate by remember { mutableStateOf(false) }
    var showTime by remember { mutableStateOf(false) }
    var pickedDateUtc by remember { mutableStateOf<Long?>(null) }

    Column(modifier = modifier.fillMaxWidth()) {
        Text(
            text = stringResource(R.string.record_time_title),
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(Modifier.height(8.dp))
        Surface(
            onClick = { showDate = true },
            shape = RoundedCornerShape(12.dp),
            color = MaterialTheme.colorScheme.surface,
            border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
        ) {
            Row(
                modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(
                    imageVector = Icons.Outlined.Schedule,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(16.dp),
                )
                Spacer(Modifier.width(8.dp))
                Text(
                    text = displayText,
                    style = MaterialTheme.typography.bodyMedium,
                    maxLines = 1,
                )
            }
        }
    }

    // 日期 → 时间 两步选择
    if (showDate) {
        val initState = rememberDatePickerState(
            initialSelectedDateMillis = selectedMillis ?: System.currentTimeMillis(),
        )
        DatePickerDialog(
            onDismissRequest = { showDate = false },
            confirmButton = {
                TextButton(
                    onClick = {
                        pickedDateUtc = initState.selectedDateMillis
                        showDate = false
                        showTime = true
                    },
                ) { Text(stringResource(R.string.done)) }
            },
            dismissButton = {
                TextButton(onClick = { showDate = false }) { Text(stringResource(R.string.cancel)) }
            },
        ) {
            DatePicker(state = initState)
        }
    }
    if (showTime) {
        val initial = java.time.Instant.ofEpochMilli(selectedMillis ?: System.currentTimeMillis())
            .atZone(java.time.ZoneId.systemDefault())
        val timeState = rememberTimePickerState(
            initialHour = initial.hour,
            initialMinute = initial.minute,
            is24Hour = true,
        )
        AlertDialog(
            onDismissRequest = { showTime = false },
            shape = MaterialTheme.shapes.large,
            title = { Text(stringResource(R.string.record_pick_datetime)) },
            text = { TimePicker(state = timeState) },
            confirmButton = {
                TextButton(
                    onClick = {
                        // DatePicker 返回 UTC 当天 0 点毫秒 → 转 LocalDate 后与本地时间合成
                        val date = java.time.Instant.ofEpochMilli(pickedDateUtc ?: System.currentTimeMillis())
                            .atZone(java.time.ZoneOffset.UTC).toLocalDate()
                        val millis = date.atTime(timeState.hour, timeState.minute)
                            .atZone(java.time.ZoneId.systemDefault()).toInstant().toEpochMilli()
                        onPick(millis)
                        showTime = false
                    },
                ) { Text(stringResource(R.string.done)) }
            },
            dismissButton = {
                TextButton(onClick = { showTime = false }) { Text(stringResource(R.string.cancel)) }
            },
        )
    }
}

@Composable
private fun CategoryCell(
    category: CategoryEntity,
    selected: Boolean,
    onClick: () -> Unit,
    onLongClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val borderColor by animateColorAsState(
        if (selected) category.color.argb() else Color.Transparent,
        label = "catBorder",
    )
    Column(
        modifier = modifier
            .clip(MaterialTheme.shapes.small)
            .background(
                if (selected) category.color.argb().copy(alpha = 0.10f)
                else Color.Transparent
            )
            .border(
                width = if (selected) 1.5.dp else 0.dp,
                color = borderColor,
                shape = MaterialTheme.shapes.small,
            )
            .combinedClickable(onClick = onClick, onLongClick = onLongClick)
            .padding(vertical = 8.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        CategoryIcon(iconKey = category.icon, color = category.color.argb(), size = 42)
        Spacer(Modifier.height(6.dp))
        Text(
            text = category.name,
            style = MaterialTheme.typography.labelMedium,
            color = if (selected) category.color.argb() else MaterialTheme.colorScheme.onSurface,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

/**
 * 大号自定义数字键盘：3 列数字区 + 右侧删除与渐变保存键。
 */
@Composable
fun AmountKeyboard(
    enabled: Boolean,
    onKey: (String) -> Unit,
    onClear: () -> Unit,
    onSave: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Surface(
        modifier = modifier.fillMaxWidth(),
        color = MaterialTheme.colorScheme.surface,
        shape = RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp),
        shadowElevation = 8.dp,
    ) {
        Row(
            modifier = Modifier
                .padding(12.dp)
                .height(232.dp),
        ) {
            // 数字区 3 列 x 4 行
            Column(Modifier.weight(3f)) {
                val rows = listOf(
                    listOf("1", "2", "3"),
                    listOf("4", "5", "6"),
                    listOf("7", "8", "9"),
                    listOf(".", "0", "del"),
                )
                rows.forEachIndexed { index, row ->
                    Row(Modifier.weight(1f)) {
                        row.forEach { key ->
                            KeyboardKey(
                                key = key,
                                modifier = Modifier
                                    .weight(1f)
                                    .fillMaxHeight(),
                                onKey = {
                                    when (key) {
                                        "del" -> onKey("del")
                                        else -> onKey(key)
                                    }
                                },
                            )
                        }
                    }
                }
            }
            Spacer(Modifier.width(10.dp))
            // 右列：删除 + 保存（大按钮）
            Column(Modifier.weight(1f)) {
                Surface(
                    onClick = onClear,
                    enabled = enabled,
                    shape = MaterialTheme.shapes.small,
                    color = MaterialTheme.colorScheme.surfaceVariant,
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f),
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(
                            Icons.Outlined.Backspace,
                            contentDescription = stringResource(R.string.delete),
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
                Spacer(Modifier.height(10.dp))
                Surface(
                    onClick = onSave,
                    enabled = enabled,
                    shape = MaterialTheme.shapes.small,
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(3f),
                ) {
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .background(
                                brush = Brush.verticalGradient(
                                    if (enabled) {
                                        listOf(
                                            MaterialTheme.colorScheme.primary,
                                            MaterialTheme.colorScheme.secondary,
                                        )
                                    } else {
                                        listOf(
                                            MaterialTheme.colorScheme.surfaceVariant,
                                            MaterialTheme.colorScheme.surfaceVariant,
                                        )
                                    }
                                )
                            ),
                        contentAlignment = Alignment.Center,
                    ) {
                        Text(
                            stringResource(R.string.record_save),
                            style = MaterialTheme.typography.titleLarge,
                            color = if (enabled) MaterialTheme.colorScheme.onPrimary
                            else MaterialTheme.colorScheme.onSurfaceVariant,
                            textAlign = TextAlign.Center,
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun KeyboardKey(
    key: String,
    onKey: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Box(
        modifier = modifier
            .padding(3.dp)
            .clip(MaterialTheme.shapes.small)
            .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f))
            .clickable(onClick = onKey),
        contentAlignment = Alignment.Center,
    ) {
        if (key == "del") {
            Icon(
                Icons.Outlined.Backspace,
                contentDescription = stringResource(R.string.delete),
                tint = MaterialTheme.colorScheme.onSurface,
            )
        } else {
            Text(
                text = key,
                fontSize = 22.sp,
                style = MaterialTheme.typography.headlineMedium,
                color = MaterialTheme.colorScheme.onSurface,
            )
        }
    }
}

/**
 * 底部保存大按钮（键盘收起时显示）。
 */
@Composable
fun SaveButton(
    enabled: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    isEdit: Boolean = false,
) {
    Surface(
        onClick = onClick,
        enabled = enabled,
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 10.dp),
        shape = RoundedCornerShape(50),
        color = Color.Transparent,
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(54.dp)
                .background(
                    brush = Brush.horizontalGradient(
                        if (enabled) {
                            listOf(
                                MaterialTheme.colorScheme.primary,
                                MaterialTheme.colorScheme.secondary,
                            )
                        } else {
                            listOf(
                                MaterialTheme.colorScheme.surfaceVariant,
                                MaterialTheme.colorScheme.surfaceVariant,
                            )
                        }
                    )
                ),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                text = stringResource(if (isEdit) R.string.record_save_edit else R.string.record_save),
                style = MaterialTheme.typography.titleLarge,
                color = if (enabled) MaterialTheme.colorScheme.onPrimary
                else MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

/**
 * 分类编辑弹窗：名称 + 图标选择 + 颜色选择；编辑已有自定义分类时可删除。
 */
@Composable
fun CategoryEditorDialog(
    target: CategoryEntity?,
    isNew: Boolean,
    onDismiss: () -> Unit,
    onSave: (name: String, icon: String, color: Int) -> Unit,
    onDelete: () -> Unit,
) {
    var name by remember(target) { mutableStateOf(target?.name.orEmpty()) }
    var iconKey by remember(target) { mutableStateOf(target?.icon ?: "restaurant") }
    var color by remember(target) {
        mutableStateOf(target?.color ?: ChartPalette.first())
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        shape = MaterialTheme.shapes.large,
        title = {
            Text(
                stringResource(if (isNew) R.string.record_category_add else R.string.record_category_edit),
                style = MaterialTheme.typography.titleMedium,
            )
        },
        text = {
            Column {
                OutlinedTextField(
                    value = name,
                    onValueChange = { if (it.length <= 6) name = it },
                    placeholder = { Text(stringResource(R.string.record_category_name_hint)) },
                    singleLine = true,
                    shape = MaterialTheme.shapes.small,
                    modifier = Modifier.fillMaxWidth(),
                )
                Spacer(Modifier.height(14.dp))
                Text(stringResource(R.string.label_icon), style = MaterialTheme.typography.labelLarge)
                Spacer(Modifier.height(8.dp))
                // 图标库全量网格展示（6 列，最多 3 行可视，可滚动）
                LazyVerticalGrid(
                    columns = GridCells.Fixed(6),
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(max = 132.dp),
                ) {
                    items(IconLibrary.keys) { key ->
                        val selected = key == iconKey
                        Box(
                            modifier = Modifier
                                .size(40.dp)
                                .clip(CircleShape)
                                .background(
                                    if (selected) Color(color) else MaterialTheme.colorScheme.surfaceVariant
                                )
                                .border(
                                    width = if (selected) 2.dp else 0.dp,
                                    color = MaterialTheme.colorScheme.primary,
                                    shape = CircleShape,
                                )
                                .clickable { iconKey = key },
                            contentAlignment = Alignment.Center,
                        ) {
                            Icon(
                                imageVector = IconLibrary.of(key),
                                contentDescription = key,
                                tint = if (selected) Color.White
                                else MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.size(20.dp),
                            )
                        }
                    }
                }
                Spacer(Modifier.height(14.dp))
                Text(stringResource(R.string.label_color), style = MaterialTheme.typography.labelLarge)
                Spacer(Modifier.height(8.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    ChartPalette.forEach { c ->
                        val selected = c == color
                        Box(
                            modifier = Modifier
                                .size(30.dp)
                                .clip(CircleShape)
                                .background(Color(c))
                                .border(
                                    width = if (selected) 2.5.dp else 0.dp,
                                    color = MaterialTheme.colorScheme.onSurface,
                                    shape = CircleShape,
                                )
                                .clickable { color = c },
                        )
                    }
                }
            }
        },
        confirmButton = {
            TextButton(
                onClick = { onSave(name, iconKey, color) },
                enabled = name.isNotBlank(),
            ) { Text(stringResource(R.string.save)) }
        },
        dismissButton = {
            Row {
                if (!isNew && target != null && !target.isDefault) {
                    TextButton(
                        onClick = onDelete,
                        colors = androidx.compose.material3.ButtonDefaults.textButtonColors(
                            contentColor = MaterialTheme.colorScheme.error,
                        ),
                    ) { Text(stringResource(R.string.delete)) }
                }
                TextButton(onClick = onDismiss) { Text(stringResource(R.string.cancel)) }
            }
        },
    )
}

/**
 * 通用单行文本输入弹窗（手动输入地点等）。
 */
@Composable
fun TextInputDialog(
    title: String,
    initial: String,
    onDismiss: () -> Unit,
    onConfirm: (String) -> Unit,
) {
    var text by remember { mutableStateOf(initial) }
    AlertDialog(
        onDismissRequest = onDismiss,
        shape = MaterialTheme.shapes.large,
        title = { Text(title, style = MaterialTheme.typography.titleMedium) },
        text = {
            OutlinedTextField(
                value = text,
                onValueChange = { text = it },
                singleLine = true,
                shape = MaterialTheme.shapes.small,
                modifier = Modifier.fillMaxWidth(),
            )
        },
        confirmButton = {
            TextButton(
                onClick = { onConfirm(text) },
                enabled = text.isNotBlank(),
            ) { Text(stringResource(R.string.done)) }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(R.string.cancel)) }
        },
    )
}
