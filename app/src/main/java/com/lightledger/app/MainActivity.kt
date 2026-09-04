package com.lightledger.app

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.platform.LocalView
import androidx.core.view.WindowCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.lightledger.app.ui.app.AppViewModel
import com.lightledger.app.ui.navigation.LightLedgerRoot
import com.lightledger.app.ui.theme.LightLedgerTheme
import com.lightledger.app.domain.model.ThemeMode
import com.lightledger.app.util.LocaleHelper

class MainActivity : ComponentActivity() {

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
        val container = (application as LightLedgerApp).container
        // 高刷适配：在 90/120Hz 屏幕上请求系统最高刷新率，滚动更顺滑
        requestHighestRefreshRate()
        setContent {
            val appViewModel: AppViewModel = viewModel(
                factory = viewModelFactory {
                    initializer { AppViewModel(container) }
                }
            )
            val themeMode by appViewModel.themeMode.collectAsStateWithLifecycle()

            // 状态栏图标颜色随主题模式切换
            val view = LocalView.current
            val systemDark = isSystemInDarkTheme()
            LaunchedEffect(themeMode, systemDark) {
                val window = (view.context as? android.app.Activity)?.window ?: return@LaunchedEffect
                val controller = WindowCompat.getInsetsController(window, view)
                controller.isAppearanceLightStatusBars = when (themeMode) {
                    ThemeMode.LIGHT -> true
                    ThemeMode.DARK -> false
                    ThemeMode.SYSTEM -> !systemDark
                }
            }

            LightLedgerTheme(themeMode = themeMode) {
                LightLedgerRoot(appViewModel = appViewModel)
            }
        }
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
