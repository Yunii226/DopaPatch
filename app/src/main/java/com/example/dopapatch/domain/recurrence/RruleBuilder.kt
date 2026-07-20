package com.example.dopapatch.domain.recurrence

import java.time.DayOfWeek
import java.time.LocalDate
import java.time.format.DateTimeFormatter

/** The three recurrence shapes the basic builder UI can express. */
enum class RecurrenceType { DAILY, WEEKLY, EVERY_N }

private val RFC_DAY = mapOf(
    DayOfWeek.MONDAY to "MO", DayOfWeek.TUESDAY to "TU", DayOfWeek.WEDNESDAY to "WE",
    DayOfWeek.THURSDAY to "TH", DayOfWeek.FRIDAY to "FR", DayOfWeek.SATURDAY to "SA",
    DayOfWeek.SUNDAY to "SU",
)

/**
 * Emit an RFC-5545 RRULE string from the basic builder's fields. Pure — unit-tested.
 * - DAILY → `FREQ=DAILY`
 * - WEEKLY → `FREQ=WEEKLY;BYDAY=MO,WE` (weekdays omitted ⇒ weekly on dtstart's weekday)
 * - EVERY_N → `FREQ=DAILY;INTERVAL=n`
 * plus `;UNTIL=YYYYMMDD` when [until] is set.
 */
fun buildRrule(
    type: RecurrenceType,
    weekdays: Set<DayOfWeek> = emptySet(),
    interval: Int = 1,
    until: LocalDate? = null,
): String {
    val parts = when (type) {
        RecurrenceType.DAILY -> mutableListOf("FREQ=DAILY")
        RecurrenceType.WEEKLY -> mutableListOf("FREQ=WEEKLY").apply {
            if (weekdays.isNotEmpty()) {
                add("BYDAY=" + weekdays.sortedBy { it.value }.joinToString(",") { RFC_DAY.getValue(it) })
            }
        }
        RecurrenceType.EVERY_N -> mutableListOf("FREQ=DAILY", "INTERVAL=${interval.coerceAtLeast(1)}")
    }
    until?.let { parts.add("UNTIL=" + it.format(DateTimeFormatter.BASIC_ISO_DATE)) }
    return parts.joinToString(";")
}

/** Builder fields recovered from an RRULE (only needs to round-trip [buildRrule]'s own output). */
data class RruleParts(
    val type: RecurrenceType,
    val weekdays: Set<DayOfWeek>,
    val interval: Int,
    val until: LocalDate?,
)

private val DAY_RFC = RFC_DAY.entries.associate { (k, v) -> v to k }

fun parseRrule(rrule: String): RruleParts {
    val map = rrule.split(";").mapNotNull {
        val kv = it.split("=", limit = 2); if (kv.size == 2) kv[0].uppercase() to kv[1] else null
    }.toMap()
    val type = when {
        map["FREQ"] == "WEEKLY" -> RecurrenceType.WEEKLY
        map.containsKey("INTERVAL") -> RecurrenceType.EVERY_N
        else -> RecurrenceType.DAILY
    }
    return RruleParts(
        type = type,
        weekdays = map["BYDAY"]?.split(",")?.mapNotNull { DAY_RFC[it.trim().uppercase()] }?.toSet() ?: emptySet(),
        interval = map["INTERVAL"]?.toIntOrNull() ?: 1,
        until = map["UNTIL"]?.let { runCatching { LocalDate.parse(it, DateTimeFormatter.BASIC_ISO_DATE) }.getOrNull() },
    )
}
