package com.lightledger.app.ui.calendar

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.KeyboardArrowLeft
import androidx.compose.material.icons.automirrored.outlined.KeyboardArrowRight
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
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
import com.lightledger.app.domain.model.TransactionType
import com.lightledger.app.ui.app.AppViewModel
import com.lightledger.app.ui.components.AppCard
import com.lightledger.app.ui.components.BillRow
import com.lightledger.app.ui.components.EmptyState
import com.lightledger.app.ui.components.SectionTitle
import com.lightledger.app.ui.theme.SemanticTheme
import com.lightledger.app.ui.theme.argb
import com.lightledger.app.util.DateUtils
import com.lightledger.app.util.MoneyFormat
import java.time.Instant
import java.time.LocalDate
import java.time.YearMonth
import java.time.ZoneId

/**
 * 日历页：月历展示每日支出（红色小字），今日高亮；
 * 点击日期格筛选下方账单列表到该日，再次点击回到整月。
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

    var yearMonth by remember { mutableStateOf(YearMonth.now()) }
    var selectedDay by remember { mutableStateOf<LocalDate?>(null) }

    val zone = remember { ZoneId.systemDefault() }
    val today = remember { LocalDate.now() }

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
                                    onClick = { selectedDay = if (date == selectedDay) null else date },
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

        // ---------- 账单列表 ----------
        SectionTitle(
            text = selectedDay?.let { stringResource(R.string.calendar_bills_of_day, DateUtils.formatGroupDate(it)) }
                ?: stringResource(R.string.calendar_bills_month),
        )
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
                                categoryIcon = category?.icon ?: "star",
                                categoryColor = category?.color?.argb()
                                    ?: MaterialTheme.colorScheme.onSurfaceVariant,
                                amountFen = item.tx.amount,
                                type = TransactionType.from(item.tx.type),
                                time = item.tx.createdAt,
                                note = item.tx.note,
                                mood = item.tx.mood,
                                thumbnailPath = item.tx.images.firstOrNull(),
                                selectionMode = false,
                                selected = false,
                                onClick = { onOpenDetail(item.tx.id) },
                            )
                        }
                    }
                }
            }
        }

        Spacer(Modifier.height(16.dp))
    }
}

/** 日历列表行：账单 + 分类（供行渲染取图标/名称） */
private data class CalendarBillItem(
    val tx: TransactionEntity,
    val category: CategoryEntity?,
)

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
    }
}
