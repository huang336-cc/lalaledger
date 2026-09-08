package com.lightledger.app.util

import android.content.ContentValues
import android.content.Context
import android.net.Uri
import android.os.Environment
import android.provider.MediaStore
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * CSV 账单导出：
 * - UTF-8 + BOM，Excel 打开中文不乱码
 * - MediaStore 保存到 Download/拉拉记账（Android 10+ 免存储权限）
 * - 表头与 [CsvImporter] 共用，保证"导出 → 导入"闭环
 */
object CsvExporter {

    /** 表头（顺序即列顺序） */
    val COLUMNS = listOf("时间", "类型", "金额", "分类", "备注", "位置", "归属成员", "付款成员", "心情")

    /** 导出的一行账单（amountText 为元，无千分位） */
    data class CsvRow(
        val timeText: String,
        val typeText: String,
        val amountText: String,
        val categoryName: String,
        val note: String = "",
        val location: String = "",
        val memberName: String = "",
        val payerName: String = "",
        val mood: String = "",
    ) {
        fun toCsvLine(): String = listOf(
            timeText, typeText, amountText, categoryName, note, location, memberName, payerName, mood,
        ).joinToString(",") { escape(it) }
    }

    /** 含分隔符/引号/换行时用双引号包裹，内部引号翻倍 */
    private fun escape(value: String): String {
        val needQuote = value.any { it == ',' || it == '"' || it == '\n' || it == '\r' }
        val body = value.replace("\"", "\"\"")
        return if (needQuote) "\"$body\"" else body
    }

    /**
     * 写入 CSV 文件并返回 Uri；失败返回 null。
     * @param fileNamePrefix 文件名前缀（如"账单"）
     */
    suspend fun export(
        context: Context,
        rows: List<CsvRow>,
        fileNamePrefix: String = "账单",
    ): Uri? = withContext(Dispatchers.IO) {
        if (rows.isEmpty()) return@withContext null
        val resolver = context.contentResolver
        val name = "拉拉记账_${fileNamePrefix}_${
            SimpleDateFormat("yyyyMMdd_HHmmss", Locale.CHINA).format(Date())
        }.csv"
        val values = ContentValues().apply {
            put(MediaStore.Downloads.DISPLAY_NAME, name)
            put(MediaStore.Downloads.MIME_TYPE, "text/csv")
            put(MediaStore.Downloads.RELATIVE_PATH, Environment.DIRECTORY_DOWNLOADS + "/拉拉记账")
            put(MediaStore.Downloads.IS_PENDING, 1)
        }
        val uri = resolver.insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, values) ?: return@withContext null
        try {
            resolver.openOutputStream(uri)?.use { out ->
                out.write("\uFEFF".toByteArray(Charsets.UTF_8)) // BOM
                out.write((COLUMNS.joinToString(",") + "\n").toByteArray(Charsets.UTF_8))
                rows.forEach { row ->
                    out.write((row.toCsvLine() + "\n").toByteArray(Charsets.UTF_8))
                }
                out.flush()
            } ?: error("openOutputStream null")
            values.clear()
            values.put(MediaStore.Downloads.IS_PENDING, 0)
            resolver.update(uri, values, null, null)
            uri
        } catch (e: Exception) {
            e.printStackTrace()
            runCatching { resolver.delete(uri, null, null) }
            null
        }
    }
}
