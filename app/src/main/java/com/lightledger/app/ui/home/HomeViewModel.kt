package com.lightledger.app.ui.home

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.lightledger.app.AppContainer
import com.lightledger.app.data.db.entity.CategoryEntity
import com.lightledger.app.data.db.entity.MemberEntity
import com.lightledger.app.data.db.entity.TransactionEntity
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

    private val recent = currentBookId.flatMapLatest { bookId ->
        if (bookId <= 0) {
            kotlinx.coroutines.flow.flowOf(emptyList<TransactionEntity>())
        } else {
            container.transactionRepository.observeRecent(bookId, 20)
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

    val uiState: StateFlow<HomeUiState> =
        combine(stats, recent, categoryMap, memberMap, currentBook) { s, bills, cats, members, book ->
            val trip = book?.isTrip == true
            s.copy(
                recent = bills.map {
                    HomeBillItem(
                        it, cats[it.categoryId],
                        member = members[it.memberId],
                        payer = members[it.payerMemberId],
                    )
                },
                isTrip = trip,
            )
        }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), HomeUiState())

    /** 旅行账本引导卡是否已读 */
    val guideTripDone: StateFlow<Boolean> = container.settings.guideTripDone
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), true)

    /** 多选功能说明是否已读 */
    val guideMultiDone: StateFlow<Boolean> = container.settings.guideMultiDone
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), true)

    fun markGuideTripDone() {
        viewModelScope.launch { container.settings.setGuideTripDone() }
    }

    fun markGuideMultiDone() {
        viewModelScope.launch { container.settings.setGuideMultiDone() }
    }

    /**
     * 多选批量删除：先清理关联小票图片文件，再删记录，返回实际删除条数。
     */
    suspend fun deleteBills(ids: List<Long>): Int {
        var deleted = 0
        for (id in ids) {
            val tx = container.transactionRepository.getById(id) ?: continue
            tx.images.forEach { com.lightledger.app.util.ImageStore.deleteFile(it) }
            container.transactionRepository.delete(id)
            deleted++
        }
        return deleted
    }
}
