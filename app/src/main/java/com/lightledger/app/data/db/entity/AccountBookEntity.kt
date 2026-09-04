package com.lightledger.app.data.db.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * 账本。账单通过 bookId 外键级联删除，实现账本间数据完全隔离。
 */
@Entity(tableName = "account_books")
data class AccountBookEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0L,
    val name: String,
    /** IconLibrary 中的图标 key */
    val icon: String = "book",
    /** ARGB 颜色值 */
    val color: Int = 0xFF5BB3A2.toInt(),
    /** v3：旅行账本开关，true 时显示成员管理与账单成员归属 */
    val isTrip: Boolean = false,
    val createdAt: Long = System.currentTimeMillis(),
)
