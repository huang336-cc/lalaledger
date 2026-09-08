package com.lightledger.app.ui.settings

import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.KeyboardArrowRight
import androidx.compose.material.icons.outlined.DeleteSweep
import androidx.compose.material.icons.outlined.Contrast
import androidx.compose.material.icons.outlined.FileUpload
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material.icons.outlined.Language
import androidx.compose.material.icons.outlined.MenuBook
import androidx.compose.material.icons.outlined.Schedule
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.RadioButtonDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.lightledger.app.LightLedgerApp
import com.lightledger.app.R
import com.lightledger.app.domain.model.ThemeMode
import com.lightledger.app.ui.app.AppViewModel
import com.lightledger.app.ui.components.AppCard
import com.lightledger.app.ui.components.ConfirmDialog
import com.lightledger.app.util.DateUtils
import com.lightledger.app.util.LocaleHelper
import com.lightledger.app.util.LocaleHelper.findActivity
import com.lightledger.app.util.MoneyFormat
import kotlinx.coroutines.launch

/** ThemeMode → 文案资源 */
private fun ThemeMode.labelRes(): Int = when (this) {
    ThemeMode.LIGHT -> R.string.theme_light
    ThemeMode.DARK -> R.string.theme_dark
    ThemeMode.SYSTEM -> R.string.theme_system
}

/** 当前应用版本：直接取构建注入的 versionName，永不与实际包版本脱节 */
private val APP_VERSION: String = com.lightledger.app.BuildConfig.VERSION_NAME

/**
 * 我的：极简设置页。深色模式 / 语言（简体中文 / English，切换即生效）/
 * 清空当前账本 / 变更履历 / 关于（开源许可 + 免责声明）。
 */
@Composable
fun SettingsScreen(
    appViewModel: AppViewModel,
    onOpenBooks: () -> Unit,
) {
    val container = (LocalContext.current.applicationContext as LightLedgerApp).container
    val themeMode by appViewModel.themeMode.collectAsStateWithLifecycle()
    val language by appViewModel.language.collectAsStateWithLifecycle()
    val currentBook by appViewModel.currentBook.collectAsStateWithLifecycle()
    val currentBookId by appViewModel.currentBookId.collectAsStateWithLifecycle()

    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    var showThemeDialog by remember { mutableStateOf(false) }
    var showLanguageDialog by remember { mutableStateOf(false) }
    var showAboutDialog by remember { mutableStateOf(false) }
    var showLicenseDialog by remember { mutableStateOf(false) }
    var showDisclaimerDialog by remember { mutableStateOf(false) }
    var showClearDialog by remember { mutableStateOf(false) }
    var showChangelogDialog by remember { mutableStateOf(false) }

    // ---------- CSV 账单导入 ----------
    val importViewModel: CsvImportViewModel = viewModel(
        factory = viewModelFactory {
            initializer { CsvImportViewModel(container, appViewModel.currentBookId) }
        }
    )
    val importState by importViewModel.state.collectAsStateWithLifecycle()
    val defaultCategoryName = stringResource(R.string.import_csv_default_category)
    val csvPicker = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri ->
        if (uri != null) importViewModel.parse(context, uri, defaultCategoryName)
    }
    // 结果/错误以 Toast 提示后回到空闲态
    LaunchedEffect(importState) {
        when (val s = importState) {
            is CsvImportViewModel.State.Done -> {
                Toast.makeText(
                    context, context.getString(R.string.import_csv_done, s.count), Toast.LENGTH_SHORT
                ).show()
                importViewModel.reset()
            }
            is CsvImportViewModel.State.Error -> {
                Toast.makeText(context, context.getString(s.messageRes), Toast.LENGTH_SHORT).show()
                importViewModel.reset()
            }
            else -> Unit
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 16.dp),
    ) {
        Spacer(Modifier.height(12.dp))
        Text(stringResource(R.string.settings_title), style = MaterialTheme.typography.headlineLarge)
        Spacer(Modifier.height(14.dp))

        // ---------- 当前账本卡片 ----------
        AppCard {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable(onClick = onOpenBooks)
                    .padding(16.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(
                    imageVector = Icons.Outlined.MenuBook,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                )
                Spacer(Modifier.width(12.dp))
                Column(Modifier.weight(1f)) {
                    Text(
                        currentBook?.name ?: stringResource(R.string.settings_no_book),
                        style = MaterialTheme.typography.titleMedium,
                    )
                    Text(
                        stringResource(R.string.settings_book_hint),
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                Icon(
                    Icons.AutoMirrored.Outlined.KeyboardArrowRight,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }

        Spacer(Modifier.height(14.dp))

        // ---------- 设置列表 ----------
        AppCard {
            SettingRow(
                icon = Icons.Outlined.Contrast,
                title = stringResource(R.string.settings_theme),
                value = stringResource(themeMode.labelRes()),
                onClick = { showThemeDialog = true },
            )
            HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.5f))
            SettingRow(
                icon = Icons.Outlined.Language,
                title = stringResource(R.string.settings_language),
                value = stringResource(if (language == LocaleHelper.EN) R.string.lang_en else R.string.lang_zh),
                onClick = { showLanguageDialog = true },
            )
            HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.5f))
            SettingRow(
                icon = Icons.Outlined.FileUpload,
                title = stringResource(R.string.settings_import_csv),
                value = currentBook?.name ?: "",
                onClick = { csvPicker.launch("*/*") },
            )
            HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.5f))
            SettingRow(
                icon = Icons.Outlined.DeleteSweep,
                title = stringResource(R.string.settings_clear),
                value = currentBook?.name ?: "",
                valueTint = MaterialTheme.colorScheme.error,
                onClick = {
                    if (currentBookId > 0) showClearDialog = true
                },
            )
            HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.5f))
            SettingRow(
                icon = Icons.Outlined.Schedule,
                title = stringResource(R.string.settings_changelog),
                value = "v$APP_VERSION",
                onClick = { showChangelogDialog = true },
            )
            HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.5f))
            SettingRow(
                icon = Icons.Outlined.Info,
                title = stringResource(R.string.settings_about),
                value = stringResource(R.string.app_name) + " $APP_VERSION",
                onClick = { showAboutDialog = true },
            )
        }

        Spacer(Modifier.height(20.dp))
        Text(
            stringResource(R.string.settings_privacy),
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(horizontal = 8.dp),
        )
        Spacer(Modifier.height(20.dp))
    }

    // ---------- 深色模式三选 ----------
    if (showThemeDialog) {
        AlertDialog(
            onDismissRequest = { showThemeDialog = false },
            shape = MaterialTheme.shapes.large,
            title = { Text(stringResource(R.string.settings_theme), style = MaterialTheme.typography.titleMedium) },
            text = {
                Column {
                    ThemeMode.entries.forEach { mode ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable {
                                    appViewModel.setThemeMode(mode)
                                    showThemeDialog = false
                                }
                                .padding(vertical = 4.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            RadioButton(
                                selected = themeMode == mode,
                                onClick = {
                                    appViewModel.setThemeMode(mode)
                                    showThemeDialog = false
                                },
                                colors = RadioButtonDefaults.colors(
                                    selectedColor = MaterialTheme.colorScheme.primary,
                                ),
                            )
                            Text(stringResource(mode.labelRes()), style = MaterialTheme.typography.bodyLarge)
                        }
                    }
                }
            },
            confirmButton = {},
            dismissButton = {
                TextButton(onClick = { showThemeDialog = false }) { Text(stringResource(R.string.cancel)) }
            },
        )
    }

    // ---------- 语言二选一（简体中文 / English，切换即时生效） ----------
    if (showLanguageDialog) {
        AlertDialog(
            onDismissRequest = { showLanguageDialog = false },
            shape = MaterialTheme.shapes.large,
            title = { Text(stringResource(R.string.settings_language), style = MaterialTheme.typography.titleMedium) },
            text = {
                Column {
                    listOf(LocaleHelper.ZH to R.string.lang_zh, LocaleHelper.EN to R.string.lang_en)
                        .forEach { (tag, nameRes) ->
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable {
                                        appViewModel.setLanguage(tag)
                                        showLanguageDialog = false
                                        // recreate 使 attachBaseContext 重新读取语言缓存
                                        context.findActivity()?.recreate()
                                    }
                                    .padding(vertical = 4.dp),
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                RadioButton(
                                    selected = language == tag,
                                    onClick = {
                                        appViewModel.setLanguage(tag)
                                        showLanguageDialog = false
                                        context.findActivity()?.recreate()
                                    },
                                    colors = RadioButtonDefaults.colors(
                                        selectedColor = MaterialTheme.colorScheme.primary,
                                    ),
                                )
                                // 语言名固定用各自语言显示：无论当前界面语言
                                Text(stringResource(nameRes), style = MaterialTheme.typography.bodyLarge)
                            }
                        }
                }
            },
            confirmButton = {},
            dismissButton = {
                TextButton(onClick = { showLanguageDialog = false }) { Text(stringResource(R.string.cancel)) }
            },
        )
    }

    // ---------- 关于（含开源许可 / 免责声明入口） ----------
    if (showAboutDialog) {
        AlertDialog(
            onDismissRequest = { showAboutDialog = false },
            shape = MaterialTheme.shapes.large,
            title = { Text(stringResource(R.string.app_name), style = MaterialTheme.typography.titleMedium) },
            text = {
                Column {
                    Text(
                        stringResource(R.string.about_body, APP_VERSION),
                        style = MaterialTheme.typography.bodyMedium,
                    )
                    Spacer(Modifier.height(12.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        TextButton(onClick = {
                            showAboutDialog = false
                            showLicenseDialog = true
                        }) { Text(stringResource(R.string.settings_license)) }
                        TextButton(onClick = {
                            showAboutDialog = false
                            showDisclaimerDialog = true
                        }) { Text(stringResource(R.string.settings_disclaimer)) }
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { showAboutDialog = false }) { Text(stringResource(R.string.ok)) }
            },
        )
    }

    // ---------- 开源许可 ----------
    if (showLicenseDialog) {
        AlertDialog(
            onDismissRequest = { showLicenseDialog = false },
            shape = MaterialTheme.shapes.large,
            title = { Text(stringResource(R.string.settings_license), style = MaterialTheme.typography.titleMedium) },
            text = {
                Text(
                    stringResource(R.string.license_body),
                    style = MaterialTheme.typography.bodySmall,
                    modifier = Modifier.verticalScroll(rememberScrollState()),
                )
            },
            confirmButton = {
                TextButton(onClick = { showLicenseDialog = false }) { Text(stringResource(R.string.ok)) }
            },
        )
    }

    // ---------- 免责声明 ----------
    if (showDisclaimerDialog) {
        AlertDialog(
            onDismissRequest = { showDisclaimerDialog = false },
            shape = MaterialTheme.shapes.large,
            title = { Text(stringResource(R.string.settings_disclaimer), style = MaterialTheme.typography.titleMedium) },
            text = {
                Text(
                    stringResource(R.string.disclaimer_body),
                    style = MaterialTheme.typography.bodySmall,
                    modifier = Modifier.verticalScroll(rememberScrollState()),
                )
            },
            confirmButton = {
                TextButton(onClick = { showDisclaimerDialog = false }) { Text(stringResource(R.string.ok)) }
            },
        )
    }

    // ---------- 变更履历 ----------
    if (showChangelogDialog) {
        AlertDialog(
            onDismissRequest = { showChangelogDialog = false },
            shape = MaterialTheme.shapes.large,
            title = { Text(stringResource(R.string.settings_changelog), style = MaterialTheme.typography.titleMedium) },
            text = {
                Column(
                    modifier = Modifier.verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(14.dp),
                ) {
                    ChangeLog.entries.forEach { entry ->
                        Column {
                            Text(
                                text = "v${entry.version}",
                                style = MaterialTheme.typography.labelLarge,
                                color = MaterialTheme.colorScheme.primary,
                            )
                            Text(
                                text = entry.date,
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                            Spacer(Modifier.height(4.dp))
                            entry.items.forEach { itemRes ->
                                Text(
                                    text = "· ${stringResource(itemRes)}",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurface,
                                )
                            }
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { showChangelogDialog = false }) { Text(stringResource(R.string.ok)) }
            },
        )
    }

    // ---------- CSV 导入：解析中 / 导入中 / 预览确认 ----------
    when (val s = importState) {
        CsvImportViewModel.State.Parsing -> {
            ProgressDialog(text = stringResource(R.string.import_csv_parsing))
        }
        CsvImportViewModel.State.Importing -> {
            ProgressDialog(text = stringResource(R.string.import_csv_importing))
        }
        is CsvImportViewModel.State.Previewing -> {
            val p = s.preview
            val zone = remember { java.time.ZoneId.systemDefault() }
            val startDate = java.time.Instant.ofEpochMilli(p.minTime).atZone(zone).toLocalDate()
            val endDate = java.time.Instant.ofEpochMilli(p.maxTime).atZone(zone).toLocalDate()
            AlertDialog(
                onDismissRequest = { importViewModel.reset() },
                shape = MaterialTheme.shapes.large,
                title = {
                    Text(
                        stringResource(R.string.import_csv_title),
                        style = MaterialTheme.typography.titleMedium,
                    )
                },
                text = {
                    Column(
                        modifier = Modifier.verticalScroll(rememberScrollState()),
                        verticalArrangement = Arrangement.spacedBy(6.dp),
                    ) {
                        Text(
                            stringResource(R.string.import_csv_book, p.bookName),
                            style = MaterialTheme.typography.titleSmall,
                        )
                        Text(
                            stringResource(
                                R.string.import_csv_count,
                                p.rows.size, p.expenseCount, p.incomeCount,
                                MoneyFormat.fenToString(p.totalFen),
                            ),
                            style = MaterialTheme.typography.bodyMedium,
                        )
                        Text(
                            stringResource(
                                R.string.import_csv_range,
                                DateUtils.formatDate(startDate),
                                DateUtils.formatDate(endDate),
                            ),
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        if (p.newCategoryNames.isNotEmpty()) {
                            Text(
                                stringResource(R.string.import_csv_new_cat, p.newCategoryNames.size) +
                                    "：" + p.newCategoryNames.take(6).joinToString("、") +
                                    (if (p.newCategoryNames.size > 6) " …" else ""),
                                style = MaterialTheme.typography.labelMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                        if (p.newMemberNames.isNotEmpty()) {
                            Text(
                                stringResource(R.string.import_csv_new_member, p.newMemberNames.size) +
                                    "：" + p.newMemberNames.take(6).joinToString("、") +
                                    (if (p.newMemberNames.size > 6) " …" else ""),
                                style = MaterialTheme.typography.labelMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                        if (p.skipped > 0) {
                            Text(
                                stringResource(R.string.import_csv_skipped, p.skipped),
                                style = MaterialTheme.typography.labelMedium,
                                color = MaterialTheme.colorScheme.error,
                            )
                        }
                    }
                },
                confirmButton = {
                    TextButton(onClick = { importViewModel.confirmImport() }) {
                        Text(stringResource(R.string.confirm))
                    }
                },
                dismissButton = {
                    TextButton(onClick = { importViewModel.reset() }) {
                        Text(stringResource(R.string.cancel))
                    }
                },
            )
        }
        else -> Unit
    }

    // ---------- 清空当前账本 ----------
    if (showClearDialog) {
        ConfirmDialog(
            title = stringResource(R.string.settings_clear_title, currentBook?.name ?: ""),
            message = stringResource(R.string.settings_clear_msg),
            confirmText = stringResource(R.string.confirm),
            onConfirm = {
                val bookId = currentBookId
                scope.launch {
                    container.transactionRepository.clearBook(bookId)
                    Toast.makeText(
                        context, context.getString(R.string.settings_clear_done), Toast.LENGTH_SHORT
                    ).show()
                }
            },
            onDismiss = { showClearDialog = false },
        )
    }
}

/** 导入 CSV 时的忙碌弹窗（不可取消） */
@Composable
private fun ProgressDialog(text: String) {
    AlertDialog(
        onDismissRequest = {},
        shape = MaterialTheme.shapes.large,
        text = {
            Row(verticalAlignment = Alignment.CenterVertically) {
                CircularProgressIndicator(
                    modifier = Modifier.size(22.dp),
                    strokeWidth = 2.dp,
                )
                Spacer(Modifier.width(14.dp))
                Text(text, style = MaterialTheme.typography.bodyMedium)
            }
        },
        confirmButton = {},
    )
}

@Composable
private fun SettingRow(
    icon: ImageVector,
    title: String,
    value: String,
    onClick: () -> Unit,
    valueTint: androidx.compose.ui.graphics.Color = MaterialTheme.colorScheme.onSurfaceVariant,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 15.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.primary,
            modifier = Modifier.size(22.dp),
        )
        Spacer(Modifier.width(12.dp))
        Text(
            title,
            style = MaterialTheme.typography.bodyLarge,
            modifier = Modifier.weight(1f),
        )
        Text(
            value,
            style = MaterialTheme.typography.labelMedium,
            color = valueTint,
            maxLines = 1,
        )
        Spacer(Modifier.width(4.dp))
        Icon(
            Icons.AutoMirrored.Outlined.KeyboardArrowRight,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

/** 版本变更履历（最新在前），条目走字符串资源支持中英文 */
private enum class ChangeLog(val version: String, val date: String, val items: List<Int>) {
    V1_8_1(
        "1.8.1", "2026-09-08",
        listOf(
            R.string.chlog_181_1,
            R.string.chlog_181_2,
        ),
    ),
    V1_8_0(
        "1.8.0", "2026-09-05",
        listOf(
            R.string.chlog_180_1,
            R.string.chlog_180_2,
            R.string.chlog_180_3,
            R.string.chlog_180_4,
        ),
    ),
    V1_7_2(
        "1.7.2", "2026-09-08",
        listOf(
            R.string.chlog_172_1,
        ),
    ),
    V1_7_1(
        "1.7.1", "2026-09-08",
        listOf(
            R.string.chlog_171_1,
        ),
    ),
    V1_7_0(
        "1.7.0", "2026-09-08",
        listOf(
            R.string.chlog_170_1,
        ),
    ),
    V1_6_3(
        "1.6.3", "2026-09-08",
        listOf(
            R.string.chlog_163_1,
        ),
    ),
    V1_6_2(
        "1.6.2", "2026-09-08",
        listOf(
            R.string.chlog_162_1,
        ),
    ),
    V1_6_1(
        "1.6.1", "2026-09-08",
        listOf(
            R.string.chlog_161_1,
        ),
    ),
    V1_6_0(
        "1.6.0", "2026-09-09",
        listOf(
            R.string.chlog_160_1,
            R.string.chlog_160_2,
            R.string.chlog_160_3,
        ),
    ),
    V1_5_0(
        "1.5.0", "2026-09-06",
        listOf(
            R.string.chlog_150_1,
            R.string.chlog_150_2,
            R.string.chlog_150_3,
            R.string.chlog_150_4,
        ),
    ),
    V1_4_9(
        "1.4.9", "2026-09-06",
        listOf(
            R.string.chlog_149_1,
        ),
    ),
    V1_4_8(
        "1.4.8", "2026-09-06",
        listOf(
            R.string.chlog_148_1,
            R.string.chlog_148_2,
        ),
    ),
    V1_4_7(
        "1.4.7", "2026-09-05",
        listOf(
            R.string.chlog_147_1,
        ),
    ),
    V1_4_6(
        "1.4.6", "2026-09-05",
        listOf(
            R.string.chlog_146_1,
        ),
    ),
    V1_4_5(
        "1.4.5", "2026-09-04",
        listOf(
            R.string.chlog_145_1,
            R.string.chlog_145_2,
            R.string.chlog_145_3,
            R.string.chlog_145_4,
        ),
    ),
    V1_4_4(
        "1.4.4", "2026-09-04",
        listOf(
            R.string.chlog_144_1,
            R.string.chlog_144_2,
            R.string.chlog_144_3,
        ),
    ),
    V1_4_3(
        "1.4.3", "2026-09-04",
        listOf(
            R.string.chlog_143_1,
            R.string.chlog_143_2,
            R.string.chlog_143_3,
        ),
    ),
    V1_4_2(
        "1.4.2", "2026-09-04",
        listOf(
            R.string.chlog_142_1,
        ),
    ),
    V1_4_1(
        "1.4.1", "2026-09-04",
        listOf(
            R.string.chlog_141_1,
            R.string.chlog_141_2,
        ),
    ),
    V1_4_0(
        "1.4.0", "2026-09-04",
        listOf(
            R.string.chlog_140_1,
            R.string.chlog_140_2,
            R.string.chlog_140_3,
            R.string.chlog_140_4,
            R.string.chlog_140_5,
            R.string.chlog_140_6,
            R.string.chlog_140_7,
            R.string.chlog_140_8,
            R.string.chlog_140_9,
            R.string.chlog_140_10,
        ),
    ),
    V1_3_0(
        "1.3.0", "2026-09-04",
        listOf(
            R.string.chlog_130_1,
            R.string.chlog_130_2,
            R.string.chlog_130_3,
            R.string.chlog_130_4,
            R.string.chlog_130_5,
            R.string.chlog_130_6,
        ),
    ),
    V1_2_0(
        "1.2.0", "2026-09-04",
        listOf(
            R.string.chlog_120_1,
            R.string.chlog_120_2,
        ),
    ),
    V1_1_0(
        "1.1.0", "2026-09-04",
        listOf(
            R.string.chlog_110_1,
            R.string.chlog_110_2,
            R.string.chlog_110_3,
            R.string.chlog_110_4,
            R.string.chlog_110_5,
            R.string.chlog_110_6,
        ),
    ),
    V1_0_0(
        "1.0.0", "2026-09-03",
        listOf(
            R.string.chlog_100_1,
            R.string.chlog_100_2,
            R.string.chlog_100_3,
        ),
    ),
}
