package com.example.dopapatch.data.local

import androidx.room.TypeConverter
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime

/** java.time <-> primitives for Room (needs core library desugaring on minSdk 24). */
class Converters {
    @TypeConverter fun instantToLong(v: Instant?): Long? = v?.toEpochMilli()
    @TypeConverter fun longToInstant(v: Long?): Instant? = v?.let(Instant::ofEpochMilli)

    @TypeConverter fun dateToLong(v: LocalDate?): Long? = v?.toEpochDay()
    @TypeConverter fun longToDate(v: Long?): LocalDate? = v?.let(LocalDate::ofEpochDay)

    @TypeConverter fun timeToInt(v: LocalTime?): Int? = v?.toSecondOfDay()
    @TypeConverter fun intToTime(v: Int?): LocalTime? = v?.let { LocalTime.ofSecondOfDay(it.toLong()) }
}
