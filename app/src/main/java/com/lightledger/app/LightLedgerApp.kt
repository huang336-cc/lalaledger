package com.lightledger.app

import android.app.Application
import com.lightledger.app.data.db.AppDatabase
import com.lightledger.app.data.prefs.SettingsDataStore
import com.lightledger.app.data.repository.BookRepository
import com.lightledger.app.data.repository.CategoryRepository
import com.lightledger.app.data.repository.MemberRepository
import com.lightledger.app.data.repository.PlaceRepository
import com.lightledger.app.data.repository.SeedData
import com.lightledger.app.data.repository.TransactionRepository
import com.lightledger.app.util.LocaleHelper
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

/**
 * 手动依赖容器（纯本地小应用，无需引入 Hilt/Dagger）。
 */
class AppContainer(val app: Application) {
    val database: AppDatabase = AppDatabase.get(app)
    val bookRepository = BookRepository(database.accountBookDao())
    val transactionRepository = TransactionRepository(database.transactionDao())
    val categoryRepository = CategoryRepository(database.categoryDao())
    val placeRepository = PlaceRepository(database.placeDao())
    val memberRepository = MemberRepository(database.memberDao())
    val settings = SettingsDataStore(app)
}

class LightLedgerApp : Application() {

    lateinit var container: AppContainer
        private set

    private val appScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    /**
     * 当前界面语言（内存缓存）：
     * Activity.attachBaseContext 直接读这里，零阻塞；
     * DataStore 变化（用户切换语言）后同步更新，再 recreate 生效。
     */
    @Volatile
    var currentLanguage: String = LocaleHelper.DEFAULT
        private set

    /**
     * 立即同步更新内存语言（切换语言时必须先于 recreate() 调用）：
     * DataStore 写入与 collector 回放都是异步的，若等它们更新内存，
     * recreate() 后的新 Activity 会读到旧语言，表现为"延迟一拍才生效"。
     */
    fun updateLanguageNow(tag: String) {
        currentLanguage = LocaleHelper.normalize(tag)
    }

    override fun onCreate() {
        super.onCreate()
        container = AppContainer(this)
        // 首次启动预置默认账本与分类
        appScope.launch {
            SeedData.seedIfNeeded(container.database)
        }
        // 持续同步语言设置到内存（切换语言后 recreate 即可生效）
        appScope.launch {
            container.settings.language.collect { currentLanguage = it }
        }
    }
}
