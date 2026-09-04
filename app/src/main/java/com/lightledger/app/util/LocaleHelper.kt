package com.lightledger.app.util

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import android.content.res.Configuration
import java.util.Locale

/**
 * 应用内语言切换（不依赖 AppCompat，零额外体积）：
 * - 支持的语言只有「简体中文 zh / 英文 en」，不再跟随系统
 * - 通过 attachBaseContext 的 ConfigurationContext 让全部资源（含 stringResource）跟随语言
 * - 切换语言后调用 [findActivity]?.recreate() 重建生效
 */
object LocaleHelper {

    const val ZH = "zh"
    const val EN = "en"

    /** DataStore 默认值：简体中文 */
    const val DEFAULT = ZH

    fun supportedTags(): List<String> = listOf(ZH, EN)

    fun normalize(tag: String?): String =
        if (tag == EN) EN else ZH

    /** 包裹 Context，使资源按指定语言解析 */
    fun wrap(context: Context, languageTag: String): Context {
        val tag = normalize(languageTag)
        val locale = Locale.forLanguageTag(tag)
        Locale.setDefault(locale)
        val config = Configuration(context.resources.configuration)
        config.setLocale(locale)
        config.setLayoutDirection(locale)
        return context.createConfigurationContext(config)
    }

    /** 从任意 Context（含 wrapper）里找回宿主 Activity */
    fun Context.findActivity(): Activity? {
        var ctx = this
        while (ctx is ContextWrapper) {
            if (ctx is Activity) return ctx
            ctx = ctx.baseContext
        }
        return null
    }
}
