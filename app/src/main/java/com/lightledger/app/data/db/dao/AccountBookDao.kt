package com.lightledger.app.data.db.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import com.lightledger.app.data.db.entity.AccountBookEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface AccountBookDao {

    @Query("SELECT * FROM account_books ORDER BY createdAt ASC")
    fun observeAll(): Flow<List<AccountBookEntity>>

    @Query("SELECT * FROM account_books WHERE id = :id")
    suspend fun getById(id: Long): AccountBookEntity?

    @Query("SELECT * FROM account_books WHERE id = :id")
    fun observeById(id: Long): Flow<AccountBookEntity?>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(book: AccountBookEntity): Long

    @Update
    suspend fun update(book: AccountBookEntity)

    @Query("DELETE FROM account_books WHERE id = :id")
    suspend fun deleteById(id: Long)

    @Query("SELECT COUNT(*) FROM account_books")
    suspend fun count(): Int
}
