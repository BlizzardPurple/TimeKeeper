package com.office.timekeeper.widget

/**
 * Snapshot of attendance data rendered by the home screen widget.
 *
 * Instances are produced by [TimeKeeperWidgetStateDefinition] each time the
 * widget refreshes and consumed by [TimeKeeperWidget.Content].
 */
data class WidgetState(
    val isCheckedIn: Boolean = false,
    val todayDurationFormatted: String = "0h 0m",
    val weekDurationFormatted: String = "0h 0m",
    val daysCompleted: Int = 0,
    val targetDays: Int = 3,
    val isWeeklyTargetMet: Boolean = false
) {
    companion object {
        /** Minimum daily milliseconds for a day to count as attended. */
        internal const val MIN_MS_FOR_ATTENDANCE: Long = 4 * 3_600_000L // 4 hours

        /** Milliseconds per hour, used for target calculations. */
        private const val MS_PER_HOUR: Long = 3_600_000L

        /**
         * Formats a duration in milliseconds as "Xh Ym".
         */
        fun formatDuration(ms: Long): String {
            val totalMinutes = ms / 60_000L
            val hours = totalMinutes / 60
            val minutes = totalMinutes % 60
            return "${hours}h ${minutes}m"
        }

        /**
         * Evaluates whether the weekly target is met.
         *
         * @param qualifyingDays count of weekdays with ≥ 4 h attendance
         * @param totalWeekMs total duration across all sessions this week
         * @param targetDays configured target days per week
         * @param targetHoursPerDay configured target hours per day
         */
        fun isTargetMet(
            qualifyingDays: Int,
            totalWeekMs: Long,
            targetDays: Int,
            targetHoursPerDay: Int
        ): Boolean {
            val targetWeeklyMs = targetDays.toLong() * targetHoursPerDay * MS_PER_HOUR
            return qualifyingDays >= targetDays && totalWeekMs >= targetWeeklyMs
        }
    }
}
