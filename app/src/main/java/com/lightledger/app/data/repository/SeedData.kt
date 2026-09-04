package com.lightledger.app.data.repository

import com.lightledger.app.data.db.AppDatabase
import com.lightledger.app.domain.model.TransactionType

/**
 * 首次启动预置数据：默认账本 + 全场景分类。
 */
object SeedData {

    private data class SeedCategory(val name: String, val icon: String, val color: Int)

    /** 预置支出分类：覆盖日常 + 旅行全场景（需求指定 14 项） */
    private val expenseCategories = listOf(
        SeedCategory("餐饮", "restaurant", 0xFF5BB3A2.toInt()),
        SeedCategory("住宿", "hotel", 0xFF7FA8D9.toInt()),
        SeedCategory("交通", "directions_bus", 0xFF8FBF9F.toInt()),
        SeedCategory("门票", "confirmation_number", 0xFFE3A29B.toInt()),
        SeedCategory("购物", "shopping_bag", 0xFFE8B04B.toInt()),
        SeedCategory("日用品", "shopping_cart", 0xFF9BA8C9.toInt()),
        SeedCategory("医疗", "medical_services", 0xFFE58F8F.toInt()),
        SeedCategory("娱乐", "sports_esports", 0xFFB59BD1.toInt()),
        SeedCategory("通讯", "phone_iphone", 0xFF7FB5D5.toInt()),
        SeedCategory("加油", "local_gas_station", 0xFFE89A6A.toInt()),
        SeedCategory("停车", "local_parking", 0xFFA8B8C8.toInt()),
        SeedCategory("礼物", "redeem", 0xFFEFA0B8.toInt()),
        SeedCategory("学习", "school", 0xFF86B6C6.toInt()),
        SeedCategory("维修", "build", 0xFFA89A8F.toInt()),
    )

    /** 预置收入分类 */
    private val incomeCategories = listOf(
        SeedCategory("工资", "payments", 0xFF5BB3A2.toInt()),
        SeedCategory("退款", "savings", 0xFF7FA8D9.toInt()),
        SeedCategory("红包", "card_giftcard", 0xFFE8896A.toInt()),
        SeedCategory("兼职", "work", 0xFF8FBF9F.toInt()),
        SeedCategory("其他收入", "star", 0xFFB59BD1.toInt()),
    )

    /** 账本可选配色 */
    val bookColors = intArrayOf(
        0xFF5BB3A2.toInt(), // 薄荷绿
        0xFF7FA8D9.toInt(), // 雾蓝
        0xFFE89A6A.toInt(), // 暖橘
        0xFFB59BD1.toInt(), // 淡紫
        0xFFEFA0B8.toInt(), // 樱粉
        0xFF8FBF9F.toInt(), // 青绿
    )

    /** 账本可选图标 */
    val bookIcons = listOf("book", "flight", "luggage", "public", "home", "star")

    /** 成员标签可选配色（新增成员默认按序取色，减少撞色） */
    val memberColors = intArrayOf(
        0xFF5BB3A2.toInt(), // 薄荷绿
        0xFF7FA8D9.toInt(), // 雾蓝
        0xFFE89A6A.toInt(), // 暖橘
        0xFFB59BD1.toInt(), // 淡紫
        0xFFEFA0B8.toInt(), // 樱粉
        0xFF8FBF9F.toInt(), // 青绿
        0xFFE58F8F.toInt(), // 珊瑚红
        0xFF7FB5D5.toInt(), // 天蓝
        0xFFE8B04B.toInt(), // 芒果黄
        0xFF86B6C6.toInt(), // 灰蓝
    )

    suspend fun seedIfNeeded(db: AppDatabase) {
        val bookDao = db.accountBookDao()
        val categoryDao = db.categoryDao()

        // 预置分类
        if (categoryDao.count() == 0) {
            expenseCategories.forEachIndexed { index, seed ->
                categoryDao.insert(
                    com.lightledger.app.data.db.entity.CategoryEntity(
                        name = seed.name,
                        icon = seed.icon,
                        color = seed.color,
                        type = TransactionType.EXPENSE.value,
                        isDefault = true,
                        sortOrder = index,
                    )
                )
            }
            incomeCategories.forEachIndexed { index, seed ->
                categoryDao.insert(
                    com.lightledger.app.data.db.entity.CategoryEntity(
                        name = seed.name,
                        icon = seed.icon,
                        color = seed.color,
                        type = TransactionType.INCOME.value,
                        isDefault = true,
                        sortOrder = index,
                    )
                )
            }
        }

        // 默认账本
        if (bookDao.count() == 0) {
            bookDao.insert(
                com.lightledger.app.data.db.entity.AccountBookEntity(
                    name = "我的旅行",
                    icon = "flight",
                    color = 0xFF5BB3A2.toInt(),
                    isTrip = true,
                )
            )
        }
    }
}
