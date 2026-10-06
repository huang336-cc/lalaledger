package com.lightledger.app.data.repository

import com.lightledger.app.data.db.dao.CategoryStatRow
import com.lightledger.app.data.db.dao.TransactionDao
import com.lightledger.app.data.db.entity.TransactionEntity
import com.lightledger.app.domain.model.TransactionType
import kotlinx.coroutines.flow.Flow

/**
 * 账单仓库：增删改查 + 统计聚合，全部按账本隔离。
 */
class TransactionRepository(private val dao: TransactionDao) {

    fun observeByBook(bookId: Long): Flow<List<TransactionEntity>> =
        dao.observeByBook(bookId)

    fun observeRecent(bookId: Long, limit: Int = 20): Flow<List<TransactionEntity>> =
        dao.observeRecent(bookId, limit)

    /**
     * 按时间下界 + 条数上限查询（首页最近账单的范围筛选）。
     * startMillis=0 表示不限下界；limit=Int.MAX_VALUE 表示不限条数。
     */
    fun observeByRangeDesc(
        bookId: Long,
        startMillis: Long,
        limit: Int,
    ): Flow<List<TransactionEntity>> = dao.observeByRangeDesc(bookId, startMillis, limit)

    fun observeById(id: Long): Flow<TransactionEntity?> = dao.observeById(id)

    suspend fun getById(id: Long): TransactionEntity? = dao.getById(id)

    suspend fun getByBookOnce(bookId: Long): List<TransactionEntity> = dao.getByBookOnce(bookId)

    /**
     * 账单搜索：备注 / 分类名 / 位置 / 金额（如输入 12.5 命中 ¥12.50）。
     * 关键词先做 LIKE 通配符转义，输入 % _ \ 均按字面匹配。
     */
    fun observeSearch(bookId: Long, keyword: String): Flow<List<TransactionEntity>> {
        val q = keyword.trim()
            .replace("\\", "\\\\")
            .replace("%", "\\%")
            .replace("_", "\\_")
        return dao.observeSearch(bookId, q)
    }

    suspend fun add(
        bookId: Long,
        type: TransactionType,
        amountFen: Long,
        categoryId: Long,
        location: String?,
        note: String?,
        images: List<String>,
        memberId: Long? = null,
        /**
         * v9：归属成员 id 列表（多选均摊）；空 = 本人，含公共 id = 全员。
         *
         * [memberId] 仍会同步写入，仅用于维持 `ON DELETE SET NULL` 外键的
         * 自动清理能力；归属读取一律走 memberIds。
         */
        memberIds: List<Long> = emptyList(),
        payerMemberId: Long? = null,
        mood: String? = null,
        createdAt: Long = System.currentTimeMillis(),
        /** v7：仅本次使用的图标 key；null = 用分类自身图标 */
        iconOverride: String? = null,
    ): Long = dao.insert(
        TransactionEntity(
            bookId = bookId,
            type = type.value,
            amount = amountFen,
            categoryId = categoryId,
            location = location?.takeIf { it.isNotBlank() },
            note = note?.takeIf { it.isNotBlank() },
            images = images,
            memberId = memberId,
            memberIds = memberIds,
            payerMemberId = payerMemberId,
            mood = mood?.takeIf { it.isNotBlank() },
            createdAt = createdAt,
            iconOverride = iconOverride,
        )
    )

    /** 批量写入（单事务：任一失败整体回滚），用于 CSV 导入等批量场景 */
    suspend fun addAll(txs: List<TransactionEntity>): List<Long> = dao.insertAll(txs)

    suspend fun update(tx: TransactionEntity) = dao.update(tx)

    /**
     * 批量改归属：把一批账单的归属统一改成 [ownerIds]（含本人哨兵 SELF_ID 与公共成员 id）。
     *
     * 只改归属相关列，其余字段（金额/分类/备注/图片/时间）原样保留，
     * 避免全量回写时新旧对象字段不一致造成误伤。
     *
     * [memberId] 镜像列按 v9 约定过滤掉本人哨兵与公共成员：
     * 该列有 `ON DELETE SET NULL` 外键，写入 -1 会违反约束；公共成员也不应作为
     * 「单个归属成员」落进这一列。真实归属统一以 [TransactionEntity.memberIds] 为准。
     *
     * @param publicMemberId 当前账本的公共成员 id（用于从镜像列排除；传 null 表示无公共成员）
     * @return 实际改动的账单条数
     */
    suspend fun updateOwnershipBatch(
        ids: List<Long>,
        ownerIds: Set<Long>,
        publicMemberId: Long? = null,
    ): Int {
        if (ids.isEmpty()) return 0
        val sanitized = ownerIds.toList()
        // 镜像列只落「真实成员」：排除本人哨兵（外键约束）与公共成员（语义上非单一归属人）
        val mirror = sanitized.firstOrNull { it != com.lightledger.app.domain.model.SELF_ID && it != publicMemberId }
        val changed = dao.getByIds(ids).map { tx ->
            tx.copy(
                memberIds = sanitized,
                memberId = mirror,
                updatedAt = System.currentTimeMillis(),
            )
        }
        if (changed.isNotEmpty()) dao.updateAll(changed)
        return changed.size
    }

    suspend fun delete(id: Long) = dao.deleteById(id)

    suspend fun clearBook(bookId: Long) = dao.clearBook(bookId)

    suspend fun sumAmount(
        bookId: Long,
        type: TransactionType,
        startMillis: Long?,
        endMillis: Long?,
    ): Long = dao.sumAmount(bookId, type.value, startMillis, endMillis)

    fun observeSum(
        bookId: Long,
        type: TransactionType,
        startMillis: Long?,
        endMillis: Long?,
    ): Flow<Long> = dao.observeSum(bookId, type.value, startMillis, endMillis)

    suspend fun firstCreatedAt(bookId: Long): Long? = dao.firstCreatedAt(bookId)

    suspend fun categoryStats(
        bookId: Long,
        type: TransactionType,
        startMillis: Long?,
        endMillis: Long?,
    ): List<CategoryStatRow> = dao.categoryStats(bookId, type.value, startMillis, endMillis)
}
