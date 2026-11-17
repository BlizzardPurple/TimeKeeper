package com.office.timekeeper.ui.dashboard

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.office.timekeeper.domain.WeatherService
import com.office.timekeeper.domain.WeekBoundaryUtil
import kotlinx.coroutines.delay
import java.time.DayOfWeek
import java.time.LocalDate

/**
 * Main dashboard screen for TimeKeeper.
 *
 * Displays the current attendance status, live session timer, today's total,
 * weekly progress, qualifying days count, and a check-in/check-out toggle.
 * Shows a weekend message on Saturdays and Sundays instead of the toggle.
 *
 * Requirements: 1.1, 1.3, 2.1, 3.2, 5.3
 */
@Composable
fun DashboardScreen(viewModel: DashboardViewModel) {
    val isCheckedIn by viewModel.isCheckedIn.collectAsStateWithLifecycle()
    val activeSession by viewModel.activeSession.collectAsStateWithLifecycle()
    val todayTotalDuration by viewModel.todayTotalDuration.collectAsStateWithLifecycle()
    val weekTotalDuration by viewModel.weekTotalDuration.collectAsStateWithLifecycle()
    val qualifyingDays by viewModel.qualifyingDays.collectAsStateWithLifecycle()
    val targetDays by viewModel.targetDays.collectAsStateWithLifecycle()
    val isWeeklyTargetMet by viewModel.isWeeklyTargetMet.collectAsStateWithLifecycle()
    val absences by viewModel.absences.collectAsStateWithLifecycle()
    val error by viewModel.error.collectAsStateWithLifecycle()
    val autoCheckInTriggered by viewModel.autoCheckInTriggered.collectAsStateWithLifecycle()
    val weatherSuggestion by viewModel.weatherSuggestion.collectAsStateWithLifecycle()
    val weatherEnabled by viewModel.weatherEnabled.collectAsStateWithLifecycle()

    val snackbarHostState = remember { SnackbarHostState() }

    // Show error via Snackbar when non-null, then clear it
    LaunchedEffect(error) {
        error?.let { message ->
            snackbarHostState.showSnackbar(message)
            viewModel.clearError()
        }
    }

    // Live session timer: ticks every second while checked in
    var currentTimeMs by remember { mutableLongStateOf(System.currentTimeMillis()) }
    LaunchedEffect(isCheckedIn) {
        while (isCheckedIn) {
            currentTimeMs = System.currentTimeMillis()
            delay(1_000L)
        }
    }

    val sessionElapsedMs = if (isCheckedIn && activeSession != null) {
        currentTimeMs - activeSession!!.checkInTime
    } else {
        0L
    }

    val isWeekend = isWeekend()

    // Target weekly ms: adjusted for absences
    val adjustedTargetDays = (targetDays - absences).coerceAtLeast(0)
    val targetWeeklyMs = adjustedTargetDays * DEFAULT_TARGET_HOURS_PER_DAY * MS_PER_HOUR

    Scaffold(
        snackbarHost = { SnackbarHost(hostState = snackbarHostState) }
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // Status & live session timer card
            StatusCard(
                isCheckedIn = isCheckedIn,
                sessionElapsedMs = sessionElapsedMs
            )

            // Auto check-in note
            if (autoCheckInTriggered) {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(12.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "📍 Auto checked in — you were near your office",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onPrimaryContainer
                        )
                        TextButton(onClick = { viewModel.clearAutoCheckInNote() }) {
                            Text("Dismiss")
                        }
                    }
                }
            }

            // Today's total card
            TodayTotalCard(todayTotalDuration = todayTotalDuration)

            // Weekly progress card
            WeeklyProgressCard(
                weekTotalDuration = weekTotalDuration,
                targetWeeklyMs = targetWeeklyMs,
                qualifyingDays = qualifyingDays,
                targetDays = adjustedTargetDays,
                absences = absences,
                isWeeklyTargetMet = isWeeklyTargetMet
            )

            // Weather suggestion card (only when enabled and has data)
            if (weatherEnabled && weatherSuggestion != null) {
                WeatherCard(suggestion = weatherSuggestion!!)
            }

            Spacer(modifier = Modifier.height(8.dp))

            // 12-hour daily cap warning
            val todayWithSession = todayTotalDuration + sessionElapsedMs
            if (todayWithSession > 12 * MS_PER_HOUR) {
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .semantics { contentDescription = "Warning: Today exceeds 12 hours" },
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.errorContainer
                    )
                ) {
                    Text(
                        text = "Today exceeds 12 hours — excess won't count toward your weekly target",
                        modifier = Modifier.padding(12.dp),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onErrorContainer
                    )
                }
            }

            // Action area: Check-in/out button or weekend message
            if (isWeekend) {
                WeekendMessage()
            } else {
                CheckInOutButton(
                    isCheckedIn = isCheckedIn,
                    onCheckIn = viewModel::onCheckIn,
                    onCheckOut = viewModel::onCheckOut
                )
            }
        }
    }
}

/**
 * Card showing the current status (IN/OUT) with a colored dot
 * and the live session timer when checked in.
 */
@Composable
private fun StatusCard(isCheckedIn: Boolean, sessionElapsedMs: Long) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .semantics { contentDescription = if (isCheckedIn) "Status: Checked in" else "Status: Checked out" },
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant
        )
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(20.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    // Colored status dot
                    Box(
                        modifier = Modifier
                            .size(12.dp)
                            .clip(CircleShape)
                            .background(
                                if (isCheckedIn) {
                                    MaterialTheme.colorScheme.primary
                                } else {
                                    MaterialTheme.colorScheme.outline
                                }
                            )
                            .semantics {
                                contentDescription = if (isCheckedIn) "Checked in indicator" else "Checked out indicator"
                            }
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = if (isCheckedIn) "IN" else "OUT",
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold,
                        color = if (isCheckedIn) {
                            MaterialTheme.colorScheme.primary
                        } else {
                            MaterialTheme.colorScheme.onSurfaceVariant
                        }
                    )
                }
            }

            // Live session timer
            Column(horizontalAlignment = Alignment.End) {
                Text(
                    text = "Session",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Text(
                    text = formatDuration(sessionElapsedMs),
                    style = MaterialTheme.typography.headlineSmall,
                    fontWeight = FontWeight.SemiBold,
                    modifier = Modifier.semantics {
                        contentDescription = "Current session duration: ${formatDuration(sessionElapsedMs)}"
                    }
                )
            }
        }
    }
}

/**
 * Card showing today's total accumulated duration.
 */
@Composable
private fun TodayTotalCard(todayTotalDuration: Long) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .semantics { contentDescription = "Today's total: ${formatDuration(todayTotalDuration)}" },
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surface
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(20.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "Today",
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Text(
                text = formatDuration(todayTotalDuration),
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.SemiBold
            )
        }
    }
}

/**
 * Card showing weekly progress: a progress bar, hour totals,
 * qualifying days count, and target met status.
 */
@Composable
private fun WeeklyProgressCard(
    weekTotalDuration: Long,
    targetWeeklyMs: Long,
    qualifyingDays: Int,
    targetDays: Int,
    absences: Int,
    isWeeklyTargetMet: Boolean
) {
    val progress = if (targetWeeklyMs > 0) {
        (weekTotalDuration.toFloat() / targetWeeklyMs).coerceIn(0f, 1f)
    } else {
        0f
    }

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .semantics {
                contentDescription = "Weekly progress: ${formatDuration(weekTotalDuration)} of ${formatDuration(targetWeeklyMs)}, $qualifyingDays of $targetDays days"
            },
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surface
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(20.dp)
        ) {
            Text(
                text = "Weekly Progress",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold
            )

            Spacer(modifier = Modifier.height(12.dp))

            // Progress bar
            LinearProgressIndicator(
                progress = { progress },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(8.dp)
                    .semantics {
                        contentDescription = "Weekly progress ${(progress * 100).toInt()} percent"
                    },
                trackColor = MaterialTheme.colorScheme.surfaceVariant,
            )

            Spacer(modifier = Modifier.height(8.dp))

            // Hours total
            Text(
                text = "${formatDuration(weekTotalDuration)} / ${formatDuration(targetWeeklyMs)}",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            Spacer(modifier = Modifier.height(12.dp))

            // Days + target met row
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = if (absences > 0) "Days: $qualifyingDays / $targetDays ($absences off)" else "Days: $qualifyingDays / $targetDays",
                    style = MaterialTheme.typography.bodyLarge,
                    fontWeight = FontWeight.Medium
                )

                Text(
                    text = if (isWeeklyTargetMet) "✓ Target Met" else "✗ In Progress",
                    style = MaterialTheme.typography.labelLarge,
                    fontWeight = FontWeight.SemiBold,
                    color = if (isWeeklyTargetMet) {
                        MaterialTheme.colorScheme.primary
                    } else {
                        MaterialTheme.colorScheme.onSurfaceVariant
                    }
                )
            }
        }
    }
}

/**
 * Check-in/check-out toggle button. Shown only on weekdays.
 */
@Composable
private fun CheckInOutButton(
    isCheckedIn: Boolean,
    onCheckIn: () -> Unit,
    onCheckOut: () -> Unit
) {
    Button(
        onClick = if (isCheckedIn) onCheckOut else onCheckIn,
        modifier = Modifier
            .fillMaxWidth()
            .height(56.dp)
            .semantics {
                contentDescription = if (isCheckedIn) "Check out" else "Check in"
            },
        colors = ButtonDefaults.buttonColors(
            containerColor = if (isCheckedIn) {
                Color(0xFFE53935)  // Explicit Material Red 600
            } else {
                MaterialTheme.colorScheme.primary
            }
        )
    ) {
        Text(
            text = if (isCheckedIn) "CHECK OUT" else "CHECK IN",
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold
        )
    }
}

/**
 * Message shown on weekends instead of the check-in/out button.
 */
@Composable
private fun WeekendMessage() {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .semantics { contentDescription = "Weekend, check-in unavailable" },
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.tertiaryContainer
        )
    ) {
        Text(
            text = "Weekend — enjoy your day!",
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Medium,
            color = MaterialTheme.colorScheme.onTertiaryContainer,
            modifier = Modifier
                .fillMaxWidth()
                .padding(20.dp)
        )
    }
}

/**
 * Weather suggestion card showing forecast and smart departure advice.
 */
@Composable
private fun WeatherCard(suggestion: WeatherService.WeatherSuggestion) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .semantics { contentDescription = "Weather suggestion" },
        colors = CardDefaults.cardColors(
            containerColor = if (suggestion.hasRainSoon)
                MaterialTheme.colorScheme.secondaryContainer
            else
                MaterialTheme.colorScheme.surfaceVariant
        )
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(
                text = suggestion.currentWeatherDesc,
                style = MaterialTheme.typography.titleSmall,
                color = MaterialTheme.colorScheme.onSecondaryContainer
            )
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = suggestion.suggestion,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSecondaryContainer
            )
        }
    }
}

// --- Helpers ---

private const val DEFAULT_TARGET_HOURS_PER_DAY = 8L
private const val MS_PER_HOUR = 3_600_000L

/**
 * Formats a duration in milliseconds as "Xh Ym" (e.g., "6h 30m").
 * Shows "0h 0m" for zero or negative durations.
 */
private fun formatDuration(ms: Long): String {
    if (ms <= 0) return "0h 0m"
    val totalMinutes = ms / 60_000
    val hours = totalMinutes / 60
    val minutes = totalMinutes % 60
    return "${hours}h ${minutes}m"
}

/**
 * Returns true if today is Saturday or Sunday in the IST timezone.
 */
private fun isWeekend(): Boolean {
    val today = LocalDate.now(WeekBoundaryUtil.IST)
    return today.dayOfWeek == DayOfWeek.SATURDAY || today.dayOfWeek == DayOfWeek.SUNDAY
}
