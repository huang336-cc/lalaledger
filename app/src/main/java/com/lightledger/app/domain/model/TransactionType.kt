package com.lightledger.app.domain.model

/**
 * 收支类型。数据库中以 Int 存储，保证查询统计简单可靠。
 */
enum class TransactionType(val value: Int) {
    EXPENSE(0),
    INCOME(1);

    companion object {
        fun from(value: Int): TransactionType =
            entries.firstOrNull { it.value == value } ?: EXPENSE
    }
}
