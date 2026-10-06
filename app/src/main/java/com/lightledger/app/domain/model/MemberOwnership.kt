package com.lightledger.app.domain.model

/**
 * 归属成员选择里的「本人」哨兵值。
 *
 * 为什么需要它：多选语义下「本人 + 张三」是合法组合，而「本人」不是一个成员行，
 * 没有办法用真实成员 id 表示。于是用一个不可能与真实 id 冲突的负数来占位。
 *
 * 安全性：`MemberEntity.id` 由 Room `autoGenerate = true` 生成，真实 id 恒 >= 1，
 * 因此 [SELF_ID] 永不冲突。
 *
 * 存储：落库时该值会**保留**在 `transactions.memberIds`（JSON 数组）里，
 * 因为「本人 + 张三」与「只有张三」在结算上完全不同，必须能区分：
 *  - `[-1, 5]` = 本人 + 成员 5，两人均摊
 *  - `[5]`     = 只有成员 5，其独担
 * 老数据 `[]`（= 本人）与 `[5]`（= 成员 5）语义与升级前完全一致，无需迁移。
 *
 * 结算：`StatsViewModel.consumeByOwner` 里把该值归一化为 `null`（本人的既有表示），
 * 从而复用原有的分摊算法。
 */
const val SELF_ID: Long = -1L

/**
 * 把账单里存储的归属 id 归一化成「含本人哨兵」的列表。
 *
 * - 历史账单 `memberIds` 为空时兜底 `memberId`（v9 之前的单值列）
 * - 空列表结果统一补上 [SELF_ID]，表示「仅本人」，调用方无需各自判空
 *
 * 这样所有展示点（详情 / 首页 / 日历 / 导出）对「本人 + 张三」与「只有张三」
 * 都能得到一致且正确的归属集合。
 */
fun normalizeOwnerIds(memberIds: List<Long>, legacyMemberId: Long?): List<Long> {
    val raw = memberIds.ifEmpty { listOfNotNull(legacyMemberId) }
    return if (raw.isEmpty()) listOf(SELF_ID) else raw
}
