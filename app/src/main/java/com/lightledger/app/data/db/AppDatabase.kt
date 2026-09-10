package com.lightledger.app.data.db

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.TypeConverters
import com.lightledger.app.data.db.dao.AccountBookDao
import com.lightledger.app.data.db.dao.CategoryDao
import com.lightledger.app.data.db.dao.MemberDao
import com.lightledger.app.data.db.dao.PlaceDao
import com.lightledger.app.data.db.dao.TransactionDao
import com.lightledger.app.data.db.entity.AccountBookEntity
import com.lightledger.app.data.db.entity.CategoryEntity
import com.lightledger.app.data.db.entity.MemberEntity
import com.lightledger.app.data.db.entity.PlaceEntity
import com.lightledger.app.data.db.entity.TransactionEntity

@Database(
    entities = [
        AccountBookEntity::class,
        TransactionEntity::class,
        CategoryEntity::class,
        PlaceEntity::class,
        MemberEntity::class,
    ],
    version = 7,
    exportSchema = true
)
@TypeConverters(Converters::class)
abstract class AppDatabase : RoomDatabase() {

    abstract fun accountBookDao(): AccountBookDao
    abstract fun transactionDao(): TransactionDao
    abstract fun categoryDao(): CategoryDao
    abstract fun placeDao(): PlaceDao
    abstract fun memberDao(): MemberDao

    companion object {
        const val DB_NAME = "light_ledger.db"

        @Volatile
        private var instance: AppDatabase? = null

        fun get(context: Context): AppDatabase =
            instance ?: synchronized(this) {
                instance ?: Room.databaseBuilder(
                    context.applicationContext,
                    AppDatabase::class.java,
                    DB_NAME
                )
                    .addMigrations(*Migrations.ALL)
                    // 降级时（如回退安装旧版本）才允许重建，正常升级永不丢数据
                    .fallbackToDestructiveMigrationOnDowngrade()
                    .build()
                    .also { instance = it }
            }
    }
}
