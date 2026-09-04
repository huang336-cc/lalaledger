package com.lightledger.app.data.db.entity

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * 旅行账本的同行成员。
 * - 挂在账本下（bookId 外键级联删除），普通个人账本没有成员
 * - 账单通过 transactions.memberId 引用成员；成员删除时账单保留（SET NULL 置空归属）
 */
@Entity(
    tableName = "members",
    foreignKeys = [
        ForeignKey(
            entity = AccountBookEntity::class,
            parentColumns = ["id"],
            childColumns = ["bookId"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [Index("bookId")]
)
data class MemberEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0L,
    val bookId: Long,
    val name: String,
    /** 成员标签颜色（ARGB） */
    val color: Int = 0xFF5BB3A2.toInt(),
    val createdAt: Long = System.currentTimeMillis(),
    /** v5：公共消费成员（全员 AA 归属）；每个旅行账本至多一个，UI 显示本地化名称且不可编辑删除 */
    val isPublic: Boolean = false,
)
