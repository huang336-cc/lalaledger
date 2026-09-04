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
        createdAt: Long = System.currentTimeMillis(),
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
            createdAt = createdAt,
        )
    )

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
