package com.lightledger.app

import android.content.Intent
import android.content.res.Configuration
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalView
import androidx.core.view.WindowCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.lightledger.app.ui.app.AppViewModel
import com.lightledger.app.ui.navigation.LightLedgerRoot
import com.lightledger.app.ui.theme.LightLedgerTheme
import com.lightledger.app.domain.model.ThemeMode
import com.lightledger.app.util.LocaleHelper

class MainActivity : ComponentActivity() {

    /** 全局 ViewModel：提升为 Activity 属性，便于 onCreate/onNewIntent 处理快捷入口 intent */
    private val appViewModel: AppViewModel by viewModels {
        viewModelFactory {
            initializer { AppViewModel((application as LightLedgerApp).container) }
        }
    }

    /**
     * 界面语言在 Activity attach 时应用（zh/en 二选一，不再跟随系统）。
     * 语言值来自 Application 内存缓存（由 DataStore 持续同步），零 IO 阻塞；
     * 用户切换语言后调用 recreate() 重走这里，全部 stringResource 自动生效。
     */
    override fun attachBaseContext(newBase: android.content.Context) {
        val lang = (newBase.applicationContext as? LightLedgerApp)?.currentLanguage
            ?: LocaleHelper.DEFAULT
        super.attachBaseContext(LocaleHelper.wrap(newBase, lang))
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        // 高刷适配：在 90/120Hz 屏幕上请求系统最高刷新率，滚动更顺滑
        requestHighestRefreshRate()
        // 冷启动快捷入口（长按图标 → 记一笔/搜索/日历/统计）
        handleShortcutIntent(intent)
        setContent {
            val themeMode by appViewModel.themeMode.collectAsStateWithLifecycle()

            // 状态栏/导航栏图标颜色：
            //  - 竖屏走 Scaffold + bottomBar，系统栏区域落在 surface 色上；
            //  - 横屏走侧边栏外壳，系统栏区域落在 background 色上。
            // 两者深浅与主题模式一致，因此统一用「实际生效的明暗」判断，
            // 不用系统暗色设置（跟随系统时会算错，横屏时间/电量看不清就是这个问题）。
            val view = LocalView.current
            val configuration = LocalConfiguration.current
            val isLandscape =
                configuration.orientation == Configuration.ORIENTATION_LANDSCAPE
            val darkTheme = when (themeMode) {
                ThemeMode.LIGHT -> false
                ThemeMode.DARK -> true
                ThemeMode.SYSTEM -> isSystemInDarkTheme()
            }
            LaunchedEffect(darkTheme, isLandscape) {
                val window = (view.context as? android.app.Activity)?.window ?: return@LaunchedEffect
                val controller = WindowCompat.getInsetsController(window, view)
                // 浅色主题 → 系统栏内容是深色（isAppearanceLight*Bars = true）
                controller.isAppearanceLightStatusBars = !darkTheme
                controller.isAppearanceLightNavigationBars = !darkTheme
            }

            LightLedgerTheme(themeMode = themeMode) {
                LightLedgerRoot(appViewModel = appViewModel)
            }
        }
    }

    /** 应用已在运行时（singleTask）快捷入口走这里 */
    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        handleShortcutIntent(intent)
    }

    private fun handleShortcutIntent(intent: Intent?) {
        intent?.getStringExtra(EXTRA_SHORTCUT_ROUTE)?.let { appViewModel.pushShortcutRoute(it) }
    }

    companion object {
        const val EXTRA_SHORTCUT_ROUTE = "route"
    }

    /** 请求设备支持的最高刷新率（90/120Hz），系统会自动回退到不支持的场景 */
    @Suppress("DEPRECATION")
    private fun requestHighestRefreshRate() {
        runCatching {
            val modes = if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.R) {
                display?.supportedModes
            } else {
                windowManager.defaultDisplay?.supportedModes
            }
            val best = modes?.maxByOrNull { it.refreshRate } ?: return
            window.attributes = window.attributes.apply {
                preferredDisplayModeId = best.modeId
            }
        }
    }
}
