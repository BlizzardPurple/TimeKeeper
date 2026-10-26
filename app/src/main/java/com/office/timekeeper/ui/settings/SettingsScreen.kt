package com.office.timekeeper.ui.settings

import android.Manifest
import android.content.pm.PackageManager
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.DateRange
import androidx.compose.material.icons.outlined.LocationOn
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material.icons.outlined.Warning
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Slider
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.google.android.gms.location.LocationServices
import kotlin.math.roundToInt

/**
 * Settings screen for configuring the attendance schedule targets.
 *
 * Displays three configurable settings:
 * - Target days per week (1–5)
 * - Target hours per day (1–12)
 * - Geofence radius (50–500 m)
 *
 * Requirements: 12.1, 12.2, 12.3, 12.4
 */
@Composable
fun SettingsScreen(
    viewModel: SettingsViewModel,
    snackbarHostState: SnackbarHostState = remember { SnackbarHostState() }
) {
    val targetDays by viewModel.targetDaysPerWeek.collectAsStateWithLifecycle()
    val targetHours by viewModel.targetHoursPerDay.collectAsStateWithLifecycle()
    val geofenceRadius by viewModel.geofenceRadiusMeters.collectAsStateWithLifecycle()
    val absences by viewModel.absencesThisWeek.collectAsStateWithLifecycle()
    val isLocationCalibrated by viewModel.isLocationCalibrated.collectAsStateWithLifecycle()
    val calibratedLat by viewModel.calibratedLatitude.collectAsStateWithLifecycle()
    val calibratedLng by viewModel.calibratedLongitude.collectAsStateWithLifecycle()
    val error by viewModel.error.collectAsStateWithLifecycle()

    // Show validation errors as snackbar
    LaunchedEffect(error) {
        error?.let {
            snackbarHostState.showSnackbar(
                message = it,
                duration = SnackbarDuration.Short
            )
            viewModel.clearError()
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(PaddingValues(16.dp)),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        Text(
            text = "Schedule Settings",
            style = MaterialTheme.typography.headlineSmall,
            color = MaterialTheme.colorScheme.onBackground
        )

        Text(
            text = "Configure your weekly attendance targets and geofence preferences.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )

        Spacer(modifier = Modifier.height(4.dp))

        // --- Target Days Per Week ---
        SettingCard(
            icon = Icons.Outlined.DateRange,
            title = "Target Days Per Week",
            description = "Number of weekdays you plan to attend the office.",
            accessibilityLabel = "Target days per week setting"
        ) {
            StepSlider(
                value = targetDays,
                valueRange = 1..5,
                label = { "$it day${if (it != 1) "s" else ""}" },
                onValueChange = { viewModel.onTargetDaysChanged(it) },
                accessibilityLabel = "Target days: $targetDays"
            )
        }

        // --- Target Hours Per Day ---
        SettingCard(
            icon = Icons.Outlined.Info,
            title = "Target Hours Per Day",
            description = "Minimum hours per office day to count toward your weekly target.",
            accessibilityLabel = "Target hours per day setting"
        ) {
            StepSlider(
                value = targetHours,
                valueRange = 1..12,
                label = { "$it hour${if (it != 1) "s" else ""}" },
                onValueChange = { viewModel.onTargetHoursChanged(it) },
                accessibilityLabel = "Target hours: $targetHours"
            )
        }

        // --- Geofence Radius ---
        SettingCard(
            icon = Icons.Outlined.LocationOn,
            title = "Geofence Radius",
            description = "Distance from the office that triggers arrival/departure notifications.",
            accessibilityLabel = "Geofence radius setting"
        ) {
            GeofenceRadiusSlider(
                value = geofenceRadius,
                onValueChange = { viewModel.onGeofenceRadiusChanged(it) }
            )
        }

        // --- Absences This Week ---
        SettingCard(
            icon = Icons.Outlined.DateRange,
            title = "WFH/Leave Days This Week",
            description = "Number of WFH or leave days. Each reduces your target by 1 day and ${targetHours}h.",
            accessibilityLabel = "WFH leave days setting"
        ) {
            StepSlider(
                value = absences,
                valueRange = 0..4,
                label = { "$it day${if (it != 1) "s" else ""} off" },
                onValueChange = { viewModel.onAbsencesChanged(it) },
                accessibilityLabel = "Absences: $absences"
            )
        }

        // --- Office Location Calibration ---
        CalibrateLocationCard(
            isCalibrated = isLocationCalibrated,
            calibratedLat = calibratedLat,
            calibratedLng = calibratedLng,
            onCalibrate = { lat, lng -> viewModel.onCalibrateLocation(lat, lng) }
        )

        // Weekly target summary
        WeeklyTargetSummary(days = targetDays, hoursPerDay = targetHours, absences = absences)

        // Feasibility warnings
        val adjustedDays = (targetDays - absences).coerceAtLeast(0)
        val adjustedHours = adjustedDays * targetHours

        if (targetHours > 12) {
            WarningCard("Target hours per day ($targetHours) exceeds the 12-hour daily maximum. Excess hours won't count.")
        }
        if (absences >= targetDays) {
            WarningCard("No office days remain this week. All $targetDays target days are marked as absences.")
        }
        if (adjustedDays > 0 && adjustedHours > adjustedDays * 12) {
            WarningCard("Adjusted target ($adjustedHours hours in $adjustedDays days) may exceed what's achievable at 12h/day max.")
        }
    }
}

// ---------------------------------------------------------------------------
// Reusable components
// ---------------------------------------------------------------------------

/**
 * A Material 3 card that groups a single setting with an icon, title,
 * description, and custom content (typically a slider).
 */
@Composable
private fun SettingCard(
    icon: ImageVector,
    title: String,
    description: String,
    accessibilityLabel: String,
    content: @Composable () -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .semantics { contentDescription = accessibilityLabel },
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surface
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(24.dp)
                )
                Spacer(modifier = Modifier.padding(horizontal = 8.dp))
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onSurface
                )
            }

            Spacer(modifier = Modifier.height(4.dp))

            Text(
                text = description,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            Spacer(modifier = Modifier.height(12.dp))

            content()
        }
    }
}

/**
 * An integer step slider with a formatted value label.
 *
 * The slider snaps to discrete integer steps. Each step change is
 * immediately committed via [onValueChange] so the ViewModel can
 * persist it.
 *
 * @param value current integer value
 * @param valueRange allowed range (inclusive)
 * @param label formats the current value for display
 * @param onValueChange called with the new integer value on each discrete step
 * @param accessibilityLabel content description for the slider
 */
@Composable
private fun StepSlider(
    value: Int,
    valueRange: IntRange,
    label: (Int) -> String,
    onValueChange: (Int) -> Unit,
    accessibilityLabel: String
) {
    Column {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = label(value),
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurface
            )
            Text(
                text = "${valueRange.first}–${valueRange.last}",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }

        Spacer(modifier = Modifier.height(4.dp))

        Slider(
            value = value.toFloat(),
            onValueChange = { floatValue ->
                val rounded = floatValue.roundToInt()
                if (rounded != value) {
                    onValueChange(rounded)
                }
            },
            valueRange = valueRange.first.toFloat()..valueRange.last.toFloat(),
            steps = valueRange.last - valueRange.first - 1,
            modifier = Modifier
                .fillMaxWidth()
                .semantics { contentDescription = accessibilityLabel }
        )
    }
}

/**
 * Slider for geofence radius (50–500 m) with meter formatting.
 */
@Composable
private fun GeofenceRadiusSlider(
    value: Float,
    onValueChange: (Float) -> Unit
) {
    Column {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "${value.roundToInt()} meters",
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurface
            )
            Text(
                text = "50–500 m",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }

        Spacer(modifier = Modifier.height(4.dp))

        Slider(
            value = value,
            onValueChange = { newValue ->
                // Snap to nearest 10 m for a clean UX
                val snapped = (newValue / 10f).roundToInt() * 10f
                onValueChange(snapped)
            },
            valueRange = 50f..500f,
            modifier = Modifier
                .fillMaxWidth()
                .semantics {
                    contentDescription = "Geofence radius: ${value.roundToInt()} meters"
                }
        )
    }
}

/**
 * A read-only summary card showing the derived weekly target.
 */
@Composable
private fun WeeklyTargetSummary(days: Int, hoursPerDay: Int, absences: Int = 0) {
    val adjustedDays = (days - absences).coerceAtLeast(0)
    val totalHours = adjustedDays * hoursPerDay
    val absenceText = if (absences > 0) " ($absences day${if (absences != 1) "s" else ""} off)" else ""

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .semantics { contentDescription = "Weekly target summary: $totalHours hours across $adjustedDays days$absenceText" },
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.primaryContainer
        )
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(
                text = "Weekly Target",
                style = MaterialTheme.typography.titleSmall,
                color = MaterialTheme.colorScheme.onPrimaryContainer
            )

            Spacer(modifier = Modifier.height(4.dp))

            Text(
                text = "$totalHours hours across $adjustedDays day${if (adjustedDays != 1) "s" else ""}$absenceText",
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onPrimaryContainer
            )
        }
    }
}

/**
 * Card for calibrating or recalibrating the office location via GPS.
 * Shows current calibrated coordinates when available.
 */
@Composable
private fun CalibrateLocationCard(
    isCalibrated: Boolean,
    calibratedLat: Double?,
    calibratedLng: Double?,
    onCalibrate: (Double, Double) -> Unit
) {
    val context = LocalContext.current
    var isCalibrating by remember { mutableStateOf(false) }

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .semantics { contentDescription = "Office location calibration" },
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surface
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    imageVector = Icons.Outlined.LocationOn,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(24.dp)
                )
                Spacer(modifier = Modifier.padding(horizontal = 8.dp))
                Text(
                    text = "Office Location",
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onSurface
                )
            }

            Spacer(modifier = Modifier.height(4.dp))

            Text(
                text = if (isCalibrated) "Tap to recalibrate using your current GPS position."
                       else "Calibrate your office location for auto check-in. Stand at your desk and tap the button.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            if (isCalibrated && calibratedLat != null && calibratedLng != null) {
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = "📍 ${String.format("%.4f", calibratedLat)}, ${String.format("%.4f", calibratedLng)}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.primary
                )
            }

            Spacer(modifier = Modifier.height(12.dp))

            OutlinedButton(
                onClick = {
                    isCalibrating = true
                    val fusedClient = LocationServices.getFusedLocationProviderClient(context)
                    if (ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED) {
                        @Suppress("MissingPermission")
                        fusedClient.lastLocation.addOnSuccessListener { location ->
                            if (location != null) {
                                onCalibrate(location.latitude, location.longitude)
                            }
                            isCalibrating = false
                        }.addOnFailureListener {
                            isCalibrating = false
                        }
                    } else {
                        isCalibrating = false
                    }
                },
                modifier = Modifier.fillMaxWidth(),
                enabled = !isCalibrating
            ) {
                Text(
                    text = if (isCalibrating) "Calibrating..." else if (isCalibrated) "📍 Recalibrate" else "📍 Calibrate Office Location"
                )
            }
        }
    }
}

/**
 * A warning card displayed when settings produce infeasible or
 * unusual target configurations.
 */
@Composable
private fun WarningCard(message: String) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .semantics { contentDescription = "Warning: $message" },
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.errorContainer
        )
    ) {
        Row(
            modifier = Modifier.padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                imageVector = Icons.Outlined.Warning,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.error,
                modifier = Modifier.size(24.dp)
            )
            Spacer(modifier = Modifier.padding(horizontal = 8.dp))
            Text(
                text = message,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onErrorContainer
            )
        }
    }
}
