package com.lightledger.app.ui.members

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.lightledger.app.LightLedgerApp
import com.lightledger.app.R
import com.lightledger.app.domain.model.SELF_ID
import androidx.lifecycle.compose.collectAsStateWithLifecycle

/**
 * 批量改归属弹窗：把日历页勾选的账单统一改成「公共」或指定成员组合。
 *
 * 做成 Dialog 而非独立页面，因为这是一次「就地批量操作」——
 * 用户刚在日历里勾完账单，操作完应当回到原来的列表位置，不该被推进另一页。
 */
@Composable
fun BatchOwnerDialog(
    billIds: List<Long>,
    bookId: Long,
    onDismiss: () -> Unit,
    onDone: (Int) -> Unit,
) {
    val context = LocalContext.current
    val container = (context.applicationContext as LightLedgerApp).container
    val viewModel: BatchOwnerViewModel = viewModel(
        key = "batchOwner-$bookId",
        factory = viewModelFactory {
            initializer { BatchOwnerViewModel(container, billIds, bookId) }
        }
    )
    val state by viewModel.uiState.collectAsStateWithLifecycle()

    val selfLabel = stringResource(R.string.record_self)
    val publicLabel = stringResource(R.string.batch_owner_public)
    val publicMember = state.members.firstOrNull { it.isPublic }

    val ownerDesc = state.resolvedOwners.joinToString("、") { id ->
        when (id) {
            SELF_ID -> selfLabel
            else -> state.members.firstOrNull { it.id == id }
                ?.let { if (it.isPublic) publicLabel else it.name }
                ?: "?"
        }
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(
                stringResource(R.string.batch_owner_title),
                style = MaterialTheme.typography.titleMedium,
            )
        },
        text = {
            Column {
                Text(
                    stringResource(R.string.batch_owner_preview, state.billCount),
                    style = MaterialTheme.typography.bodyMedium,
                )
                Spacer(Modifier.height(4.dp))
                Text(
                    text = "$ownerDesc · " + stringResource(
                        R.string.batch_owner_split, state.resolvedOwners.size
                    ),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )

                Spacer(Modifier.height(14.dp))
                Text(
                    stringResource(R.string.batch_owner_new_label),
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Spacer(Modifier.height(8.dp))

                // 快捷项：仅本人 / 公共
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OwnerChip(
                        text = stringResource(R.string.batch_owner_self),
                        selected = state.selectedOwners == setOf(SELF_ID),
                        onClick = { viewModel.selectSelfOnly() },
                    )
                    if (publicMember != null) {
                        OwnerChip(
                            text = publicLabel,
                            selected = state.selectedOwners == setOf(SELF_ID, publicMember.id),
                            onClick = { viewModel.selectPublic() },
                        )
                    }
                }

                // 逐成员多选（公共成员走上面的快捷入口，不重复列出）。
                // 只让这一块滚动，避免与弹窗自身的滚动嵌套造成手势冲突。
                Spacer(Modifier.height(6.dp))
                Box(
                    Modifier
                        .fillMaxWidth()
                        .heightIn(max = 220.dp)
                        .verticalScroll(rememberScrollState())
                ) {
                    Column {
                        state.members.filter { !it.isPublic }.forEach { m ->
                            val picked = m.id in state.selectedOwners
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable { viewModel.toggleOwner(m.id) }
                                    .padding(vertical = 9.dp),
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                Text(
                                    text = m.name,
                                    style = MaterialTheme.typography.bodyLarge,
                                    modifier = Modifier.weight(1f),
                                )
                                if (picked) {
                                    Text(
                                        text = "✓",
                                        color = MaterialTheme.colorScheme.primary,
                                        style = MaterialTheme.typography.titleMedium,
                                    )
                                }
                            }
                        }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(
                enabled = state.billCount > 0,
                onClick = { viewModel.apply { n -> onDone(n) } },
            ) {
                Text(stringResource(R.string.batch_owner_apply))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(R.string.cancel))
            }
        },
    )
}

/** 归属快捷选项 chip（仅本人 / 公共） */
@Composable
private fun OwnerChip(
    text: String,
    selected: Boolean,
    onClick: () -> Unit,
) {
    Text(
        text = text,
        style = MaterialTheme.typography.labelLarge,
        color = if (selected) MaterialTheme.colorScheme.onPrimary
        else MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier
            .clip(RoundedCornerShape(10.dp))
            .background(
                if (selected) MaterialTheme.colorScheme.primary
                else MaterialTheme.colorScheme.surfaceVariant
            )
            .clickable(onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 7.dp),
    )
}
