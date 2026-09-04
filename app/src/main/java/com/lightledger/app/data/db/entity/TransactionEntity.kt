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
    indices = [Index("bookId"), Index("categoryId"), Index("createdAt"), Index("memberId"), Index("payerMemberId")]
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
    /** v3：归属成员 id（谁消费）；null = 本人；删除成员后 Room 自动置空，账单保留 */
    val memberId: Long? = null,
    /** v4：付款成员 id（谁垫付）；null = 本人 */
    val payerMemberId: Long? = null,
    val createdAt: Long = System.currentTimeMillis(),
    /** v1 -> v2 新增字段，示例迁移见 [com.lightledger.app.data.db.Migrations] */
    val updatedAt: Long = System.currentTimeMillis(),
)
