package com.lightledger.app.data.repository

import com.lightledger.app.data.db.dao.CategoryDao
import com.lightledger.app.data.db.entity.CategoryEntity
import com.lightledger.app.domain.model.TransactionType
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

/**
 * 分类仓库：预置分类 + 用户自定义分类的增删改。
 */
class CategoryRepository(private val dao: CategoryDao) {

    fun observeByType(type: TransactionType): Flow<List<CategoryEntity>> =
        dao.observeByType(type.value)

    fun observeAll(): Flow<List<CategoryEntity>> = dao.observeAll()

    suspend fun getById(id: Long): CategoryEntity? = dao.getById(id)

    suspend fun add(name: String, icon: String, color: Int, type: TransactionType): Long =
        dao.insert(
            CategoryEntity(
                name = name.trim(),
                icon = icon,
                color = color,
                type = type.value,
                isDefault = false,
                sortOrder = 999,
            )
        )

    suspend fun update(category: CategoryEntity) = dao.update(category)

    suspend fun delete(id: Long) = dao.deleteById(id)

    suspend fun isEmpty(): Boolean = dao.count() == 0

    /** id -> 分类 实时映射（首页/统计展示用） */
    fun observeIdMap(): Flow<Map<Long, CategoryEntity>> =
        dao.observeAll().map { list -> list.associateBy { it.id } }

    suspend fun getAllMap(): Map<Long, CategoryEntity> =
        dao.getAll().associateBy { it.id }
}
