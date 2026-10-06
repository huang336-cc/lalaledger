package com.lightledger.app.data.repository

import com.lightledger.app.data.db.dao.MemberDao
import com.lightledger.app.data.db.dao.TransactionDao
import com.lightledger.app.data.db.entity.MemberEntity
import kotlinx.coroutines.flow.Flow

/**
 * 成员仓库：旅行账本的同行成员增删改查。
 * 删除成员时 Room 外键 SET NULL 自动把历史账单的 memberId 置空（账单本身保留）；
 * 但 v9 的 memberIds（JSON 列）没有外键，需在 [delete] / [deleteAllNormal] 里手动摘除，
 * 否则账单会指向一个不存在的成员，统计时金额既不计入任何人也不会退回「本人」。
 */
class MemberRepository(
    private val dao: MemberDao,
    private val txDao: TransactionDao,
) {

    /** 公共成员的默认标签色（中性蓝紫，与普通成员色板区分） */
    private val publicColor = 0xFF7C89D6.toInt()

    fun observeByBook(bookId: Long): Flow<List<MemberEntity>> = dao.observeByBook(bookId)

    suspend fun getByBook(bookId: Long): List<MemberEntity> = dao.getByBook(bookId)

    suspend fun getById(id: Long): MemberEntity? = dao.getById(id)

    suspend fun add(bookId: Long, name: String, color: Int): Long =
        dao.insert(MemberEntity(bookId = bookId, name = name.trim(), color = color))

    suspend fun update(member: MemberEntity) = dao.update(member)

    /**
     * 删除成员：先把该 id 从所有账单的 memberIds 里摘干净，再删成员行。
     * 顺序不能反——反了以后 memberIds 已残留、无从查询（精确匹配需要 id 已知）。
     */
    suspend fun delete(id: Long) {
        stripFromBills(setOf(id))
        dao.deleteById(id)
    }

    /** 一键删除本账本全部普通成员（公共成员保留），返回删除条数。历史账单保留，归属置为本人 */
    suspend fun deleteAllNormal(bookId: Long): Int {
        val ids = dao.getByBook(bookId).filter { !it.isPublic }.map { it.id }.toSet()
        stripFromBills(ids)
        return dao.deleteAllNormalInBook(bookId)
    }

    /** 把一批成员 id 从账单 memberIds / memberId 中摘除（公共成员不会被传进来） */
    private suspend fun stripFromBills(ids: Set<Long>) {
        if (ids.isEmpty()) return
        // LIKE 粗筛：JSON 里 id 形如 "12"，带引号匹配可避开 12 / 112 / 312 的误命中
        val candidates = ids.flatMap { txDao.getReferencingMember(it, "\"$it\"") }
            .distinctBy { it.id }
        val changed = candidates.mapNotNull { tx ->
            val kept = tx.memberIds.filter { it !in ids }
            if (kept.size == tx.memberIds.size && tx.memberId !in ids) {
                null
            } else {
                tx.copy(
                    memberIds = kept,
                    memberId = tx.memberId.takeIf { it !in ids },
                )
            }
        }
        if (changed.isNotEmpty()) txDao.updateAll(changed)
    }

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
