package com.lightledger.app.ui.books

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.MoreVert
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.lightledger.app.R
import com.lightledger.app.data.db.entity.AccountBookEntity
import com.lightledger.app.domain.model.IconLibrary
import com.lightledger.app.data.repository.SeedData
import com.lightledger.app.ui.app.AppViewModel
import com.lightledger.app.ui.components.AppCard
import com.lightledger.app.ui.components.ConfirmDialog
import com.lightledger.app.ui.components.EmptyState
import com.lightledger.app.ui.record.TextInputDialog

/**
 * 账本管理：卡片列表，当前账本高亮边框；支持新建 / 重命名 / 换图标颜色 / 删除。
 * 旅行账本显示"旅行"标识，可进入成员管理；新建时可勾选旅行账本。
 */
@Composable
fun BooksScreen(
    appViewModel: AppViewModel,
    onBack: () -> Unit,
    onOpenMembers: (Long) -> Unit = {},
) {
    val books by appViewModel.books.collectAsStateWithLifecycle()
    val currentId by appViewModel.currentBookId.collectAsStateWithLifecycle()

    // 弹窗状态
    var showCreate by remember { mutableStateOf(false) }
    var renameTarget by remember { mutableStateOf<AccountBookEntity?>(null) }
    var deleteTarget by remember { mutableStateOf<AccountBookEntity?>(null) }
    var menuTarget by remember { mutableStateOf<AccountBookEntity?>(null) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .statusBarsPadding(),
    ) {
        // 顶栏
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 8.dp, vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            IconButton(onClick = onBack) {
                Icon(
                    Icons.AutoMirrored.Outlined.ArrowBack,
                    contentDescription = stringResource(R.string.back),
                    tint = MaterialTheme.colorScheme.onBackground,
                )
            }
            Text(stringResource(R.string.books_title), style = MaterialTheme.typography.titleLarge)
        }

        Column(
            modifier = Modifier
                .weight(1f)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp),
        ) {
            if (books.isEmpty()) {
                EmptyState(stringResource(R.string.books_empty))
            }
            books.forEach { book ->
                val isCurrent = book.id == currentId
                AppCard {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { appViewModel.switchBook(book.id) }
                            .padding(14.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        // 图标
                        Box(
                            modifier = Modifier
                                .size(44.dp)
                                .background(Color(book.color).copy(alpha = 0.15f), CircleShape),
                            contentAlignment = Alignment.Center,
                        ) {
                            Icon(
                                imageVector = IconLibrary.of(book.icon),
                                contentDescription = null,
                                tint = Color(book.color),
                                modifier = Modifier.size(22.dp),
                            )
                        }
                        Spacer(Modifier.width(12.dp))
                        Column(Modifier.weight(1f)) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text(book.name, style = MaterialTheme.typography.titleMedium)
                                if (book.isTrip) {
                                    Spacer(Modifier.width(6.dp))
                                    Text(
                                        stringResource(R.string.books_trip),
                                        style = MaterialTheme.typography.labelSmall,
                                        color = Color(book.color),
                                        modifier = Modifier
                                            .background(Color(book.color).copy(alpha = 0.14f), RoundedCornerShape(50))
                                            .padding(horizontal = 6.dp, vertical = 2.dp),
                                    )
                                }
                            }
                            Text(
                                if (isCurrent) stringResource(R.string.books_current) else stringResource(R.string.books_switch_hint),
                                style = MaterialTheme.typography.labelMedium,
                                color = if (isCurrent) MaterialTheme.colorScheme.primary
                                else MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                        if (isCurrent) {
                            Text("✓", color = MaterialTheme.colorScheme.primary,
                                style = MaterialTheme.typography.titleMedium)
                            Spacer(Modifier.width(4.dp))
                        }
                        Box {
                            IconButton(onClick = { menuTarget = book }) {
                                Icon(
                                    Icons.Outlined.MoreVert,
                                    contentDescription = stringResource(R.string.books_title),
                                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                            DropdownMenu(
                                expanded = menuTarget?.id == book.id,
                                onDismissRequest = { menuTarget = null },
                            ) {
                                if (book.isTrip) {
                                    DropdownMenuItem(
                                        text = { Text(stringResource(R.string.home_manage_members)) },
                                        onClick = {
                                            menuTarget = null
                                            onOpenMembers(book.id)
                                        },
                                    )
                                }
                                DropdownMenuItem(
                                    text = { Text(stringResource(if (book.isTrip) R.string.books_trip_off else R.string.books_trip_on)) },
                                    onClick = {
                                        appViewModel.setBookTrip(book.id, !book.isTrip)
                                        menuTarget = null
                                    },
                                )
                                DropdownMenuItem(
                                    text = { Text(stringResource(R.string.books_rename)) },
                                    onClick = {
                                        renameTarget = book
                                        menuTarget = null
                                    },
                                )
                                DropdownMenuItem(
                                    text = { Text(stringResource(R.string.books_delete)) },
                                    onClick = {
                                        deleteTarget = book
                                        menuTarget = null
                                    },
                                )
                            }
                        }
                    }
                }
                Spacer(Modifier.height(10.dp))
            }
            Spacer(Modifier.height(64.dp))
        }

        // 新建按钮（悬浮于底部）
    }

    // 新建账本 FAB
    Box(Modifier.fillMaxSize()) {
        Surface(
            onClick = { showCreate = true },
            shape = CircleShape,
            color = MaterialTheme.colorScheme.primary,
            shadowElevation = 6.dp,
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .padding(end = 20.dp, bottom = 28.dp),
        ) {
            Row(
                modifier = Modifier.padding(horizontal = 20.dp, vertical = 14.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(
                    Icons.Outlined.Add,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onPrimary,
                )
                Spacer(Modifier.width(4.dp))
                Text(stringResource(R.string.books_new), color = MaterialTheme.colorScheme.onPrimary,
                    style = MaterialTheme.typography.labelLarge)
            }
        }
    }

    // ---------- 新建账本弹窗 ----------
    if (showCreate) {
        BookEditorDialog(
            title = stringResource(R.string.books_new),
            initialName = "",
            initialIcon = "flight",
            initialColor = SeedData.bookColors.first(),
            onDismiss = { showCreate = false },
            onConfirm = { name, icon, color, isTrip ->
                appViewModel.createBook(name, icon, color, isTrip)
                showCreate = false
            },
        )
    }

    // ---------- 重命名 ----------
    renameTarget?.let { target ->
        TextInputDialog(
            title = stringResource(R.string.books_rename_title),
            initial = target.name,
            onDismiss = { renameTarget = null },
            onConfirm = {
                appViewModel.renameBook(target.id, it)
                renameTarget = null
            },
        )
    }

    // ---------- 删除确认 ----------
    deleteTarget?.let { target ->
        ConfirmDialog(
            title = stringResource(R.string.books_delete_title, target.name),
            message = stringResource(R.string.books_delete_msg),
            onConfirm = { appViewModel.deleteBook(target.id) },
            onDismiss = { deleteTarget = null },
        )
    }
}

/**
 * 新建账本弹窗：名称 + 图标 + 颜色 + 旅行账本开关。
 */
@Composable
private fun BookEditorDialog(
    title: String,
    initialName: String,
    initialIcon: String,
    initialColor: Int,
    onDismiss: () -> Unit,
    onConfirm: (name: String, icon: String, color: Int, isTrip: Boolean) -> Unit,
) {
    var name by remember { mutableStateOf(initialName) }
    var iconKey by remember { mutableStateOf(initialIcon) }
    var color by remember { mutableStateOf(initialColor) }
    var isTrip by remember { mutableStateOf(false) }

    AlertDialog(
        onDismissRequest = onDismiss,
        shape = MaterialTheme.shapes.large,
        title = { Text(title, style = MaterialTheme.typography.titleMedium) },
        text = {
            Column {
                OutlinedTextField(
                    value = name,
                    onValueChange = { if (it.length <= 12) name = it },
                    placeholder = { Text(stringResource(R.string.books_name_hint)) },
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
                                .background(
                                    if (selected) Color(color)
                                    else MaterialTheme.colorScheme.surfaceVariant,
                                    CircleShape,
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
                    SeedData.bookColors.forEach { c ->
                        val selected = c == color
                        Box(
                            modifier = Modifier
                                .size(30.dp)
                                .background(Color(c), CircleShape)
                                .border(
                                    width = if (selected) 2.5.dp else 0.dp,
                                    color = MaterialTheme.colorScheme.onSurface,
                                    shape = CircleShape,
                                )
                                .clickable { color = c },
                        )
                    }
                }
                Spacer(Modifier.height(14.dp))
                // 旅行账本开关
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(12.dp))
                        .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f))
                        .clickable { isTrip = !isTrip }
                        .padding(horizontal = 12.dp, vertical = 10.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Icon(
                        imageVector = IconLibrary.of("flight"),
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(18.dp),
                    )
                    Spacer(Modifier.width(8.dp))
                    Column(Modifier.weight(1f)) {
                        Text(stringResource(R.string.books_trip_switch), style = MaterialTheme.typography.bodyMedium)
                        Text(
                            stringResource(R.string.books_trip_hint),
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    Switch(
                        checked = isTrip,
                        onCheckedChange = { isTrip = it },
                    )
                }
            }
        },
        confirmButton = {
            TextButton(
                onClick = { onConfirm(name, iconKey, color, isTrip) },
                enabled = name.isNotBlank(),
            ) { Text(stringResource(R.string.create)) }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(R.string.cancel)) }
        },
    )
}
