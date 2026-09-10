package com.lightledger.app.data.repository

import com.lightledger.app.data.db.dao.MemberDao
import com.lightledger.app.data.db.entity.MemberEntity
import kotlinx.coroutines.flow.Flow

/**
 * 成员仓库：旅行账本的同行成员增删改查。
 * 删除成员时 Room 外键 SET NULL 自动把历史账单归属置空（账单本身保留）。
 */
class MemberRepository(private val dao: MemberDao) {

    /** 公共成员的默认标签色（中性蓝紫，与普通成员色板区分） */
    private val publicColor = 0xFF7C89D6.toInt()

    fun observeByBook(bookId: Long): Flow<List<MemberEntity>> = dao.observeByBook(bookId)

    suspend fun getByBook(bookId: Long): List<MemberEntity> = dao.getByBook(bookId)

    suspend fun getById(id: Long): MemberEntity? = dao.getById(id)

    suspend fun add(bookId: Long, name: String, color: Int): Long =
        dao.insert(MemberEntity(bookId = bookId, name = name.trim(), color = color))

    suspend fun update(member: MemberEntity) = dao.update(member)

    suspend fun delete(id: Long) = dao.deleteById(id)

    /** 一键删除本账本全部普通成员（公共成员保留），返回删除条数。历史账单保留，归属置为本人 */
    suspend fun deleteAllNormal(bookId: Long): Int = dao.deleteAllNormalInBook(bookId)

    /**
     * 确保旅行账本存在"公共消费"成员（全员 AA 归属），没有则自动创建。
     * 数据库名字固定存"公共"，所有 UI 显示点按 isPublic 用本地化文案覆盖。
     */
    suspend fun ensurePublicMember(bookId: Long): MemberEntity? {
        if (bookId <= 0) return null
        val existing = dao.getByBook(bookId).firstOrNull { it.isPublic }
        if (existing != null) return existing
        val id = dao.insert(
            MemberEntity(bookId = bookId, name = "公共", color = publicColor, isPublic = true)
        )
        return dao.getById(id)
    }
}
