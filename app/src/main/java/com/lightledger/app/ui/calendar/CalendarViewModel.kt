package com.lightledger.app.ui.calendar

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.lightledger.app.AppContainer
import com.lightledger.app.data.db.entity.AccountBookEntity
import com.lightledger.app.data.db.entity.CategoryEntity
import com.lightledger.app.data.db.entity.TransactionEntity
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.stateIn

/** 日历页状态：当前账本全部账单 + 分类映射（UI 按月/按日过滤聚合） */
data class CalendarUiState(
    val txs: List<TransactionEntity> = emptyList(),
    val categories: Map<Long, CategoryEntity> = emptyMap(),
    /** 当前账本是否旅行账本 */
    val isTrip: Boolean = false,
)

/**
 * 日历页：加载当前账本全部账单（本地数据量小，UI 层按月过滤聚合），
 * 跟随全局当前账本切换实时刷新。
 */
@OptIn(ExperimentalCoroutinesApi::class)
class CalendarViewModel(
    container: AppContainer,
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

    val uiState: StateFlow<CalendarUiState> =
        combine(txs, categoryMap, currentBook) { list, cats, book ->
            CalendarUiState(txs = list, categories = cats, isTrip = book?.isTrip == true)
        }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), CalendarUiState())
}
