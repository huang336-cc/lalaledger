package com.lightledger.app.data.db.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * 收支分类。分类全局共享（不按账本隔离），预置 14 个支出分类覆盖日常+旅行场景。
 */
@Entity(tableName = "categories")
data class CategoryEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0L,
    val name: String,
    /** IconLibrary 中的图标 key */
    val icon: String,
    /** ARGB 颜色值 */
    val color: Int,
    /** 0 = 支出，1 = 收入 */
    val type: Int,
    /** 预置分类默认不可删除改名后的语义标记；自定义分类为 false */
    val isDefault: Boolean = false,
    val sortOrder: Int = 0,
)
