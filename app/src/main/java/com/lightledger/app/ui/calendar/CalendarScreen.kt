package com.lightledger.app.ui.calendar

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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.KeyboardArrowLeft
import androidx.compose.material.icons.automirrored.outlined.KeyboardArrowRight
import androidx.compose.material3.AlertDialog
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
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.lightledger.app.LightLedgerApp
import com.lightledger.app.R
import com.lightledger.app.data.db.entity.CategoryEntity
import com.lightledger.app.data.db.entity.TransactionEntity
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
import java.time.YearMonth
import java.time.ZoneId

/**
 * 日历页：月历展示每日支出（红色小字），今日高亮；
 * 点击日期格筛选下方账单列表到该日，再次点击回到整月。
 *
 * 「本月账单」列表支持多选：勾选一笔或多笔账单后可生成汇总图片或批量删除。
 */
@Composable
fun CalendarScreen(
    appViewModel: AppViewModel,
    onOpenDetail: (Long) -> Unit,
) {
    val container = (LocalContext.current.applicationContext as LightLedgerApp).container
    val viewModel: CalendarViewModel = viewModel(
        factory = viewModelFactory {
            initializer { CalendarViewModel(container, appViewModel.currentBookId) }
        }
    )
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val guideMultiDone by viewModel.guideCalendarMultiDone.collectAsStateWithLifecycle()

    var yearMonth by remember { mutableStateOf(YearMonth.now()) }
    var selectedDay by remember { mutableStateOf<LocalDate?>(null) }

    val zone = remember { ZoneId.systemDefault() }
    val today = remember { LocalDate.now() }

    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    val themeMode by appViewModel.themeMode.collectAsStateWithLifecycle()
    val systemDark = isSystemInDarkTheme()
    val isDark = when (themeMode) {
        ThemeMode.LIGHT -> false
        ThemeMode.DARK -> true
        ThemeMode.SYSTEM -> systemDark
    }

    // ---------- 多选模式状态 ----------
    var selectionMode by remember { mutableStateOf(false) }
    var selectedIds by remember { mutableStateOf(emptySet<Long>()) }
    var confirmDeleteBills by remember { mutableStateOf(false) }
    var exportingImage by remember { mutableStateOf(false) }
    var showExportMaskChoice by remember { mutableStateOf(false) }
    var previewImages by remember { mutableStateOf<List<String>?>(null) }
    var previewIndex by remember { mutableStateOf(0) }

    val selfLabel = stringResource(R.string.record_self)
    val publicLabel = stringResource(R.string.member_public)

    // ---------- 本月账单 + 按日聚合 ----------
    val monthTxs = remember(state.txs, yearMonth, zone) {
        val y = yearMonth.year
        val m = yearMonth.monthValue
        state.txs.filter { tx ->
            val d = Instant.ofEpochMilli(tx.createdAt).atZone(zone).toLocalDate()
            d.year == y && d.monthValue == m
        }
    }
    val byDay = remember(monthTxs, state.categories, zone) {
        monthTxs.groupBy(
            keySelector = { tx -> Instant.ofEpochMilli(tx.createdAt).atZone(zone).toLocalDate() },
            valueTransform = { tx -> CalendarBillItem(tx, state.categories[tx.categoryId]) },
        )
    }
    val monthExpense = remember(monthTxs) {
        monthTxs.filter { it.type == TransactionType.EXPENSE.value }.sumOf { it.amount }
    }
    val monthIncome = remember(monthTxs) {
        monthTxs.filter { it.type == TransactionType.INCOME.value }.sumOf { it.amount }
    }

    // ---------- 网格布局：周日起始，前后补空 ----------
    val daysInMonth = yearMonth.lengthOfMonth()
    val leadingBlanks = yearMonth.atDay(1).dayOfWeek.value % 7 // 周日=7 → 0
    val rows = (leadingBlanks + daysInMonth + 6) / 7
    val weekOrder = remember {
        listOf(
            java.time.DayOfWeek.SUNDAY, java.time.DayOfWeek.MONDAY, java.time.DayOfWeek.TUESDAY,
            java.time.DayOfWeek.WEDNESDAY, java.time.DayOfWeek.THURSDAY, java.time.DayOfWeek.FRIDAY,
            java.time.DayOfWeek.SATURDAY,
        )
    }

    // ---------- 列表：选中某日 → 该日账单；否则整月按日分组 ----------
    val displayGroups = remember(selectedDay, byDay) {
        if (selectedDay != null) byDay.filterKeys { it == selectedDay } else byDay
    }

    // 当前列表可见的全部账单（多选统计与全选仅作用于可见范围，符合直觉）
    val visibleItems = remember(displayGroups) { displayGroups.values.flatten() }
    val selItems = remember(visibleItems, selectedIds) {
        visibleItems.filter { it.tx.id in selectedIds }
    }
    val selExpense = selItems
        .filter { it.tx.type == TransactionType.EXPENSE.value }
        .sumOf { it.tx.amount }
    val selIncome = selItems
        .filter { it.tx.type == TransactionType.INCOME.value }
        .sumOf { it.tx.amount }
    val selNet = selExpense - selIncome
    val allSelected = visibleItems.isNotEmpty() && selectedIds.size == visibleItems.size

    fun exitSelection() {
        selectionMode = false
        selectedIds = emptySet()
    }

    // 切换月份/日期时退出多选，避免选中项跨月残留造成统计困惑
    fun resetSelection() {
        if (selectionMode) exitSelection()
    }

    // ---------- 导出选中账单汇总图（mask=true 时所有金额打码为「¥***」） ----------
    fun doExportSummary(mask: Boolean) {
        val bookName = appViewModel.currentBook.value?.name
            ?: context.getString(R.string.default_book)
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
                timeText = DateUtils.formatBillTime(item.tx.createdAt),
                thumbnailPath = item.tx.images.firstOrNull(),
                memberName = if (state.isTrip) {
                    state.members[item.tx.memberId]
                        ?.let { if (it.isPublic) publicLabel else it.name } ?: selfLabel
                } else null,
                memberColor = if (state.isTrip && state.members[item.tx.memberId] == null) {
                    0xFF6C7A9C.toInt()
                } else state.members[item.tx.memberId]?.color ?: 0xFF5BB3A2.toInt(),
                payerName = if (state.isTrip) {
                    state.members[item.tx.payerMemberId]
                        ?.let { if (it.isPublic) publicLabel else it.name } ?: selfLabel
                } else null,
                payerColor = if (state.isTrip && state.members[item.tx.payerMemberId] == null) {
                    0xFF6C7A9C.toInt()
                } else state.members[item.tx.payerMemberId]?.color ?: 0xFF5BB3A2.toInt(),
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
            Text(
                stringResource(R.string.nav_calendar),
                style = MaterialTheme.typography.headlineLarge,
            )
            Spacer(Modifier.height(10.dp))

            // ---------- 月份切换 + 本月收支 ----------
            Row(verticalAlignment = Alignment.CenterVertically) {
                IconButton(onClick = {
                    yearMonth = yearMonth.minusMonths(1)
                    selectedDay = null
                    resetSelection()
                }) {
                    Icon(
                        Icons.AutoMirrored.Outlined.KeyboardArrowLeft,
                        contentDescription = stringResource(R.string.calendar_prev_month),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                Text(
                    text = DateUtils.formatYearMonth(yearMonth),
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.weight(1f),
                )
                IconButton(onClick = {
                    yearMonth = yearMonth.plusMonths(1)
                    selectedDay = null
                    resetSelection()
                }) {
                    Icon(
                        Icons.AutoMirrored.Outlined.KeyboardArrowRight,
                        contentDescription = stringResource(R.string.calendar_next_month),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            Spacer(Modifier.height(4.dp))
            Row(Modifier.fillMaxWidth()) {
                Text(
                    text = stringResource(R.string.record_expense) + " ¥" + MoneyFormat.fenToString(monthExpense),
                    style = MaterialTheme.typography.labelLarge,
                    color = SemanticTheme.colors.expense,
                )
                Spacer(Modifier.width(14.dp))
                Text(
                    text = stringResource(R.string.record_income) + " ¥" + MoneyFormat.fenToString(monthIncome),
                    style = MaterialTheme.typography.labelLarge,
                    color = SemanticTheme.colors.income,
                )
            }

            Spacer(Modifier.height(10.dp))

            // ---------- 月历卡片 ----------
            AppCard {
                Column(Modifier.padding(horizontal = 10.dp, vertical = 12.dp)) {
                    // 周标题行
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        weekOrder.forEach { dow ->
                            Text(
                                text = DateUtils.dowText(dow).let { it.substring(it.length - 1) },
                                style = MaterialTheme.typography.labelMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                textAlign = TextAlign.Center,
                                modifier = Modifier.weight(1f),
                            )
                        }
                    }
                    Spacer(Modifier.height(6.dp))
                    // 日期网格
                    repeat(rows) { row ->
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            repeat(7) { col ->
                                val dayNum = row * 7 + col - leadingBlanks + 1
                                if (dayNum in 1..daysInMonth) {
                                    val date = yearMonth.atDay(dayNum)
                                    val dayItems = byDay[date].orEmpty()
                                    val expense = dayItems
                                        .filter { it.tx.type == TransactionType.EXPENSE.value }
                                        .sumOf { it.tx.amount }
                                    val income = dayItems
                                        .filter { it.tx.type == TransactionType.INCOME.value }
                                        .sumOf { it.tx.amount }
                                    DayCell(
                                        date = date,
                                        expenseFen = expense,
                                        incomeFen = income,
                                        isToday = date == today,
                                        isSelected = date == selectedDay,
                                        onClick = {
                                            selectedDay = if (date == selectedDay) null else date
                                            resetSelection()
                                        },
                                        modifier = Modifier.weight(1f),
                                    )
                                } else {
                                    Spacer(Modifier.weight(1f))
                                }
                            }
                        }
                    }
                    Spacer(Modifier.height(2.dp))
                    Text(
                        text = stringResource(R.string.calendar_tap_hint),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
                        modifier = Modifier.padding(start = 4.dp),
                    )
                }
            }

            Spacer(Modifier.height(14.dp))

            // ---------- 账单列表标题 + 多选入口 ----------
            SectionTitle(
                text = selectedDay?.let { stringResource(R.string.calendar_bills_of_day, DateUtils.formatGroupDate(it)) }
                    ?: stringResource(R.string.calendar_bills_month),
                trailing = {
                    Text(
                        text = if (selectionMode) stringResource(R.string.home_exit_select)
                        else stringResource(R.string.calendar_select),
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

            // ---------- 小字说明：告诉用户可多选单笔账单导出图片 ----------
            Text(
                text = stringResource(R.string.calendar_multi_hint),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.8f),
                modifier = Modifier.padding(start = 2.dp, top = 4.dp),
            )

            // ---------- 多选功能说明（一次性） ----------
            AnimatedVisibility(visible = selectionMode && !guideMultiDone) {
                AppCard(modifier = Modifier.padding(top = 8.dp)) {
                    Column(Modifier.padding(horizontal = 16.dp, vertical = 12.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(
                                stringResource(R.string.guide_calendar_multi_title),
                                style = MaterialTheme.typography.titleSmall,
                                modifier = Modifier.weight(1f),
                            )
                            Text(
                                stringResource(R.string.got_it),
                                style = MaterialTheme.typography.labelLarge,
                                color = MaterialTheme.colorScheme.primary,
                                modifier = Modifier
                                    .clip(RoundedCornerShape(50))
                                    .clickable { viewModel.markGuideCalendarMultiDone() }
                                    .padding(horizontal = 10.dp, vertical = 4.dp),
                            )
                        }
                        Spacer(Modifier.height(4.dp))
                        Text(
                            stringResource(R.string.guide_calendar_multi_body),
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
                                        visibleItems.map { it.tx.id }.toSet()
                                    }
                                }
                                .padding(horizontal = 8.dp, vertical = 6.dp),
                        )
                    }
                }
            }

            Spacer(Modifier.height(8.dp))
            AppCard {
                if (displayGroups.isEmpty()) {
                    EmptyState(stringResource(R.string.home_empty))
                } else {
                    Column(Modifier.padding(vertical = 6.dp)) {
                        displayGroups.forEach { (day, items) ->
                            val dayExpense = items
                                .filter { it.tx.type == TransactionType.EXPENSE.value }
                                .sumOf { it.tx.amount }
                            val dayIncome = items
                                .filter { it.tx.type == TransactionType.INCOME.value }
                                .sumOf { it.tx.amount }
                            DaySummaryHeader(day, dayExpense, dayIncome)
                            items.forEach { item ->
                                val category = item.category
                                BillRow(
                                    categoryName = category?.name ?: stringResource(R.string.uncategorized),
                                    categoryIcon = item.tx.iconOverride ?: category?.icon ?: "star",
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

/** 日历列表行：账单 + 分类（供行渲染取图标/名称） */
private data class CalendarBillItem(
    val tx: TransactionEntity,
    val category: CategoryEntity?,
)

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

/** 日历格：日号（今日圆底高亮）+ 当日支出红色小字；选中淡色圆角底 */
@Composable
private fun DayCell(
    date: LocalDate,
    expenseFen: Long,
    incomeFen: Long,
    isToday: Boolean,
    isSelected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val bg = when {
        isSelected -> MaterialTheme.colorScheme.primary.copy(alpha = 0.13f)
        isToday -> MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.5f)
        else -> MaterialTheme.colorScheme.surface
    }
    Column(
        modifier = modifier
            .padding(horizontal = 2.dp, vertical = 2.dp)
            .clip(RoundedCornerShape(10.dp))
            .background(bg)
            .clickable(onClick = onClick)
            .padding(vertical = 5.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Box(
            contentAlignment = Alignment.Center,
            modifier = Modifier
                .size(26.dp)
                .background(
                    if (isToday) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surface,
                    CircleShape,
                ),
        ) {
            Text(
                text = date.dayOfMonth.toString(),
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = if (isToday) FontWeight.SemiBold else FontWeight.Normal,
                color = if (isToday) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurface,
            )
        }
        // 支出：红色带负号；数值过大用 k/m 缩写
        if (expenseFen > 0) {
            Text(
                text = MoneyFormat.fenToCompactSigned(expenseFen, negative = true),
                style = MaterialTheme.typography.labelSmall,
                fontSize = 10.sp,
                lineHeight = 12.sp,
                color = SemanticTheme.colors.expense,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                textAlign = TextAlign.Center,
            )
        } else {
            // 无支出时占同等高度，保证每行格子等高
            Spacer(Modifier.height(12.dp))
        }
        // 收入：绿色带正号
        if (incomeFen > 0) {
            Text(
                text = MoneyFormat.fenToCompactSigned(incomeFen, negative = false),
                style = MaterialTheme.typography.labelSmall,
                fontSize = 10.sp,
                lineHeight = 12.sp,
                color = SemanticTheme.colors.income,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                textAlign = TextAlign.Center,
            )
        } else {
            Spacer(Modifier.height(12.dp))
        }
    }
}

/** 日历列表组头：日期 + 当日支出/收入合计（不可折叠，样式与首页组头一致） */
@Composable
private fun DaySummaryHeader(
    day: LocalDate,
    expenseFen: Long,
    incomeFen: Long,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(start = 8.dp, end = 8.dp, top = 12.dp, bottom = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = DateUtils.formatGroupDate(day),
            style = MaterialTheme.typography.titleSmall,
            fontWeight = FontWeight.SemiBold,
        )
        Spacer(Modifier.weight(1f))
        if (expenseFen > 0) {
            // 「支出」标签弱化为灰，仅金额保留语义色，降低整屏红色密度
            Text(
                text = stringResource(R.string.record_expense),
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Text(
                text = " ¥" + MoneyFormat.fenToString(expenseFen),
                style = MaterialTheme.typography.labelMedium,
                color = SemanticTheme.colors.expense,
            )
        }
        if (incomeFen > 0) {
            Spacer(Modifier.width(10.dp))
            Text(
                text = stringResource(R.string.record_income),
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Text(
                text = " ¥" + MoneyFormat.fenToString(incomeFen),
                style = MaterialTheme.typography.labelMedium,
                color = SemanticTheme.colors.income,
            )
        }
    }
}
