package com.lightledger.app.data.db

import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

/**
 * 数据库版本迁移。
 *
 * 约定：新增字段一律追加 Migration，不使用破坏性重建；
 * 首装直接按最新版本建表，老用户升级时走对应 Migration 保留数据。
 */
object Migrations {

    /**
     * v1 -> v2 示例：
     * 1. transactions 表新增 updatedAt 字段（账单编辑时间）
     * 2. 新增 places 常用地点表
     * 后续如再加字段，照此模板追加 MIGRATION_2_3 即可。
     */
    val MIGRATION_1_2 = object : Migration(1, 2) {
        override fun migrate(db: SupportSQLiteDatabase) {
            // 1) transactions 新增 updatedAt
            db.execSQL(
                "ALTER TABLE transactions ADD COLUMN updatedAt INTEGER NOT NULL DEFAULT 0"
            )
            // 将历史数据的 updatedAt 对齐 createdAt，避免显示异常
            db.execSQL(
                "UPDATE transactions SET updatedAt = createdAt WHERE updatedAt = 0"
            )
            // 2) 新建 places 表
            db.execSQL(
                """
                CREATE TABLE IF NOT EXISTS places (
                    id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                    name TEXT NOT NULL,
                    useCount INTEGER NOT NULL DEFAULT 1,
                    lastUsedAt INTEGER NOT NULL DEFAULT 0
                )
                """.trimIndent()
            )
        }
    }

    /**
     * v2 -> v3：旅行定向记账
     * 1. 新建 members 成员表（挂在账本下，级联删除）
     * 2. account_books 新增 isTrip 旅行账本开关
     * 3. transactions 新增 memberId（外键 SET NULL：删除成员保留历史账单，归属置空）
     */
    val MIGRATION_2_3 = object : Migration(2, 3) {
        override fun migrate(db: SupportSQLiteDatabase) {
            // 1) 新建 members 表
            db.execSQL(
                """
                CREATE TABLE IF NOT EXISTS members (
                    id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                    bookId INTEGER NOT NULL,
                    name TEXT NOT NULL,
                    color INTEGER NOT NULL,
                    createdAt INTEGER NOT NULL,
                    FOREIGN KEY(bookId) REFERENCES account_books(id) ON UPDATE NO ACTION ON DELETE CASCADE
                )
                """.trimIndent()
            )
            db.execSQL("CREATE INDEX IF NOT EXISTS index_members_bookId ON members(bookId)")
            // 2) account_books 新增 isTrip
            db.execSQL("ALTER TABLE account_books ADD COLUMN isTrip INTEGER NOT NULL DEFAULT 0")
            // 3) transactions 新增 memberId（列级外键，与 Room 实体声明一致）
            db.execSQL(
                "ALTER TABLE transactions ADD COLUMN memberId INTEGER REFERENCES members(id) " +
                    "ON UPDATE NO ACTION ON DELETE SET NULL"
            )
            db.execSQL("CREATE INDEX IF NOT EXISTS index_transactions_memberId ON transactions(memberId)")
        }
    }

    /**
     * v3 -> v4：垫付人维度
     * transactions 新增 payerMemberId（谁垫付，外键 SET NULL：删除成员账单保留）
     */
    val MIGRATION_3_4 = object : Migration(3, 4) {
        override fun migrate(db: SupportSQLiteDatabase) {
            db.execSQL(
                "ALTER TABLE transactions ADD COLUMN payerMemberId INTEGER REFERENCES members(id) " +
                    "ON UPDATE NO ACTION ON DELETE SET NULL"
            )
            db.execSQL("CREATE INDEX IF NOT EXISTS index_transactions_payerMemberId ON transactions(payerMemberId)")
        }
    }

    /**
     * v4 -> v5：公共消费成员（全员 AA）
     * members 新增 isPublic 标记；旅行账本首次记账时自动创建一个公共成员，
     * 归属选公共 = 这笔账单由全体成员 AA 分摊。
     */
    val MIGRATION_4_5 = object : Migration(4, 5) {
        override fun migrate(db: SupportSQLiteDatabase) {
            db.execSQL("ALTER TABLE members ADD COLUMN isPublic INTEGER NOT NULL DEFAULT 0")
        }
    }

    /**
     * v5 -> v6：心情标记
     * transactions 新增 mood（emoji 文本，可空）：记账时可选一个心情，
     * 纯展示属性不参与统计，历史账单为 null。
     */
    val MIGRATION_5_6 = object : Migration(5, 6) {
        override fun migrate(db: SupportSQLiteDatabase) {
            db.execSQL("ALTER TABLE transactions ADD COLUMN mood TEXT")
        }
    }

    val ALL: Array<Migration> = arrayOf(
        MIGRATION_1_2, MIGRATION_2_3, MIGRATION_3_4, MIGRATION_4_5, MIGRATION_5_6,
    )
}
