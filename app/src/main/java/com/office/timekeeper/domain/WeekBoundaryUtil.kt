package com.office.timekeeper.domain

import java.time.DayOfWeek
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId
import java.time.ZonedDateTime
import java.time.temporal.TemporalAdjusters

/**
 * Calculates week start/end boundaries using Sunday 12:00 PM IST as the week edge.
 *
 * The attendance week runs Monday–Friday. The boundary between weeks is
 * Sunday at noon IST — before noon you're still in the previous week,
 * at/after noon you're in the next week.
 */
object WeekBoundaryUtil {

    /** Asia/Kolkata timezone used for all week boundary calculations. */
    val IST: ZoneId = ZoneId.of("Asia/Kolkata")

    /** Hour (24h) at which the weekly reset occurs on Sunday. */
    const val RESET_HOUR = 12

    private val WEEK_RESET_DAY = DayOfWeek.SUNDAY

    /**
     * Returns the Monday that starts the current attendance week.
     *
     * - Sunday before noon IST → previous week's Monday
     * - Sunday at/after noon IST → following Monday (tomorrow)
     * - Monday through Saturday → Monday of the current ISO week
     */
    fun getCurrentWeekStart(): LocalDate {
        val now = ZonedDateTime.now(IST)
        return getWeekStartForInstant(now)
    }

    /**
     * Returns the Friday that ends the current attendance week.
     * Always 4 days after the week-start Monday.
     */
    fun getCurrentWeekEnd(): LocalDate {
        return getCurrentWeekStart().plusDays(4)
    }

    /**
     * Returns the Monday that starts the attendance week containing [date].
     *
     * This is a date-only helper — it uses ISO week semantics (Monday = start).
     * For Sunday dates it returns the following Monday (next-week semantics),
     * matching the "Sunday at/after noon → new week" rule.
     */
    fun getWeekStartForDate(date: LocalDate): LocalDate {
        return if (date.dayOfWeek == DayOfWeek.SUNDAY) {
            // Sunday belongs to the next week (after-noon boundary default)
            date.plusDays(1)
        } else {
            date.with(DayOfWeek.MONDAY)
        }
    }

    /**
     * Returns the Friday that ends the attendance week containing [date].
     */
    fun getWeekEndForDate(date: LocalDate): LocalDate {
        return getWeekStartForDate(date).plusDays(4)
    }

    /**
     * Returns `true` if [date] falls within the current attendance week
     * (Monday through Friday inclusive).
     */
    fun isWithinCurrentWeek(date: LocalDate): Boolean {
        val start = getCurrentWeekStart()
        val end = getCurrentWeekEnd()
        return !date.isBefore(start) && !date.isAfter(end)
    }

    /**
     * Returns the next Sunday 12:00 PM IST as an [Instant].
     *
     * If the current time is already past Sunday noon this week, returns
     * next Sunday's noon.
     */
    fun getNextResetInstant(): Instant {
        val now = ZonedDateTime.now(IST)
        val today = now.toLocalDate()
        val currentDow = today.dayOfWeek

        val nextSunday = if (currentDow == DayOfWeek.SUNDAY) {
            // If it's Sunday but we haven't passed noon yet, reset is today
            if (now.toLocalTime() < LocalTime.of(RESET_HOUR, 0)) {
                today
            } else {
                // Already past noon Sunday — next reset is next Sunday
                today.plusWeeks(1)
            }
        } else {
            // Move forward to the next Sunday
            today.with(TemporalAdjusters.next(DayOfWeek.SUNDAY))
        }

        return nextSunday
            .atTime(LocalTime.of(RESET_HOUR, 0))
            .atZone(IST)
            .toInstant()
    }

    /**
     * Returns all weekday (Monday–Friday) dates between [start] and [end] inclusive.
     */
    fun getWeekdaysInRange(start: LocalDate, end: LocalDate): List<LocalDate> {
        if (start.isAfter(end)) return emptyList()

        val weekdays = mutableListOf<LocalDate>()
        var current = start
        while (!current.isAfter(end)) {
            if (current.dayOfWeek != DayOfWeek.SATURDAY &&
                current.dayOfWeek != DayOfWeek.SUNDAY
            ) {
                weekdays.add(current)
            }
            current = current.plusDays(1)
        }
        return weekdays
    }

    // ---- internal helpers ----

    /**
     * Core logic: determine the week-start Monday for a given [ZonedDateTime] in IST.
     */
    internal fun getWeekStartForInstant(now: ZonedDateTime): LocalDate {
        val today = now.toLocalDate()
        return when (today.dayOfWeek) {
            DayOfWeek.SUNDAY -> {
                if (now.toLocalTime() < LocalTime.of(RESET_HOUR, 0)) {
                    // Still in the previous week → go back 6 days to that week's Monday
                    today.minusDays(6)
                } else {
                    // New week starts → Monday is tomorrow
                    today.plusDays(1)
                }
            }
            else -> {
                // Monday through Saturday — Monday of the current ISO week
                today.with(DayOfWeek.MONDAY)
            }
        }
    }
}
