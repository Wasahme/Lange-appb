package com.bitchat.app.data.database

import androidx.room.TypeConverter
import com.bitchat.app.data.entities.*
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

class Converters {

    @TypeConverter
    fun fromStringList(value: List<String>): String {
        return Json.encodeToString(value)
    }

    @TypeConverter
    fun toStringList(value: String): List<String> {
        return Json.decodeFromString(value)
    }

    @TypeConverter
    fun fromMessageContent(value: MessageContent): String {
        return Json.encodeToString(value)
    }

    @TypeConverter
    fun toMessageContent(value: String): MessageContent {
        return Json.decodeFromString(value)
    }

    @TypeConverter
    fun fromUserLocation(value: UserLocation?): String? {
        return value?.let { Json.encodeToString(it) }
    }

    @TypeConverter
    fun toUserLocation(value: String?): UserLocation? {
        return value?.let { Json.decodeFromString(it) }
    }

    @TypeConverter
    fun fromPrivacySettings(value: PrivacySettings): String {
        return Json.encodeToString(value)
    }

    @TypeConverter
    fun toPrivacySettings(value: String): PrivacySettings {
        return Json.decodeFromString(value)
    }

    @TypeConverter
    fun fromDeviceCapabilities(value: DeviceCapabilities): String {
        return Json.encodeToString(value)
    }

    @TypeConverter
    fun toDeviceCapabilities(value: String): DeviceCapabilities {
        return Json.decodeFromString(value)
    }

    @TypeConverter
    fun fromDeviceLocation(value: DeviceLocation?): String? {
        return value?.let { Json.encodeToString(it) }
    }

    @TypeConverter
    fun toDeviceLocation(value: String?): DeviceLocation? {
        return value?.let { Json.decodeFromString(it) }
    }

    @TypeConverter
    fun fromMessageTypeList(value: List<MessageType>): String {
        return Json.encodeToString(value)
    }

    @TypeConverter
    fun toMessageTypeList(value: String): List<MessageType> {
        return Json.decodeFromString(value)
    }

    @TypeConverter
    fun fromStringMap(value: Map<String, List<String>>): String {
        return Json.encodeToString(value)
    }

    @TypeConverter
    fun toStringMap(value: String): Map<String, List<String>> {
        return Json.decodeFromString(value)
    }
}