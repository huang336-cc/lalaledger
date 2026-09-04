package com.lightledger.app.data.repository

import com.lightledger.app.data.db.dao.AccountBookDao
import com.lightledger.app.data.db.entity.AccountBookEntity
import kotlinx.coroutines.flow.Flow

/**
 * 账本仓库：创建 / 删除 / 重命名 / 查询。
 */
class BookRepository(private val dao: AccountBookDao) {

    fun observeAll(): Flow<List<AccountBookEntity>> = dao.observeAll()

    fun observeById(id: Long): Flow<AccountBookEntity?> = dao.observeById(id)

    suspend fun getById(id: Long): AccountBookEntity? = dao.getById(id)

    suspend fun create(name: String, icon: String, color: Int, isTrip: Boolean = false): Long =
        dao.insert(AccountBookEntity(name = name.trim(), icon = icon, color = color, isTrip = isTrip))

    suspend fun rename(id: Long, name: String) {
        dao.getById(id)?.let { dao.update(it.copy(name = name.trim())) }
    }

    suspend fun updateStyle(id: Long, icon: String, color: Int) {
        dao.getById(id)?.let { dao.update(it.copy(icon = icon, color = color)) }
    }

    /** 切换旅行账本标记；旅行账本才显示成员管理 */
    suspend fun setTrip(id: Long, isTrip: Boolean) {
        dao.getById(id)?.let { dao.update(it.copy(isTrip = isTrip)) }
    }

    /** 删除账本；Room 外键 CASCADE 自动清空该账本全部账单 */
    suspend fun delete(id: Long) = dao.deleteById(id)

    suspend fun isEmpty(): Boolean = dao.count() == 0
}
