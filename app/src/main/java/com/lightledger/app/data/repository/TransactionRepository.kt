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
            payerMemberId = payerMemberId,
            mood = mood?.takeIf { it.isNotBlank() },
            createdAt = createdAt,
            iconOverride = iconOverride,
        )
    )

    /** 批量写入（单事务：任一失败整体回滚），用于 CSV 导入等批量场景 */
    suspend fun addAll(txs: List<TransactionEntity>): List<Long> = dao.insertAll(txs)

    suspend fun update(tx: TransactionEntity) = dao.update(tx)

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
