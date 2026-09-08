package com.lightledger.app.ui.stats

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.widget.Toast
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
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.ArrowDropDown
import androidx.compose.material.icons.outlined.ContentCopy
import androidx.compose.material.icons.outlined.Description
import androidx.compose.material.icons.outlined.Group
import androidx.compose.material.icons.outlined.Image
import androidx.compose.material.icons.outlined.IosShare
import androidx.compose.material.icons.outlined.SwapHoriz
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DateRangePicker
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberDateRangePickerState
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.lightledger.app.LightLedgerApp
import com.lightledger.app.R
import com.lightledger.app.domain.model.StatPeriod
import com.lightledger.app.domain.model.ThemeMode
import com.lightledger.app.domain.model.TransactionType
import com.lightledger.app.ui.app.AppViewModel
import com.lightledger.app.ui.components.AppCard
import com.lightledger.app.ui.components.CategoryIcon
import com.lightledger.app.ui.components.EmptyState
import com.lightledger.app.ui.components.MemberChip
import com.lightledger.app.ui.components.SectionTitle
import com.lightledger.app.ui.theme.SemanticTheme
import com.lightledger.app.ui.theme.argb
import com.lightledger.app.util.CsvExporter
import com.lightledger.app.util.MoneyFormat
import com.lightledger.app.util.StatsImageExporter
import com.lightledger.app.util.DateUtils
import kotlinx.coroutines.launch
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId

/** StatPeriod → 文案资源 */
private fun StatPeriod.labelRes(): Int = when (this) {
    StatPeriod.Today -> R.string.period_today
    StatPeriod.ThisWeek -> R.string.period_week
    StatPeriod.ThisMonth -> R.string.period_month
    StatPeriod.All -> R.string.period_all
    is StatPeriod.Custom -> R.string.period_custom
}

/**
 * 统计面板：切换账本 + 四张汇总卡 + 时间筛选 + 分类筛选 + 归属/垫付成员筛选 +
 * 分类占比饼图 + 分类排行 + 成员明细 + AA 结算卡（复制结算文本）。
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun StatsScreen(
    appViewModel: AppViewModel,
    onOpenDetail: (Long) -> Unit,
) {
    val container = (LocalContext.current.applicationContext as LightLedgerApp).container
    val viewModel: StatsViewModel = viewModel(
        factory = viewModelFactory {
            initializer { StatsViewModel(container, appViewModel.currentBookId) }
        }
    )
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val books by viewModel.books.collectAsStateWithLifecycle()

    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var showRangePicker by remember { mutableStateOf(false) }
    var ownerMenuOpen by remember { mutableStateOf(false) }
    var payerMenuOpen by remember { mutableStateOf(false) }
    var bookMenuOpen by remember { mutableStateOf(false) }
    var exportingStats by remember { mutableStateOf(false) }
    // 导出方式弹窗（图片 / CSV）
    var showExportDialog by remember { mutableStateOf(false) }
    // 打码分享：打开后导出的图片/CSV 中所有金额替换为「¥***」
    var maskAmounts by remember { mutableStateOf(false) }
    // 饼图下钻：当前查看的分类 id（非空时弹出底部明细面板，分类数据实时从 state 取）
    var drilldownId by remember { mutableStateOf<Long?>(null) }
    // 饼图选中态：点扇区选中（中心联动 + 高亮），再点同一扇区或环心取消
    var selectedSliceId by remember { mutableStateOf<Long?>(null) }

    // 主题明暗（导出统计图配色用）
    val themeMode by appViewModel.themeMode.collectAsStateWithLifecycle()
    val systemDark = isSystemInDarkTheme()
    val isDark = when (themeMode) {
        ThemeMode.LIGHT -> false
        ThemeMode.DARK -> true
        ThemeMode.SYSTEM -> systemDark
    }

    // 成员名解析：null = 本人（随语言切换）；公共成员显示本地化名称
    val selfLabel = stringResource(R.string.record_self)
    val publicLabel = stringResource(R.string.member_public)
    val nameOf: (Long?) -> String = { id ->
        if (id == null) selfLabel
        else state.members.firstOrNull { it.id == id }?.let { if (it.isPublic) publicLabel else it.name }
            ?: selfLabel
    }
    val selfColor = MaterialTheme.colorScheme.primary
    val colorOf: (Long?) -> Color = { id ->
        if (id == null) selfColor
        else Color(state.members.firstOrNull { it.id == id }?.color ?: 0xFF6C7A9C.toInt())
    }

    // ---------- 导出统计长图（文案提前解析，onClick 非组合上下文） ----------
    val exportTitle = stringResource(R.string.stats_export_title)
    val rankTitleText = stringResource(R.string.stats_rank_title)
    val rankEmptyText = stringResource(R.string.stats_rank_empty)
    val expenseLabel = stringResource(R.string.stats_total_expense)
    val incomeLabel = stringResource(R.string.stats_total_income)
    val netLabel = stringResource(R.string.stats_net)
    val dailyLabel = stringResource(R.string.stats_daily)
    val memberTitleText = stringResource(R.string.stats_personal_title)
    val memberNoteText = stringResource(R.string.stats_personal_note)
    val consumeLabel = stringResource(R.string.stats_personal_consume)
    val paidLabel = stringResource(R.string.stats_personal_paid)
    val aaTitleText = stringResource(R.string.stats_aa_title)
    val aaEmptyText = stringResource(R.string.stats_aa_empty)
    val receiveLabel = stringResource(R.string.stats_aa_receive)
    val payLabel = stringResource(R.string.stats_aa_pay)
    val settledLabel = stringResource(R.string.stats_aa_settled)
    val transferFmt = stringResource(R.string.stats_aa_transfer_fmt)
    // 当前筛选时段文案（随 state.period 变化重组刷新）
    val periodText = stringResource(state.period.labelRes())

    fun buildStatsExportData(periodText: String, mask: Boolean): StatsImageExporter.StatsData {
        val maskText = "¥***"
        return StatsImageExporter.StatsData(
            titleText = exportTitle,
            bookName = state.statBook?.name ?: "",
            periodText = periodText,
            expenseLabel = expenseLabel,
            expenseText = if (mask) maskText else MoneyFormat.fenToString(state.totalExpense),
            incomeLabel = incomeLabel,
            incomeText = if (mask) maskText else MoneyFormat.fenToString(state.totalIncome),
            netLabel = netLabel,
            netText = if (mask) maskText else MoneyFormat.fenToString(state.netExpense),
            dailyLabel = dailyLabel,
            dailyText = if (mask) maskText else MoneyFormat.fenToString(state.dailyAvg),
            rankTitle = rankTitleText,
            rankEmptyText = rankEmptyText,
            categories = state.categories.take(15).map {
                StatsImageExporter.CategoryLine(
                    name = it.name,
                    color = it.color,
                    amountText = if (mask) maskText else MoneyFormat.fenToString(it.total),
                    percentText = "${(it.ratio * 100).toInt()}%",
                    ratio = it.ratio,
                )
            },
            memberTitle = if (state.isTrip && state.memberDetails.isNotEmpty()) memberTitleText else null,
            memberNote = if (state.isTrip && state.memberDetails.isNotEmpty()) memberNoteText else null,
            memberConsumeLabel = consumeLabel,
            memberPaidLabel = paidLabel,
            members = state.memberDetails.map {
                StatsImageExporter.MemberLine(
                    name = nameOf(it.memberId),
                    color = it.color,
                    consumeText = if (mask) maskText else MoneyFormat.fenToString(it.consume),
                    paidText = if (mask) maskText else MoneyFormat.fenToString(it.paid),
                )
            },
            aaTitle = if (state.isTrip) aaTitleText else null,
            aaEmptyText = aaEmptyText,
            aaSettledText = settledLabel,
            aaLines = state.aaBalances.map { b ->
                StatsImageExporter.AaLine(
                    name = nameOf(b.memberId),
                    color = b.color,
                    netText = when {
                        mask -> "$receiveLabel $maskText"
                        b.net > 0 -> "$receiveLabel ¥${MoneyFormat.fenToString(b.net)}"
                        b.net < 0 -> "$payLabel ¥${MoneyFormat.fenToString(-b.net)}"
                        else -> settledLabel
                    },
                    netType = when {
                        mask && b.net != 0L -> if (b.net > 0) 1 else 2
                        b.net > 0 -> 1
                        b.net < 0 -> 2
                        else -> 0
                    },
                )
            },
            transfers = state.aaTransfers.map {
                StatsImageExporter.TransferLine(
                    transferFmt.format(
                        nameOf(it.fromId),
                        nameOf(it.toId),
                        if (mask) maskText else MoneyFormat.fenToString(it.amount),
                    )
                )
            },
        )
    }

    // ---------- CSV 导出：当前筛选后的全部明细 ----------
    val categoryMap by container.categoryRepository.observeIdMap()
        .collectAsStateWithLifecycle(initialValue = emptyMap())

    fun toast(resId: Int) {
        Toast.makeText(context, context.getString(resId), Toast.LENGTH_SHORT).show()
    }

    fun buildCsvRows(mask: Boolean = false): List<CsvExporter.CsvRow> = state.detailTxs.map { tx ->
        CsvExporter.CsvRow(
            timeText = DateUtils.formatCsvTime(tx.createdAt),
            typeText = if (tx.type == TransactionType.EXPENSE.value) expenseLabel else incomeLabel,
            amountText = if (mask) "***" else MoneyFormat.fenToPlain(tx.amount),
            categoryName = categoryMap[tx.categoryId]?.name.orEmpty(),
            note = tx.note.orEmpty(),
            location = tx.location.orEmpty(),
            memberName = if (tx.memberId == null) "" else nameOf(tx.memberId),
            payerName = if (tx.payerMemberId == null) "" else nameOf(tx.payerMemberId),
            mood = tx.mood.orEmpty(),
        )
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 16.dp),
    ) {
        Spacer(Modifier.height(12.dp))

        // ---------- 标题 + 导出 + 切换账本 ----------
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                stringResource(R.string.stats_title),
                style = MaterialTheme.typography.headlineLarge,
                modifier = Modifier.weight(1f),
            )
            IconButton(
                enabled = !exportingStats,
                onClick = { showExportDialog = true },
            ) {
                Icon(
                    Icons.Outlined.IosShare,
                    contentDescription = stringResource(R.string.stats_export),
                    tint = MaterialTheme.colorScheme.primary,
                )
            }
            Box {
                Surface(
                    onClick = { bookMenuOpen = true },
                    shape = RoundedCornerShape(50),
                    color = MaterialTheme.colorScheme.surface,
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 14.dp, vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(
                            text = state.statBook?.name ?: stringResource(R.string.stats_switch_book),
                            style = MaterialTheme.typography.labelLarge,
                            maxLines = 1,
                        )
                        Icon(
                            Icons.Outlined.ArrowDropDown,
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
                            text = { Text(book.name) },
                            trailingIcon = {
                                if (state.statBook?.id == book.id) CheckMark()
                            },
                            onClick = {
                                viewModel.selectBook(book.id)
                                bookMenuOpen = false
                            },
                        )
                    }
                }
            }
        }
        Spacer(Modifier.height(14.dp))

        // ---------- 汇总卡片 2x2 ----------
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            SummaryCard(
                title = stringResource(R.string.stats_total_expense),
                value = "¥${MoneyFormat.fenToString(state.totalExpense)}",
                valueColor = SemanticTheme.colors.expense,
                modifier = Modifier.weight(1f),
            )
            SummaryCard(
                title = stringResource(R.string.stats_total_income),
                value = "¥${MoneyFormat.fenToString(state.totalIncome)}",
                valueColor = SemanticTheme.colors.income,
                modifier = Modifier.weight(1f),
            )
        }
        Spacer(Modifier.height(10.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            SummaryCard(
                title = stringResource(R.string.stats_net),
                value = "¥${MoneyFormat.fenToString(state.netExpense)}",
                valueColor = MaterialTheme.colorScheme.onSurface,
                modifier = Modifier.weight(1f),
            )
            SummaryCard(
                title = stringResource(R.string.stats_daily),
                value = "¥${MoneyFormat.fenToString(state.dailyAvg)}",
                valueColor = MaterialTheme.colorScheme.onSurface,
                modifier = Modifier.weight(1f),
            )
        }

        Spacer(Modifier.height(14.dp))

        // ---------- 时间段筛选 ----------
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            PeriodChip(stringResource(R.string.period_today), state.period is StatPeriod.Today, Modifier.weight(1f)) {
                viewModel.selectPeriod(StatPeriod.Today)
            }
            PeriodChip(stringResource(R.string.period_week), state.period is StatPeriod.ThisWeek, Modifier.weight(1f)) {
                viewModel.selectPeriod(StatPeriod.ThisWeek)
            }
            PeriodChip(stringResource(R.string.period_month), state.period is StatPeriod.ThisMonth, Modifier.weight(1f)) {
                viewModel.selectPeriod(StatPeriod.ThisMonth)
            }
            PeriodChip(stringResource(R.string.period_all), state.period is StatPeriod.All, Modifier.weight(1f)) {
                viewModel.selectPeriod(StatPeriod.All)
            }
            PeriodChip(stringResource(R.string.period_custom), state.period is StatPeriod.Custom, Modifier.weight(1f)) {
                showRangePicker = true
            }
        }

        // ---------- 分类筛选（账单中出现过的分类，多选） ----------
        if (state.filterableCategories.isNotEmpty()) {
            Spacer(Modifier.height(10.dp))
            LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                item(key = "all") {
                    FilterChip(
                        text = stringResource(R.string.stats_all_categories),
                        color = MaterialTheme.colorScheme.primary,
                        selected = state.selectedCategoryIds.isEmpty(),
                        onClick = viewModel::clearCategoryFilter,
                    )
                }
                items(state.filterableCategories, key = { it.id }) { cat ->
                    FilterChip(
                        text = cat.name,
                        color = Color(cat.color),
                        selected = cat.id in state.selectedCategoryIds,
                        onClick = { viewModel.toggleCategory(cat.id) },
                    )
                }
            }
        }

        // ---------- 成员筛选（仅旅行账本）：按归属 / 按垫付 ----------
        if (state.isTrip) {
            Spacer(Modifier.height(10.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Box {
                    Surface(
                        onClick = { ownerMenuOpen = true },
                        shape = RoundedCornerShape(50),
                        color = MaterialTheme.colorScheme.surface,
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 14.dp, vertical = 8.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Icon(
                                Icons.Outlined.Group,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(18.dp),
                            )
                            Spacer(Modifier.width(6.dp))
                            Text(
                                text = stringResource(R.string.stats_filter_owner) + " · " +
                                    memberFilterLabel(state.ownerFilter, state, selfLabel, publicLabel),
                                style = MaterialTheme.typography.labelLarge,
                                maxLines = 1,
                            )
                            Icon(
                                Icons.Outlined.ArrowDropDown,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                    MemberFilterMenu(
                        expanded = ownerMenuOpen,
                        onDismiss = { ownerMenuOpen = false },
                        current = state.ownerFilter,
                        state = state,
                        showPublic = true,
                        publicLabel = publicLabel,
                        onSelect = {
                            viewModel.selectOwner(it)
                            ownerMenuOpen = false
                        },
                    )
                }
                Box {
                    Surface(
                        onClick = { payerMenuOpen = true },
                        shape = RoundedCornerShape(50),
                        color = MaterialTheme.colorScheme.surface,
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 14.dp, vertical = 8.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Icon(
                                Icons.Outlined.SwapHoriz,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(18.dp),
                            )
                            Spacer(Modifier.width(6.dp))
                            Text(
                                text = stringResource(R.string.stats_filter_payer) + " · " +
                                    memberFilterLabel(state.payerFilter, state, selfLabel, publicLabel),
                                style = MaterialTheme.typography.labelLarge,
                                maxLines = 1,
                            )
                            Icon(
                                Icons.Outlined.ArrowDropDown,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                    MemberFilterMenu(
                        expanded = payerMenuOpen,
                        onDismiss = { payerMenuOpen = false },
                        current = state.payerFilter,
                        state = state,
                        onSelect = {
                            viewModel.selectPayer(it)
                            payerMenuOpen = false
                        },
                    )
                }
            }
        }

        Spacer(Modifier.height(14.dp))

        // ---------- 分类占比饼图 ----------
        AppCard {
            Column(Modifier.padding(16.dp)) {
                SectionTitle(stringResource(R.string.stats_pie_title))
                Spacer(Modifier.height(12.dp))
                // 缓存 slices：仅在分类数据变化时重建，避免普通重组重放进场动画
                val pieSlices = remember(state.categories) {
                    state.categories.take(8).map {
                        PieSlice(
                            label = it.name,
                            ratio = it.ratio,
                            color = Color(it.color),
                        )
                    }
                }
                // 选中分类（可能已被筛选清掉，取不到时按未选中渲染）
                val selectedCat = state.categories.firstOrNull { it.categoryId == selectedSliceId }
                val selectedPieIndex = selectedCat?.let { sel ->
                    state.categories.take(8).indexOfFirst { it.categoryId == sel.categoryId }
                        .takeIf { it in 0 until 6 } // 第 7、8 类在图上并入"其他"，无独立扇区
                }
                Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
                    CategoryPieChart(
                        slices = pieSlices,
                        otherLabel = stringResource(R.string.stats_pie_other),
                        selectedSlice = selectedPieIndex,
                        centerTitle = selectedCat?.let {
                            it.name + " · " + String.format(java.util.Locale.US, "%.1f%%", it.ratio * 100)
                        } ?: stringResource(R.string.stats_pie_center, stringResource(state.period.labelRes())),
                        centerValue = selectedCat?.let {
                            "¥${MoneyFormat.fenToString(it.total)}"
                        } ?: "¥${MoneyFormat.fenToString(state.totalExpense)}",
                        onSliceClick = { index ->
                            // 点扇区：选中/取消（"其他"合并段点击视为清除选中）
                            val catId = state.categories.getOrNull(index)?.categoryId
                            selectedSliceId = if (catId != null && selectedSliceId == catId) null else catId
                        },
                        onCenterClick = { selectedSliceId = null },
                    )
                }
                Spacer(Modifier.height(2.dp))
                Text(
                    stringResource(R.string.stats_pie_tap_hint),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
                    modifier = Modifier.padding(start = 4.dp),
                )
                // 图例（前 6 类；点击选中并联动高亮扇形，再次点击打开下钻明细）
                if (state.categories.isNotEmpty()) {
                    Spacer(Modifier.height(12.dp))
                    state.categories.take(6).forEach { item ->
                        val isSelected = selectedSliceId == item.categoryId
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(8.dp))
                                .background(
                                    if (isSelected) MaterialTheme.colorScheme.primary.copy(alpha = 0.08f)
                                    else Color.Transparent
                                )
                                .clickable {
                                    if (selectedSliceId == item.categoryId) {
                                        drilldownId = item.categoryId
                                    } else {
                                        selectedSliceId = item.categoryId
                                    }
                                }
                                .padding(horizontal = 4.dp, vertical = 4.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(10.dp)
                                    .background(Color(item.color), CircleShape),
                            )
                            Spacer(Modifier.width(8.dp))
                            Text(
                                item.name,
                                style = MaterialTheme.typography.bodyMedium,
                                modifier = Modifier.weight(1f),
                            )
                            Text(
                                "${(item.ratio * 100).toInt()}%",
                                style = MaterialTheme.typography.labelMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                }
            }
        }

        Spacer(Modifier.height(14.dp))

        // ---------- 分类排行 ----------
        SectionTitle(stringResource(R.string.stats_rank_title))
        Spacer(Modifier.height(8.dp))
        AppCard {
            if (state.categories.isEmpty()) {
                EmptyState(stringResource(R.string.stats_rank_empty))
            } else {
                Column(Modifier.padding(vertical = 6.dp)) {
                    state.categories.forEachIndexed { index, item ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 16.dp, vertical = 10.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Text(
                                text = "${index + 1}",
                                style = MaterialTheme.typography.titleMedium,
                                color = if (index < 3) MaterialTheme.colorScheme.primary
                                else MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.width(24.dp),
                            )
                            CategoryIcon(iconKey = item.icon, color = item.color.argb(), size = 38)
                            Spacer(Modifier.width(12.dp))
                            Column(Modifier.weight(1f)) {
                                Text(item.name, style = MaterialTheme.typography.bodyLarge)
                                Text(
                                    stringResource(
                                        R.string.stats_bills_count, item.count, (item.ratio * 100).toInt()
                                    ),
                                    style = MaterialTheme.typography.labelMedium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                            Text(
                                "¥${MoneyFormat.fenToString(item.total)}",
                                style = MaterialTheme.typography.titleMedium,
                                color = SemanticTheme.colors.expense,
                            )
                        }
                    }
                }
            }
        }

        // ---------- 成员明细（仅旅行账本） ----------
        if (state.isTrip && state.memberDetails.isNotEmpty()) {
            Spacer(Modifier.height(14.dp))
            SectionTitle(stringResource(R.string.stats_personal_title))
            Text(
                stringResource(R.string.stats_personal_note),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
                modifier = Modifier.padding(start = 4.dp),
            )
            Spacer(Modifier.height(8.dp))
            AppCard {
                Column(Modifier.padding(vertical = 6.dp)) {
                    state.memberDetails.forEach { detail ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 16.dp, vertical = 10.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            MemberChip(
                                name = nameOf(detail.memberId),
                                color = if (detail.memberId == null) MaterialTheme.colorScheme.primary
                                else Color(detail.color),
                            )
                            Spacer(Modifier.width(12.dp))
                            Column(Modifier.weight(1f)) {
                                Text(
                                    stringResource(R.string.stats_personal_consume),
                                    style = MaterialTheme.typography.labelMedium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                                Text(
                                    "¥${MoneyFormat.fenToString(detail.consume)}",
                                    style = MaterialTheme.typography.titleSmall,
                                )
                            }
                            Column(horizontalAlignment = Alignment.End) {
                                Text(
                                    stringResource(R.string.stats_personal_paid),
                                    style = MaterialTheme.typography.labelMedium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                                Text(
                                    "¥${MoneyFormat.fenToString(detail.paid)}",
                                    style = MaterialTheme.typography.titleSmall,
                                )
                            }
                        }
                    }
                }
            }
        }

        // ---------- AA 结算卡（仅旅行账本） ----------
        if (state.isTrip && state.aaBalances.isEmpty()) {
            Spacer(Modifier.height(14.dp))
            AppCard {
                Box(Modifier.fillMaxWidth().padding(vertical = 24.dp), contentAlignment = Alignment.Center) {
                    Text(
                        stringResource(R.string.stats_aa_empty),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }
        if (state.isTrip && state.aaBalances.isNotEmpty()) {
            Spacer(Modifier.height(14.dp))
            AaSettleCard(
                state = state,
                nameOf = nameOf,
                colorOf = colorOf,
                onCopy = {
                    val text = buildSettleText(
                        state = state,
                        nameOf = nameOf,
                        consumeLabel = context.getString(R.string.stats_personal_consume),
                        paidLabel = context.getString(R.string.stats_personal_paid),
                        settledLabel = context.getString(R.string.stats_aa_settled),
                        receiveLabel = context.getString(R.string.stats_aa_receive),
                        payLabel = context.getString(R.string.stats_aa_pay),
                    )
                    val cm = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                    cm.setPrimaryClip(ClipData.newPlainText("AA", text))
                    Toast.makeText(
                        context, context.getString(R.string.stats_aa_copied), Toast.LENGTH_SHORT
                    ).show()
                },
            )
        }

        Spacer(Modifier.height(16.dp))
    }

    // ---------- 导出方式弹窗：统计图片 / CSV 账单 ----------
    if (showExportDialog) {
        AlertDialog(
            onDismissRequest = { if (!exportingStats) showExportDialog = false },
            title = {
                Text(
                    stringResource(R.string.stats_export_choose),
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                )
            },
            text = {
                Column {
                    ExportOptionRow(
                        icon = Icons.Outlined.Image,
                        title = stringResource(R.string.stats_export_image),
                        subtitle = stringResource(R.string.stats_export_image_sub),
                        enabled = !exportingStats,
                        onClick = {
                            showExportDialog = false
                            scope.launch {
                                exportingStats = true
                                val uri = StatsImageExporter.export(
                                    context, buildStatsExportData(periodText, maskAmounts), isDark,
                                )
                                exportingStats = false
                                toast(if (uri != null) R.string.export_saved else R.string.export_failed)
                            }
                        },
                    )
                    Spacer(Modifier.height(8.dp))
                    ExportOptionRow(
                        icon = Icons.Outlined.Description,
                        title = stringResource(R.string.stats_export_csv),
                        subtitle = stringResource(R.string.stats_export_csv_sub),
                        enabled = !exportingStats,
                        onClick = {
                            val rows = buildCsvRows(maskAmounts)
                            if (rows.isEmpty()) {
                                toast(R.string.export_csv_empty)
                            } else {
                                showExportDialog = false
                                scope.launch {
                                    exportingStats = true
                                    val uri = CsvExporter.export(context, rows, "账单")
                                    exportingStats = false
                                    toast(if (uri != null) R.string.export_csv_saved else R.string.export_failed)
                                }
                            }
                        },
                    )
                    HorizontalDivider(modifier = Modifier.padding(vertical = 10.dp))
                    // 打码分享：所有金额显示为「¥***」（CSV 中为 ***）
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Column(Modifier.weight(1f)) {
                            Text(
                                stringResource(R.string.stats_export_mask),
                                style = MaterialTheme.typography.bodyMedium,
                            )
                            Text(
                                stringResource(R.string.stats_export_mask_sub),
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                        Switch(
                            checked = maskAmounts,
                            onCheckedChange = { maskAmounts = it },
                        )
                    }
                }
            },
            confirmButton = {},
            dismissButton = {
                TextButton(onClick = { showExportDialog = false }) {
                    Text(stringResource(R.string.cancel))
                }
            },
        )
    }

    // ---------- 自定义时间段 ----------
    if (showRangePicker) {
        CustomRangeDialog(
            onDismiss = { showRangePicker = false },
            onConfirm = { start, end ->
                viewModel.selectPeriod(StatPeriod.Custom(start, end))
                showRangePicker = false
            },
        )
    }

    // ---------- 饼图下钻：分类明细底部面板（备注小计 + 账单明细） ----------
    val drilldownCat = state.categories.firstOrNull { it.categoryId == drilldownId }
    if (drilldownCat != null) {
        val cat = drilldownCat
        val catTxs = state.expenseTxs
            .filter { it.categoryId == cat.categoryId }
            .sortedByDescending { it.createdAt }
        // "二级分类统计"：按备注聚合小计（无备注合并为一行），无需数据模型支持
        val noteGroups = catTxs
            .groupBy { it.note?.trim()?.takeIf { n -> n.isNotEmpty() } }
            .map { (note, list) -> Triple(note, list.sumOf { it.amount }, list.size) }
            .sortedByDescending { it.second }
        ModalBottomSheet(
            onDismissRequest = { drilldownId = null },
            sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
        ) {
            Column(
                modifier = Modifier
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = 16.dp)
                    .padding(bottom = 24.dp),
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    CategoryIcon(iconKey = cat.icon, color = cat.color.argb(), size = 42)
                    Spacer(Modifier.width(12.dp))
                    Column(Modifier.weight(1f)) {
                        Text(cat.name, style = MaterialTheme.typography.titleLarge)
                        Text(
                            stringResource(R.string.stats_bills_count, cat.count, (cat.ratio * 100).toInt()),
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    Text(
                        text = "¥${MoneyFormat.fenToString(cat.total)}",
                        style = MaterialTheme.typography.titleMedium,
                        color = SemanticTheme.colors.expense,
                    )
                }
                Spacer(Modifier.height(14.dp))
                HorizontalDivider()
                Spacer(Modifier.height(12.dp))
                Text(
                    stringResource(R.string.stats_by_note),
                    style = MaterialTheme.typography.titleSmall,
                )
                Spacer(Modifier.height(2.dp))
                if (noteGroups.isEmpty()) {
                    Text(
                        stringResource(R.string.stats_rank_empty),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(vertical = 10.dp),
                    )
                }
                noteGroups.forEach { (note, sum, cnt) ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 7.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(
                            text = note ?: stringResource(R.string.stats_no_note),
                            style = MaterialTheme.typography.bodyMedium,
                            maxLines = 1,
                            overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis,
                            modifier = Modifier.weight(1f),
                        )
                        Text(
                            text = stringResource(R.string.home_bills_n, cnt),
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        Spacer(Modifier.width(10.dp))
                        Text(
                            text = "¥${MoneyFormat.fenToString(sum)}",
                            style = MaterialTheme.typography.labelLarge,
                        )
                    }
                }
                Spacer(Modifier.height(12.dp))
                HorizontalDivider()
                Spacer(Modifier.height(12.dp))
                Text(
                    stringResource(R.string.stats_sheet_bills),
                    style = MaterialTheme.typography.titleSmall,
                )
                Spacer(Modifier.height(2.dp))
                catTxs.forEach { tx ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(8.dp))
                            .clickable { onOpenDetail(tx.id) }
                            .padding(horizontal = 4.dp, vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Column(Modifier.weight(1f)) {
                            Text(
                                text = tx.note?.takeIf { it.isNotBlank() } ?: cat.name,
                                style = MaterialTheme.typography.bodyMedium,
                                maxLines = 1,
                                overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis,
                            )
                            Text(
                                text = DateUtils.formatBillTime(tx.createdAt),
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                        Text(
                            text = "¥${MoneyFormat.fenToString(tx.amount)}",
                            style = MaterialTheme.typography.titleSmall,
                            color = SemanticTheme.colors.expense,
                        )
                    }
                }
            }
        }
    }
}

/** 构建复制到剪贴板的结算文本（标签随应用语言） */
private fun buildSettleText(
    state: StatsUiState,
    nameOf: (Long?) -> String,
    consumeLabel: String,
    paidLabel: String,
    settledLabel: String,
    receiveLabel: String,
    payLabel: String,
): String {
    val sb = StringBuilder()
    sb.append("AA\n")
    state.aaBalances.forEach { b ->
        val net = b.net
        val netLabel = when {
            net > 0 -> "$receiveLabel ¥${MoneyFormat.fenToString(net)}"
            net < 0 -> "$payLabel ¥${MoneyFormat.fenToString(-net)}"
            else -> settledLabel
        }
        sb.append("${nameOf(b.memberId)}｜$consumeLabel ¥${MoneyFormat.fenToString(b.consume)}")
        .append("｜$paidLabel ¥${MoneyFormat.fenToString(b.paid)}｜$netLabel\n")
    }
    if (state.aaTransfers.isEmpty()) {
        sb.append(settledLabel)
    } else {
        sb.append("-----\n")
        state.aaTransfers.forEach { t ->
            sb.append("${nameOf(t.fromId)} → ${nameOf(t.toId)} ¥${MoneyFormat.fenToString(t.amount)}\n")
        }
    }
    return sb.toString()
}

/** AA 结算卡片：参与人账目 + 简化转账方案 + 复制按钮 */
@Composable
private fun AaSettleCard(
    state: StatsUiState,
    nameOf: (Long?) -> String,
    colorOf: (Long?) -> Color,
    onCopy: () -> Unit,
) {
    AppCard {
        Column(Modifier.padding(16.dp)) {
            SectionTitle(stringResource(R.string.stats_aa_title))
            Spacer(Modifier.height(2.dp))
            Text(
                stringResource(R.string.stats_aa_subtitle),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.height(10.dp))

            // 参与人账目行
            state.aaBalances.forEach { b ->
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 5.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    MemberChip(
                        name = nameOf(b.memberId),
                        color = colorOf(b.memberId),
                    )
                    Spacer(Modifier.width(10.dp))
                    Text(
                        "¥${MoneyFormat.fenToString(b.paid)}",
                        style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.onSurface,
                    )
                    Spacer(Modifier.weight(1f))
                    val (label, color) = when {
                        b.net > 0 -> stringResource(R.string.stats_aa_receive) to SemanticTheme.colors.income
                        b.net < 0 -> stringResource(R.string.stats_aa_pay) to SemanticTheme.colors.expense
                        else -> stringResource(R.string.stats_aa_settled) to MaterialTheme.colorScheme.onSurfaceVariant
                    }
                    Text(
                        text = if (b.net != 0L) "$label ¥${MoneyFormat.fenToString(kotlin.math.abs(b.net))}" else label,
                        style = MaterialTheme.typography.labelLarge,
                        color = color,
                        fontWeight = FontWeight.SemiBold,
                    )
                }
            }

            Spacer(Modifier.height(8.dp))

            if (state.aaTransfers.isEmpty()) {
                Text(
                    stringResource(R.string.stats_aa_settled),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            } else {
                // 简化转账方案
                Surface(
                    shape = MaterialTheme.shapes.small,
                    color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                ) {
                    Column(Modifier.padding(12.dp)) {
                        state.aaTransfers.forEach { t ->
                            Text(
                                stringResource(
                                    R.string.stats_aa_transfer_fmt,
                                    nameOf(t.fromId),
                                    nameOf(t.toId),
                                    MoneyFormat.fenToString(t.amount),
                                ),
                                style = MaterialTheme.typography.bodyMedium,
                                modifier = Modifier.padding(vertical = 3.dp),
                            )
                        }
                    }
                }
            }

            Spacer(Modifier.height(12.dp))
            Surface(
                onClick = onCopy,
                shape = RoundedCornerShape(50),
                color = MaterialTheme.colorScheme.primaryContainer,
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 9.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Icon(
                        Icons.Outlined.ContentCopy,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(16.dp),
                    )
                    Spacer(Modifier.width(6.dp))
                    Text(
                        stringResource(R.string.stats_aa_copy),
                        style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.primary,
                    )
                }
            }
        }
    }
}

/** 成员筛选按钮文案 */
@Composable
private fun memberFilterLabel(filter: Long?, state: StatsUiState, selfLabel: String, publicLabel: String): String =
    when (filter) {
        null -> stringResource(R.string.stats_all_members)
        StatsUiState.FILTER_SELF -> selfLabel
        StatsUiState.FILTER_PUBLIC -> publicLabel
        else -> state.members.firstOrNull { it.id == filter }?.let {
            if (it.isPublic) publicLabel else it.name
        } ?: selfLabel
    }

/** 成员筛选下拉菜单（全部 / 本人 / 公共（可选）/ 成员列表） */
@Composable
private fun MemberFilterMenu(
    expanded: Boolean,
    onDismiss: () -> Unit,
    current: Long?,
    state: StatsUiState,
    onSelect: (Long?) -> Unit,
    showPublic: Boolean = false,
    publicLabel: String = "",
) {
    val selfLabel = stringResource(R.string.record_self)
    val publicMember = state.members.firstOrNull { it.isPublic }
    DropdownMenu(
        expanded = expanded,
        onDismissRequest = onDismiss,
        shape = MaterialTheme.shapes.medium,
    ) {
        DropdownMenuItem(
            text = { Text(stringResource(R.string.stats_all_members)) },
            trailingIcon = { if (current == null) CheckMark() },
            onClick = { onSelect(null) },
        )
        DropdownMenuItem(
            text = { Text(selfLabel) },
            trailingIcon = { if (current == StatsUiState.FILTER_SELF) CheckMark() },
            onClick = { onSelect(StatsUiState.FILTER_SELF) },
        )
        if (showPublic && publicMember != null) {
            DropdownMenuItem(
                leadingIcon = {
                    Box(
                        modifier = Modifier
                            .size(10.dp)
                            .background(Color(publicMember.color), CircleShape),
                    )
                },
                text = { Text(publicLabel) },
                trailingIcon = { if (current == StatsUiState.FILTER_PUBLIC) CheckMark() },
                onClick = { onSelect(StatsUiState.FILTER_PUBLIC) },
            )
        }
        state.members.forEach { member ->
            if (member.isPublic) return@forEach
            DropdownMenuItem(
                leadingIcon = {
                    Box(
                        modifier = Modifier
                            .size(10.dp)
                            .background(Color(member.color), CircleShape),
                    )
                },
                text = { Text(member.name) },
                trailingIcon = { if (current == member.id) CheckMark() },
                onClick = { onSelect(member.id) },
            )
        }
    }
}

/** 分类筛选 chip：多选，选中态用分类色 */
@Composable
private fun FilterChip(
    text: String,
    color: Color,
    selected: Boolean,
    onClick: () -> Unit,
) {
    Surface(
        onClick = onClick,
        shape = RoundedCornerShape(50),
        color = if (selected) color else MaterialTheme.colorScheme.surface,
        border = androidx.compose.foundation.BorderStroke(
            width = 1.dp,
            color = if (selected) color else MaterialTheme.colorScheme.outline,
        ),
    ) {
        Text(
            text = text,
            style = MaterialTheme.typography.labelMedium,
            color = if (selected) Color.White else MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 7.dp),
        )
    }
}

/** 筛选菜单选中标记 */
@Composable
private fun CheckMark() {
    Text("✓", color = MaterialTheme.colorScheme.primary)
}

@Composable
private fun SummaryCard(
    title: String,
    value: String,
    valueColor: Color,
    modifier: Modifier = Modifier,
) {
    AppCard(modifier = modifier) {
        Column(Modifier.padding(16.dp)) {
            Text(
                title,
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.height(8.dp))
            Text(
                value,
                style = MaterialTheme.typography.headlineMedium,
                color = valueColor,
                maxLines = 1,
            )
        }
    }
}

@Composable
private fun PeriodChip(
    text: String,
    selected: Boolean,
    modifier: Modifier = Modifier,
    onClick: () -> Unit,
) {
    Surface(
        onClick = onClick,
        modifier = modifier,
        shape = RoundedCornerShape(50),
        color = if (selected) MaterialTheme.colorScheme.primary
        else MaterialTheme.colorScheme.surface,
    ) {
        Text(
            text = text,
            style = MaterialTheme.typography.labelMedium,
            color = if (selected) MaterialTheme.colorScheme.onPrimary
            else MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(vertical = 8.dp),
            textAlign = androidx.compose.ui.text.style.TextAlign.Center,
            maxLines = 1,
        )
    }
}

/** 自定义时间段选择（M3 DateRangePicker，UTC 毫秒转本地日期） */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun CustomRangeDialog(
    onDismiss: () -> Unit,
    onConfirm: (LocalDate, LocalDate) -> Unit,
) {
    val pickerState = rememberDateRangePickerState()
    val zone = ZoneId.systemDefault()

    fun Long?.toLocalDate(): LocalDate? = this?.let {
        Instant.ofEpochMilli(it).atZone(zone).toLocalDate()
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        shape = MaterialTheme.shapes.large,
        title = { Text(stringResource(R.string.stats_custom_title), style = MaterialTheme.typography.titleMedium) },
        text = {
            DateRangePicker(
                state = pickerState,
                showModeToggle = false,
                modifier = Modifier.height(420.dp),
            )
        },
        confirmButton = {
            val start = pickerState.selectedStartDateMillis.toLocalDate()
            val end = pickerState.selectedEndDateMillis.toLocalDate()
            TextButton(
                onClick = { onConfirm(start!!, end ?: start!!) },
                enabled = start != null,
            ) { Text(stringResource(R.string.confirm)) }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(R.string.cancel)) }
        },
    )
}

/** 导出弹窗中的单个选项行：图标 + 标题 + 副标题 */
@Composable
private fun ExportOptionRow(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    title: String,
    subtitle: String,
    enabled: Boolean,
    onClick: () -> Unit,
) {
    Surface(
        onClick = onClick,
        enabled = enabled,
        shape = MaterialTheme.shapes.medium,
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.55f),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 14.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(
                icon,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(24.dp),
            )
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text(
                    title,
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.SemiBold,
                )
                Text(
                    subtitle,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}
