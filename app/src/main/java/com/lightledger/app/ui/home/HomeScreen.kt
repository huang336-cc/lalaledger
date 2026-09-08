package com.lightledger.app.ui.home

import android.widget.Toast
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.ArrowDropDown
import androidx.compose.material.icons.outlined.DarkMode
import androidx.compose.material.icons.outlined.EditNote
import androidx.compose.material.icons.outlined.Group
import androidx.compose.material.icons.outlined.LightMode
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material.icons.outlined.SettingsBackupRestore
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.lightledger.app.LightLedgerApp
import com.lightledger.app.R
import com.lightledger.app.domain.model.IconLibrary
import com.lightledger.app.domain.model.ThemeMode
import com.lightledger.app.domain.model.TransactionType
import com.lightledger.app.ui.app.AppViewModel
import com.lightledger.app.ui.components.AppCard
import com.lightledger.app.ui.components.BillRow
import com.lightledger.app.ui.components.ConfirmDialog
import com.lightledger.app.ui.components.EmptyState
import com.lightledger.app.ui.components.ImagePreviewDialog
import com.lightledger.app.ui.components.SectionTitle
import com.lightledger.app.ui.theme.SemanticTheme
import com.lightledger.app.ui.theme.argb
import com.lightledger.app.util.DateUtils
import com.lightledger.app.util.MoneyFormat
import com.lightledger.app.util.SummaryImageExporter

import kotlinx.coroutines.launch
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId

/**
 * 首页：账本切换 + 主题快捷入口 + 记一笔按钮 + 三张支出统计卡 + 最近账单。
 * 账单列表支持多选模式：圆形勾选框 + 全选 + 实时统计 + 生成汇总图片 / 批量删除。
 */
@Composable
fun HomeScreen(
    appViewModel: AppViewModel,
    onOpenBooks: () -> Unit,
    onOpenDetail: (Long) -> Unit,
    onGoRecord: () -> Unit,
    onOpenMembers: (Long) -> Unit = {},
    onOpenSearch: () -> Unit = {},
) {
    val container = (LocalContext.current.applicationContext as LightLedgerApp).container
    val viewModel: HomeViewModel = viewModel(
        factory = viewModelFactory {
            initializer { HomeViewModel(container, appViewModel.currentBookId) }
        }
    )

    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val guideTripDone by viewModel.guideTripDone.collectAsStateWithLifecycle()
    val guideMultiDone by viewModel.guideMultiDone.collectAsStateWithLifecycle()
    val books by appViewModel.books.collectAsStateWithLifecycle()
    val currentBook by appViewModel.currentBook.collectAsStateWithLifecycle()
    val themeMode by appViewModel.themeMode.collectAsStateWithLifecycle()
    val currentBookId by appViewModel.currentBookId.collectAsStateWithLifecycle()

    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var bookMenuOpen by remember { mutableStateOf(false) }

    // ---------- 多选模式状态 ----------
    var selectionMode by remember { mutableStateOf(false) }
    var selectedIds by remember { mutableStateOf(emptySet<Long>()) }
    var confirmDeleteBills by remember { mutableStateOf(false) }
    var exportingImage by remember { mutableStateOf(false) }
    // 导出分享图前询问是否隐藏金额（打码分享）
    var showExportMaskChoice by remember { mutableStateOf(false) }
    var previewImages by remember { mutableStateOf<List<String>?>(null) }
    var previewIndex by remember { mutableStateOf(0) }

    // ---------- 日期分组折叠状态（记录已折叠的日期） ----------
    var collapsedDays by remember { mutableStateOf(emptySet<LocalDate>()) }

    val selfLabel = stringResource(R.string.record_self)
    val publicLabel = stringResource(R.string.member_public)

    val systemDark = isSystemInDarkTheme()
    val isDark = when (themeMode) {
        ThemeMode.LIGHT -> false
        ThemeMode.DARK -> true
        ThemeMode.SYSTEM -> systemDark
    }

    // ---------- 选中项实时统计 ----------
    val selItems = state.recent.filter { it.tx.id in selectedIds }
    val selExpense = selItems
        .filter { it.tx.type == TransactionType.EXPENSE.value }
        .sumOf { it.tx.amount }
    val selIncome = selItems
        .filter { it.tx.type == TransactionType.INCOME.value }
        .sumOf { it.tx.amount }
    val selNet = selExpense - selIncome
    val allSelected = state.recent.isNotEmpty() && selectedIds.size == state.recent.size

    fun exitSelection() {
        selectionMode = false
        selectedIds = emptySet()
    }

    // ---------- 导出选中账单汇总图（mask=true 时所有金额打码为「¥***」） ----------
    fun doExportSummary(mask: Boolean) {
        val bookName = currentBook?.name ?: context.getString(R.string.default_book)
        val fallbackColor = 0xFF9A968D.toInt()
        val maskText = "¥***"
        val rows = selItems.map { item ->
            SummaryImageExporter.Row(
                title = item.tx.note?.takeIf { it.isNotBlank() }
                    ?: (item.category?.name ?: context.getString(R.string.uncategorized)),
                categoryName = item.category?.name
                    ?: context.getString(R.string.uncategorized),
                categoryColor = item.category?.color ?: fallbackColor,
                amountText = if (mask) maskText
                else (if (item.tx.type == TransactionType.EXPENSE.value) "-" else "+") +
                    MoneyFormat.fenToPlain(item.tx.amount),
                isExpense = item.tx.type == TransactionType.EXPENSE.value,
                timeText = com.lightledger.app.util.DateUtils.formatBillTime(item.tx.createdAt),
                thumbnailPath = item.tx.images.firstOrNull(),
                memberName = if (state.isTrip) {
                    item.member?.let { if (it.isPublic) publicLabel else it.name } ?: selfLabel
                } else null,
                memberColor = if (state.isTrip && item.member == null) 0xFF6C7A9C.toInt()
                else item.member?.color ?: 0xFF5BB3A2.toInt(),
                payerName = if (state.isTrip) {
                    item.payer?.let { if (it.isPublic) publicLabel else it.name } ?: selfLabel
                } else null,
                payerColor = if (state.isTrip && item.payer == null) 0xFF6C7A9C.toInt()
                else item.payer?.color ?: 0xFF5BB3A2.toInt(),
            )
        }
        val summary = SummaryImageExporter.Summary(
            bookName = bookName,
            count = selItems.size,
            expenseText = if (mask) maskText else MoneyFormat.fenToString(selExpense),
            incomeText = if (mask) maskText else MoneyFormat.fenToString(selIncome),
            netText = if (mask) maskText else MoneyFormat.fenToString(selNet),
        )
        scope.launch {
            exportingImage = true
            val uri = SummaryImageExporter.export(context, summary, rows, isDark)
            exportingImage = false
            Toast.makeText(
                context,
                context.getString(
                    if (uri != null) R.string.export_saved else R.string.export_failed
                ),
                Toast.LENGTH_SHORT,
            ).show()
        }
    }

    Box(modifier = Modifier.fillMaxSize()) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp),
        ) {
            Spacer(Modifier.height(12.dp))

            // ---------- 顶部：账本切换 + 主题切换 ----------
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box {
                    Surface(
                        onClick = { bookMenuOpen = true },
                        shape = RoundedCornerShape(50),
                        color = MaterialTheme.colorScheme.surface,
                    ) {
                        Row(
                            modifier = Modifier.padding(start = 14.dp, end = 8.dp, top = 8.dp, bottom = 8.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Icon(
                                imageVector = IconLibrary.of(currentBook?.icon ?: "book"),
                                contentDescription = null,
                                tint = currentBook?.color?.argb() ?: MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(18.dp),
                            )
                            Spacer(Modifier.width(6.dp))
                            Text(
                                text = currentBook?.name ?: stringResource(R.string.select_book),
                                style = MaterialTheme.typography.titleMedium,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                            Icon(
                                imageVector = Icons.Outlined.ArrowDropDown,
                                contentDescription = stringResource(R.string.stats_switch_book),
                                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                    DropdownMenu(
                        expanded = bookMenuOpen,
                        onDismissRequest = { bookMenuOpen = false },
                        shape = MaterialTheme.shapes.medium,
                    ) {
                        books.forEach { book ->
                            DropdownMenuItem(
                                text = {
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        Icon(
                                            imageVector = IconLibrary.of(book.icon),
                                            contentDescription = null,
                                            tint = book.color.argb(),
                                            modifier = Modifier.size(18.dp),
                                        )
                                        Spacer(Modifier.width(8.dp))
                                        Text(book.name)
                                    }
                                },
                                trailingIcon = {
                                    if (book.id == currentBook?.id) {
                                        Text("✓", color = MaterialTheme.colorScheme.primary)
                                    }
                                },
                                onClick = {
                                    appViewModel.switchBook(book.id)
                                    bookMenuOpen = false
                                },
                            )
                        }
                        HorizontalDivider(color = MaterialTheme.colorScheme.outline)
                        // 旅行账本：进入成员管理
                        if (currentBook?.isTrip == true) {
                            DropdownMenuItem(
                                text = { Text(stringResource(R.string.home_manage_members)) },
                                leadingIcon = {
                                    Icon(
                                        Icons.Outlined.Group,
                                        contentDescription = null,
                                        modifier = Modifier.size(18.dp),
                                    )
                                },
                                onClick = {
                                    bookMenuOpen = false
                                    currentBook?.let { onOpenMembers(it.id) }
                                },
                            )
                        }
                        DropdownMenuItem(
                            text = { Text(stringResource(R.string.home_manage_books)) },
                            leadingIcon = {
                                Icon(
                                    Icons.Outlined.SettingsBackupRestore,
                                    contentDescription = null,
                                    modifier = Modifier.size(18.dp),
                                )
                            },
                            onClick = {
                                bookMenuOpen = false
                                onOpenBooks()
                            },
                        )
                    }
                }

                Spacer(Modifier.weight(1f))

                // 主题切换：浅/深一键切换；跟随系统模式在"我的"页选择
                IconButton(onClick = {
                    appViewModel.setThemeMode(if (isDark) ThemeMode.LIGHT else ThemeMode.DARK)
                }) {
                    Icon(
                        imageVector = if (isDark) Icons.Outlined.LightMode else Icons.Outlined.DarkMode,
                        contentDescription = stringResource(R.string.settings_theme),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }

            Spacer(Modifier.height(12.dp))

            // ---------- 账单搜索入口（点击进搜索页，自动聚焦） ----------
            Surface(
                onClick = onOpenSearch,
                shape = RoundedCornerShape(50),
                color = MaterialTheme.colorScheme.surface,
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 14.dp, vertical = 10.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Icon(
                        imageVector = Icons.Outlined.Search,
                        contentDescription = stringResource(R.string.search_hint),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(18.dp),
                    )
                    Spacer(Modifier.width(8.dp))
                    Text(
                        text = stringResource(R.string.search_hint),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }

            Spacer(Modifier.height(18.dp))

            // ---------- 记一笔 ----------
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(104.dp)
                    .clip(MaterialTheme.shapes.medium)
                    .background(
                        brush = Brush.horizontalGradient(
                            listOf(
                                MaterialTheme.colorScheme.primary,
                                if (isDark) Color(0xFF5D8FB5) else MaterialTheme.colorScheme.secondary,
                            )
                        )
                    )
                    .clickable(onClick = onGoRecord),
                contentAlignment = Alignment.Center,
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Column {
                        Text(
                            text = stringResource(R.string.home_add_bill),
                            style = MaterialTheme.typography.headlineMedium,
                            color = MaterialTheme.colorScheme.onPrimary,
                        )
                        Text(
                            text = stringResource(R.string.home_add_bill_sub),
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onPrimary.copy(alpha = 0.85f),
                        )
                    }
                    Spacer(Modifier.width(16.dp))
                    Icon(
                        imageVector = Icons.Outlined.EditNote,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onPrimary,
                        modifier = Modifier.size(40.dp),
                    )
                }
            }

            // ---------- 旅行账本引导卡（一次性，可跳成员管理） ----------
            if (state.isTrip && !guideTripDone) {
                Spacer(Modifier.height(14.dp))
                AppCard {
                    Column(Modifier.padding(16.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(
                                stringResource(R.string.guide_trip_title),
                                style = MaterialTheme.typography.titleMedium,
                                modifier = Modifier.weight(1f),
                            )
                            Text(
                                stringResource(R.string.got_it),
                                style = MaterialTheme.typography.labelLarge,
                                color = MaterialTheme.colorScheme.primary,
                                modifier = Modifier
                                    .clip(RoundedCornerShape(50))
                                    .clickable { viewModel.markGuideTripDone() }
                                    .padding(horizontal = 10.dp, vertical = 4.dp),
                            )
                        }
                        Spacer(Modifier.height(6.dp))
                        Text(
                            stringResource(R.string.guide_trip_body),
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        Spacer(Modifier.height(10.dp))
                        Text(
                            stringResource(R.string.guide_trip_action),
                            style = MaterialTheme.typography.labelLarge,
                            color = MaterialTheme.colorScheme.primary,
                            modifier = Modifier
                                .clip(RoundedCornerShape(50))
                                .clickable {
                                    viewModel.markGuideTripDone()
                                    currentBook?.let { onOpenMembers(it.id) }
                                }
                                .padding(horizontal = 10.dp, vertical = 4.dp),
                        )
                    }
                }
            }

            Spacer(Modifier.height(16.dp))

            // ---------- 三张统计卡片 ----------
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                StatCard(stringResource(R.string.home_today), state.todayExpense, Modifier.weight(1f))
                StatCard(stringResource(R.string.home_week), state.weekExpense, Modifier.weight(1f))
                StatCard(stringResource(R.string.home_month), state.monthExpense, Modifier.weight(1f))
            }

            Spacer(Modifier.height(20.dp))

            // ---------- 最近账单标题 + 多选入口 ----------
            SectionTitle(
                stringResource(R.string.home_recent),
                trailing = {
                    Text(
                        text = if (selectionMode) stringResource(R.string.home_exit_select)
                        else stringResource(R.string.home_select),
                        style = MaterialTheme.typography.labelLarge,
                        color = if (selectionMode) {
                            SemanticTheme.colors.expense
                        } else {
                            MaterialTheme.colorScheme.primary
                        },
                        modifier = Modifier
                            .clip(RoundedCornerShape(50))
                            .clickable {
                                if (selectionMode) exitSelection() else selectionMode = true
                            }
                            .padding(horizontal = 10.dp, vertical = 4.dp),
                    )
                },
            )

            // ---------- 多选功能说明（一次性） ----------
            AnimatedVisibility(visible = selectionMode && !guideMultiDone) {
                AppCard(modifier = Modifier.padding(top = 8.dp)) {
                    Column(Modifier.padding(horizontal = 16.dp, vertical = 12.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(
                                stringResource(R.string.guide_multi_title),
                                style = MaterialTheme.typography.titleSmall,
                                modifier = Modifier.weight(1f),
                            )
                            Text(
                                stringResource(R.string.got_it),
                                style = MaterialTheme.typography.labelLarge,
                                color = MaterialTheme.colorScheme.primary,
                                modifier = Modifier
                                    .clip(RoundedCornerShape(50))
                                    .clickable { viewModel.markGuideMultiDone() }
                                    .padding(horizontal = 10.dp, vertical = 4.dp),
                            )
                        }
                        Spacer(Modifier.height(4.dp))
                        Text(
                            stringResource(R.string.guide_multi_body),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }

            // ---------- 多选实时统计条 ----------
            AnimatedVisibility(visible = selectionMode) {
                AppCard(modifier = Modifier.padding(top = 8.dp)) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 14.dp, vertical = 12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Column {
                            SelectionStat(
                                stringResource(R.string.sel_count),
                                stringResource(R.string.home_bills_n, selItems.size),
                                MaterialTheme.colorScheme.onSurface,
                            )
                            Spacer(Modifier.height(4.dp))
                            SelectionStat(
                                stringResource(R.string.sel_expense),
                                "¥${MoneyFormat.fenToString(selExpense)}",
                                SemanticTheme.colors.expense,
                            )
                        }
                        Spacer(Modifier.width(16.dp))
                        Column {
                            SelectionStat(
                                stringResource(R.string.sel_income),
                                "¥${MoneyFormat.fenToString(selIncome)}",
                                SemanticTheme.colors.income,
                            )
                            Spacer(Modifier.height(4.dp))
                            SelectionStat(
                                stringResource(R.string.sel_net),
                                "¥${MoneyFormat.fenToString(selNet)}",
                                MaterialTheme.colorScheme.onSurface,
                            )
                        }
                        Spacer(Modifier.weight(1f))
                        Text(
                            text = if (allSelected) stringResource(R.string.home_clear_all)
                            else stringResource(R.string.home_select_all),
                            style = MaterialTheme.typography.labelLarge,
                            color = MaterialTheme.colorScheme.primary,
                            modifier = Modifier
                                .clip(RoundedCornerShape(50))
                                .clickable {
                                    selectedIds = if (allSelected) {
                                        emptySet()
                                    } else {
                                        state.recent.map { it.tx.id }.toSet()
                                    }
                                }
                                .padding(horizontal = 8.dp, vertical = 6.dp),
                        )
                    }
                }
            }

            Spacer(Modifier.height(8.dp))

            // ---------- 账单列表（按日期分组，组头显示当日收支合计，可折叠） ----------
            AppCard {
                if (state.recent.isEmpty()) {
                    EmptyState(stringResource(R.string.home_empty))
                } else {
                    Column(Modifier.padding(vertical = 6.dp)) {
                        val zone = remember { ZoneId.systemDefault() }
                        val dayGroups = remember(state.recent, zone) {
                            state.recent.groupBy { item ->
                                Instant.ofEpochMilli(item.tx.createdAt).atZone(zone).toLocalDate()
                            }
                        }
                        dayGroups.forEach { (day, items) ->
                            val dayExpense = items
                                .filter { it.tx.type == TransactionType.EXPENSE.value }
                                .sumOf { it.tx.amount }
                            val dayIncome = items
                                .filter { it.tx.type == TransactionType.INCOME.value }
                                .sumOf { it.tx.amount }
                            val collapsed = day in collapsedDays
                            DayGroupHeader(
                                day = day,
                                expenseFen = dayExpense,
                                incomeFen = dayIncome,
                                collapsed = collapsed,
                                onToggle = {
                                    collapsedDays = if (collapsed) collapsedDays - day else collapsedDays + day
                                },
                            )
                            if (!collapsed) {
                                items.forEach { item ->
                                    val category = item.category
                                    BillRow(
                                        categoryName = category?.name ?: stringResource(R.string.uncategorized),
                                        categoryIcon = category?.icon ?: "star",
                                        categoryColor = category?.color?.argb()
                                            ?: MaterialTheme.colorScheme.onSurfaceVariant,
                                        amountFen = item.tx.amount,
                                        type = TransactionType.from(item.tx.type),
                                        time = item.tx.createdAt,
                                        note = item.tx.note,
                                        mood = item.tx.mood,
                                        thumbnailPath = item.tx.images.firstOrNull(),
                                        onThumbnailClick = {
                                            previewImages = item.tx.images
                                            previewIndex = 0
                                        },
                                        selectionMode = selectionMode,
                                        selected = item.tx.id in selectedIds,
                                        onClick = {
                                            if (selectionMode) {
                                                selectedIds = if (item.tx.id in selectedIds) {
                                                    selectedIds - item.tx.id
                                                } else {
                                                    selectedIds + item.tx.id
                                                }
                                            } else {
                                                onOpenDetail(item.tx.id)
                                            }
                                        },
                                        // 长按账单：快速进入多选模式并选中该条
                                        onLongPress = {
                                            selectionMode = true
                                            selectedIds = setOf(item.tx.id)
                                        },
                                    )
                                }
                            }
                        }
                    }
                }
            }

            // 多选模式底部操作栏的空间占位
            AnimatedVisibility(visible = selectionMode) {
                Spacer(Modifier.height(96.dp))
            }

            Spacer(Modifier.height(16.dp))
        }

        // ---------- 多选模式底部操作栏 ----------
        AnimatedVisibility(
            visible = selectionMode,
            enter = slideInVertically(initialOffsetY = { it }) + fadeIn(),
            exit = slideOutVertically(targetOffsetY = { it }) + fadeOut(),
            modifier = Modifier.align(Alignment.BottomCenter),
        ) {
            Surface(
                color = MaterialTheme.colorScheme.surface,
                shadowElevation = 12.dp,
                shape = RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp),
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .navigationBarsPadding()
                        .padding(horizontal = 16.dp, vertical = 14.dp),
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    SelectionAction(
                        text = if (exportingImage) stringResource(R.string.action_exporting)
                        else stringResource(R.string.action_export_image),
                        filled = true,
                        enabled = selectedIds.isNotEmpty() && !exportingImage,
                        modifier = Modifier.weight(1.2f),
                        onClick = { showExportMaskChoice = true },
                    )
                    SelectionAction(
                        text = stringResource(R.string.action_batch_delete),
                        filled = false,
                        danger = true,
                        enabled = selectedIds.isNotEmpty(),
                        modifier = Modifier.weight(1f),
                        onClick = { confirmDeleteBills = true },
                    )
                    SelectionAction(
                        text = stringResource(R.string.cancel),
                        filled = false,
                        enabled = true,
                        modifier = Modifier.weight(0.8f),
                        onClick = { exitSelection() },
                    )
                }
            }
        }
    }

    // ---------- 批量删除二次确认 ----------
    if (confirmDeleteBills) {
        ConfirmDialog(
            title = stringResource(R.string.home_delete_bills_title, selectedIds.size),
            message = stringResource(R.string.home_delete_bills_msg),
            confirmText = stringResource(R.string.delete),
            onConfirm = {
                val ids = selectedIds.toList()
                scope.launch {
                    val n = viewModel.deleteBills(ids)
                    Toast.makeText(
                        context, context.getString(R.string.home_deleted_n, n), Toast.LENGTH_SHORT
                    ).show()
                }
                exitSelection()
            },
            onDismiss = { confirmDeleteBills = false },
        )
    }

    // ---------- 导出分享图：是否隐藏金额（打码分享） ----------
    if (showExportMaskChoice) {
        AlertDialog(
            onDismissRequest = { showExportMaskChoice = false },
            title = {
                Text(
                    stringResource(R.string.action_export_image),
                    style = MaterialTheme.typography.titleMedium,
                )
            },
            text = {
                Column {
                    Text(
                        stringResource(R.string.export_mask_ask),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Spacer(Modifier.height(12.dp))
                    TextButton(
                        onClick = {
                            showExportMaskChoice = false
                            doExportSummary(mask = false)
                        },
                        modifier = Modifier.fillMaxWidth(),
                    ) { Text(stringResource(R.string.export_with_amount)) }
                    TextButton(
                        onClick = {
                            showExportMaskChoice = false
                            doExportSummary(mask = true)
                        },
                        modifier = Modifier.fillMaxWidth(),
                    ) { Text(stringResource(R.string.export_mask_amount)) }
                }
            },
            confirmButton = {},
            dismissButton = {
                TextButton(onClick = { showExportMaskChoice = false }) {
                    Text(stringResource(R.string.cancel))
                }
            },
        )
    }

    // ---------- 缩略图预览 ----------
    previewImages?.let { images ->
        ImagePreviewDialog(
            images = images,
            initialIndex = previewIndex,
            onDismiss = { previewImages = null },
        )
    }
}

/** 首页账单按日分组的组头：日期 + 当日支出/收入合计 + 折叠箭头（点击折叠/展开当日账单） */
@Composable
private fun DayGroupHeader(
    day: LocalDate,
    expenseFen: Long,
    incomeFen: Long,
    collapsed: Boolean,
    onToggle: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(8.dp))
            .clickable(onClick = onToggle)
            .padding(horizontal = 8.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = DateUtils.formatGroupDate(day),
            style = MaterialTheme.typography.titleSmall,
            fontWeight = FontWeight.SemiBold,
        )
        Spacer(Modifier.weight(1f))
        if (expenseFen > 0) {
            Text(
                text = stringResource(R.string.record_expense) + " ¥" + MoneyFormat.fenToString(expenseFen),
                style = MaterialTheme.typography.labelMedium,
                color = SemanticTheme.colors.expense,
            )
        }
        if (incomeFen > 0) {
            Spacer(Modifier.width(10.dp))
            Text(
                text = stringResource(R.string.record_income) + " ¥" + MoneyFormat.fenToString(incomeFen),
                style = MaterialTheme.typography.labelMedium,
                color = SemanticTheme.colors.income,
            )
        }
        Icon(
            imageVector = Icons.Outlined.ArrowDropDown,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.rotate(if (collapsed) 0f else 180f),
        )
    }
}

/** 多选统计条里的单项（标签弱化 + 数值语义色） */
@Composable
private fun SelectionStat(label: String, value: String, valueColor: Color) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(Modifier.width(6.dp))
        Text(
            text = value,
            style = MaterialTheme.typography.labelLarge,
            color = valueColor,
        )
    }
}

/** 底部操作栏按钮：主操作填充渐变色，次要操作描边样式 */
@Composable
private fun SelectionAction(
    text: String,
    filled: Boolean,
    enabled: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    danger: Boolean = false,
) {
    val shape = RoundedCornerShape(50)
    if (filled) {
        Box(
            modifier = modifier
                .height(46.dp)
                .clip(shape)
                .background(
                    brush = Brush.horizontalGradient(
                        listOf(
                            if (enabled) MaterialTheme.colorScheme.primary
                            else MaterialTheme.colorScheme.surfaceVariant,
                            if (enabled) MaterialTheme.colorScheme.secondary
                            else MaterialTheme.colorScheme.surfaceVariant,
                        )
                    )
                )
                .clickable(enabled = enabled, onClick = onClick),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                text = text,
                style = MaterialTheme.typography.labelLarge,
                color = if (enabled) MaterialTheme.colorScheme.onPrimary
                else MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
            )
        }
    } else {
        val contentColor = when {
            !enabled -> MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f)
            danger -> SemanticTheme.colors.expense
            else -> MaterialTheme.colorScheme.onSurface
        }
        Box(
            modifier = modifier
                .height(46.dp)
                .clip(shape)
                .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.55f))
                .clickable(enabled = enabled, onClick = onClick),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                text = text,
                style = MaterialTheme.typography.labelLarge,
                color = contentColor,
                maxLines = 1,
            )
        }
    }
}

/** 首页小统计卡：标题弱化、数字突出 */
@Composable
private fun StatCard(
    title: String,
    amountFen: Long,
    modifier: Modifier = Modifier,
) {
    Card(
        modifier = modifier,
        shape = MaterialTheme.shapes.medium,
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
    ) {
        Column(Modifier.padding(horizontal = 12.dp, vertical = 14.dp)) {
            Text(
                text = title,
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
            )
            Spacer(Modifier.height(8.dp))
            Text(
                text = MoneyFormat.fenToString(amountFen),
                style = MaterialTheme.typography.titleLarge,
                color = SemanticTheme.colors.expense,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}
