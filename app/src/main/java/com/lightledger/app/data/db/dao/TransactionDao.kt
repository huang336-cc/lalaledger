package com.lightledger.app.data.db.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Update
import com.lightledger.app.data.db.entity.TransactionEntity
import kotlinx.coroutines.flow.Flow

/**
 * 账单查询全部按 bookId 过滤，保证账本间数据隔离。
 */
@Dao
interface TransactionDao {

    @Query(
        """
        SELECT * FROM transactions
        WHERE bookId = :bookId
        ORDER BY createdAt DESC, id DESC
        """
    )
    fun observeByBook(bookId: Long): Flow<List<TransactionEntity>>

    @Query(
        """
        SELECT * FROM transactions
        WHERE bookId = :bookId
        ORDER BY createdAt DESC, id DESC
        LIMIT :limit
        """
    )
    fun observeRecent(bookId: Long, limit: Int): Flow<List<TransactionEntity>>

    @Query("SELECT * FROM transactions WHERE id = :id")
    fun observeById(id: Long): Flow<TransactionEntity?>

    @Query("SELECT * FROM transactions WHERE id = :id")
    suspend fun getById(id: Long): TransactionEntity?

    /** 一次性读取账本全部账单（按时间升序） */
    @Query("SELECT * FROM transactions WHERE bookId = :bookId ORDER BY createdAt ASC")
    suspend fun getByBookOnce(bookId: Long): List<TransactionEntity>

    @Insert
    suspend fun insert(tx: TransactionEntity): Long

    @Update
    suspend fun update(tx: TransactionEntity)

    @Query("DELETE FROM transactions WHERE id = :id")
    suspend fun deleteById(id: Long)

    /** 清空指定账本的全部账单（保留账本本身） */
    @Query("DELETE FROM transactions WHERE bookId = :bookId")
    suspend fun clearBook(bookId: Long)

    // ---------- 统计聚合 ----------

    /** 指定账本某类型的总金额（分）。startMillis/endMillis 传 null 表示不限时间段 */
    @Query(
        """
        SELECT COALESCE(SUM(amount), 0) FROM transactions
        WHERE bookId = :bookId AND type = :type
          AND (:startMillis IS NULL OR createdAt >= :startMillis)
          AND (:endMillis   IS NULL OR createdAt <= :endMillis)
        """
    )
    suspend fun sumAmount(
        bookId: Long,
        type: Int,
        startMillis: Long?,
        endMillis: Long?
    ): Long

    @Query(
        """
        SELECT COALESCE(SUM(amount), 0) FROM transactions
        WHERE bookId = :bookId AND type = :type
          AND (:startMillis IS NULL OR createdAt >= :startMillis)
          AND (:endMillis   IS NULL OR createdAt <= :endMillis)
        """
    )
    fun observeSum(
        bookId: Long,
        type: Int,
        startMillis: Long?,
        endMillis: Long?
    ): Flow<Long>

    /** 该账本最早一笔账单时间，用于计算"日均消费" */
    @Query("SELECT MIN(createdAt) FROM transactions WHERE bookId = :bookId")
    suspend fun firstCreatedAt(bookId: Long): Long?

    @Query("SELECT COUNT(*) FROM transactions WHERE bookId = :bookId")
    suspend fun countInBook(bookId: Long): Int

    /** 分类统计：金额从高到低 */
    @Query(
        """
        SELECT t.categoryId AS categoryId,
               c.name       AS categoryName,
               c.icon       AS categoryIcon,
               c.color      AS categoryColor,
               SUM(t.amount) AS total,
               COUNT(t.id)   AS count
        FROM transactions t
        INNER JOIN categories c ON c.id = t.categoryId
        WHERE t.bookId = :bookId AND t.type = :type
          AND (:startMillis IS NULL OR t.createdAt >= :startMillis)
          AND (:endMillis   IS NULL OR t.createdAt <= :endMillis)
        GROUP BY t.categoryId
        ORDER BY total DESC
        """
    )
    suspend fun categoryStats(
        bookId: Long,
        type: Int,
        startMillis: Long?,
        endMillis: Long?
    ): List<CategoryStatRow>
}

/** 分类统计聚合行 */
data class CategoryStatRow(
    val categoryId: Long,
    val categoryName: String,
    val categoryIcon: String,
    val categoryColor: Int,
    val total: Long,
    val count: Int,
)
