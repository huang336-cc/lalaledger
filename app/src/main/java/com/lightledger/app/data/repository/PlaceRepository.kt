package com.lightledger.app.data.repository

import com.lightledger.app.data.db.dao.PlaceDao
import com.lightledger.app.data.db.entity.PlaceEntity
import kotlinx.coroutines.flow.Flow

/**
 * 常用地点仓库：去重保存，使用即提升排序。
 */
class PlaceRepository(private val dao: PlaceDao) {

    fun observeRecent(limit: Int = 12): Flow<List<PlaceEntity>> = dao.observeRecent(limit)

    /** 保存地点：已存在则计数+1 并刷新时间；不存在则插入 */
    suspend fun recordUsage(name: String) {
        val trimmed = name.trim()
        if (trimmed.isEmpty()) return
        val exist = dao.getByName(trimmed)
        if (exist == null) {
            dao.insert(PlaceEntity(name = trimmed))
        } else {
            dao.touch(trimmed)
        }
    }

    suspend fun delete(id: Long) = dao.deleteById(id)
}
