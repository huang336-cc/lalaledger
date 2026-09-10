package com.lightledger.app.data.db.dao

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Update
import com.lightledger.app.data.db.entity.MemberEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface MemberDao {

    @Query("SELECT * FROM members WHERE bookId = :bookId ORDER BY createdAt ASC, id ASC")
    fun observeByBook(bookId: Long): Flow<List<MemberEntity>>

    @Query("SELECT * FROM members WHERE bookId = :bookId")
    suspend fun getByBook(bookId: Long): List<MemberEntity>

    @Query("SELECT * FROM members WHERE id = :id")
    suspend fun getById(id: Long): MemberEntity?

    @Query("SELECT COUNT(*) FROM members WHERE bookId = :bookId")
    suspend fun countByBook(bookId: Long): Int

    @Insert
    suspend fun insert(member: MemberEntity): Long

    @Update
    suspend fun update(member: MemberEntity)

    @Delete
    suspend fun delete(member: MemberEntity)

    @Query("DELETE FROM members WHERE id = :id")
    suspend fun deleteById(id: Long)

    /**
     * 一键删除本账本的全部普通成员（isPublic = 0）。
     * 公共成员是系统内置项（全员 AA 归属），始终保留，否则历史公共账单归属会悬空。
     * 历史账单本身不受影响：Room 外键 SET NULL 会把归属/垫付置空，显示为"本人"。
     */
    @Query("DELETE FROM members WHERE bookId = :bookId AND isPublic = 0")
    suspend fun deleteAllNormalInBook(bookId: Long): Int
}
