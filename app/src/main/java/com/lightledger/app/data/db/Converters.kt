package com.lightledger.app.data.db

import androidx.room.TypeConverter
import org.json.JSONArray

/**
 * Room 类型转换器：图片路径列表 <-> JSON 字符串。
 * 使用平台自带 org.json，无第三方依赖。
 */
class Converters {

    @TypeConverter
    fun fromStringList(value: List<String>): String {
        val array = JSONArray()
        value.forEach { array.put(it) }
        return array.toString()
    }

    @TypeConverter
    fun toStringList(value: String): List<String> =
        runCatching {
            val array = JSONArray(value)
            buildList {
                for (i in 0 until array.length()) add(array.getString(i))
            }
        }.getOrDefault(emptyList())
}
