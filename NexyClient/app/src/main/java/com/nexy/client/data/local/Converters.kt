package com.nexy.client.data.local

import androidx.room.TypeConverter
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import com.nexy.client.data.models.ReactionCount

class Converters {
    private val gson = Gson()

    @TypeConverter
    fun fromReactionCountList(value: List<ReactionCount>?): String? {
        return value?.let { gson.toJson(it) }
    }

    @TypeConverter
    fun toReactionCountList(value: String?): List<ReactionCount>? {
        return value?.let {
            val type = object : TypeToken<List<ReactionCount>>() {}.type
            gson.fromJson(it, type)
        }
    }

    @TypeConverter
    fun fromIntList(value: List<Int>?): String? {
        return value?.let { gson.toJson(it) }
    }

    @TypeConverter
    fun toIntList(value: String?): List<Int>? {
        return value?.let {
            val type = object : TypeToken<List<Int>>() {}.type
            gson.fromJson<List<Int>>(it, type)
        }
    }
}
