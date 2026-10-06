package com.lightledger.app.data.db

import androidx.room.TypeConverter
import org.json.JSONArray

/**
 * Room 类型转换器：列表 <-> JSON 字符串。
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

    /** v9：账单归属成员 id 列表（多选均摊） */
    @TypeConverter
    fun fromLongList(value: List<Long>): String {
        val array = JSONArray()
        value.forEach { array.put(it) }
        return array.toString()
    }

    @TypeConverter
    fun toLongList(value: String): List<Long> =
        runCatching {
            val array = JSONArray(value)
            buildList {
                for (i in 0 until array.length()) add(array.getLong(i))
            }
        }.getOrDefault(emptyList())
}
