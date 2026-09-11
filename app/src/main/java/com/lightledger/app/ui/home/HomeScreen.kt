package com.lightledger.app.ui.home

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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
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
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
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
import com.lightledger.app.data.prefs.SettingsDataStore
import com.lightledger.app.domain.model.IconLibrary
import com.lightledger.app.domain.model.ThemeMode
import com.lightledger.app.domain.model.TransactionType
import com.lightledger.app.ui.app.AppViewModel
import com.lightledger.app.ui.components.AppCard
import com.lightledger.app.ui.components.BillRow
import com.lightledger.app.ui.components.EmptyState
import com.lightledger.app.ui.components.ImagePreviewDialog
import com.lightledger.app.ui.components.SectionTitle
import com.lightledger.app.ui.theme.SemanticTheme
import com.lightledger.app.ui.theme.argb
import com.lightledger.app.util.DateUtils
import com.lightledger.app.util.MoneyFormat
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId

/**
 * 首页：账本切换 + 主题快捷入口 + 记一笔按钮 + 三张支出统计卡 + 最近账单。
 * 最近账单支持时间范围筛选（当天/本周/本月/全部，选择后记忆）；
 * 多选批量操作（导出汇总图 / 批量删除）统一收敛在日历页。
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
    val books by appViewModel.books.collectAsStateWithLifecycle()
    val currentBook by appViewModel.currentBook.collectAsStateWithLifecycle()
    val themeMode by appViewModel.themeMode.collectAsStateWithLifecycle()
    val currentBookId by appViewModel.currentBookId.collectAsStateWithLifecycle()

    var bookMenuOpen by remember { mutableStateOf(false) }
    // 最近账单的时间范围下拉菜单开关
    var rangeMenuOpen by remember { mutableStateOf(false) }

    // 缩略图预览状态
    var previewImages by remember { mutableStateOf<List<String>?>(null) }
    var previewIndex by remember { mutableStateOf(0) }

    // ---------- 日期分组折叠状态（记录已折叠的日期） ----------
    var collapsedDays by remember { mutableStateOf(emptySet<LocalDate>()) }

    // 四个范围的本地化文案预先取出（stringResource 只能在 @Composable 上下文调用）
    val rangeTodayLabel = stringResource(R.string.recent_range_today)
    val rangeWeekLabel = stringResource(R.string.recent_range_week)
    val rangeMonthLabel = stringResource(R.string.recent_range_month)
    val rangeAllLabel = stringResource(R.string.recent_range_all)
    val recentRangeLabel: (String) -> String = { range ->
        when (range) {
            SettingsDataStore.RECENT_RANGE_TODAY -> rangeTodayLabel
            SettingsDataStore.RECENT_RANGE_WEEK -> rangeWeekLabel
            SettingsDataStore.RECENT_RANGE_MONTH -> rangeMonthLabel
            else -> rangeAllLabel
        }
    }

    val systemDark = isSystemInDarkTheme()
    val isDark = when (themeMode) {
        ThemeMode.LIGHT -> false
        ThemeMode.DARK -> true
        ThemeMode.SYSTEM -> systemDark
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
                    // 应用图标：PNG 自带浅色圆底，必须关闭 Icon 默认 tint，否则整图被染成单色
                    Icon(
                        painter = painterResource(R.drawable.ic_app_logo),
                        contentDescription = stringResource(R.string.home_add_bill),
                        tint = Color.Unspecified,
                        modifier = Modifier
                            .size(54.dp)
                            .clip(CircleShape),
                    )
                    Spacer(Modifier.width(14.dp))
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

            // ---------- 最近账单标题 + 时间范围筛选 ----------
            SectionTitle(
                stringResource(R.string.home_recent),
                trailing = {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        // 时间范围下拉：当天 / 本周 / 本月 / 全部（选择后记忆，下次进入仍生效）
                        Box {
                            Row(
                                modifier = Modifier
                                    .clip(RoundedCornerShape(50))
                                    .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f))
                                    .clickable { rangeMenuOpen = true }
                                    .padding(start = 12.dp, end = 4.dp, top = 4.dp, bottom = 4.dp),
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                Text(
                                    text = recentRangeLabel(state.recentRange),
                                    style = MaterialTheme.typography.labelLarge,
                                    color = MaterialTheme.colorScheme.primary,
                                )
                                Icon(
                                    imageVector = Icons.Outlined.ArrowDropDown,
                                    contentDescription = stringResource(R.string.recent_range_all),
                                    tint = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.size(18.dp),
                                )
                            }
                            DropdownMenu(
                                expanded = rangeMenuOpen,
                                onDismissRequest = { rangeMenuOpen = false },
                                shape = MaterialTheme.shapes.medium,
                            ) {
                                RecentRangeOption(
                                    label = stringResource(R.string.recent_range_today),
                                    selected = state.recentRange == SettingsDataStore.RECENT_RANGE_TODAY,
                                ) {
                                    viewModel.setRecentRange(SettingsDataStore.RECENT_RANGE_TODAY)
                                    rangeMenuOpen = false
                                }
                                RecentRangeOption(
                                    label = stringResource(R.string.recent_range_week),
                                    selected = state.recentRange == SettingsDataStore.RECENT_RANGE_WEEK,
                                ) {
                                    viewModel.setRecentRange(SettingsDataStore.RECENT_RANGE_WEEK)
                                    rangeMenuOpen = false
                                }
                                RecentRangeOption(
                                    label = stringResource(R.string.recent_range_month),
                                    selected = state.recentRange == SettingsDataStore.RECENT_RANGE_MONTH,
                                ) {
                                    viewModel.setRecentRange(SettingsDataStore.RECENT_RANGE_MONTH)
                                    rangeMenuOpen = false
                                }
                                RecentRangeOption(
                                    label = stringResource(R.string.recent_range_all),
                                    selected = state.recentRange == SettingsDataStore.RECENT_RANGE_ALL,
                                ) {
                                    viewModel.setRecentRange(SettingsDataStore.RECENT_RANGE_ALL)
                                    rangeMenuOpen = false
                                }
                            }
                        }
                    }
                },
            )

            Spacer(Modifier.height(8.dp))

            // ---------- 账单列表（按日期分组，组头显示当日收支合计，可折叠） ----------
            AppCard {
                if (state.recent.isEmpty()) {
                    // 有筛选范围且范围内为空时，提示切换范围而非「还没有账单」
                    val isFiltered = state.recentRange != SettingsDataStore.RECENT_RANGE_ALL
                    EmptyState(
                        stringResource(
                            if (isFiltered) R.string.recent_range_empty else R.string.home_empty
                        )
                    )
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
                                        onClick = { onOpenDetail(item.tx.id) },
                                    )
                                }
                            }
                        }
                    }
                }
            }

            Spacer(Modifier.height(16.dp))
        }
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
        Icon(
            imageVector = Icons.Outlined.ArrowDropDown,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.rotate(if (collapsed) 0f else 180f),
        )
    }
}

/** 最近账单时间范围下拉项：当前选中项以主题色高亮 + 打勾 */
@Composable
private fun RecentRangeOption(
    label: String,
    selected: Boolean,
    onClick: () -> Unit,
) {
    DropdownMenuItem(
        text = {
            Text(
                text = label,
                color = if (selected) MaterialTheme.colorScheme.primary
                else MaterialTheme.colorScheme.onSurface,
            )
        },
        trailingIcon = {
            if (selected) {
                Text("✓", color = MaterialTheme.colorScheme.primary)
            }
        },
        onClick = onClick,
    )
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
