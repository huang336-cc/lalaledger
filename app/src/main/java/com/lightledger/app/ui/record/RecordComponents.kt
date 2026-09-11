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
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
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
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.DatePickerState
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
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
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.LayoutCoordinates
import androidx.compose.ui.layout.onGloballyPositioned
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
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
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
                style = MaterialTheme.typography.headlineLarge.copy(fontSize = 34.sp, lineHeight = 40.sp),
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
 * 时间 + 地点合并行：一行两个等宽胶囊，压缩纵向空间。
 * 左：时间胶囊（点击 → 日期 → 时间两步选择）；
 * 右：地点胶囊（点击弹菜单：当前定位 / 手动输入 / 常用地点 / 清除）。
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TimePlaceRow(
    selectedMillis: Long?,
    displayText: String,
    onPick: (Long) -> Unit,
    selectedPlace: String?,
    places: List<String>,
    onSelectPlace: (String) -> Unit,
    onClearLocation: () -> Unit,
    onManualInput: () -> Unit,
    onLocate: () -> Unit,
    modifier: Modifier = Modifier,
) {
    var showDate by remember { mutableStateOf(false) }
    var showTime by remember { mutableStateOf(false) }
    var pickedDateUtc by remember { mutableStateOf<Long?>(null) }
    var placeMenu by remember { mutableStateOf(false) }

    Row(
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        // 时间胶囊
        Surface(
            onClick = { showDate = true },
            shape = RoundedCornerShape(12.dp),
            color = MaterialTheme.colorScheme.surface,
            border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
            modifier = Modifier.weight(1.15f),
        ) {
            Row(
                modifier = Modifier.padding(horizontal = 10.dp, vertical = 11.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(
                    imageVector = Icons.Outlined.Schedule,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(15.dp),
                )
                Spacer(Modifier.width(6.dp))
                Text(
                    text = displayText,
                    style = MaterialTheme.typography.bodySmall,
                    maxLines = 1,
                )
            }
        }
        // 地点胶囊 + 弹出菜单
        Box(modifier = Modifier.weight(1f)) {
            Surface(
                onClick = { placeMenu = true },
                shape = RoundedCornerShape(12.dp),
                color = MaterialTheme.colorScheme.surface,
                border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
                modifier = Modifier.fillMaxWidth(),
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 11.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Icon(
                        imageVector = Icons.Outlined.LocationOn,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(15.dp),
                    )
                    Spacer(Modifier.width(6.dp))
                    Text(
                        text = selectedPlace ?: stringResource(R.string.record_add_location),
                        style = MaterialTheme.typography.bodySmall,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
            DropdownMenu(expanded = placeMenu, onDismissRequest = { placeMenu = false }) {
                DropdownMenuItem(
                    text = { Text(stringResource(R.string.record_current_location)) },
                    onClick = { placeMenu = false; onLocate() },
                )
                DropdownMenuItem(
                    text = { Text(stringResource(R.string.record_manual_location)) },
                    onClick = { placeMenu = false; onManualInput() },
                )
                if (places.isNotEmpty()) {
                    HorizontalDivider()
                    places.forEach { place ->
                        DropdownMenuItem(
                            text = { Text(place, maxLines = 1, overflow = TextOverflow.Ellipsis) },
                            onClick = { placeMenu = false; onSelectPlace(place) },
                        )
                    }
                }
                if (selectedPlace != null) {
                    HorizontalDivider()
                    DropdownMenuItem(
                        text = { Text(stringResource(R.string.record_clear_location)) },
                        onClick = { placeMenu = false; onClearLocation() },
                    )
                }
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
/**
 * 备注 + 拍照 + 相册：一行加权分布（备注 2 : 拍照 1 : 相册 1，同高 48dp），
 * 备注保持可读宽度，按钮仍有充足触摸区。
 */
@Composable
fun NotePhotoRow(
    note: String,
    onNoteChange: (String) -> Unit,
    onTakePhoto: () -> Unit,
    onPickAlbum: () -> Unit,
    onNoteFocus: () -> Unit,
    modifier: Modifier = Modifier,
    // 备注输入框的布局信息回传给调用方：用于判断「一次点击是否落在备注框内」，
    // 从而决定要不要释放输入焦点（避免重演 v1.8.11 / v1.8.12 的焦点问题）
    onNotePlaced: (LayoutCoordinates) -> Unit = {},
) {
    Row(
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        OutlinedTextField(
            value = note,
            onValueChange = onNoteChange,
            placeholder = {
                Text(
                    text = stringResource(R.string.record_note_hint),
                    style = MaterialTheme.typography.bodyMedium,
                    maxLines = 1,
                )
            },
            modifier = Modifier
                .weight(2f)
                .height(48.dp)
                .onGloballyPositioned(onNotePlaced)
                // 聚焦备注框（系统输入法弹出）时，自定义数字键盘必须让位
                .onFocusChanged { if (it.isFocused) onNoteFocus() },
            shape = MaterialTheme.shapes.small,
            singleLine = true,
            textStyle = MaterialTheme.typography.bodyMedium,
        )
        PhotoIconButton(
            icon = Icons.Outlined.CameraAlt,
            description = stringResource(R.string.record_photo),
            onClick = onTakePhoto,
            modifier = Modifier.weight(1f),
        )
        PhotoIconButton(
            icon = Icons.Outlined.PhotoLibrary,
            description = stringResource(R.string.record_album),
            onClick = onPickAlbum,
            modifier = Modifier.weight(1f),
        )
    }
}

/** 拍照 / 相册：方形图标按钮（48dp 高，图标 22dp，宽度由布局三等分决定） */
@Composable
private fun PhotoIconButton(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    description: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Surface(
        onClick = onClick,
        modifier = modifier.height(48.dp),
        shape = MaterialTheme.shapes.small,
        color = MaterialTheme.colorScheme.surface,
        border = androidx.compose.foundation.BorderStroke(
            1.dp, MaterialTheme.colorScheme.outline
        ),
    ) {
        Box(contentAlignment = Alignment.Center) {
            Icon(
                imageVector = icon,
                contentDescription = description,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(22.dp),
            )
        }
    }
}

/**
 * 已选图片缩略图行：独立成行且有图时才渲染，无图时不占纵向空间。
 */
@Composable
fun ImageThumbRow(
    images: List<String>,
    onRemove: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    LazyRow(
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        items(images, key = { it }) { path ->
            Box(modifier = Modifier.size(72.dp)) {
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

/**
 * 分类平铺：5 列网格全量列出，点击选中、长按编辑；
 * 末尾固定一个「更多」格，点开全量图标选择弹窗（可替换常驻分类图标）。
 */
@Composable
fun CategorySection(
    categories: List<CategoryEntity>,
    selectedId: Long?,
    moreLabel: String,
    /** v7：仅本次使用的图标 key；null = 未启用（用分类自身图标） */
    oneOffIcon: String? = null,
    /** v7：临时图标的显示名（跟随界面语言） */
    oneOffName: String = "",
    /** v7：取消临时图标，回到分类自身图标 */
    onClearOneOff: () -> Unit = {},
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
            // 「更多」紧随分类标签右侧：带描边的圆形按钮（与拍照/相册同风格，可点击感明确）
            Spacer(Modifier.width(8.dp))
            Box(
                modifier = Modifier
                    .size(32.dp)
                    .clip(CircleShape)
                    .background(MaterialTheme.colorScheme.surface)
                    .border(
                        width = 1.dp,
                        color = MaterialTheme.colorScheme.outline,
                        shape = CircleShape,
                    )
                    .combinedClickable(onClick = onMore),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    imageVector = Icons.Outlined.MoreHoriz,
                    contentDescription = moreLabel,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(20.dp),
                )
            }
            // v7：临时图标胶囊——「仅本次使用」生效时显示，点 × 作废回到分类图标
            if (oneOffIcon != null) {
                Spacer(Modifier.width(8.dp))
                Row(
                    modifier = Modifier
                        .height(30.dp)
                        .clip(CircleShape)
                        .background(MaterialTheme.colorScheme.primaryContainer)
                        .padding(start = 8.dp, end = 4.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Icon(
                        imageVector = IconLibrary.of(oneOffIcon),
                        contentDescription = oneOffName,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(16.dp),
                    )
                    Spacer(Modifier.width(4.dp))
                    Text(
                        text = stringResource(R.string.icon_once_chip, oneOffName),
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.primary,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.widthIn(max = 120.dp),
                    )
                    Spacer(Modifier.width(2.dp))
                    Box(
                        modifier = Modifier
                            .size(22.dp)
                            .clip(CircleShape)
                            .clickable(onClick = onClearOneOff),
                        contentAlignment = Alignment.Center,
                    ) {
                        Icon(
                            imageVector = Icons.Outlined.Close,
                            contentDescription = stringResource(R.string.icon_once_clear),
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(14.dp),
                        )
                    }
                }
            }
            Spacer(Modifier.weight(1f))
            if (oneOffIcon == null) {
                Text(
                    text = stringResource(R.string.record_category_edit_hint),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
                )
            }
        }
        Spacer(Modifier.height(6.dp))
        // 网格只放分类（无「更多」格），行数随分类数量收敛
        categories.chunked(5).forEach { rowItems ->
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                rowItems.forEach { item ->
                    // v7：仅本次使用的图标只作用于「当前选中的分类格子」——
                    // 格子图标换成临时图标，但格子下方文字仍显示分类原名。
                    val effectiveIcon = if (oneOffIcon != null && item.id == selectedId) {
                        oneOffIcon
                    } else {
                        item.icon
                    }
                    CategoryCell(
                        category = item,
                        iconOverrideKey = effectiveIcon,
                        selected = item.id == selectedId,
                        onClick = { onSelect(item.id) },
                        onLongClick = { onLongPress(item) },
                        modifier = Modifier.weight(1f),
                    )
                }
                // 末行不满 5 列时空位补齐
                repeat(5 - rowItems.size) { Spacer(Modifier.weight(1f)) }
            }
            Spacer(Modifier.height(6.dp))
        }
    }
}

/**
 * 全量图标选择弹窗：网格列出图标库全部图标，每个图标下方带名称。
 * 顶部带搜索框，可按中文名 / 英文名 / key 实时过滤。
 * 点击图标后回调 [onPick]，用于替换常驻分类图标等场景。
 */
@Composable
fun IconPickerDialog(
    title: String,
    useZh: Boolean,
    onDismiss: () -> Unit,
    onPick: (String) -> Unit,
) {
    var query by remember { mutableStateOf("") }
    val keyword = query.trim()
    // 按当前语言过滤：中文界面匹配中文名，英文界面匹配英文名；同时都支持匹配 key 本身
    val filtered = remember(keyword, useZh) {
        if (keyword.isEmpty()) {
            IconLibrary.keys
        } else {
            val lower = keyword.lowercase()
            IconLibrary.keys.filter { key ->
                key.lowercase().contains(lower) ||
                    IconLibrary.displayName(key, useZh).lowercase().contains(lower)
            }
        }
    }
    AlertDialog(
        onDismissRequest = onDismiss,
        shape = MaterialTheme.shapes.large,
        title = {
            Text(title, style = MaterialTheme.typography.titleMedium)
        },
        text = {
            Column(modifier = Modifier.fillMaxWidth()) {
                // 搜索框：即时过滤，无需点按钮
                OutlinedTextField(
                    value = query,
                    onValueChange = { query = it },
                    placeholder = {
                        Text(
                            stringResource(R.string.icon_search_hint),
                            style = MaterialTheme.typography.bodySmall,
                        )
                    },
                    singleLine = true,
                    shape = MaterialTheme.shapes.small,
                    leadingIcon = {
                        Icon(
                            imageVector = Icons.Outlined.Search,
                            contentDescription = null,
                            modifier = Modifier.size(18.dp),
                        )
                    },
                    trailingIcon = {
                        if (query.isNotEmpty()) {
                            Icon(
                                imageVector = Icons.Outlined.Close,
                                contentDescription = stringResource(R.string.icon_search_clear),
                                modifier = Modifier
                                    .size(18.dp)
                                    .clickable { query = "" },
                            )
                        }
                    },
                    modifier = Modifier.fillMaxWidth(),
                )
                Spacer(Modifier.height(10.dp))
                if (filtered.isEmpty()) {
                    // 空状态：给出明确反馈，避免看起来像"加载失败"
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(120.dp),
                        contentAlignment = Alignment.Center,
                    ) {
                        Text(
                            text = stringResource(R.string.icon_search_empty),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                } else {
                    LazyVerticalGrid(
                        columns = GridCells.Fixed(4),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalArrangement = Arrangement.spacedBy(10.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(max = 300.dp),
                    ) {
                        items(filtered) { key ->
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
 * v7：选完图标后的用途二选一。
 * - 替换常驻分类：进入分类列表，把某个分类的图标与名称一起换掉（老行为）
 * - 仅本次使用：只在这一笔账单上用该图标，不动任何分类
 */
@Composable
fun IconUseChoiceDialog(
    iconKey: String,
    iconName: String,
    useZh: Boolean,
    onDismiss: () -> Unit,
    onReplaceCategory: () -> Unit,
    onUseOnce: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        shape = MaterialTheme.shapes.large,
        title = {
            Text(
                stringResource(R.string.icon_use_choice_title),
                style = MaterialTheme.typography.titleMedium,
            )
        },
        text = {
            Column(modifier = Modifier.fillMaxWidth()) {
                // 已选图标预览
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Box(
                        modifier = Modifier
                            .size(44.dp)
                            .clip(CircleShape)
                            .background(MaterialTheme.colorScheme.surfaceVariant),
                        contentAlignment = Alignment.Center,
                    ) {
                        Icon(
                            imageVector = IconLibrary.of(iconKey),
                            contentDescription = IconLibrary.displayName(iconKey, useZh),
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(24.dp),
                        )
                    }
                    Spacer(Modifier.width(12.dp))
                    Column {
                        Text(
                            text = IconLibrary.displayName(iconKey, useZh),
                            style = MaterialTheme.typography.titleSmall,
                            color = MaterialTheme.colorScheme.onSurface,
                        )
                        Text(
                            text = stringResource(R.string.icon_use_choice_subtitle),
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
                Spacer(Modifier.height(14.dp))
                ChoiceRow(
                    title = stringResource(R.string.icon_use_replace),
                    subtitle = stringResource(R.string.icon_use_replace_desc),
                    onClick = onReplaceCategory,
                )
                Spacer(Modifier.height(8.dp))
                ChoiceRow(
                    title = stringResource(R.string.icon_use_once),
                    subtitle = stringResource(R.string.icon_use_once_desc),
                    onClick = onUseOnce,
                )
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

/** 二选一弹窗中的单个选项行：主标题 + 灰色说明，整行可点 */
@Composable
private fun ChoiceRow(
    title: String,
    subtitle: String,
    onClick: () -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(MaterialTheme.shapes.medium)
            .border(
                width = 1.dp,
                color = MaterialTheme.colorScheme.outline,
                shape = MaterialTheme.shapes.medium,
            )
            .clickable(onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 10.dp),
    ) {
        Text(
            text = title,
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.primary,
        )
        Spacer(Modifier.height(2.dp))
        Text(
            text = subtitle,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
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
 * 归属 + 垫付合并双列行（旅行账本专用）：「这笔归谁」「谁垫付」左右各一个描边卡片，
 * 卡片内为小标题 + 成员胶囊横排（可横向滚动），视觉分区明确、不易错位。
 * 「+ 成员」入口仅显示在归属卡片标题右侧（用 Box+clickable 实现，避免 Surface(onClick)
 * 的最小触摸尺寸把标题行撑到 48dp 造成两列错位）。
 * [publicLabel] 非空时，成员列表中的 isPublic 成员按该本地化文案显示（公共消费 = 全员 AA）。
 */
@Composable
fun OwnerPayerRow(
    ownerTitle: String,
    payerTitle: String,
    selfLabel: String,
    members: List<MemberEntity>,
    selectedOwnerId: Long?,
    selectedPayerId: Long?,
    publicLabel: String?,
    addLabel: String,
    onSelectOwner: (Long?) -> Unit,
    onSelectPayer: (Long?) -> Unit,
    onAddMember: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier
            .fillMaxWidth(),
            // 不用 IntrinsicSize.Min：FlowRow 的固有高度测量偏小会裁掉多行成员；
            // 两卡片标题行已等高且渲染同一成员列表，自然包裹内容即天然等高，
            // 成员换行时边框随内容自动向下延伸
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        // 左卡片：这笔归谁（含「+ 成员」入口）
        Column(
            modifier = Modifier
                .weight(1f)
                .clip(MaterialTheme.shapes.small)
                .background(MaterialTheme.colorScheme.surface)
                .border(1.dp, MaterialTheme.colorScheme.outline, MaterialTheme.shapes.small)
                .padding(horizontal = 10.dp, vertical = 8.dp),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = ownerTitle,
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                )
                Spacer(Modifier.weight(1f))
                AddMemberChip(label = addLabel, onClick = onAddMember)
            }
            Spacer(Modifier.height(6.dp))
            MemberChipsRow(
                selfLabel = selfLabel,
                members = members,
                selectedId = selectedOwnerId,
                publicLabel = publicLabel,
                onSelect = onSelectOwner,
            )
        }
        // 右卡片：谁垫付
        Column(
            modifier = Modifier
                .weight(1f)
                .clip(MaterialTheme.shapes.small)
                .background(MaterialTheme.colorScheme.surface)
                .border(1.dp, MaterialTheme.colorScheme.outline, MaterialTheme.shapes.small)
                .padding(horizontal = 10.dp, vertical = 8.dp),
        ) {
            Text(
                text = payerTitle,
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                // 等高对齐：左卡片标题行被「+ 成员」胶囊撑高（labelSmall 行高 14sp + 3dp×2），
                // 此处补 labelMedium(16sp) + 2dp×2 = 同高，两卡片标题与成员行基线完全对齐
                modifier = Modifier.padding(vertical = 2.dp),
            )
            Spacer(Modifier.height(6.dp))
            MemberChipsRow(
                selfLabel = selfLabel,
                members = members,
                selectedId = selectedPayerId,
                publicLabel = publicLabel,
                onSelect = onSelectPayer,
            )
        }
    }
}

/**
 * 「+ 成员」小胶囊：Box + clickable 实现（布局高度不膨胀），
 * 视觉与 MemberChip 同风格，避免 Surface(onClick) 的 48dp 最小布局尺寸。
 */
@Composable
private fun AddMemberChip(label: String, onClick: () -> Unit) {
    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(50))
            .background(MaterialTheme.colorScheme.primaryContainer)
            .clickable(onClick = onClick)
            .padding(horizontal = 8.dp, vertical = 3.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.primary,
            maxLines = 1,
        )
    }
}

/**
 * 成员胶囊流式布局：「本人」固定在最左；宽度不足时自动换行（一般两行内放下），
 * 使用紧凑模式胶囊（更小内边距与字号），不再横向滚动裁切。
 * isPublic 成员显示为公共文案。
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun MemberChipsRow(
    selfLabel: String,
    members: List<MemberEntity>,
    selectedId: Long?,
    publicLabel: String?,
    onSelect: (Long?) -> Unit,
) {
    FlowRow(
        horizontalArrangement = Arrangement.spacedBy(6.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        MemberChip(
            name = selfLabel,
            color = MaterialTheme.colorScheme.primary,
            selected = selectedId == null,
            onClick = { onSelect(null) },
            compact = true,
        )
        members.forEach { member ->
            val isPublic = member.isPublic && publicLabel != null
            if (!member.isPublic || publicLabel != null) {
                MemberChip(
                    name = if (isPublic) publicLabel!! else member.name,
                    color = Color(member.color),
                    selected = selectedId == member.id,
                    onClick = { onSelect(member.id) },
                    compact = true,
                )
            }
        }
    }
}

@Composable
private fun CategoryCell(
    category: CategoryEntity,
    selected: Boolean,
    onClick: () -> Unit,
    onLongClick: () -> Unit,
    modifier: Modifier = Modifier,
    /**
     * 实际渲染用的图标 key。默认等于分类自身图标；
     * 「仅本次使用」生效且本格是选中分类时，由调用方传入临时图标 key，
     * 此时只换图标、保留下方分类原名不变。
     */
    iconOverrideKey: String = category.icon,
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
            .padding(vertical = 4.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        CategoryIcon(iconKey = iconOverrideKey, color = category.color.argb(), size = 34)
        Spacer(Modifier.height(4.dp))
        Text(
            // 注意：这里始终显示分类原名，不用临时图标的名字——
            // 「仅本次使用」只改图标外观，不改这个格子代表的分类
            text = category.name,
            style = MaterialTheme.typography.labelSmall,
            color = if (selected) category.color.argb() else MaterialTheme.colorScheme.onSurface,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

/**
 * 大号自定义数字键盘：3 列数字区 + 右侧删除与「完成」键。
 * 完成键只收起键盘（金额输入完毕），保存走键盘收起后的底部按钮，
 * 避免分类/备注等信息还没填就误存。
 */
@Composable
fun AmountKeyboard(
    enabled: Boolean,
    onKey: (String) -> Unit,
    onClear: () -> Unit,
    onDone: () -> Unit,
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
            // 右列：删除 + 完成（收起键盘，继续填其他信息）
            Column(Modifier.weight(1f)) {
                // 退格大键：点击逐位删除，长按清空全部（此前点击直接清空，易误删）
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f)
                        .clip(MaterialTheme.shapes.small)
                        .background(MaterialTheme.colorScheme.surfaceVariant)
                        .combinedClickable(
                            onClick = { onKey("del") },
                            onLongClick = onClear,
                        ),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(
                        Icons.Outlined.Backspace,
                        contentDescription = stringResource(R.string.delete),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                Spacer(Modifier.height(10.dp))
                Surface(
                    onClick = onDone,
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
                            stringResource(R.string.record_done),
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
