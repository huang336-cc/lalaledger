package com.lightledger.app.util

import android.content.Context
import android.net.Uri
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * 图片本地存储：
 * - 拍照 / 相册选择的图片统一复制压缩到应用私有目录 files/Receipts/
 * - 数据库仅存绝对路径，卸载 App 时随目录一起清理
 */
object ImageStore {

    private const val DIR_NAME = "Receipts"
    private const val MAX_DIMENSION = 1600   // 最长边像素
    private const val JPEG_QUALITY = 85

    fun receiptsDir(context: Context): File =
        File(context.filesDir, DIR_NAME).apply { if (!exists()) mkdirs() }

    /** 创建拍照用的临时文件（配合 FileProvider / TakePicture） */
    fun newCameraFile(context: Context): File {
        val stamp = SimpleDateFormat("yyyyMMdd_HHmmss_SSS", Locale.US).format(Date())
        return File(receiptsDir(context), "IMG_$stamp.jpg")
    }

    /**
     * 将 content uri 指向的图片复制压缩进私有目录，返回绝对路径；失败返回 null。
     */
    fun importFromUri(context: Context, uri: Uri): String? = runCatching {
        val bitmap = context.contentResolver.openInputStream(uri)?.use { input ->
            // 先解码边界，按比例采样，避免 OOM
            val bounds = android.graphics.BitmapFactory.Options().apply { inJustDecodeBounds = true }
            context.contentResolver.openInputStream(uri)?.use { boundsStream ->
                android.graphics.BitmapFactory.decodeStream(boundsStream, null, bounds)
            }
            val sample = calculateInSampleSize(bounds.outWidth, bounds.outHeight)
            val opts = android.graphics.BitmapFactory.Options().apply { inSampleSize = sample }
            android.graphics.BitmapFactory.decodeStream(input, null, opts)
        } ?: return null

        // 等比缩放到最大边 1600px
        val scaled = scaleDown(bitmap)
        val target = newCameraFile(context)
        target.outputStream().use { out ->
            scaled.compress(android.graphics.Bitmap.CompressFormat.JPEG, JPEG_QUALITY, out)
        }
        if (scaled !== bitmap) bitmap.recycle()
        scaled.recycle()
        target.absolutePath
    }.getOrNull()

    private fun calculateInSampleSize(width: Int, height: Int): Int {
        var sample = 1
        var maxSide = maxOf(width, height)
        while (maxSide / (sample * 2) >= MAX_DIMENSION) sample *= 2
        return sample
    }

    private fun scaleDown(bitmap: android.graphics.Bitmap): android.graphics.Bitmap {
        val maxSide = maxOf(bitmap.width, bitmap.height)
        if (maxSide <= MAX_DIMENSION) return bitmap
        val ratio = MAX_DIMENSION.toFloat() / maxSide
        val w = (bitmap.width * ratio).toInt().coerceAtLeast(1)
        val h = (bitmap.height * ratio).toInt().coerceAtLeast(1)
        return android.graphics.Bitmap.createScaledBitmap(bitmap, w, h, true)
    }

    /** 删除本地图片文件（账单删除 / 图片移除时调用） */
    fun deleteFile(path: String) {
        runCatching { File(path).takeIf { it.exists() }?.delete() }
    }
}
