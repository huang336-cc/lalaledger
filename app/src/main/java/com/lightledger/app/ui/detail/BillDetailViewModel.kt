package com.lightledger.app.ui.detail

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.lightledger.app.AppContainer
import com.lightledger.app.data.db.entity.CategoryEntity
import com.lightledger.app.data.db.entity.MemberEntity
import com.lightledger.app.data.db.entity.TransactionEntity
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
    /** 旅行账本归属成员；null = 本人 / 非旅行账本 */
    val member: MemberEntity? = null,
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
                    BillDetailState(
                        tx = tx,
                        category = cats[tx.categoryId],
                        member = members.firstOrNull { it.id == tx.memberId },
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
