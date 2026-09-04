package com.lightledger.app.data.db.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.lightledger.app.data.db.entity.PlaceEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface PlaceDao {

    @Query("SELECT * FROM places ORDER BY lastUsedAt DESC LIMIT :limit")
    fun observeRecent(limit: Int = 12): Flow<List<PlaceEntity>>

    @Query("SELECT * FROM places WHERE name = :name LIMIT 1")
    suspend fun getByName(name: String): PlaceEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(place: PlaceEntity): Long

    @Query(
        """
        UPDATE places
        SET useCount = useCount + 1, lastUsedAt = :now
        WHERE name = :name
        """
    )
    suspend fun touch(name: String, now: Long = System.currentTimeMillis())

    @Query("DELETE FROM places WHERE id = :id")
    suspend fun deleteById(id: Long)
}
