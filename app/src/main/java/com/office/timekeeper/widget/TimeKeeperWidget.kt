package com.office.timekeeper.widget

import android.content.Context
import android.content.Intent
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.glance.Button
import androidx.glance.GlanceId
import androidx.glance.GlanceModifier
import androidx.glance.GlanceTheme
import androidx.glance.LocalContext
import androidx.glance.action.clickable
import androidx.glance.appwidget.GlanceAppWidget
import androidx.glance.appwidget.action.ActionCallback
import androidx.glance.appwidget.action.actionRunCallback
import androidx.glance.appwidget.action.actionStartActivity
import androidx.glance.appwidget.provideContent
import androidx.glance.background
import androidx.glance.color.ColorProvider
import androidx.glance.currentState
import androidx.glance.layout.Alignment
import androidx.glance.layout.Box
import androidx.glance.layout.Column
import androidx.glance.layout.Row
import androidx.glance.layout.Spacer
import androidx.glance.layout.fillMaxSize
import androidx.glance.layout.fillMaxWidth
import androidx.glance.layout.height
import androidx.glance.layout.padding
import androidx.glance.layout.size
import androidx.glance.layout.width
import androidx.glance.text.FontWeight
import androidx.glance.text.Text
import androidx.glance.text.TextStyle
import com.office.timekeeper.MainActivity
import com.office.timekeeper.data.datastore.SettingsDataStore
import com.office.timekeeper.data.db.AppDatabase
import com.office.timekeeper.data.repository.AttendanceRepository

// Reusable color providers
private val GreenDot = ColorProvider(
    day = Color(0xFF00C9B7),   // Nielsen Teal
    night = Color(0xFF00C9B7)
)
private val GrayDot = ColorProvider(
    day = Color(0xFF73777F),
    night = Color(0xFF73777F)
)

/**
 * 2×2 Glance home screen widget displaying attendance status with
 * a tap-to-toggle check-in/check-out button.
 *
 * Layout:
 * ```
 * ┌─────────────────────────┐
 * │ ● IN   |  4h 23m       │
 * │ Week: 18h 45m           │
 * │ Days: 3/3  ✓            │
 * │ [  TAP TO SIGN OUT  ]   │
 * └─────────────────────────┘
 * ```
 */
class TimeKeeperWidget : GlanceAppWidget() {

    override val stateDefinition = TimeKeeperWidgetStateDefinition

    override suspend fun provideGlance(context: Context, id: GlanceId) {
        provideContent {
            GlanceTheme {
                WidgetContent()
            }
        }
    }

    @Composable
    private fun WidgetContent() {
        val state = currentState<WidgetState>()

        Box(
            modifier = GlanceModifier
                .fillMaxSize()
                .clickable(
                    actionStartActivity(
                        Intent(
                            LocalContext.current,
                            MainActivity::class.java
                        ).apply {
                            flags = Intent.FLAG_ACTIVITY_NEW_TASK or
                                    Intent.FLAG_ACTIVITY_CLEAR_TOP
                        }
                    )
                )
        ) {
            Column(
                modifier = GlanceModifier
                    .fillMaxSize()
                    .background(GlanceTheme.colors.surface)
                    .padding(12.dp),
                verticalAlignment = Alignment.Top,
                horizontalAlignment = Alignment.Start
            ) {
                // Row 1: Status dot + label  |  Today's hours
                Row(
                    modifier = GlanceModifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    // Status indicator dot (green = IN, gray = OUT)
                    Box(
                        modifier = GlanceModifier
                            .size(10.dp)
                            .background(if (state.isCheckedIn) GreenDot else GrayDot),
                        contentAlignment = Alignment.Center
                    ) {}

                    Spacer(modifier = GlanceModifier.width(6.dp))

                    Text(
                        text = if (state.isCheckedIn) "IN" else "OUT",
                        style = TextStyle(
                            color = GlanceTheme.colors.onSurface,
                            fontSize = 14.sp,
                            fontWeight = FontWeight.Bold
                        )
                    )

                    Spacer(modifier = GlanceModifier.defaultWeight())

                    Text(
                        text = state.todayDurationFormatted,
                        style = TextStyle(
                            color = GlanceTheme.colors.onSurface,
                            fontSize = 14.sp,
                            fontWeight = FontWeight.Medium
                        )
                    )
                }

                Spacer(modifier = GlanceModifier.height(6.dp))

                // Row 2: Weekly total
                Text(
                    text = "Week: ${state.weekDurationFormatted}",
                    style = TextStyle(
                        color = GlanceTheme.colors.onSurfaceVariant,
                        fontSize = 12.sp
                    )
                )

                Spacer(modifier = GlanceModifier.height(4.dp))

                // Row 3: Days completed / target + check mark if met
                Row(
                    modifier = GlanceModifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "Days: ${state.daysCompleted}/${state.targetDays}",
                        style = TextStyle(
                            color = GlanceTheme.colors.onSurfaceVariant,
                            fontSize = 12.sp
                        )
                    )

                    if (state.isWeeklyTargetMet) {
                        Spacer(modifier = GlanceModifier.width(4.dp))
                        Text(
                            text = "✓",
                            style = TextStyle(
                                color = GreenDot,
                                fontSize = 14.sp,
                                fontWeight = FontWeight.Bold
                            )
                        )
                    }
                }

                Spacer(modifier = GlanceModifier.defaultWeight())

                // Row 4: Toggle button
                Button(
                    text = if (state.isCheckedIn) "Sign Out" else "Sign In",
                    modifier = GlanceModifier.fillMaxWidth(),
                    onClick = actionRunCallback<ToggleCheckInAction>()
                )
            }
        }
    }
}

/**
 * [ActionCallback] invoked when the user taps the widget toggle button.
 *
 * If currently checked out on a weekday → checks in.
 * If currently checked in → checks out the active session.
 * After the toggle, the widget refreshes to reflect the new state.
 */
class ToggleCheckInAction : ActionCallback {

    override suspend fun onAction(
        context: Context,
        glanceId: GlanceId,
        parameters: androidx.glance.action.ActionParameters
    ) {
        val db = AppDatabase.getInstance(context)
        val settingsDs = SettingsDataStore(context.widgetSettingsDataStore)
        val repo = AttendanceRepository(db.timeSessionDao(), db.weeklySummaryDao(), db.archivedTimeSessionDao(), settingsDs)

        try {
            val openSession = db.timeSessionDao().getOpenSessionSnapshot()
            if (openSession != null) {
                repo.checkOut(openSession.id)
            } else {
                repo.checkIn()
            }
        } catch (_: Exception) {
            // Silently handle — widget will show stale until next refresh
        }

        // Refresh the widget to reflect updated state
        TimeKeeperWidget().update(context, glanceId)

        // Launch the app so the user sees the updated dashboard
        val launchIntent = context.packageManager.getLaunchIntentForPackage(context.packageName)
        if (launchIntent != null) {
            launchIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            context.startActivity(launchIntent)
        }
    }
}
