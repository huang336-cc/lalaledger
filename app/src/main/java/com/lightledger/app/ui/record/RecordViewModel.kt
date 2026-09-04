package com.lightledger.app.ui.record

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.lightledger.app.AppContainer
import com.lightledger.app.data.db.entity.AccountBookEntity
import com.lightledger.app.data.db.entity.CategoryEntity
import com.lightledger.app.data.db.entity.MemberEntity
import com.lightledger.app.data.db.entity.PlaceEntity
import com.lightledger.app.domain.model.TransactionType
import com.lightledger.app.util.MoneyFormat
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

data class RecordUiState(
    val amountText: String = "",
    val type: TransactionType = TransactionType.EXPENSE,
    val categories: List<CategoryEntity> = emptyList(),
    val selectedCategoryId: Long? = null,
    val location: String? = null,
    val note: String = "",
    val images: List<String> = emptyList(),
    val places: List<PlaceEntity> = emptyList(),
    val saving: Boolean = false,
    /** 数字键盘浮层是否展开（点金额弹出、选分类收起） */
    val keyboardOpen: Boolean = false,
    /** 编辑模式：非 null 表示正在修改这笔账单 */
    val editTxId: Long? = null,
    /** 旅行账本开关：true 时显示成员归属选择 */
    val isTripBook: Boolean = false,
    /** 旅行账本成员列表 */
    val members: List<MemberEntity> = emptyList(),
    /** 归属成员（谁消费）；null = 本人（默认） */
    val selectedMemberId: Long? = null,
    /** 付款成员（谁垫付）；null = 本人（默认） */
    val selectedPayerId: Long? = null,
    /** 账单时间；null = 使用当前时间 */
    val billTime: Long? = null,
) {
    val canSave: Boolean
        get() = !saving && MoneyFormat.yuanToFen(amountText) != null && selectedCategoryId != null
}

/**
 * 记账页：快速录入状态机。
 * 金额键盘为浮层弹出式：点击金额区弹出，选中分类后自动收起，保证分类选择不被遮挡。
 * 支持编辑模式：loadForEdit 回填后保存走 update 分支，保留原创建时间。
 */
@OptIn(ExperimentalCoroutinesApi::class)
class RecordViewModel(
    private val container: AppContainer,
    private val currentBookId: StateFlow<Long>,
) : ViewModel() {

    private val amountText = MutableStateFlow("")
    private val type = MutableStateFlow(TransactionType.EXPENSE)
    private val selectedCategoryId = MutableStateFlow<Long?>(null)
    private val location = MutableStateFlow<String?>(null)
    private val note = MutableStateFlow("")
    private val images = MutableStateFlow<List<String>>(emptyList())
    private val saving = MutableStateFlow(false)
    private val keyboardOpen = MutableStateFlow(false)

    /** 归属成员（谁消费）：null = 本人；仅旅行账本可修改 */
    private val selectedMemberId = MutableStateFlow<Long?>(null)

    /** 付款成员（谁垫付）：null = 本人；仅旅行账本可修改 */
    private val selectedPayerId = MutableStateFlow<Long?>(null)

    /** 账单时间：null = 当前时间（公开只读供 UI 展示选中值） */
    val billTime = MutableStateFlow<Long?>(null)

    /** 编辑模式：目标账单 id；null = 新建 */
    private val editTxId = MutableStateFlow<Long?>(null)
    private var editingBase: com.lightledger.app.data.db.entity.TransactionEntity? = null

    /** 编辑回填保护：分类列表未加载完成前，禁止自动重置选中分类 */
    private val editingRestore = MutableStateFlow(false)

    /** 分类编辑弹窗：null = 关闭；CategoryEditorTarget(null 分类) = 新增 */
    val editorTarget = MutableStateFlow<CategoryEntity?>(null)
    val editorOpen = MutableStateFlow(false)
    val editorIsNew = MutableStateFlow(false)

    val places: StateFlow<List<PlaceEntity>> = container.placeRepository.observeRecent(12)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    val categories: StateFlow<List<CategoryEntity>> = type
        .flatMapLatest { t -> container.categoryRepository.observeByType(t) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    /** 当前账本是否旅行账本（决定是否显示成员归属选择） */
    private val isTripBook: StateFlow<Boolean> = currentBookId
        .flatMapLatest { id ->
            if (id <= 0) kotlinx.coroutines.flow.flowOf<AccountBookEntity?>(null)
            else container.bookRepository.observeById(id)
        }
        .map { it?.isTrip == true }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), false)

    /** 当前账本成员列表（仅旅行账本非空） */
    val members: StateFlow<List<MemberEntity>> = currentBookId
        .flatMapLatest { id ->
            if (id <= 0) kotlinx.coroutines.flow.flowOf(emptyList())
            else container.memberRepository.observeByBook(id)
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    /** 账本切换到非旅行账本时，归属与垫付自动回落本人 */
    init {
        viewModelScope.launch {
            isTripBook.collect { trip ->
                if (trip) {
                    // 旅行账本：确保"公共消费"（全员 AA）成员存在，归属行可选
                    container.memberRepository.ensurePublicMember(currentBookId.value)
                } else {
                    selectedMemberId.value = null
                    selectedPayerId.value = null
                }
            }
        }
    }

    init {
        // 类型切换 / 分类变化时，保证选中项有效；默认选中第一个（少一次点击）
        viewModelScope.launch {
            combine(categories, selectedCategoryId) { list, selected -> list to selected }
                .collect { (list, selected) ->
                    if (editingRestore.value) {
                        // 编辑回填中：等分类列表加载出该分类后再解除保护，避免被误重置
                        when {
                            selected != null && list.any { it.id == selected } ->
                                editingRestore.value = false
                            // 列表已加载完但确实没有该分类（已被删除）→ 放弃保护并选第一个
                            list.isNotEmpty() && selected != null -> {
                                selectedCategoryId.value = list.firstOrNull()?.id
                                editingRestore.value = false
                            }
                        }
                    } else if (selected == null || list.none { it.id == selected }) {
                        selectedCategoryId.value = list.firstOrNull()?.id
                    }
                }
        }
    }

    /** 次级输入聚合，避免超过 combine 的参数限制 */
    private data class TextInputs(
        val location: String?,
        val note: String,
        val images: List<String>,
        val saving: Boolean,
    )

    private val textInputs = combine(location, note, images, saving) { l, n, i, s ->
        TextInputs(l, n, i, s)
    }

    private data class KeyboardInputs(
        val keyboardOpen: Boolean,
        val editTxId: Long?,
        val extra: TextInputs,
    )

    private val keyboardInputs = combine(
        keyboardOpen, editTxId, textInputs,
    ) { open, editId, extra -> KeyboardInputs(open, editId, extra) }

    private data class MemberInputs(
        val isTripBook: Boolean,
        val members: List<MemberEntity>,
        val selectedMemberId: Long?,
        val selectedPayerId: Long?,
        val billTime: Long?,
    )

    private val memberInputs = combine(
        isTripBook, members, selectedMemberId, selectedPayerId, billTime,
    ) { trip, m, owner, payer, time ->
        MemberInputs(trip, m, owner, payer, time)
    }

    val uiState: StateFlow<RecordUiState> = combine(
        amountText, type, categories, selectedCategoryId, places, keyboardInputs, memberInputs,
    ) { values ->
        val kb = values[5] as KeyboardInputs
        val mi = values[6] as MemberInputs
        RecordUiState(
            amountText = values[0] as String,
            type = values[1] as TransactionType,
            categories = values[2] as List<CategoryEntity>,
            selectedCategoryId = values[3] as Long?,
            places = values[4] as List<PlaceEntity>,
            location = kb.extra.location,
            note = kb.extra.note,
            images = kb.extra.images,
            saving = kb.extra.saving,
            keyboardOpen = kb.keyboardOpen,
            editTxId = kb.editTxId,
            isTripBook = mi.isTripBook,
            members = mi.members,
            selectedMemberId = mi.selectedMemberId,
            selectedPayerId = mi.selectedPayerId,
            billTime = mi.billTime,
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), RecordUiState())

    // ---------- 键盘浮层 ----------

    /**
     * 最近一次打开键盘的时刻（uptimeMillis）。
     * 主界面手势与金额卡 clickable 的事件分发顺序是「子先父后」：
     * 点金额卡时 openKeyboard 先于父层手势执行，若手势直接 closeKeyboard 会把键盘立刻关掉
     * （表现为"点击不弹出"）。用时间窗口识别"本次点按就是打开键盘的那次"，跳过收起。
     */
    private var lastOpenKeyboardAt = 0L

    fun openKeyboard() {
        lastOpenKeyboardAt = android.os.SystemClock.uptimeMillis()
        keyboardOpen.value = true
    }

    fun closeKeyboard() {
        keyboardOpen.value = false
    }

    /** 主界面（键盘外）点按收起：若本次点按刚触发了打开键盘，则忽略 */
    fun onTapMainArea() {
        if (android.os.SystemClock.uptimeMillis() - lastOpenKeyboardAt < 120) return
        keyboardOpen.value = false
    }

    // ---------- 数字键盘 ----------

    /** key ∈ ["0".."9", ".", "del"] */
    fun onAmountKey(key: String) {
        val current = amountText.value
        val next = when (key) {
            "del" -> if (current.isNotEmpty()) current.dropLast(1) else current
            "." -> if (current.contains('.')) current else {
                if (current.isEmpty()) "0." else "$current."
            }
            else -> {
                // 整数最多 8 位，小数最多 2 位
                val candidate = if (current == "0") key else current + key
                val dot = candidate.indexOf('.')
                val intPart = if (dot >= 0) candidate.substring(0, dot) else candidate
                val fracPart = if (dot >= 0) candidate.length - dot - 1 else 0
                if (intPart.length > 8 || fracPart > 2) current else candidate
            }
        }
        amountText.value = next
    }

    fun clearAmount() {
        amountText.value = ""
    }

    // ---------- 类型 / 分类 / 地点 / 备注 ----------

    fun selectType(t: TransactionType) {
        if (type.value != t) type.value = t
        // 键盘为弹出式：操作非金额区时一律自动收起，避免遮挡
        keyboardOpen.value = false
    }

    fun selectCategory(id: Long) {
        selectedCategoryId.value = id
        // 选完分类立即收起，露出完整分类区
        keyboardOpen.value = false
    }

    fun setLocation(place: String) {
        location.value = place.trim().takeIf { it.isNotEmpty() }
        keyboardOpen.value = false
    }

    fun clearLocation() {
        location.value = null
        keyboardOpen.value = false
    }

    fun setNote(text: String) {
        note.value = text
    }

    // ---------- 成员归属（旅行账本） ----------

    /** 选择归属成员（谁消费）；memberId=null 表示本人 */
    fun selectMember(memberId: Long?) {
        selectedMemberId.value = memberId
    }

    /** 选择付款成员（谁垫付）；memberId=null 表示本人 */
    fun selectPayer(memberId: Long?) {
        selectedPayerId.value = memberId
    }

    /** 选择账单时间（毫秒） */
    fun setBillTime(millis: Long) {
        billTime.value = millis
    }

    /** 恢复默认：使用当前时间 */
    fun clearBillTime() {
        billTime.value = null
    }

    /** 记账页快捷添加成员（默认取色板轮转色） */
    fun addMemberQuick(name: String, onError: (Int) -> Unit) {
        val trimmed = name.trim()
        if (trimmed.isEmpty()) {
            onError(com.lightledger.app.R.string.record_err_member)
            return
        }
        val bookId = currentBookId.value
        if (bookId <= 0) {
            onError(com.lightledger.app.R.string.record_err_book)
            return
        }
        viewModelScope.launch {
            val color = com.lightledger.app.data.repository.SeedData.memberColors[
                members.value.size % com.lightledger.app.data.repository.SeedData.memberColors.size
            ]
            val id = container.memberRepository.add(bookId, trimmed, color)
            selectedMemberId.value = id
        }
    }

    // ---------- 图片 ----------

    fun addImage(path: String) {
        if (path.isBlank()) return
        images.value = images.value + path
        keyboardOpen.value = false
    }

    fun removeImage(path: String) {
        images.value = images.value - path
        com.lightledger.app.util.ImageStore.deleteFile(path)
    }

    // ---------- 编辑模式 ----------

    /** 进入编辑模式：加载账单并回填全部输入 */
    fun loadForEdit(txId: Long) {
        if (editTxId.value == txId) return
        viewModelScope.launch {
            val tx = container.transactionRepository.getById(txId) ?: return@launch
            editingBase = tx
            editTxId.value = tx.id
            editingRestore.value = true
            type.value = TransactionType.from(tx.type)
            selectedCategoryId.value = tx.categoryId
            location.value = tx.location
            note.value = tx.note.orEmpty()
            images.value = tx.images
            selectedMemberId.value = tx.memberId.takeIf { id ->
                // 原归属成员可能已被删除，仅回填仍存在的成员
                id != null && container.memberRepository.getById(id) != null
            }
            selectedPayerId.value = tx.payerMemberId.takeIf { id ->
                id != null && container.memberRepository.getById(id) != null
            }
            billTime.value = tx.createdAt
            // 无千分位格式回填，保证继续按键追加时长度校验正常
            amountText.value = MoneyFormat.fenToPlain(tx.amount)
        }
    }

    // ---------- 保存 ----------

    fun save(onSuccess: () -> Unit, onError: (Int) -> Unit) {
        val fen = MoneyFormat.yuanToFen(amountText.value)
        val catId = selectedCategoryId.value
        if (fen == null) {
            onError(com.lightledger.app.R.string.record_err_amount)
            return
        }
        if (catId == null) {
            onError(com.lightledger.app.R.string.record_err_category)
            return
        }
        saving.value = true
        val existing = editingBase
        if (editTxId.value != null && existing != null) {
            saveEdit(existing, fen, catId, onSuccess, onError)
        } else {
            saveNew(fen, catId, onSuccess, onError)
        }
    }

    private fun saveNew(
        fen: Long,
        catId: Long,
        onSuccess: () -> Unit,
        onError: (Int) -> Unit,
    ) {
        val bookId = currentBookId.value
        if (bookId <= 0) {
            saving.value = false
            onError(com.lightledger.app.R.string.record_err_book)
            return
        }
        viewModelScope.launch {
            runCatching {
                val loc = location.value
                container.transactionRepository.add(
                    bookId = bookId,
                    type = type.value,
                    amountFen = fen,
                    categoryId = catId,
                    location = loc,
                    note = note.value,
                    images = images.value,
                    memberId = selectedMemberId.value,
                    payerMemberId = selectedPayerId.value,
                    createdAt = billTime.value ?: System.currentTimeMillis(),
                )
                loc?.let { container.placeRepository.recordUsage(it) }
            }.onSuccess {
                // 重置输入，保留类型/分类/成员选择，方便连续记录
                amountText.value = ""
                note.value = ""
                location.value = null
                images.value = emptyList()
                billTime.value = null
                keyboardOpen.value = false
                saving.value = false
                onSuccess()
            }.onFailure {
                saving.value = false
                onError(com.lightledger.app.R.string.record_err_save)
            }
        }
    }

    private fun saveEdit(
        base: com.lightledger.app.data.db.entity.TransactionEntity,
        fen: Long,
        catId: Long,
        onSuccess: () -> Unit,
        onError: (Int) -> Unit,
    ) {
        viewModelScope.launch {
            runCatching {
                val loc = location.value
                container.transactionRepository.update(
                    base.copy(
                        type = type.value.value,
                        amount = fen,
                        categoryId = catId,
                        location = loc?.takeIf { it.isNotBlank() },
                        note = note.value.trim().takeIf { it.isNotEmpty() },
                        images = images.value,
                        memberId = selectedMemberId.value,
                        payerMemberId = selectedPayerId.value,
                        createdAt = billTime.value ?: base.createdAt,
                        updatedAt = System.currentTimeMillis(),
                    )
                )
                // 清理被移除的图片文件
                val kept = images.value.toSet()
                base.images.filter { it !in kept }.forEach {
                    com.lightledger.app.util.ImageStore.deleteFile(it)
                }
                loc?.takeIf { it.isNotBlank() }?.let { container.placeRepository.recordUsage(it) }
            }.onSuccess {
                saving.value = false
                keyboardOpen.value = false
                onSuccess()
            }.onFailure {
                saving.value = false
                onError(com.lightledger.app.R.string.record_err_save)
            }
        }
    }

    // ---------- 分类管理 ----------

    fun openCategoryEditor(category: CategoryEntity?, isNew: Boolean) {
        editorTarget.value = category
        editorIsNew.value = isNew
        editorOpen.value = true
        keyboardOpen.value = false
    }

    fun closeCategoryEditor() {
        editorOpen.value = false
    }

    /** 全量图标选择后，把常驻分类替换为所选图标：图标与名称一起替换（颜色不变，历史账单归属不变） */
    fun replaceCategoryIcon(categoryId: Long, iconKey: String, newName: String) {
        viewModelScope.launch {
            val target = container.categoryRepository.getById(categoryId) ?: return@launch
            val name = newName.trim()
            container.categoryRepository.update(
                target.copy(icon = iconKey, name = name.ifEmpty { target.name })
            )
        }
    }

    fun saveCategory(name: String, icon: String, color: Int) {
        val trimmed = name.trim()
        if (trimmed.isEmpty()) return
        val target = editorTarget.value
        viewModelScope.launch {
            if (target == null) {
                container.categoryRepository.add(trimmed, icon, color, type.value)
            } else {
                container.categoryRepository.update(
                    target.copy(name = trimmed, icon = icon, color = color)
                )
            }
            editorOpen.value = false
        }
    }

    fun deleteSelectedCategory() {
        val target = editorTarget.value ?: return
        if (target.isDefault) return // 预置分类允许改名换色，但保留在列表中不提供删除入口
        viewModelScope.launch {
            container.categoryRepository.delete(target.id)
            editorOpen.value = false
        }
    }
}
