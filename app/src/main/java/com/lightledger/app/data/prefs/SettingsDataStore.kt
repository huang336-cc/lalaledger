package com.lightledger.app.data.prefs

import android.content.Context
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.lightledger.app.domain.model.ThemeMode
import com.lightledger.app.util.LocaleHelper
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map

private val Context.dataStore by preferencesDataStore(name = "settings")

/**
 * 轻量设置存储：主题模式 + 当前账本 id + 界面语言。
 */
class SettingsDataStore(private val context: Context) {

    private object Keys {
        val THEME = stringPreferencesKey("theme_mode")
        val CURRENT_BOOK = longPreferencesKey("current_book_id")
        val LANGUAGE = stringPreferencesKey("language")
        val GUIDE_TRIP_DONE = booleanPreferencesKey("guide_trip_done")
        val GUIDE_MULTI_DONE = booleanPreferencesKey("guide_multi_done")
    }

    val themeMode: Flow<ThemeMode> = context.dataStore.data.map { prefs ->
        runCatching {
            ThemeMode.valueOf(prefs[Keys.THEME] ?: ThemeMode.SYSTEM.name)
        }.getOrDefault(ThemeMode.SYSTEM)
    }

    val currentBookId: Flow<Long> = context.dataStore.data.map { prefs ->
        prefs[Keys.CURRENT_BOOK] ?: 0L
    }

    /** 界面语言：zh / en（不再跟随系统，默认简体中文） */
    val language: Flow<String> = context.dataStore.data.map { prefs ->
        LocaleHelper.normalize(prefs[Keys.LANGUAGE] ?: LocaleHelper.DEFAULT)
    }

    suspend fun setThemeMode(mode: ThemeMode) {
        context.dataStore.edit { it[Keys.THEME] = mode.name }
    }

    suspend fun setCurrentBookId(id: Long) {
        context.dataStore.edit { it[Keys.CURRENT_BOOK] = id }
    }

    suspend fun setLanguage(tag: String) {
        context.dataStore.edit { it[Keys.LANGUAGE] = LocaleHelper.normalize(tag) }
    }

    /** 一次性读取当前账本（用于首次进入时初始化） */
    suspend fun currentBookIdOnce(): Long = currentBookId.first()

    /** 旅行账本引导是否已读（首页一次性展示） */
    val guideTripDone: Flow<Boolean> = context.dataStore.data.map { prefs ->
        prefs[Keys.GUIDE_TRIP_DONE] ?: false
    }

    /** 多选功能说明是否已读（首次进入多选时展示） */
    val guideMultiDone: Flow<Boolean> = context.dataStore.data.map { prefs ->
        prefs[Keys.GUIDE_MULTI_DONE] ?: false
    }

    suspend fun setGuideTripDone() {
        context.dataStore.edit { it[Keys.GUIDE_TRIP_DONE] = true }
    }

    suspend fun setGuideMultiDone() {
        context.dataStore.edit { it[Keys.GUIDE_MULTI_DONE] = true }
    }
}
