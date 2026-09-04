package com.lightledger.app.ui.app

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.lightledger.app.AppContainer
import com.lightledger.app.data.db.entity.AccountBookEntity
import com.lightledger.app.domain.model.ThemeMode
import com.lightledger.app.util.LocaleHelper
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/**
 * 全局状态：主题模式、账本列表、当前账本。
 *
 * 当前账本 id 持久化在 DataStore；切换账本后首页/记账/统计页全部响应式跟随，
 * 账本间数据完全隔离。删除当前账本时自动回退到第一个剩余账本。
 */
@OptIn(ExperimentalCoroutinesApi::class)
class AppViewModel(private val container: AppContainer) : ViewModel() {

    // ---------- 主题 ----------

    val themeMode: StateFlow<ThemeMode> = container.settings.themeMode
        .stateIn(viewModelScope, SharingStarted.Eagerly, ThemeMode.SYSTEM)

    fun setThemeMode(mode: ThemeMode) {
        viewModelScope.launch { container.settings.setThemeMode(mode) }
    }

    // ---------- 语言 ----------

    /** 界面语言：zh / en（MainActivity.attachBaseContext 与 Activity.recreate 配合生效） */
    val language: StateFlow<String> = container.settings.language
        .stateIn(viewModelScope, SharingStarted.Eagerly, LocaleHelper.DEFAULT)

    fun setLanguage(tag: String) {
        // 关键：先同步更新 Application 内存缓存（recreate() 的新 Activity
        // 在 attachBaseContext 直接读它），再异步持久化到 DataStore；
        // 写完后 collector 收到相同值，幂等不冲突。
        (container.app as? com.lightledger.app.LightLedgerApp)?.updateLanguageNow(tag)
        viewModelScope.launch { container.settings.setLanguage(tag) }
    }

    // ---------- 账本 ----------

    val books: StateFlow<List<AccountBookEntity>> = container.bookRepository.observeAll()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    /**
     * 当前账本 id：
     * - 已保存且存在 -> 保存值
     * - 保存值失效或未保存 -> 回退第一个账本
     * - 没有任何账本 -> -1（空态）
     */
    val currentBookId: StateFlow<Long> =
        combine(container.settings.currentBookId, books) { saved, list ->
            when {
                list.isEmpty() -> -1L
                list.any { it.id == saved } -> saved
                else -> list.first().id
            }
        }.stateIn(viewModelScope, SharingStarted.Eagerly, -1L)

    val currentBook: StateFlow<AccountBookEntity?> = currentBookId
        .flatMapLatest { id ->
            container.bookRepository.observeAll().map { list ->
                list.firstOrNull { it.id == id }
            }
        }
        .stateIn(viewModelScope, SharingStarted.Eagerly, null)

    fun switchBook(id: Long) {
        viewModelScope.launch { container.settings.setCurrentBookId(id) }
    }

    fun createBook(name: String, icon: String, color: Int, isTrip: Boolean = false) {
        viewModelScope.launch {
            val id = container.bookRepository.create(name, icon, color, isTrip)
            switchBook(id)
        }
    }

    fun renameBook(id: Long, name: String) {
        viewModelScope.launch { container.bookRepository.rename(id, name) }
    }

    fun updateBookStyle(id: Long, icon: String, color: Int) {
        viewModelScope.launch { container.bookRepository.updateStyle(id, icon, color) }
    }

    fun deleteBook(id: Long) {
        viewModelScope.launch { container.bookRepository.delete(id) }
    }

    /** 切换旅行账本标记 */
    fun setBookTrip(id: Long, isTrip: Boolean) {
        viewModelScope.launch { container.bookRepository.setTrip(id, isTrip) }
    }
}
