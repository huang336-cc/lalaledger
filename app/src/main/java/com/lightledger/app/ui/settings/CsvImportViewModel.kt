package com.lightledger.app.ui.settings

import android.content.Context
import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.lightledger.app.AppContainer
import com.lightledger.app.R
import com.lightledger.app.data.repository.SeedData
import com.lightledger.app.domain.model.TransactionType
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

/** CSV 账单导入：解析 → 预览 → 确认写入当前账本 */
class CsvImportViewModel(
    private val container: AppContainer,
    private val currentBookId: StateFlow<Long>,
) : ViewModel() {

    /** 解析出的一行账单（分类/成员仍为名称，导入时才落库） */
    data class ParsedRow(
        val type: TransactionType,
        val amountFen: Long,
        val categoryName: String,
        val note: String?,
        val location: String?,
        val memberName: String?,
        val payerName: String?,
        val mood: String?,
        val createdAt: Long,
    )

    data class Preview(
        val rows: List<ParsedRow>,
        val skipped: Int,
        val newCategoryNames: List<String>,
        val newMemberNames: List<String>,
        val bookName: String,
    ) {
        val expenseCount: Int get() = rows.count { it.type == TransactionType.EXPENSE }
        val incomeCount: Int get() = rows.count { it.type == TransactionType.INCOME }
        val minTime: Long get() = rows.minOfOrNull { it.createdAt } ?: 0L
        val maxTime: Long get() = rows.maxOfOrNull { it.createdAt } ?: 0L
        val totalFen: Long get() = rows.sumOf { it.amountFen }
    }

    sealed interface State {
        data object Idle : State
        data object Parsing : State
        data class Previewing(val preview: Preview) : State
        data object Importing : State
        data class Done(val count: Int) : State
        data class Error(val messageRes: Int) : State
    }

    private val _state = MutableStateFlow<State>(State.Idle)
    val state: StateFlow<State> = _state

    /** 解析选中的 CSV 文件，得到可导入预览 */
    fun parse(context: Context, uri: Uri, defaultCategoryName: String) {
        viewModelScope.launch {
            _state.value = State.Parsing
            val result = withContext(Dispatchers.IO) { parseInternal(context, uri, defaultCategoryName) }
            _state.value = result
        }
    }

    /** 确认导入：按需创建缺失分类/成员，再逐条写入账单 */
    fun confirmImport() {
        val preview = (_state.value as? State.Previewing)?.preview ?: return
        val bookId = currentBookId.value
        if (bookId <= 0) {
            _state.value = State.Error(R.string.import_csv_no_book)
            return
        }
        viewModelScope.launch {
            _state.value = State.Importing
            val count = withContext(Dispatchers.IO) { writeInternal(bookId, preview) }
            _state.value = State.Done(count)
        }
    }

    fun reset() { _state.value = State.Idle }

    // ---------- 解析 ----------

    @Suppress("SameParameterValue")
    private suspend fun parseInternal(context: Context, uri: Uri, defaultCategoryName: String): State {
        val text = runCatching {
            context.contentResolver.openInputStream(uri)?.use { it.readBytes() }
                ?.toString(Charsets.UTF_8)?.removePrefix("\uFEFF")
        }.getOrNull()
        if (text.isNullOrBlank()) return State.Error(R.string.import_csv_failed)

        val table = parseCsv(text)
        if (table.isEmpty()) return State.Error(R.string.import_csv_empty)

        // 表头识别：命中任一已知列名则视为表头行
        val header = table.first().map { it.trim().lowercase(Locale.ROOT) }
        val hasHeader = header.any { h -> ALIASES.values.flatten().any { alias -> alias in h } }
        val colIndex: (Field) -> Int = { field ->
            if (hasHeader) header.indexOfFirst { h -> ALIASES[field]!!.any { alias -> alias in h } } else -1
        }
        val idx = mapOf(
            Field.TIME to colIndex(Field.TIME),
            Field.TYPE to colIndex(Field.TYPE),
            Field.AMOUNT to colIndex(Field.AMOUNT),
            Field.CATEGORY to colIndex(Field.CATEGORY),
            Field.NOTE to colIndex(Field.NOTE),
            Field.LOCATION to colIndex(Field.LOCATION),
            Field.MEMBER to colIndex(Field.MEMBER),
            Field.PAYER to colIndex(Field.PAYER),
            Field.MOOD to colIndex(Field.MOOD),
        )

        val rows = mutableListOf<ParsedRow>()
        var skipped = 0
        val dataRows = if (hasHeader) table.drop(1) else table
        dataRows.forEach { cols ->
            fun get(field: Field): String {
                val i = idx[field] ?: -1
                // 表头缺失时回退到默认列序（与 CsvExporter.COLUMNS 一致）
                val fallback = DEFAULT_ORDER.indexOf(field)
                val pos = if (i >= 0) i else fallback
                return cols.getOrNull(pos)?.trim().orEmpty()
            }
            val time = parseTime(get(Field.TIME)) ?: run { skipped++; return@forEach }
            val amountFen = parseAmount(get(Field.AMOUNT)) ?: run { skipped++; return@forEach }
            // 类型列缺失或无法识别时按支出处理
            val type = parseType(get(Field.TYPE)) ?: TransactionType.EXPENSE
            val categoryName = get(Field.CATEGORY).ifBlank { defaultCategoryName }
            rows += ParsedRow(
                type = type,
                amountFen = amountFen,
                categoryName = categoryName,
                note = get(Field.NOTE).ifBlank { null },
                location = get(Field.LOCATION).ifBlank { null },
                memberName = get(Field.MEMBER).ifBlank { null },
                payerName = get(Field.PAYER).ifBlank { null },
                mood = get(Field.MOOD).ifBlank { null },
                createdAt = time,
            )
        }
        if (rows.isEmpty()) return State.Error(R.string.import_csv_empty)

        // 与库中已有分类/成员比对，得到"将新建"的名单
        val existingCats = container.categoryRepository.getAllMap().values
        val existingCatKeys = existingCats.map { it.type to it.name }.toSet()
        val newCats = rows.map { it.type.value to it.categoryName }
            .distinct()
            .filter { it !in existingCatKeys }
            .map { it.second }
            .distinct()

        val bookId = currentBookId.value
        val existingMembers = if (bookId > 0) container.memberRepository.getByBook(bookId) else emptyList()
        val existingMemberNames = existingMembers.map { it.name }.toSet()
        val newMembers = rows.flatMap { listOfNotNull(it.memberName, it.payerName) }
            .distinct()
            .filter { it !in existingMemberNames }

        val bookName = if (bookId > 0) {
            runCatching { container.bookRepository.getById(bookId)?.name }.getOrNull()
        } else null

        return State.Previewing(
            Preview(
                rows = rows.sortedByDescending { it.createdAt },
                skipped = skipped,
                newCategoryNames = newCats,
                newMemberNames = newMembers,
                bookName = bookName.orEmpty(),
            )
        )
    }

    // ---------- 写入 ----------

    private suspend fun writeInternal(bookId: Long, preview: Preview): Int {
        // 分类：按 type+name 匹配，缺失则新建（同批内缓存，避免重名重复建）
        val catCache = container.categoryRepository.getAllMap().values
            .associateBy { it.type to it.name }
            .toMutableMap()
        suspend fun categoryId(type: TransactionType, name: String): Long {
            val key = type.value to name
            catCache[key]?.let { return it.id }
            val color = if (type == TransactionType.EXPENSE) NEW_EXPENSE_COLOR else NEW_INCOME_COLOR
            val id = container.categoryRepository.add(name, NEW_ICON, color, type)
            catCache[key] = com.lightledger.app.data.db.entity.CategoryEntity(
                id = id, name = name, icon = NEW_ICON, color = color, type = type.value,
            )
            return id
        }

        // 成员：按 name 匹配，缺失则新建
        val memberCache = container.memberRepository.getByBook(bookId)
            .associateBy { it.name }.toMutableMap()
        suspend fun memberId(name: String?): Long? {
            if (name.isNullOrBlank()) return null
            memberCache[name]?.let { return it.id }
            val color = SeedData.memberColors[memberCache.size % SeedData.memberColors.size]
            val id = container.memberRepository.add(bookId, name, color)
            memberCache[name] = com.lightledger.app.data.db.entity.MemberEntity(
                id = id, bookId = bookId, name = name, color = color,
            )
            return id
        }

        var count = 0
        preview.rows.forEach { row ->
            container.transactionRepository.add(
                bookId = bookId,
                type = row.type,
                amountFen = row.amountFen,
                categoryId = categoryId(row.type, row.categoryName),
                location = row.location,
                note = row.note,
                images = emptyList(),
                memberId = memberId(row.memberName),
                payerMemberId = memberId(row.payerName),
                createdAt = row.createdAt,
                mood = row.mood,
            )
            count++
        }
        return count
    }

    // ---------- CSV / 字段解析 ----------

    private enum class Field { TIME, TYPE, AMOUNT, CATEGORY, NOTE, LOCATION, MEMBER, PAYER, MOOD }

    private val DEFAULT_ORDER = listOf(
        Field.TIME, Field.TYPE, Field.AMOUNT, Field.CATEGORY,
        Field.NOTE, Field.LOCATION, Field.MEMBER, Field.PAYER, Field.MOOD,
    )

    /** 表头别名（中英兼容，含常见记账 App 列名） */
    private val ALIASES = mapOf(
        Field.TIME to listOf("时间", "日期", "time", "date"),
        Field.TYPE to listOf("类型", "收支", "type"),
        Field.AMOUNT to listOf("金额", "amount", "money"),
        Field.CATEGORY to listOf("分类", "类别", "category"),
        Field.NOTE to listOf("备注", "说明", "note", "remark"),
        Field.LOCATION to listOf("位置", "地点", "location", "place"),
        Field.MEMBER to listOf("归属", "成员", "member", "owner"),
        Field.PAYER to listOf("付款", "垫付", "支付", "payer", "paid"),
        Field.MOOD to listOf("心情", "mood"),
    )

    /** 支持引号与换行的 CSV 行解析 */
    private fun parseCsv(text: String): List<List<String>> {
        val rows = mutableListOf<List<String>>()
        var row = mutableListOf<String>()
        var field = StringBuilder()
        var inQuotes = false
        var i = 0
        while (i < text.length) {
            val c = text[i]
            when {
                inQuotes -> when (c) {
                    '"' -> if (i + 1 < text.length && text[i + 1] == '"') {
                        field.append('"'); i++
                    } else inQuotes = false
                    else -> field.append(c)
                }
                c == '"' -> inQuotes = true
                c == ',' -> { row.add(field.toString()); field = StringBuilder() }
                c == '\n' -> { row.add(field.toString()); rows.add(row); row = mutableListOf(); field = StringBuilder() }
                c == '\r' -> Unit
                else -> field.append(c)
            }
            i++
        }
        row.add(field.toString())
        rows.add(row)
        return rows.filter { cols -> cols.any { it.isNotBlank() } }
    }

    private val timePatterns = listOf(
        "yyyy-MM-dd HH:mm:ss", "yyyy-MM-dd HH:mm", "yyyy-MM-dd",
        "yyyy/M/d HH:mm:ss", "yyyy/M/d HH:mm", "yyyy/M/d",
        "yyyy.M.d HH:mm", "yyyy.M.d",
        "yyyy年M月d日 HH:mm", "yyyy年M月d日",
    )

    private fun parseTime(raw: String): Long? {
        val text = raw.trim()
        if (text.isEmpty()) return null
        val zone = ZoneId.systemDefault()
        timePatterns.forEach { pattern ->
            val fmt = DateTimeFormatter.ofPattern(pattern)
            runCatching { LocalDateTime.parse(text, fmt) }.getOrNull()?.let {
                return it.atZone(zone).toInstant().toEpochMilli()
            }
            runCatching { LocalDate.parse(text, fmt) }.getOrNull()?.let {
                return it.atStartOfDay(zone).toInstant().toEpochMilli()
            }
        }
        // 纯数字时间戳（毫秒 / 秒）
        text.toLongOrNull()?.let {
            return if (it > 100_000_000_000L) it else it * 1000
        }
        return null
    }

    private fun parseAmount(raw: String): Long? {
        var text = raw.trim().replace(",", "").replace("¥", "").replace("￥", "").trim()
        if (text.isEmpty()) return null
        text = text.trimStart('+', '-')
        val value = text.toDoubleOrNull() ?: return null
        val fen = kotlin.math.abs(Math.round(value * 100))
        if (fen <= 0L) return null
        return fen
    }

    private fun parseType(raw: String): TransactionType? = when (raw.trim().lowercase(Locale.ROOT)) {
        "支出", "expense", "expenses", "0", "-", "out" -> TransactionType.EXPENSE
        "收入", "income", "1", "+", "in" -> TransactionType.INCOME
        else -> null
    }

    companion object {
        private const val NEW_ICON = "star"
        private const val NEW_EXPENSE_COLOR = 0xFF9BA8C9.toInt()
        private const val NEW_INCOME_COLOR = 0xFFB59BD1.toInt()
    }
}
