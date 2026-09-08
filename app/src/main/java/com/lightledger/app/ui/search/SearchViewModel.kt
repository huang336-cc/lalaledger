package com.lightledger.app.ui.search

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.lightledger.app.AppContainer
import com.lightledger.app.data.db.entity.TransactionEntity
import com.lightledger.app.ui.home.HomeBillItem
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn

/** DAO LIMIT 同步：结果上限，用于“仅显示前 N 条”提示 */
const val SEARCH_RESULT_LIMIT = 500

/** 搜索页状态 */
data class SearchUiState(
    val results: List<HomeBillItem> = emptyList(),
    /** 关键词非空 = 处于搜索态（区分初始引导与无结果） */
    val searching: Boolean = false,
)

/**
 * 账单搜索：关键词 250ms 防抖后查询当前账本，
 * 命中结果复用 [HomeBillItem]（账单 + 分类）供 BillRow 渲染；
 * 输入框回显走 [query] 实时流，列表与“无结果”态走防抖后的查询，避免闪跳。
 */
@OptIn(ExperimentalCoroutinesApi::class, FlowPreview::class)
class SearchViewModel(
    private val container: AppContainer,
    currentBookId: StateFlow<Long>,
) : ViewModel() {

    /** 搜索关键词（输入框实时值） */
    val query = MutableStateFlow("")

    /** 防抖后的（关键词, 命中账单）对；账本切换或数据变化时自动重查 */
    private val hits = combine(query.debounce(250), currentBookId) { q, bookId -> q to bookId }
        .flatMapLatest { (q, bookId) ->
            if (bookId <= 0 || q.isBlank()) {
                flowOf(q to emptyList<TransactionEntity>())
            } else {
                container.transactionRepository.observeSearch(bookId, q)
                    .map { list -> q to list }
            }
        }

    val uiState: StateFlow<SearchUiState> = combine(
        hits,
        container.categoryRepository.observeIdMap(),
    ) { (q, txs), cats ->
        SearchUiState(
            results = txs.map { HomeBillItem(it, cats[it.categoryId]) },
            searching = q.isNotBlank(),
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), SearchUiState())
}
