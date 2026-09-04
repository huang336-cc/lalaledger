package com.lightledger.app.util

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.location.Geocoder
import android.location.Location
import android.location.LocationManager
import androidx.core.content.ContextCompat
import java.util.Locale

/**
 * "当前地点"工具：
 * - 使用系统 LocationManager 获取最近已知位置（前台单次，不轮询、不上传）
 * - 使用系统 Geocoder 反查地名（系统服务，非云端接口）
 * - 全程失败则返回 null，由 UI 回退为手动输入
 */
object LocationHelper {

    fun hasLocationPermission(context: Context): Boolean =
        ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_FINE_LOCATION) ==
            PackageManager.PERMISSION_GRANTED ||
            ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_COARSE_LOCATION) ==
            PackageManager.PERMISSION_GRANTED

    fun hasCameraPermission(context: Context): Boolean =
        ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) ==
            PackageManager.PERMISSION_GRANTED

    /** 返回"当前地点"描述文本，失败返回 null */
    fun fetchCurrentPlace(context: Context): String? = runCatching {
        if (!hasLocationPermission(context)) return null
        val lm = context.getSystemService(Context.LOCATION_SERVICE) as LocationManager
        val location = listOf(
            LocationManager.NETWORK_PROVIDER,
            LocationManager.GPS_PROVIDER,
            LocationManager.PASSIVE_PROVIDER,
        ).firstNotNullOfOrNull { provider ->
            runCatching { lm.getLastKnownLocation(provider) }.getOrNull()
        } ?: return null

        reverseGeocode(context, location)
    }.getOrNull()

    private fun reverseGeocode(context: Context, location: Location): String? =
        runCatching {
            if (!Geocoder.isPresent()) return@runCatching null
            val geocoder = Geocoder(context, Locale.CHINA)
            @Suppress("DEPRECATION")
            val addresses = geocoder.getFromLocation(location.latitude, location.longitude, 1)
            val a = addresses?.firstOrNull() ?: return@runCatching null
            // 组合出简洁可读的地名：城市 + 区 + 街道/地标
            listOfNotNull(
                a.locality ?: a.subAdminArea,
                a.subLocality ?: a.thoroughfare,
                a.featureName?.takeIf { it !in setOf(a.locality, a.subLocality) },
            ).distinct().joinToString("·").ifBlank { null }
        }.getOrNull()
}
