package com.lightledger.app.ui.home

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.lightledger.app.AppContainer
import com.lightledger.app.data.db.entity.CategoryEntity
import com.lightledger.app.data.db.entity.MemberEntity
import com.lightledger.app.data.db.entity.TransactionEntity
import com.lightledger.app.data.prefs.SettingsDataStore
import com.lightledger.app.domain.model.TransactionType
import com.lightledger.app.util.DateUtils
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/** 首页账单行（账单 + 分类 + 归属/垫付成员） */
data class HomeBillItem(
    val tx: TransactionEntity,
    val category: CategoryEntity?,
    /** 归属成员（谁消费）；null = 本人或非旅行账本 */
    val member: MemberEntity? = null,
    /** 垫付成员（谁垫付）；null = 本人或非旅行账本 */
    val payer: MemberEntity? = null,
)

data class HomeUiState(
    val todayExpense: Long = 0L,
    val weekExpense: Long = 0L,
    val monthExpense: Long = 0L,
    val recent: List<HomeBillItem> = emptyList(),
    /** 当前账本是否旅行账本（决定显示成员标签与引导卡） */
    val isTrip: Boolean = false,
    /** 最近账单的时间范围（TODAY / WEEK / MONTH / ALL），可在首页切换并记忆 */
    val recentRange: String = SettingsDataStore.RECENT_RANGE_ALL,
)

/**
 * 首页数据：今日/本周/本月支出 + 最近账单，全部跟随当前账本切换。
 */
@OptIn(ExperimentalCoroutinesApi::class)
class HomeViewModel(
    private val container: AppContainer,
    currentBookId: StateFlow<Long>,
) : ViewModel() {

    private val stats = currentBookId.flatMapLatest { bookId ->
        if (bookId <= 0) {
            kotlinx.coroutines.flow.flowOf(HomeUiState())
        } else {
            combine(
                container.transactionRepository.observeSum(
                    bookId, TransactionType.EXPENSE,
                    DateUtils.startOfDayMillis(), DateUtils.endOfDayMillis()
                ),
                container.transactionRepository.observeSum(
                    bookId, TransactionType.EXPENSE,
                    DateUtils.startOfWeekMillis(), DateUtils.endOfDayMillis()
                ),
                container.transactionRepository.observeSum(
                    bookId, TransactionType.EXPENSE,
                    DateUtils.startOfMonthMillis(), DateUtils.endOfDayMillis()
                ),
            ) { today, week, month ->
                HomeUiState(todayExpense = today, weekExpense = week, monthExpense = month)
            }
        }
    }

    /**
     * 最近账单：按用户选择的时间范围筛选（当天 / 本周 / 本月 / 全部），
     * 「全部」时仍限制最近 20 条以避免长列表卡顿；其余范围取该区间全部。
     */
    private val recent = combine(
        currentBookId,
        container.settings.recentRange,
    ) { bookId, range -> bookId to range }
        .flatMapLatest { (bookId, range) ->
            if (bookId <= 0) {
                kotlinx.coroutines.flow.flowOf(emptyList<TransactionEntity>())
            } else {
                val (start, limit) = when (range) {
                    SettingsDataStore.RECENT_RANGE_TODAY ->
                        DateUtils.startOfDayMillis() to Int.MAX_VALUE
                    SettingsDataStore.RECENT_RANGE_WEEK ->
                        DateUtils.startOfWeekMillis() to Int.MAX_VALUE
                    SettingsDataStore.RECENT_RANGE_MONTH ->
                        DateUtils.startOfMonthMillis() to Int.MAX_VALUE
                    else -> 0L to 20
                }
                container.transactionRepository.observeByRangeDesc(bookId, start, limit)
            }
        }

    private val categoryMap = container.categoryRepository.observeIdMap()

    /** 当前账本的成员 id 映射（仅旅行账本非空） */
    private val memberMap = currentBookId.flatMapLatest { bookId ->
        if (bookId <= 0) {
            kotlinx.coroutines.flow.flowOf(emptyMap<Long, MemberEntity>())
        } else {
            container.memberRepository.observeByBook(bookId)
                .map { list -> list.associateBy { it.id } }
        }
    }

    /** 当前账本（判断旅行开关） */
    private val currentBook = currentBookId.flatMapLatest { bookId ->
        if (bookId <= 0) {
            kotlinx.coroutines.flow.flowOf<com.lightledger.app.data.db.entity.AccountBookEntity?>(null)
        } else {
            container.bookRepository.observeById(bookId)
        }
    }

    // 最近账单相关上下文（账单 + 分类 + 成员 + 当前账本 + 范围）先合成一个中间对象，
    // 避免直接把 6 个流塞进 combine 造成 Kotlin 类型推断失败。
    private data class RecentContext(
        val bills: List<TransactionEntity>,
        val cats: Map<Long, CategoryEntity>,
        val members: Map<Long, MemberEntity>,
        val isTrip: Boolean,
        val range: String,
    )

    private val recentContext = combine(
        recent,
        categoryMap,
        memberMap,
        currentBook,
        container.settings.recentRange,
    ) { bills, cats, members, book, range ->
        RecentContext(
            bills = bills,
            cats = cats,
            members = members,
            isTrip = book?.isTrip == true,
            range = range,
        )
    }

    val uiState: StateFlow<HomeUiState> =
        combine(stats, recentContext) { s, ctx ->
            s.copy(
                recent = ctx.bills.map {
                    HomeBillItem(
                        it, ctx.cats[it.categoryId],
                        member = ctx.members[it.memberId],
                        payer = ctx.members[it.payerMemberId],
                    )
                },
                isTrip = ctx.isTrip,
                recentRange = ctx.range,
            )
        }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), HomeUiState())

    /** 旅行账本引导卡是否已读 */
    val guideTripDone: StateFlow<Boolean> = container.settings.guideTripDone
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), true)

    fun markGuideTripDone() {
        viewModelScope.launch { container.settings.setGuideTripDone() }
    }

    /** 切换「最近账单」的时间范围（当天 / 本周 / 本月 / 全部），并持久化记忆 */
    fun setRecentRange(range: String) {
        viewModelScope.launch { container.settings.setRecentRange(range) }
    }
}
