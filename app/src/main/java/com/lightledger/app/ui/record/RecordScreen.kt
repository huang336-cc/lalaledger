package com.lightledger.app.ui.record

import android.Manifest
import android.net.Uri
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
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
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.LayoutCoordinates
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.FileProvider
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.lightledger.app.LightLedgerApp
import com.lightledger.app.R
import com.lightledger.app.domain.model.IconLibrary
import com.lightledger.app.ui.app.AppViewModel
import com.lightledger.app.util.ImageStore
import com.lightledger.app.util.LocaleHelper
import com.lightledger.app.util.LocationHelper
import kotlinx.coroutines.launch
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * 记账页：收支胶囊 → 金额（点击弹出数字键盘）→ 归属/垫付成员（旅行账本）→ 日期时间 →
 * 分类平铺 → 地点 → 图片 → 备注 → 保存。
 * 键盘为浮层弹出式：点金额弹出，选完分类自动收起。
 * 键盘收起手势：仅「点击主界面」收起；滑动滚动主界面不收起（位移阈值判定，滤掉小幅抖动）。
 * 编辑模式（editTxId > 0）：加载原账单回填，保存走更新分支，成功后返回上一页。
 * 权限按需申请：拍照申请相机；定位申请位置；相册走系统 Photo Picker，无需存储权限。
 */
@Composable
fun RecordScreen(
    appViewModel: AppViewModel,
    editTxId: Long = -1L,
    onDone: () -> Unit = {},
) {
    val container = (LocalContext.current.applicationContext as LightLedgerApp).container
    val viewModel: RecordViewModel = viewModel(
        factory = viewModelFactory {
            initializer { RecordViewModel(container, appViewModel.currentBookId) }
        }
    )
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val editorOpen by viewModel.editorOpen.collectAsStateWithLifecycle()
    val editorTarget by viewModel.editorTarget.collectAsStateWithLifecycle()
    val editorIsNew by viewModel.editorIsNew.collectAsStateWithLifecycle()
    val billTime by viewModel.billTime.collectAsStateWithLifecycle()

    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    val isEditMode = editTxId > 0
    LaunchedEffect(editTxId) {
        if (isEditMode) viewModel.loadForEdit(editTxId)
    }

    // 系统返回键：键盘展开时先收键盘，再按一次才返回
    BackHandler(enabled = state.keyboardOpen) {
        viewModel.closeKeyboard()
    }

    // ---------- 拍照：先备好目标文件，相机权限通过后再拉起相机 ----------
    var pendingCameraFile by remember { mutableStateOf<File?>(null) }

    fun fileProviderUri(context: android.content.Context, file: File): Uri =
        FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)

    val takePictureLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.TakePicture()
    ) { success ->
        if (success) {
            pendingCameraFile?.let { viewModel.addImage(it.absolutePath) }
        }
        pendingCameraFile = null
    }

    val cameraPermissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        val file = pendingCameraFile
        if (granted && file != null) {
            takePictureLauncher.launch(fileProviderUri(context, file))
        } else if (!granted) {
            Toast.makeText(
                context, context.getString(R.string.record_msg_no_camera_perm), Toast.LENGTH_SHORT
            ).show()
        }
    }

    fun onTakePhoto() {
        val file = ImageStore.newCameraFile(context)
        pendingCameraFile = file
        val uri = fileProviderUri(context, file)
        if (LocationHelper.hasCameraPermission(context)) {
            takePictureLauncher.launch(uri)
        } else {
            cameraPermissionLauncher.launch(Manifest.permission.CAMERA)
        }
    }

    // ---------- 相册（Photo Picker，无需存储权限） ----------
    val albumLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.PickMultipleVisualMedia(maxItems = 9)
    ) { uris ->
        scope.launch {
            // 图片解码压缩是 IO 操作，切到 IO 线程执行
            val paths = uris.mapNotNull { uri ->
                kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
                    ImageStore.importFromUri(context, uri)
                }
            }
            paths.forEach { viewModel.addImage(it) }
            if (paths.isNotEmpty()) {
                Toast.makeText(
                    context,
                    context.getString(R.string.record_msg_images_added, paths.size),
                    Toast.LENGTH_SHORT,
                ).show()
            }
        }
    }

    // ---------- 定位 ----------
    val locationPermissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { grants ->
        if (grants.values.any { it }) {
            scope.launch {
                val place = LocationHelper.fetchCurrentPlace(context)
                if (place != null) {
                    viewModel.setLocation(place)
                } else {
                    Toast.makeText(
                        context,
                        context.getString(R.string.record_msg_locate_failed),
                        Toast.LENGTH_SHORT,
                    ).show()
                }
            }
        } else {
            Toast.makeText(
                context, context.getString(R.string.record_msg_no_location_perm), Toast.LENGTH_SHORT
            ).show()
        }
    }

    var manualPlaceDialog by remember { mutableStateOf(false) }
    var showQuickMember by remember { mutableStateOf(false) }

    // 保存成功后的「再记一笔」浮层：确定 = 返回上一页；再记一笔 = 留在当前页继续输入
    var showSaveAgain by remember { mutableStateOf(false) }

    // 全量图标选择（更多入口）：先选图标 → 再选用途（替换常驻分类 / 仅本次使用）
    var showIconPicker by remember { mutableStateOf(false) }
    var pickedIcon by remember { mutableStateOf<String?>(null) }
    var showIconUseChoice by remember { mutableStateOf(false) }
    var showCategoryPick by remember { mutableStateOf(false) }
    // 图标名显示语言（跟随界面语言）
    val useZh = LocaleHelper.normalize(appViewModel.language.value) == LocaleHelper.ZH

    // 焦点管理：点备注框以外的任何位置都释放备注输入焦点（光标停止闪烁、系统键盘随焦点收起）
    val focusManager = LocalFocusManager.current

    fun handleSave() {
        // 保存前先释放备注焦点：收起系统输入法，避免「再记一笔」时光标仍在闪
        focusManager.clearFocus()
        viewModel.save(
            onSuccess = {
                if (isEditMode) {
                    Toast.makeText(context, context.getString(R.string.record_updated), Toast.LENGTH_SHORT).show()
                    onDone()
                } else {
                    // 弹「再记一笔」浮层代替 Toast：金额/备注等已由 VM 重置，类型/分类/成员保留
                    showSaveAgain = true
                }
            },
            onError = { msgRes -> Toast.makeText(context, context.getString(msgRes), Toast.LENGTH_SHORT).show() },
        )
    }

    // 键盘浮层动画：graphicsLayer 绘制层平移，不触发 measure/layout，避免卡顿
    val keyboardShown = remember { mutableStateOf(false) }
    val keyboardSlide = remember { Animatable(1f) } // 1f = 完全收起（屏幕外），0f = 展开
    // 备注输入框的布局信息（由 NotePhotoRow 回传）；滚动时 LayoutCoordinates 实例不变，
    // 但 boundsInRoot() 是实时值，因此下面的命中判断始终跟着界面滚动走，不会失效
    var noteFieldCoords by remember { mutableStateOf<LayoutCoordinates?>(null) }
    // 可滚动手势区的布局信息：用于把按下坐标换算到 root，与备注框的 root 边界统一比较
    var scrollAreaCoords by remember { mutableStateOf<LayoutCoordinates?>(null) }
    LaunchedEffect(state.keyboardOpen) {
        if (state.keyboardOpen) {
            keyboardShown.value = true
            keyboardSlide.animateTo(0f, tween(240, easing = FastOutSlowInEasing))
        } else if (keyboardShown.value) {
            keyboardSlide.animateTo(1f, tween(180, easing = FastOutSlowInEasing))
            keyboardShown.value = false
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background),
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .statusBarsPadding(),
        ) {
        // ---------- 编辑模式顶栏 ----------
        if (isEditMode) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 8.dp, vertical = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                IconButton(onClick = onDone) {
                    Icon(
                        Icons.AutoMirrored.Outlined.ArrowBack,
                        contentDescription = stringResource(R.string.back),
                        tint = MaterialTheme.colorScheme.onBackground,
                    )
                }
                Spacer(Modifier.weight(1f))
                Text(
                    stringResource(R.string.record_edit_title),
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onBackground,
                )
                Spacer(Modifier.weight(2f))
            }
        }

        // ---------- 可滚动内容区 ----------
        // 手势策略：主界面「点按或滑动」均收起数字键盘（滑动手势位移超阈值即视为滑动）；
        // 点金额卡打开键盘的这次点击由 onTapMainArea 的 120ms 时间窗保护，不会误关。
        // 释放备注焦点按「按下坐标是否命中备注输入框」判定：
        //   · 命中备注框（含在框内拖动选词）→ 保留焦点，否则会重演 v1.8.11「备注点不开」；
        //   · 未命中（空白、分类、类型、心情、拍照按钮等任何其它区域）→ 释放焦点，光标立即停止闪烁。
        // 这里不能用 clickable 代替：clickable 只响应未被子控件消费的点击，
        // 而分类/心情/按钮等都消费了事件，正是 v1.8.12「点了别处光标不灭」的根因。
        Column(
            modifier = Modifier
                .weight(1f)
                .verticalScroll(rememberScrollState())
                .onGloballyPositioned { scrollAreaCoords = it }
                .pointerInput(Unit) {
                    awaitEachGesture {
                        val down = awaitFirstDown(requireUnconsumed = false)
                        // 把按下点换算到备注框自身的坐标系，判断是否命中备注框
                        val areaCoords = scrollAreaCoords
                        val noteCoords = noteFieldCoords
                        val hitNoteField = if (areaCoords != null && noteCoords != null) {
                            val p = noteCoords.localPositionOf(areaCoords, down.position)
                            p.x >= 0f && p.y >= 0f &&
                                p.x <= noteCoords.size.width.toFloat() &&
                                p.y <= noteCoords.size.height.toFloat()
                        } else {
                            // 布局信息尚不可用时保守处理：不动焦点，避免误伤备注框
                            true
                        }
                        while (true) {
                            val event = awaitPointerEvent()
                            val change = event.changes.firstOrNull { it.id == down.id } ?: break
                            if (!change.pressed) break
                        }
                        // 手势结束（点按或滑动）统一收起数字键盘
                        viewModel.onTapMainArea()
                        // 备注框之外一律释放输入焦点，光标停止闪烁、系统输入法同步收起
                        if (!hitNoteField) focusManager.clearFocus()
                    }
                }
                .padding(horizontal = 16.dp),
        ) {
            // 金额显示：点击弹出数字键盘（紧贴状态栏，不再留顶部空白）
            AmountDisplay(
                amountText = state.amountText,
                onClick = viewModel::openKeyboard,
            )

            Spacer(Modifier.height(8.dp))

            TypeSwitch(
                current = state.type,
                onSelect = viewModel::selectType,
            )

            // ---------- 分类（视觉第二重点：金额 → 类型 → 分类 → 其他） ----------
            // 分类平铺全量列出；长按编辑；末尾「更多」点开全量图标选择（替换图标与名称）
            Spacer(Modifier.height(8.dp))
            CategorySection(
                categories = state.categories,
                selectedId = state.selectedCategoryId,
                moreLabel = stringResource(R.string.category_more),
                oneOffIcon = state.iconOverride,
                oneOffName = state.iconOverride?.let { IconLibrary.displayName(it, useZh) }.orEmpty(),
                onClearOneOff = viewModel::clearIconOverride,
                onSelect = viewModel::selectCategory,
                onLongPress = { viewModel.openCategoryEditor(it, isNew = false) },
                onMore = { showIconPicker = true },
            )

            // ---------- 时间 + 地点（合并一行，压缩纵向空间） ----------
            Spacer(Modifier.height(8.dp))
            TimePlaceRow(
                selectedMillis = billTime,
                displayText = SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.getDefault())
                    .format(Date(billTime ?: System.currentTimeMillis())),
                onPick = viewModel::setBillTime,
                selectedPlace = state.location,
                places = state.places.map { it.name },
                onSelectPlace = viewModel::setLocation,
                onClearLocation = viewModel::clearLocation,
                onManualInput = {
                    viewModel.closeKeyboard()
                    manualPlaceDialog = true
                },
                onLocate = {
                    viewModel.closeKeyboard()
                    if (LocationHelper.hasLocationPermission(context)) {
                        scope.launch {
                            val place = LocationHelper.fetchCurrentPlace(context)
                            if (place != null) viewModel.setLocation(place)
                            else Toast.makeText(
                                context,
                                context.getString(R.string.record_msg_locate_failed),
                                Toast.LENGTH_SHORT,
                            ).show()
                        }
                    } else {
                        locationPermissionLauncher.launch(
                            arrayOf(
                                Manifest.permission.ACCESS_FINE_LOCATION,
                                Manifest.permission.ACCESS_COARSE_LOCATION,
                            )
                        )
                    }
                },
            )

            Spacer(Modifier.height(8.dp))

            // ---------- 备注 + 相册（并排一行，压缩纵向空间，保证一屏可见） ----------
            NotePhotoRow(
                note = state.note,
                onNoteChange = viewModel::setNote,
                onTakePhoto = {
                    // 收起自定义键盘再拉起相机，避免叠加
                    viewModel.closeKeyboard()
                    onTakePhoto()
                },
                onPickAlbum = {
                    viewModel.closeKeyboard()
                    albumLauncher.launch(
                        PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)
                    )
                },
                onNoteFocus = { viewModel.closeKeyboard() },
                onNotePlaced = { noteFieldCoords = it },
            )

            // 已选图片：有图时才多占一行
            if (state.images.isNotEmpty()) {
                Spacer(Modifier.height(8.dp))
                ImageThumbRow(
                    images = state.images,
                    onRemove = viewModel::removeImage,
                )
            }

            // ---------- 心情 emoji（可选中/再点取消，随账单保存） ----------
            Spacer(Modifier.height(8.dp))
            MoodPickerRow(
                selected = state.mood,
                onSelect = viewModel::selectMood,
            )

            // ---------- 归属 / 垫付（旅行账本；合并为一排双列，保证一屏可见） ----------
            // 「+ 成员」按钮固定在「这笔归谁」列标题右侧；「公共」= 全员 AA
            if (state.isTripBook) {
                Spacer(Modifier.height(8.dp))
                OwnerPayerRow(
                    ownerTitle = stringResource(R.string.record_owner_title),
                    payerTitle = stringResource(R.string.record_payer_title),
                    selfLabel = stringResource(R.string.record_self),
                    members = state.members,
                    selectedOwnerId = state.selectedMemberId,
                    selectedPayerId = state.selectedPayerId,
                    publicLabel = stringResource(R.string.member_public),
                    addLabel = stringResource(R.string.record_add_member),
                    onSelectOwner = viewModel::selectMember,
                    onSelectPayer = viewModel::selectPayer,
                    onAddMember = { showQuickMember = true },
                )
            }

            Spacer(Modifier.height(16.dp))
        }

        // ---------- 底部保存按钮（常驻；键盘展开时被浮层覆盖，布局不跳动） ----------
        SaveButton(
            enabled = state.canSave,
            isEdit = isEditMode,
            onClick = { handleSave() },
        )
        }

        // ---------- 数字键盘：绘制层滑入滑出（无布局重排，丝滑不卡顿） ----------
        if (keyboardShown.value) {
            Box(
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .fillMaxWidth()
                    // 拦截浮层区域的点击，避免穿透到保存按钮与内容
                    .pointerInput(Unit) {
                        detectTapGestures { }
                    },
            ) {
                AmountKeyboard(
                    enabled = !state.saving,
                    onKey = viewModel::onAmountKey,
                    onClear = viewModel::clearAmount,
                    // 键盘上的大键 = 完成：只收起键盘，保存交给底部保存按钮
                    // （避免分类/备注等信息还没填就误存）
                    onDone = { viewModel.closeKeyboard() },
                    modifier = Modifier.graphicsLayer {
                        translationY = size.height * keyboardSlide.value
                    },
                )
            }
        }
    }

    // ---------- 分类编辑弹窗 ----------
    if (editorOpen) {
        CategoryEditorDialog(
            target = editorTarget,
            isNew = editorIsNew,
            onDismiss = viewModel::closeCategoryEditor,
            onSave = viewModel::saveCategory,
            onDelete = viewModel::deleteSelectedCategory,
        )
    }

    // ---------- 手动输入地点 ----------
    if (manualPlaceDialog) {
        TextInputDialog(
            title = stringResource(R.string.record_input_location),
            initial = state.location.orEmpty(),
            onDismiss = { manualPlaceDialog = false },
            onConfirm = {
                viewModel.setLocation(it)
                manualPlaceDialog = false
            },
        )
    }

    // ---------- 快捷添加成员（归属 / 垫付共用，添加后两行都可见） ----------
    if (showQuickMember) {
        TextInputDialog(
            title = stringResource(R.string.record_add_member_title),
            initial = "",
            onDismiss = { showQuickMember = false },
            onConfirm = {
                viewModel.addMemberQuick(it) { msgRes ->
                    Toast.makeText(context, context.getString(msgRes), Toast.LENGTH_SHORT).show()
                }
                showQuickMember = false
            },
        )
    }

    // ---------- 全量图标选择（分类区「更多」入口） ----------
    if (showIconPicker) {
        IconPickerDialog(
            title = stringResource(R.string.icon_picker_title),
            useZh = useZh,
            onDismiss = { showIconPicker = false },
            onPick = { key ->
                pickedIcon = key
                showIconPicker = false
                // 选完图标先问用途：替换常驻分类 / 仅本次使用
                showIconUseChoice = true
            },
        )
    }

    // ---------- 图标用途二选一（替换常驻分类 / 仅本次使用） ----------
    if (showIconUseChoice) {
        val icon = pickedIcon
        if (icon != null) {
            IconUseChoiceDialog(
                iconKey = icon,
                iconName = IconLibrary.displayName(icon, useZh),
                useZh = useZh,
                onDismiss = { showIconUseChoice = false },
                onReplaceCategory = {
                    showIconUseChoice = false
                    showCategoryPick = true
                },
                onUseOnce = {
                    showIconUseChoice = false
                    viewModel.useIconOnce(icon)
                    Toast.makeText(
                        context,
                        context.getString(
                            R.string.icon_use_once_done,
                            IconLibrary.displayName(icon, useZh),
                        ),
                        Toast.LENGTH_SHORT,
                    ).show()
                },
            )
        }
    }

    // ---------- 选择要替换图标的常驻分类 ----------
    if (showCategoryPick) {
        val icon = pickedIcon
        if (icon != null) {
            CategoryPickDialog(
                title = stringResource(R.string.category_replace_title),
                categories = state.categories,
                pickedIcon = icon,
                useZh = useZh,
                onDismiss = { showCategoryPick = false },
                onPickCategory = { category ->
                    val newName = IconLibrary.displayName(icon, useZh)
                    viewModel.replaceCategoryIcon(category.id, icon, newName)
                    showCategoryPick = false
                    Toast.makeText(
                        context,
                        context.getString(R.string.category_replace_done, newName),
                        Toast.LENGTH_SHORT,
                    ).show()
                },
            )
        }
    }

    // ---------- 保存成功：再记一笔 / 知道了 ----------
    if (showSaveAgain) {
        AlertDialog(
            onDismissRequest = { showSaveAgain = false },
            title = { Text(stringResource(R.string.record_saved_again_title)) },
            text = { Text(stringResource(R.string.record_saved_again_msg)) },
            confirmButton = {
                TextButton(
                    onClick = {
                        // 关掉浮层继续输入：VM 已重置金额/备注/图片/时间，类型/分类/成员保留
                        showSaveAgain = false
                    }
                ) { Text(stringResource(R.string.record_save_again)) }
            },
            dismissButton = {
                TextButton(
                    onClick = {
                        showSaveAgain = false
                        onDone()
                    }
                ) { Text(stringResource(R.string.record_saved_ok)) }
            },
        )
    }
}

/** 常用心情 emoji（横滑选择，再点同一枚取消） */
private val MOOD_EMOJIS = listOf(
    "😀", "🥰", "😎", "🤩", "😂", "😌",
    "😪", "🥱", "😢", "😭", "😤", "😡", "🤒", "🥺",
)

@Composable
private fun MoodPickerRow(
    selected: String?,
    onSelect: (String?) -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .horizontalScroll(rememberScrollState()),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = stringResource(R.string.record_mood),
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(Modifier.width(4.dp))
        MOOD_EMOJIS.forEach { emoji ->
            val isSel = emoji == selected
            Surface(
                onClick = { onSelect(if (isSel) null else emoji) },
                shape = RoundedCornerShape(50),
                color = if (isSel) MaterialTheme.colorScheme.primaryContainer
                else MaterialTheme.colorScheme.surface,
                border = if (isSel) BorderStroke(1.dp, MaterialTheme.colorScheme.primary) else null,
                modifier = Modifier.padding(end = 6.dp),
            ) {
                Text(
                    text = emoji,
                    fontSize = 20.sp,
                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                )
            }
        }
    }
}
