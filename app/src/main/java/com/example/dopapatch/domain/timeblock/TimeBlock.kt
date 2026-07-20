package com.example.dopapatch.domain.timeblock

import java.time.LocalTime

/** Part of the day a task falls in, derived from its time (never stored). */
enum class TimeBlock { ANYTIME, MORNING, AFTERNOON, EVENING, NIGHT }

/**
 * Default boundaries from CLAUDE.md — Morning 05–12, Afternoon 12–17, Evening 17–21,
 * Night 21–05; no time ⇒ Anytime. Kept as one constant here; becomes a user setting later.
 */
object TimeBlocks {
    val MORNING_START: LocalTime = LocalTime.of(5, 0)
    val AFTERNOON_START: LocalTime = LocalTime.of(12, 0)
    val EVENING_START: LocalTime = LocalTime.of(17, 0)
    val NIGHT_START: LocalTime = LocalTime.of(21, 0)

    fun of(time: LocalTime?): TimeBlock = when {
        time == null -> TimeBlock.ANYTIME
        time < MORNING_START -> TimeBlock.NIGHT      // 00:00–04:59
        time < AFTERNOON_START -> TimeBlock.MORNING  // 05:00–11:59
        time < EVENING_START -> TimeBlock.AFTERNOON  // 12:00–16:59
        time < NIGHT_START -> TimeBlock.EVENING      // 17:00–20:59
        else -> TimeBlock.NIGHT                       // 21:00–23:59
    }
}
