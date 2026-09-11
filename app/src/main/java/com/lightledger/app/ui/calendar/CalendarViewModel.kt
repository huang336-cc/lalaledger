package com.lightledger.app.ui.calendar

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.lightledger.app.AppContainer
import com.lightledger.app.data.db.entity.AccountBookEntity
import com.lightledger.app.data.db.entity.CategoryEntity
import com.lightledger.app.data.db.entity.MemberEntity
import com.lightledger.app.data.db.entity.TransactionEntity
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/** 日历页状态：当前账本全部账单 + 分类映射（UI 按月/按日过滤聚合） */
data class CalendarUiState(
    val txs: List<TransactionEntity> = emptyList(),
    val categories: Map<Long, CategoryEntity> = emptyMap(),
    /** 当前账本是否旅行账本 */
    val isTrip: Boolean = false,
    /** 当前账本成员映射（导出汇总图时标注归属/垫付） */
    val members: Map<Long, MemberEntity> = emptyMap(),
)

/**
 * 日历页：加载当前账本全部账单（本地数据量小，UI 层按月过滤聚合），
 * 跟随全局当前账本切换实时刷新。
 */
@OptIn(ExperimentalCoroutinesApi::class)
class CalendarViewModel(
    private val container: AppContainer,
    currentBookId: StateFlow<Long>,
) : ViewModel() {

    private val txs = currentBookId.flatMapLatest { bookId ->
        if (bookId <= 0) flowOf(emptyList<TransactionEntity>())
        else container.transactionRepository.observeByBook(bookId)
    }

    private val categoryMap = container.categoryRepository.observeIdMap()

    private val currentBook = currentBookId.flatMapLatest { bookId ->
        if (bookId <= 0) flowOf<AccountBookEntity?>(null)
        else container.bookRepository.observeById(bookId)
    }

    /** 当前账本的成员 id 映射（仅旅行账本非空，供导出汇总图标注归属/垫付） */
    private val memberMap = currentBookId.flatMapLatest { bookId ->
        if (bookId <= 0) flowOf(emptyMap<Long, MemberEntity>())
        else container.memberRepository.observeByBook(bookId)
            .map { list -> list.associateBy { it.id } }
    }

    val uiState: StateFlow<CalendarUiState> =
        combine(txs, categoryMap, currentBook, memberMap) { list, cats, book, members ->
            CalendarUiState(
                txs = list,
                categories = cats,
                isTrip = book?.isTrip == true,
                members = members,
            )
        }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), CalendarUiState())

    /** 日历页多选功能说明是否已读 */
    val guideCalendarMultiDone: StateFlow<Boolean> = container.settings.guideCalendarMultiDone
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), true)

    fun markGuideCalendarMultiDone() {
        viewModelScope.launch { container.settings.setGuideCalendarMultiDone() }
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
