package com.lightledger.app.ui.detail

import android.Manifest
import android.widget.Toast
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.Edit
import androidx.compose.material.icons.outlined.Group
import androidx.compose.material.icons.outlined.IosShare
import androidx.compose.material.icons.outlined.LocationOn
import androidx.compose.material.icons.outlined.Notes
import androidx.compose.material.icons.outlined.Schedule
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import coil.compose.AsyncImage
import com.lightledger.app.LightLedgerApp
import com.lightledger.app.R
import com.lightledger.app.domain.model.ThemeMode
import com.lightledger.app.domain.model.TransactionType
import com.lightledger.app.ui.app.AppViewModel
import com.lightledger.app.ui.components.AppCard
import com.lightledger.app.ui.components.CategoryIcon
import com.lightledger.app.ui.components.ConfirmDialog
import com.lightledger.app.ui.components.EmptyState
import com.lightledger.app.ui.components.MemberChip
import com.lightledger.app.ui.theme.SemanticTheme
import com.lightledger.app.ui.theme.argb

import com.lightledger.app.util.DateUtils
import com.lightledger.app.util.MoneyFormat
import com.lightledger.app.util.SummaryImageExporter
import kotlinx.coroutines.launch

/**
 * 账单详情：分层卡片展示金额/成员/分类/地点/备注/时间/图片；
 * 图片点击放大（渐入+缩放过渡），删除前二次确认，顶栏可进入编辑或导出分享图。
 */
@Composable
fun BillDetailScreen(
    txId: Long,
    appViewModel: AppViewModel,
    onBack: () -> Unit,
    onEdit: () -> Unit = {},
) {
    val container = (LocalContext.current.applicationContext as LightLedgerApp).container
    val viewModel: BillDetailViewModel = viewModel(
        factory = viewModelFactory {
            initializer { BillDetailViewModel(container, txId) }
        }
    )
    val state by viewModel.state.collectAsStateWithLifecycle()
    val themeMode by appViewModel.themeMode.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    // 文案与颜色提前解析（onClick 非组合上下文不可调用 stringResource/MaterialTheme）
    val selfLabel = stringResource(R.string.record_self)
    val publicLabel = stringResource(R.string.member_public)
    val appName = stringResource(R.string.app_name)
    val uncategorizedLabel = stringResource(R.string.uncategorized)

    var confirmDelete by remember { mutableStateOf(false) }
    var previewIndex by remember { mutableStateOf<Int?>(null) }
    var exportingImage by remember { mutableStateOf(false) }

    val systemDark = isSystemInDarkTheme()
    val isDark = when (themeMode) {
        ThemeMode.LIGHT -> false
        ThemeMode.DARK -> true
        ThemeMode.SYSTEM -> systemDark
    }

    val tx = state.tx

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .statusBarsPadding(),
    ) {
        // ---------- 顶栏：返回 + 编辑 + 删除 ----------
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 8.dp, vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            IconButton(onClick = onBack) {
                Icon(
                    Icons.AutoMirrored.Outlined.ArrowBack,
                    contentDescription = stringResource(R.string.back),
                    tint = MaterialTheme.colorScheme.onBackground,
                )
            }
            Spacer(Modifier.weight(1f))
            IconButton(
                enabled = !exportingImage,
                onClick = {
                    val t = state.tx ?: return@IconButton
                    scope.launch {
                        exportingImage = true
                        val uri = SummaryImageExporter.exportSingle(
                            context,
                            SummaryImageExporter.SingleBill(
                                bookName = container.bookRepository.getById(t.bookId)?.name ?: appName,
                                categoryName = state.category?.name ?: uncategorizedLabel,
                                categoryColor = state.category?.color ?: 0xFF9A968D.toInt(),
                                amountText = (if (TransactionType.from(t.type) == TransactionType.EXPENSE) "-" else "+") +
                                    MoneyFormat.fenToPlain(t.amount),
                                isExpense = TransactionType.from(t.type) == TransactionType.EXPENSE,
                                memberName = state.member?.let { if (it.isPublic) publicLabel else it.name } ?: selfLabel,
                                memberColor = state.member?.color ?: 0xFF6C7A9C.toInt(),
                                payerName = state.payer?.let { if (it.isPublic) publicLabel else it.name } ?: selfLabel,
                                payerColor = state.payer?.color ?: 0xFF6C7A9C.toInt(),
                                timeText = DateUtils.formatFullTime(t.createdAt),
                                locationText = t.location,
                                noteText = t.note,
                                thumbnailPath = t.images.firstOrNull(),
                            ),
                            isDark,
                        )
                        exportingImage = false
                        Toast.makeText(
                            context,
                            context.getString(
                                if (uri != null) R.string.export_saved else R.string.export_failed
                            ),
                            Toast.LENGTH_SHORT,
                        ).show()
                    }
                },
            ) {
                Icon(
                    Icons.Outlined.IosShare,
                    contentDescription = stringResource(R.string.detail_export),
                    tint = MaterialTheme.colorScheme.primary,
                )
            }
            IconButton(onClick = onEdit) {
                Icon(
                    Icons.Outlined.Edit,
                    contentDescription = stringResource(R.string.record_edit_title),
                    tint = MaterialTheme.colorScheme.primary,
                )
            }
            IconButton(onClick = { confirmDelete = true }) {
                Icon(
                    Icons.Outlined.Delete,
                    contentDescription = stringResource(R.string.delete),
                    tint = MaterialTheme.colorScheme.error,
                )
            }
        }

        if (tx == null) {
            EmptyState(stringResource(R.string.empty_hint))
            return@Column
        }

        Column(
            modifier = Modifier
                .weight(1f)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp),
        ) {
            Spacer(Modifier.height(6.dp))

            val type = TransactionType.from(tx.type)

            // ---------- 金额主卡片 ----------
            AppCard {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(20.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    state.category?.let {
                        CategoryIcon(
                            iconKey = tx.iconOverride ?: it.icon,
                            color = it.color.argb(),
                            size = 52,
                        )
                        Spacer(Modifier.height(10.dp))
                        Text(it.name, style = MaterialTheme.typography.titleMedium)
                        Spacer(Modifier.height(6.dp))
                    }
                    Text(
                        text = (if (type == TransactionType.EXPENSE) "-" else "+") +
                            "¥${MoneyFormat.fenToString(tx.amount)}",
                        style = MaterialTheme.typography.headlineLarge,
                        color = if (type == TransactionType.EXPENSE) {
                            SemanticTheme.colors.expense
                        } else {
                            SemanticTheme.colors.income
                        },
                    )
                    Spacer(Modifier.height(4.dp))
                    Text(
                        text = stringResource(if (type == TransactionType.EXPENSE) R.string.record_expense else R.string.record_income),
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }

            Spacer(Modifier.height(12.dp))

            // ---------- 信息卡片：成员 / 地点 / 备注 / 时间 ----------
            AppCard {
                Column(Modifier.padding(vertical = 6.dp)) {
                    DetailRow(
                        icon = { Icon(Icons.Outlined.Schedule, null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(20.dp)) },
                        label = stringResource(R.string.detail_time),
                        value = DateUtils.formatFullTime(tx.createdAt),
                    )
                    // 归属 / 垫付成员行（有任一成员信息才显示；同人只显示一行）
                    val selfLabel = stringResource(R.string.record_self)
                    val selfColor = MaterialTheme.colorScheme.primary
                    val fallback = Color(0xFF6C7A9C)
                    if (state.member != null || state.payer != null) {
                        val ownerName = state.member?.let { if (it.isPublic) publicLabel else it.name } ?: selfLabel
                        val ownerColor = state.member?.color?.argb() ?: selfColor
                        val payerName = state.payer?.let { if (it.isPublic) publicLabel else it.name } ?: selfLabel
                        val payerColor = state.payer?.color?.argb() ?: selfColor
                        MemberDetailRow(label = stringResource(R.string.detail_owner), name = ownerName, color = ownerColor)
                        if (payerName != ownerName) {
                            MemberDetailRow(label = stringResource(R.string.detail_payer), name = payerName, color = payerColor)
                        }
                    }
                    tx.location?.let {
                        DetailRow(
                            icon = { Icon(Icons.Outlined.LocationOn, null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(20.dp)) },
                            label = stringResource(R.string.detail_location),
                            value = it,
                        )
                    }
                    tx.note?.let {
                        DetailRow(
                            icon = { Icon(Icons.Outlined.Notes, null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(20.dp)) },
                            label = stringResource(R.string.detail_note),
                            value = it,
                        )
                    }
                    // 心情行：emoji + 标签（仅记账时选了心情才显示）
                    tx.mood?.takeIf { it.isNotBlank() }?.let { mood ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 16.dp, vertical = 12.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Text(mood, fontSize = 17.sp)
                            Spacer(Modifier.width(10.dp))
                            Text(
                                stringResource(R.string.detail_mood),
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                }
            }

            // ---------- 图片卡片 ----------
            if (tx.images.isNotEmpty()) {
                Spacer(Modifier.height(12.dp))
                AppCard {
                    Column(Modifier.padding(16.dp)) {
                        Text(
                            stringResource(R.string.detail_receipts, tx.images.size),
                            style = MaterialTheme.typography.labelLarge,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        Spacer(Modifier.height(10.dp))
                        LazyRow(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                            itemsIndexed(tx.images) { index, path ->
                                AsyncImage(
                                    model = path,
                                    contentDescription = "小票图片 ${index + 1}",
                                    contentScale = ContentScale.Crop,
                                    modifier = Modifier
                                        .size(width = 110.dp, height = 84.dp)
                                        .clip(MaterialTheme.shapes.small)
                                        .clickable { previewIndex = index },
                                )
                            }
                        }
                    }
                }
            }

            Spacer(Modifier.height(20.dp))
        }
    }

    // ---------- 删除二次确认 ----------
    if (confirmDelete) {
        ConfirmDialog(
            title = stringResource(R.string.detail_delete_title),
            message = stringResource(R.string.detail_delete_msg),
            onConfirm = { viewModel.delete(onDone = onBack) },
            onDismiss = { confirmDelete = false },
        )
    }

    // ---------- 图片放大预览（渐入 + 缩放过渡，支持左右滑动） ----------
    AnimatedVisibility(
        visible = previewIndex != null,
        enter = fadeIn() + scaleIn(initialScale = 0.85f),
        exit = fadeOut(),
    ) {
        val images = tx?.images.orEmpty()
        val pagerState = rememberPagerState(
            initialPage = previewIndex?.coerceAtLeast(0) ?: 0,
            pageCount = { images.size },
        )
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Color.Black.copy(alpha = 0.92f))
                .clickable { previewIndex = null },
            contentAlignment = Alignment.Center,
        ) {
            HorizontalPager(state = pagerState) { page ->
                AsyncImage(
                    model = images.getOrNull(page),
                    contentDescription = "大图预览",
                    contentScale = ContentScale.Fit,
                    modifier = Modifier
                        .fillMaxSize()
                        .clickable(enabled = false) { },
                )
            }
            Text(
                text = "${pagerState.currentPage + 1} / ${images.size}",
                style = MaterialTheme.typography.labelMedium,
                color = Color.White,
                textAlign = TextAlign.Center,
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .navigationBarsPadding()
                    .padding(24.dp),
            )
        }
    }
}

/** 详情页成员行：图标 + 标签（归属/垫付）+ 成员胶囊 */
@Composable
private fun MemberDetailRow(label: String, name: String, color: Color) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            Icons.Outlined.Group,
            null,
            tint = MaterialTheme.colorScheme.primary,
            modifier = Modifier.size(20.dp),
        )
        Spacer(Modifier.width(10.dp))
        Text(
            label,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.width(48.dp),
        )
        Spacer(Modifier.width(8.dp))
        MemberChip(name = name, color = color)
    }
}

@Composable
private fun DetailRow(
    icon: @Composable () -> Unit,
    label: String,
    value: String,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        icon()
        Spacer(Modifier.width(10.dp))
        Text(
            label,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.width(48.dp),
        )
        Spacer(Modifier.width(8.dp))
        Text(value, style = MaterialTheme.typography.bodyLarge)
    }
}
