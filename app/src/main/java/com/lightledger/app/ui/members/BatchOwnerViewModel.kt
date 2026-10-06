package com.lightledger.app.ui.members

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.lightledger.app.AppContainer
import com.lightledger.app.data.db.entity.MemberEntity
import com.lightledger.app.domain.model.SELF_ID
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/**
 * 批量改归属：把**已勾选的账单**统一改成指定成员组合（含「公共」= 全员 AA）。
 *
 * 入口在日历页多选后的操作栏——那里的勾选本身就是筛选条件，
 * 所以这里只接收一笔明确的账单 id 列表，不再做日期范围筛选；
 * 「选哪些账」交给日历页的现有交互，避免两套筛选逻辑各说各话。
 *
 * 约束：**只改归属**，金额/分类/备注/时间一律不动。
 */
class BatchOwnerViewModel(
    private val container: AppContainer,
    /** 本次要改的账单 id（来自日历页多选） */
    private val billIds: List<Long>,
    /** 账本 id，用于取成员列表 */
    private val bookId: Long,
) : ViewModel() {

    private val members = MutableStateFlow<List<MemberEntity>>(emptyList())
    private val selectedOwners = MutableStateFlow<Set<Long>>(setOf(SELF_ID))

    private val _uiState = MutableStateFlow(BatchOwnerUiState(billCount = billIds.size))
    val uiState: StateFlow<BatchOwnerUiState> = _uiState.asStateFlow()

    init {
        viewModelScope.launch {
            members.value = container.memberRepository.getByBook(bookId)
            sync()
        }
    }

    /** 勾选 / 取消某个成员 */
    fun toggleOwner(id: Long) {
        val next = selectedOwners.value.let { if (id in it) it - id else it + id }
        // 空集没有意义（无人承担），回落到仅本人
        selectedOwners.value = next.ifEmpty { setOf(SELF_ID) }
        sync()
    }

    /** 快捷：公共（全员 AA） */
    fun selectPublic() {
        val pub = members.value.firstOrNull { it.isPublic } ?: return
        selectedOwners.value = setOf(SELF_ID, pub.id)
        sync()
    }

    /** 快捷：仅本人 */
    fun selectSelfOnly() {
        selectedOwners.value = setOf(SELF_ID)
        sync()
    }

    private fun sync() {
        _uiState.update {
            it.copy(
                members = members.value,
                selectedOwners = selectedOwners.value,
                resolvedOwners = resolveOwners(selectedOwners.value, members.value),
            )
        }
    }

    /**
     * 按「公共优先」解析最终参与人：含公共 = 本人 + 全部真实成员；否则取选中项。
     *
     * 排序固定为「本人最前 + 真实成员按 id 升序」：顺序决定余数分摊时多出的几分补给谁，
     * 依赖 Set 迭代顺序会引入不确定性，这里显式定序。
     */
    private fun resolveOwners(picked: Set<Long>, ms: List<MemberEntity>): List<Long> {
        val pub = ms.firstOrNull { it.isPublic }
        if (pub != null && pub.id in picked) {
            return listOf(SELF_ID) + ms.filter { !it.isPublic }.map { it.id }.sorted()
        }
        val valid = ms.map { it.id }.toSet()
        val reals = picked.filter { it != SELF_ID && it in valid }.sorted()
        return if (SELF_ID in picked || reals.isEmpty()) listOf(SELF_ID) + reals
        else reals
    }

    /** 执行批量修改，回调实际改动的条数 */
    fun apply(onDone: (Int) -> Unit) {
        val owners = _uiState.value.resolvedOwners
        if (billIds.isEmpty()) {
            onDone(0)
            return
        }
        viewModelScope.launch {
            val pubId = members.value.firstOrNull { it.isPublic }?.id
            val n = container.transactionRepository.updateOwnershipBatch(
                billIds, owners.toSet(), pubId
            )
            onDone(n)
        }
    }
}

data class BatchOwnerUiState(
    val members: List<MemberEntity> = emptyList(),
    val selectedOwners: Set<Long> = setOf(SELF_ID),
    /** 本次将改动的账单数（来自日历页勾选） */
    val billCount: Int = 0,
    /** 解析后的最终参与人（含公共展开结果） */
    val resolvedOwners: List<Long> = listOf(SELF_ID),
)
