package com.office.timekeeper.ui.history

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.office.timekeeper.data.db.ArchivedTimeSession
import com.office.timekeeper.data.db.WeeklySummary
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter

/**
 * History screen displaying archived weekly attendance summaries.
 *
 * Each week is shown as an expandable card revealing daily session details.
 * An export button triggers CSV generation and share.
 *
 * Requirements: 13.1, 13.2, 13.3, 14.1, 14.2
 */
@Composable
fun HistoryScreen(viewModel: HistoryViewModel) {
    val archivedWeeks by viewModel.archivedWeeks.collectAsStateWithLifecycle()

    if (archivedWeeks.isEmpty()) {
        EmptyHistoryState()
    } else {
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            items(archivedWeeks, key = { it.id }) { summary ->
                WeekCard(summary = summary, viewModel = viewModel)
            }
        }
    }
}

/**
 * Empty state shown when no archived weeks exist.
 */
@Composable
private fun EmptyHistoryState() {
    Box(
        modifier = Modifier.fillMaxSize(),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = "No archived weeks yet",
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

/**
 * Expandable card for a single archived week.
 *
 * Shows week date range, total hours, days attended, and target met indicator.
 * Expands on click to show daily session details.
 * Export button triggers CSV share.
 */
@Composable
private fun WeekCard(summary: WeeklySummary, viewModel: HistoryViewModel) {
    var expanded by remember { mutableStateOf(false) }
    val sessionsByDate = remember { mutableStateMapOf<String, List<ArchivedTimeSession>>() }
    val context = LocalContext.current

    // Load sessions when expanded
    LaunchedEffect(expanded) {
        if (expanded && sessionsByDate.isEmpty()) {
            val sessions = viewModel.getSessionsForWeek(summary.id)
            sessionsByDate.clear()
            sessions.groupBy { it.date }.forEach { (date, dateSessions) ->
                sessionsByDate[date] = dateSessions
            }
        }
    }

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { expanded = !expanded }
            .semantics {
                contentDescription = "Week ${summary.weekStartDate} to ${summary.weekEndDate}"
            },
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surface
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            // Header row: date range + expand/collapse + export
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "${summary.weekStartDate} – ${summary.weekEndDate}",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold
                    )
                }

                IconButton(
                    onClick = { viewModel.onExportCsv(context, summary.id) },
                    modifier = Modifier.semantics {
                        contentDescription = "Export CSV for week ${summary.weekStartDate}"
                    }
                ) {
                    Icon(
                        imageVector = Icons.Default.Share,
                        contentDescription = "Export CSV",
                        tint = MaterialTheme.colorScheme.primary
                    )
                }

                Icon(
                    imageVector = if (expanded) {
                        Icons.Default.KeyboardArrowUp
                    } else {
                        Icons.Default.KeyboardArrowDown
                    },
                    contentDescription = if (expanded) "Collapse" else "Expand",
                    tint = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            Spacer(modifier = Modifier.height(8.dp))

            // Summary metrics row
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = formatDuration(summary.totalMs),
                    style = MaterialTheme.typography.bodyLarge,
                    fontWeight = FontWeight.Medium
                )
                Text(
                    text = "${summary.daysAttended} day${if (summary.daysAttended != 1) "s" else ""} attended",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                TargetMetIndicator(targetMet = summary.targetMet)
            }

            // Expandable daily session details
            AnimatedVisibility(
                visible = expanded,
                enter = expandVertically(),
                exit = shrinkVertically()
            ) {
                Column {
                    Spacer(modifier = Modifier.height(12.dp))
                    HorizontalDivider()
                    Spacer(modifier = Modifier.height(8.dp))

                    val sortedDates = sessionsByDate.keys.sorted()
                    if (sortedDates.isEmpty() && expanded) {
                        Text(
                            text = "Loading sessions…",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    for (date in sortedDates) {
                        val sessions = sessionsByDate[date] ?: continue
                        DailySessionGroup(date = date, sessions = sessions)
                        Spacer(modifier = Modifier.height(8.dp))
                    }
                }
            }
        }
    }
}

/**
 * Target met indicator — green check or "Missed" label.
 */
@Composable
private fun TargetMetIndicator(targetMet: Boolean) {
    if (targetMet) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(
                imageVector = Icons.Default.CheckCircle,
                contentDescription = "Target met",
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(18.dp)
            )
            Spacer(modifier = Modifier.width(4.dp))
            Text(
                text = "Target met",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.primary
            )
        }
    } else {
        Text(
            text = "Missed",
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.error
        )
    }
}

/**
 * A group of sessions for a single date within an expanded week card.
 *
 * Req 13.2 — individual sessions grouped by date with check-in, check-out, and duration.
 */
@Composable
private fun DailySessionGroup(date: String, sessions: List<ArchivedTimeSession>) {
    val dailyTotal = sessions.sumOf { it.durationMs }

    Column {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text(
                text = date,
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.Medium
            )
            Text(
                text = formatDuration(dailyTotal),
                style = MaterialTheme.typography.titleSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }

        Spacer(modifier = Modifier.height(4.dp))

        for (session in sessions) {
            SessionRow(session = session)
        }
    }
}

/**
 * A single session row showing check-in time, check-out time, and duration.
 */
@Composable
private fun SessionRow(session: ArchivedTimeSession) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(start = 8.dp, top = 2.dp, bottom = 2.dp),
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text(
            text = "${formatEpochAsTime(session.checkInTime)} – ${formatEpochAsTime(session.checkOutTime)}",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Text(
            text = formatDuration(session.durationMs),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

// --- Formatting Helpers ---

private val IST: ZoneId = ZoneId.of("Asia/Kolkata")
private val TIME_FORMATTER: DateTimeFormatter = DateTimeFormatter.ofPattern("HH:mm").withZone(IST)

/**
 * Formats a duration in milliseconds as "Xh Ym" (e.g., "24h 30m").
 *
 * Shows "0h 0m" for zero or negative durations.
 */
internal fun formatDuration(ms: Long): String {
    if (ms <= 0) return "0h 0m"
    val totalMinutes = ms / 60_000
    val hours = totalMinutes / 60
    val minutes = totalMinutes % 60
    return "${hours}h ${minutes}m"
}

/**
 * Formats an epoch millisecond timestamp as HH:mm in IST.
 */
private fun formatEpochAsTime(epochMs: Long): String {
    return TIME_FORMATTER.format(Instant.ofEpochMilli(epochMs))
}
