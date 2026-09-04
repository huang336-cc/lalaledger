package com.lightledger.app.ui.stats

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.lightledger.app.AppContainer
import com.lightledger.app.data.db.entity.AccountBookEntity
import com.lightledger.app.data.db.entity.CategoryEntity
import com.lightledger.app.data.db.entity.MemberEntity
import com.lightledger.app.data.db.entity.TransactionEntity
import com.lightledger.app.domain.model.StatPeriod
import com.lightledger.app.domain.model.TransactionType
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.time.LocalDate
import java.time.ZoneId

/** 分类排行条目 */
data class CategoryStatItem(
    val categoryId: Long,
    val name: String,
    val icon: String,
    val color: Int,
    val total: Long,
    val count: Int,
) {
    /** 占比 0f..1f（相对总支出） */
    var ratio: Float = 0f
}

/** 成员明细行：消费 / 垫付 */
data class MemberDetailItem(
    val memberId: Long?,   // null = 本人
    val color: Int,
    val consume: Long,
    val paid: Long,
)

/** AA 结算参与人账目：净额 = 垫付 − 消费（>0 应收，<0 应付） */
data class MemberBalance(
    val memberId: Long?,
    val color: Int,
    val paid: Long,
    val consume: Long,
) {
    val net: Long get() = paid - consume
}

/** 简化转账方案：from 向 to 转 amount（分） */
data class TransferItem(
    val fromId: Long?,
    val toId: Long?,
    val amount: Long,
)

data class StatsUiState(
    val totalExpense: Long = 0L,
    val totalIncome: Long = 0L,
    val netExpense: Long = 0L,
    val dailyAvg: Long = 0L,
    val categories: List<CategoryStatItem> = emptyList(),
    val period: StatPeriod = StatPeriod.ThisMonth,
    val isEmpty: Boolean = true,
    /** 统计中的账本是否旅行账本：true 时显示成员筛选 / 明细 / AA 结算 */
    val isTrip: Boolean = false,
    /** 统计中的账本成员列表 */
    val members: List<MemberEntity> = emptyList(),
    /** 归属成员筛选（谁消费）：null = 全部；[FILTER_SELF] = 本人；其余 = 成员 id */
    val ownerFilter: Long? = null,
    /** 垫付成员筛选（谁先付的钱） */
    val payerFilter: Long? = null,
    /** 分类多选筛选：空集 = 全部 */
    val selectedCategoryIds: Set<Long> = emptySet(),
    /** 该账本账单中出现过的分类（供筛选选择） */
    val filterableCategories: List<CategoryEntity> = emptyList(),
    /** 账本列表（切换账本用） */
    val books: List<AccountBookEntity> = emptyList(),
    /** 当前统计的账本 */
    val statBook: AccountBookEntity? = null,
    /** 成员明细（消费 / 垫付，按垫付降序） */
    val memberDetails: List<MemberDetailItem> = emptyList(),
    /** AA 结算：各参与人账目（含本人） */
    val aaBalances: List<MemberBalance> = emptyList(),
    /** AA 结算：简化转账方案 */
    val aaTransfers: List<TransferItem> = emptyList(),
) {
    companion object {
        /** 成员筛选中的"本人"哨兵值（本人账单 memberId 为 null） */
        const val FILTER_SELF = -1L

        /** 成员筛选中的"公共消费"哨兵值（全员 AA 归属，对应 isPublic 成员的真实 id） */
        const val FILTER_PUBLIC = -2L
    }
}

/**
 * 统计面板：总支出/总收入/净花费/日均消费 + 分类占比 + 排行。
 * 支持切换统计账本（默认跟随全局当前账本）、分类多选筛选、
 * 旅行账本按归属/垫付双维度成员筛选 + 成员明细 + AA 结算（贪心简化转账）。
 * 任何数据变化实时刷新。
 */
@OptIn(ExperimentalCoroutinesApi::class)
class StatsViewModel(
    container: AppContainer,
    currentBookId: StateFlow<Long>,
) : ViewModel() {

    val period = MutableStateFlow<StatPeriod>(StatPeriod.ThisMonth)

    /** 统计账本 id；null = 跟随全局当前账本 */
    private val statBookId = MutableStateFlow<Long?>(null)

    /** 归属成员筛选 */
    private val ownerFilter = MutableStateFlow<Long?>(null)

    /** 垫付成员筛选 */
    private val payerFilter = MutableStateFlow<Long?>(null)

    /** 分类多选筛选 */
    private val selectedCategories = MutableStateFlow<Set<Long>>(emptySet())

    /** 全部账本（切换账本菜单） */
    val books: StateFlow<List<AccountBookEntity>> = container.bookRepository.observeAll()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    /** 生效统计账本：显式选择优先，否则跟随全局 */
    private val effectiveBookId = combine(statBookId, currentBookId) { picked, current ->
        picked ?: current
    }

    private val transactions = effectiveBookId.flatMapLatest { bookId ->
        if (bookId <= 0) flowOf(emptyList<TransactionEntity>()) else container.transactionRepository.observeByBook(bookId)
    }

    private val statBook = effectiveBookId.flatMapLatest { id ->
        if (id <= 0) flowOf<AccountBookEntity?>(null) else container.bookRepository.observeById(id)
    }

    private val categoryMap = container.categoryRepository.observeIdMap()

    /** 统计账本成员列表 */
    private val members = effectiveBookId.flatMapLatest { id ->
        if (id <= 0) flowOf(emptyList<MemberEntity>()) else container.memberRepository.observeByBook(id)
    }

    init {
        // 账本变化 → 清空成员与分类筛选，避免残留无效 id
        viewModelScope.launch {
            effectiveBookId.collect {
                ownerFilter.value = null
                payerFilter.value = null
                selectedCategories.value = emptySet()
            }
        }
    }

    private data class MemberInputs(
        val isTrip: Boolean,
        val members: List<MemberEntity>,
        val owner: Long?,
        val payer: Long?,
        /** 本账本"公共消费"成员 id（全员 AA）；null = 尚未创建 */
        val publicId: Long?,
    )

    private val memberInputs = combine(statBook, members, ownerFilter, payerFilter) { book, m, o, p ->
        MemberInputs(book?.isTrip == true, m, o, p, m.firstOrNull { it.isPublic }?.id)
    }

    private data class FilterInputs(
        val selectedCategories: Set<Long>,
        val statBook: AccountBookEntity?,
    )

    private val filterInputs = combine(selectedCategories, statBook) { sel, book ->
        FilterInputs(sel, book)
    }

    val uiState: StateFlow<StatsUiState> = combine(
        transactions, categoryMap, period, memberInputs, filterInputs,
    ) { values ->
        @Suppress("UNCHECKED_CAST")
        compute(
            txs = values[0] as List<TransactionEntity>,
            cats = values[1] as Map<Long, CategoryEntity>,
            selectedPeriod = values[2] as StatPeriod,
            mi = values[3] as MemberInputs,
            fi = values[4] as FilterInputs,
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), StatsUiState())

    fun selectPeriod(p: StatPeriod) {
        period.value = p
    }

    /** 切换统计账本 */
    fun selectBook(bookId: Long) {
        statBookId.value = bookId
    }

    /** 回到跟随全局当前账本 */
    fun followGlobalBook() {
        statBookId.value = null
    }

    /** 选择归属成员筛选；memberId=null 全部，[StatsUiState.FILTER_SELF] 本人，其余成员 id */
    fun selectOwner(filter: Long?) {
        ownerFilter.value = filter
    }

    /** 选择垫付成员筛选 */
    fun selectPayer(filter: Long?) {
        payerFilter.value = filter
    }

    /** 切换分类选中态（多选）；空集 = 全部分类 */
    fun toggleCategory(categoryId: Long) {
        val cur = selectedCategories.value
        selectedCategories.value = if (categoryId in cur) cur - categoryId else cur + categoryId
    }

    /** 清空分类筛选（回到全部） */
    fun clearCategoryFilter() {
        selectedCategories.value = emptySet()
    }

    // ---------- 聚合 ----------

    private fun compute(
        txs: List<TransactionEntity>,
        cats: Map<Long, CategoryEntity>,
        selectedPeriod: StatPeriod,
        mi: MemberInputs,
        fi: FilterInputs,
    ): StatsUiState {
        val range = selectedPeriod.toRange()
        val byTime = if (range == null) txs else txs.filter {
            it.createdAt >= range.first && it.createdAt <= range.second
        }

        // 账单中出现过的分类（全周期口径，供筛选 chips 选择）
        val filterableCategories = byTime.map { it.categoryId }.distinct()
            .mapNotNull { cats[it] }
            .sortedBy { it.sortOrder }

        // 分类筛选（空集 = 全部）
        val byCategory = if (fi.selectedCategories.isEmpty()) byTime else byTime.filter {
            it.categoryId in fi.selectedCategories
        }

        // 成员双维度筛选（影响汇总卡 / 饼图 / 排行）
        val byMember = byCategory.filter { tx ->
            val ownerOk = when (mi.owner) {
                null -> true
                StatsUiState.FILTER_SELF -> tx.memberId == null
                StatsUiState.FILTER_PUBLIC -> mi.publicId != null && tx.memberId == mi.publicId
                else -> tx.memberId == mi.owner
            }
            val payerOk = when (mi.payer) {
                null -> true
                StatsUiState.FILTER_SELF -> tx.payerMemberId == null
                else -> tx.payerMemberId == mi.payer
            }
            ownerOk && payerOk
        }

        val expenses = byMember.filter { it.type == TransactionType.EXPENSE.value }
        val incomes = byMember.filter { it.type == TransactionType.INCOME.value }
        val totalExpense = expenses.sumOf { it.amount }
        val totalIncome = incomes.sumOf { it.amount }

        // 日均：范围天数（至少 1 天），全部时段从第一笔账单算起
        val zone = ZoneId.systemDefault()
        val startDay: LocalDate = range?.first?.let {
            java.time.Instant.ofEpochMilli(it).atZone(zone).toLocalDate()
        } ?: expenses.minOfOrNull { it.createdAt }?.let {
            java.time.Instant.ofEpochMilli(it).atZone(zone).toLocalDate()
        } ?: LocalDate.now()
        val endDay = LocalDate.now()
        val days = maxOf(1, java.time.temporal.ChronoUnit.DAYS.between(startDay, endDay).toInt() + 1)
        val dailyAvg = totalExpense / days

        // 分类排行
        val grouped = expenses.groupBy { it.categoryId }
            .map { (catId, items) ->
                val cat = cats[catId]
                CategoryStatItem(
                    categoryId = catId,
                    name = cat?.name ?: "—",
                    icon = cat?.icon ?: "star",
                    color = cat?.color ?: 0xFF9BA8C9.toInt(),
                    total = items.sumOf { it.amount },
                    count = items.size,
                )
            }
            .sortedByDescending { it.total }
        if (totalExpense > 0) {
            grouped.forEach { it.ratio = it.total.toFloat() / totalExpense }
        }

        // ---------- 旅行账本：成员明细 + AA 结算 ----------
        // 口径：时间 + 分类筛选后的全部支出（无视成员筛选，AA 是全员视角）；
        // 公共消费（归属 = 公共成员）由全体参与人（本人 + 真实成员）AA 均摊。
        var memberDetails: List<MemberDetailItem> = emptyList()
        var aaBalances: List<MemberBalance> = emptyList()
        var aaTransfers: List<TransferItem> = emptyList()
        if (mi.isTrip) {
            val tripExpenses = byCategory.filter { it.type == TransactionType.EXPENSE.value }
            if (tripExpenses.isNotEmpty()) {
            val publicId = mi.publicId
            val publicTxs = if (publicId != null) tripExpenses.filter { it.memberId == publicId } else emptyList()
            val publicTotal = publicTxs.sumOf { it.amount }
            val realTxs = if (publicId != null) tripExpenses.filter { it.memberId != publicId } else tripExpenses

            val consumeByMember = realTxs.groupBy { it.memberId }
                .mapValues { (_, items) -> items.sumOf { it.amount } }
            val paidByMember = tripExpenses.groupBy { it.payerMemberId }
                .mapValues { (_, items) -> items.sumOf { it.amount } }

            // 参与人 = 本人 + 全部真实成员（公共成员自身不参与分摊；全员列出便于对账）
            val realMembers = mi.members.filter { !it.isPublic }
            val participantIds: List<Long?> = listOf<Long?>(null) + realMembers.map { it.id }
            val n = participantIds.size
            // 公共总额均摊：整除平摊，余数（分）由前几个人各多担 1 分，保证总额一致
            val share = if (n > 0) publicTotal / n else 0L
            val remainder = if (n > 0) publicTotal - share * n else 0L

            val colorOf: (Long?) -> Int = { id ->
                if (id == null) 0xFF6C7A9C.toInt()
                else mi.members.firstOrNull { it.id == id }?.color ?: 0xFF6C7A9C.toInt()
            }
            memberDetails = participantIds.mapIndexed { index, id ->
                MemberDetailItem(
                    memberId = id,
                    color = colorOf(id),
                    consume = (consumeByMember[id] ?: 0L) + share + (if (index < remainder) 1L else 0L),
                    paid = paidByMember[id] ?: 0L,
                )
            }.sortedByDescending { it.consume + it.paid }
            aaBalances = memberDetails.map {
                MemberBalance(memberId = it.memberId, color = it.color, paid = it.paid, consume = it.consume)
            }
            aaTransfers = settle(aaBalances)
            }
        }

        return StatsUiState(
            totalExpense = totalExpense,
            totalIncome = totalIncome,
            netExpense = totalExpense - totalIncome,
            dailyAvg = dailyAvg,
            categories = grouped,
            period = selectedPeriod,
            isEmpty = txs.isEmpty(),
            isTrip = mi.isTrip,
            members = mi.members,
            ownerFilter = mi.owner,
            payerFilter = mi.payer,
            selectedCategoryIds = fi.selectedCategories,
            filterableCategories = filterableCategories,
            statBook = fi.statBook,
            memberDetails = memberDetails,
            aaBalances = aaBalances,
            aaTransfers = aaTransfers,
        )
    }

    /**
     * 贪心配对生成简化转账方案：最大债权人依次向最大债务人收款。
     * 净额 > 0 应收（creditor），< 0 应付（debtor）。
     */
    private fun settle(balances: List<MemberBalance>): List<TransferItem> {
        var creditors = balances.filter { it.net > 0 }
            .map { it.memberId to it.net }
            .sortedByDescending { it.second }
            .toMutableList()
        var debtors = balances.filter { it.net < 0 }
            .map { it.memberId to -it.net }
            .sortedByDescending { it.second }
            .toMutableList()
        val plan = mutableListOf<TransferItem>()
        var i = 0
        var j = 0
        while (i < debtors.size && j < creditors.size) {
            val (dId, dAmt) = debtors[i]
            val (cId, cAmt) = creditors[j]
            val amt = minOf(dAmt, cAmt)
            if (amt > 0) plan += TransferItem(fromId = dId, toId = cId, amount = amt)
            val rd = dAmt - amt
            val rc = cAmt - amt
            if (rd == 0L) i++ else debtors[i] = dId to rd
            if (rc == 0L) j++ else creditors[j] = cId to rc
        }
        return plan
    }
}
