package com.lightledger.app.ui.detail

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.lightledger.app.AppContainer
import com.lightledger.app.data.db.entity.CategoryEntity
import com.lightledger.app.data.db.entity.MemberEntity
import com.lightledger.app.data.db.entity.TransactionEntity
import com.lightledger.app.domain.model.SELF_ID
import com.lightledger.app.domain.model.normalizeOwnerIds
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

data class BillDetailState(
    val tx: TransactionEntity? = null,
    val category: CategoryEntity? = null,
    /**
     * 旅行账本归属成员（v2.3.9 起支持多选）。
     * 空列表 = 非旅行账本或无成员；是否含本人由 [includeSelf] 单独表示。
     */
    val members: List<MemberEntity> = emptyList(),
    /** 归属中是否包含本人（v2.3.10：本人可与成员并存） */
    val includeSelf: Boolean = false,
    /** 旅行账本垫付成员；null = 本人 / 非旅行账本 */
    val payer: MemberEntity? = null,
)

@OptIn(ExperimentalCoroutinesApi::class)
class BillDetailViewModel(
    private val container: AppContainer,
    txId: Long,
) : ViewModel() {

    val state: StateFlow<BillDetailState> = container.transactionRepository.observeById(txId)
        .flatMapLatest { tx ->
            if (tx == null) {
                flowOf(BillDetailState())
            } else {
                combine(
                    container.categoryRepository.observeIdMap(),
                    container.memberRepository.observeByBook(tx.bookId),
                ) { cats, members ->
                    // 归属以 memberIds 为准（v2.3.10 起含 SELF_ID 表示本人）；
                    // 老数据兜底 memberId，保证历史账单不丢归属。
                    val ownerIds = normalizeOwnerIds(tx.memberIds, tx.memberId)
                    BillDetailState(
                        tx = tx,
                        category = cats[tx.categoryId],
                        members = ownerIds.mapNotNull { id -> members.firstOrNull { it.id == id } },
                        // 是否含本人在归属里（用于详情页显示「本人」胶囊）
                        includeSelf = SELF_ID in ownerIds,
                        payer = members.firstOrNull { it.id == tx.payerMemberId },
                    )
                }
            }
        }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), BillDetailState())

    /** 删除账单并清理本地图片文件 */
    fun delete(onDone: () -> Unit) {
        val tx = state.value.tx ?: return
        viewModelScope.launch {
            tx.images.forEach { com.lightledger.app.util.ImageStore.deleteFile(it) }
            container.transactionRepository.delete(tx.id)
            onDone()
        }
    }
}
