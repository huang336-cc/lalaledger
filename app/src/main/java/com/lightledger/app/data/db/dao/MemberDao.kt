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
}
