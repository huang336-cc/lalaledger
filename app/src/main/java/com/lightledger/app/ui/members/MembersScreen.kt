package com.lightledger.app.ui.members

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.Edit
import androidx.compose.material.icons.outlined.GroupAdd
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.lightledger.app.LightLedgerApp
import com.lightledger.app.R
import com.lightledger.app.data.db.entity.MemberEntity
import com.lightledger.app.data.repository.SeedData
import com.lightledger.app.ui.components.AppCard
import com.lightledger.app.ui.components.ConfirmDialog
import com.lightledger.app.ui.components.EmptyState
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/**
 * 成员管理：旅行账本内维护同行成员（新增 / 编辑 / 删除）。
 * 删除成员时历史账单保留，仅归属置空显示为"本人"。
 */
@Composable
fun MembersScreen(
    bookId: Long,
    onBack: () -> Unit,
) {
    val container = (LocalContext.current.applicationContext as LightLedgerApp).container
    val viewModel: MembersViewModel = viewModel(
        factory = viewModelFactory {
            initializer { MembersViewModel(container, bookId) }
        }
    )
    val members by viewModel.members.collectAsStateWithLifecycle()

    // 弹窗状态：null = 关闭；MemberTarget(null 成员) = 新增
    var editorTarget by remember { mutableStateOf<MemberEntity?>(null) }
    var showEditor by remember { mutableStateOf(false) }
    var deleteTarget by remember { mutableStateOf<MemberEntity?>(null) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .statusBarsPadding(),
    ) {
        // 顶栏
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
            Text(stringResource(R.string.members_title), style = MaterialTheme.typography.titleLarge)
            Spacer(Modifier.weight(1f))
            Text(
                stringResource(R.string.members_add),
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier
                    .padding(horizontal = 12.dp, vertical = 6.dp)
                    .clickable { showEditor = true },
            )
        }

        Column(
            modifier = Modifier
                .weight(1f)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp),
        ) {
            if (members.isEmpty()) {
                EmptyState(
                    stringResource(R.string.members_empty),
                    icon = Icons.Outlined.GroupAdd,
                )
            }
            members.forEach { member ->
                val publicLabel = stringResource(R.string.member_public)
                AppCard {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 14.dp, vertical = 12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        // 成员色圆（首字）
                        Box(
                            modifier = Modifier
                                .size(40.dp)
                                .background(Color(member.color), CircleShape),
                            contentAlignment = Alignment.Center,
                        ) {
                            Text(
                                if (member.isPublic) publicLabel.take(1) else member.name.firstOrNull()?.toString() ?: "成",
                                color = Color.White,
                                style = MaterialTheme.typography.titleMedium,
                            )
                        }
                        Spacer(Modifier.width(12.dp))
                        Column(Modifier.weight(1f)) {
                            Text(
                                if (member.isPublic) publicLabel else member.name,
                                style = MaterialTheme.typography.titleMedium,
                            )
                            Text(
                                stringResource(
                                    if (member.isPublic) R.string.member_public_hint else R.string.members_item_hint
                                ),
                                style = MaterialTheme.typography.labelMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                        // 公共成员为系统内置（全员 AA 归属），不提供编辑与删除
                        if (!member.isPublic) {
                            IconButton(onClick = { editorTarget = member; showEditor = true }) {
                                Icon(
                                    Icons.Outlined.Edit,
                                    contentDescription = stringResource(R.string.members_edit),
                                    tint = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.size(20.dp),
                                )
                            }
                            IconButton(onClick = { deleteTarget = member }) {
                                Icon(
                                    Icons.Outlined.Delete,
                                    contentDescription = stringResource(R.string.delete),
                                    tint = MaterialTheme.colorScheme.error,
                                    modifier = Modifier.size(20.dp),
                                )
                            }
                        }
                    }
                }
                Spacer(Modifier.height(10.dp))
            }
            Spacer(Modifier.height(32.dp))
        }
    }

    // ---------- 新增 / 编辑成员弹窗 ----------
    if (showEditor) {
        MemberEditorDialog(
            initial = editorTarget,
            nextColor = viewModel.nextColor(),
            onDismiss = { showEditor = false },
            onConfirm = { target, name, color ->
                if (target == null) viewModel.add(name, color) else viewModel.update(target, name, color)
                showEditor = false
            },
        )
    }

    // ---------- 删除确认 ----------
    deleteTarget?.let { target ->
        ConfirmDialog(
            title = stringResource(R.string.members_delete_title, target.name),
            message = stringResource(R.string.members_delete_msg),
            onConfirm = { viewModel.delete(target.id) },
            onDismiss = { deleteTarget = null },
        )
    }
}

/**
 * 新增 / 编辑成员弹窗：名字 + 标签颜色。
 */
@Composable
private fun MemberEditorDialog(
    initial: MemberEntity?,
    nextColor: Int,
    onDismiss: () -> Unit,
    onConfirm: (initial: MemberEntity?, name: String, color: Int) -> Unit,
) {
    var name by remember { mutableStateOf(initial?.name.orEmpty()) }
    var color by remember { mutableStateOf(initial?.color ?: nextColor) }

    AlertDialog(
        onDismissRequest = onDismiss,
        shape = MaterialTheme.shapes.large,
        title = { Text(stringResource(if (initial == null) R.string.members_add_title else R.string.members_edit), style = MaterialTheme.typography.titleMedium) },
        text = {
            Column {
                OutlinedTextField(
                    value = name,
                    onValueChange = { if (it.length <= 8) name = it },
                    placeholder = { Text(stringResource(R.string.members_name_hint)) },
                    singleLine = true,
                    shape = MaterialTheme.shapes.small,
                    modifier = Modifier.fillMaxWidth(),
                )
                Spacer(Modifier.height(14.dp))
                Text(stringResource(R.string.members_color), style = MaterialTheme.typography.labelLarge)
                Spacer(Modifier.height(8.dp))
                LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    items(SeedData.memberColors.toList()) { c ->
                        val selected = c == color
                        Box(
                            modifier = Modifier
                                .size(30.dp)
                                .background(Color(c), CircleShape)
                                .border(
                                    width = if (selected) 2.5.dp else 0.dp,
                                    color = MaterialTheme.colorScheme.onSurface,
                                    shape = CircleShape,
                                )
                                .clickable { color = c },
                        )
                    }
                }
            }
        },
        confirmButton = {
            TextButton(
                onClick = { onConfirm(initial, name, color) },
                enabled = name.isNotBlank(),
            ) { Text(stringResource(R.string.save)) }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(R.string.cancel)) }
        },
    )
}

/**
 * 成员管理 ViewModel：按账本观察成员 + 增删改。
 */
class MembersViewModel(
    private val container: com.lightledger.app.AppContainer,
    private val bookId: Long,
) : ViewModel() {

    val members: StateFlow<List<MemberEntity>> = container.memberRepository.observeByBook(bookId)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    /** 新增成员的默认颜色：按现有数量取色板轮转，减少撞色 */
    fun nextColor(): Int {
        val n = members.value.size
        return SeedData.memberColors[n % SeedData.memberColors.size]
    }

    fun add(name: String, color: Int) {
        val trimmed = name.trim()
        if (trimmed.isEmpty() || bookId <= 0) return
        viewModelScope.launch { container.memberRepository.add(bookId, trimmed, color) }
    }

    fun update(target: MemberEntity, name: String, color: Int) {
        if (target.isPublic) return // 公共成员为内置项，禁止改名换色
        val trimmed = name.trim()
        if (trimmed.isEmpty()) return
        viewModelScope.launch { container.memberRepository.update(target.copy(name = trimmed, color = color)) }
    }

    fun delete(id: Long) {
        viewModelScope.launch {
            // 公共成员为内置项，禁止删除（删除会导致历史公共账单归属悬空）
            val target = container.memberRepository.getById(id)
            if (target?.isPublic == true) return@launch
            container.memberRepository.delete(id)
        }
    }
}
