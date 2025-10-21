package fr.lkn.ganbare.core.db

import androidx.room.TypeConverter
import java.time.Instant

class InstantConverters {
    @TypeConverter fun fromEpoch(value: Long?): Instant? = value?.let { Instant.ofEpochMilli(it) }
    @TypeConverter fun toEpoch(instant: Instant?): Long? = instant?.toEpochMilli()
}
