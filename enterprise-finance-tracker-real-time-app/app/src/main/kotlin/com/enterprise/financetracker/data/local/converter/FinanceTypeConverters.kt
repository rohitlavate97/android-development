package com.enterprise.financetracker.data.local.converter

import androidx.room.TypeConverter
import kotlinx.datetime.Instant

/**
 * Room Type Converters for non-primitive types.
 * (Phase 8 Concept 3)
 */
class FinanceTypeConverters {

    @TypeConverter
    fun fromTimestamp(epochMillis: Long?): Instant? {
        return epochMillis?.let { Instant.fromEpochMilliseconds(it) }
    }

    @TypeConverter
    fun toTimestamp(instant: Instant?): Long? {
        return instant?.toEpochMilliseconds()
    }

    @TypeConverter
    fun fromTagsString(tagsString: String?): List<String> {
        return if (tagsString.isNullOrBlank()) {
            emptyList()
        } else {
            tagsString.split(",")
        }
    }

    @TypeConverter
    fun toTagsString(tags: List<String>?): String {
        return tags?.joinToString(",") ?: ""
    }
}
