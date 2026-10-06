package com.lightledger.app.data.db.entity

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * 账单。
 * - 金额以"分"为单位用 Long 存储，避免浮点误差
 * - 图片保存本地文件路径，数据库仅存路径列表（JSON）
 */
@Entity(
    tableName = "transactions",
    foreignKeys = [
        ForeignKey(
            entity = AccountBookEntity::class,
            parentColumns = ["id"],
            childColumns = ["bookId"],
            onDelete = ForeignKey.CASCADE
        ),
        ForeignKey(
            entity = MemberEntity::class,
            parentColumns = ["id"],
            childColumns = ["memberId"],
            onDelete = ForeignKey.SET_NULL
        ),
        ForeignKey(
            entity = MemberEntity::class,
            parentColumns = ["id"],
            childColumns = ["payerMemberId"],
            onDelete = ForeignKey.SET_NULL
        )
    ],
    indices = [
        Index("bookId"),
        Index("categoryId"),
        Index("createdAt"),
        Index("memberId"),
        Index("payerMemberId"),
        // v8：账单列表统一以「WHERE bookId=? ORDER BY createdAt DESC, id DESC」取数，
        // 单列索引无法同时覆盖过滤与排序，SQLite 需建临时 B 树排序。
        // 复合索引让过滤 + 排序走同一次索引扫描，列表首帧不再因排序阻塞。
        Index("bookId", "createdAt"),
    ]
)
data class TransactionEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0L,
    val bookId: Long,
    /** 0 = 支出，1 = 收入 */
    val type: Int,
    /** 金额，单位：分 */
    val amount: Long,
    val categoryId: Long,
    val location: String? = null,
    val note: String? = null,
    /** 小票图片本地绝对路径列表 */
    val images: List<String> = emptyList(),
    /**
     * v3：归属成员 id（谁消费）；null = 本人；删除成员后 Room 自动置空，账单保留。
     *
     * v9 起**不再作为读取来源**，保留该列仅为继续享受 `ON DELETE SET NULL` 外键的
     * 数据库级自动清理（删成员时 SQLite 会帮我们置空）。归属的读取一律走 [memberIds]。
     */
    val memberId: Long? = null,
    /**
     * v9：归属成员 id 列表（多选，金额自动均摊）。
     * - 空列表 = 本人（与旧的 memberId == null 语义一致）
     * - 含公共成员 id = 全员参与均摊（「公共」现为全选快捷方式）
     *
     * 注意：JSON 列没有外键，删除成员后残留 id 由 MemberRepository 清理，
     * 读取侧也会按当前成员列表过滤，双重兜底。
     */
    val memberIds: List<Long> = emptyList(),
    /** v4：付款成员 id（谁垫付）；null = 本人 */
    val payerMemberId: Long? = null,
    /** v6：心情 emoji（如 "😀"）；null = 未标记 */
    val mood: String? = null,
    /**
     * v7：仅本次使用的图标 key（IconLibrary）；null = 用分类自身图标。
     * 用于「更多 → 全量图标 → 仅本次使用」：账单列表/详情显示该图标，
     * 但不动任何常驻分类的图标与名称，统计口径仍按 categoryId 归属分类。
     */
    val iconOverride: String? = null,
    val createdAt: Long = System.currentTimeMillis(),
    /** v1 -> v2 新增字段，示例迁移见 [com.lightledger.app.data.db.Migrations] */
    val updatedAt: Long = System.currentTimeMillis(),
)
