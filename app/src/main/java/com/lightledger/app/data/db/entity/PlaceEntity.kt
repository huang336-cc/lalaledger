package com.lightledger.app.data.db.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * 常用地点。记账时一键复用；使用次数越多排序越靠前。
 */
@Entity(tableName = "places")
data class PlaceEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0L,
    val name: String,
    val useCount: Int = 1,
    val lastUsedAt: Long = System.currentTimeMillis(),
)
