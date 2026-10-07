package com.office.timekeeper

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.util.Log
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Modifier
import androidx.core.content.ContextCompat
import androidx.lifecycle.lifecycleScope
import com.office.timekeeper.ui.dashboard.DashboardViewModel
import com.office.timekeeper.ui.history.HistoryViewModel
import com.office.timekeeper.ui.navigation.NavGraph
import com.office.timekeeper.ui.settings.SettingsViewModel
import com.office.timekeeper.ui.theme.TimeKeeperTheme
import kotlinx.coroutines.launch

class MainActivity : ComponentActivity() {

    companion object {
        private const val TAG = "MainActivity"
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val appModule = (application as TimeKeeperApplication).appModule

        val dashboardViewModel = DashboardViewModel(
            appModule.attendanceRepository,
            appModule.settingsRepository,
            application
        )
        val historyViewModel = HistoryViewModel(appModule.attendanceRepository)
        val settingsViewModel = SettingsViewModel(appModule.settingsRepository)

        setContent {
            TimeKeeperTheme {
                // Step 2: Background location launcher (requested AFTER foreground is granted)
                val bgLocationLauncher = rememberLauncherForActivityResult(
                    ActivityResultContracts.RequestPermission()
                ) { granted ->
                    Log.d(TAG, "Background location granted: $granted")
                    if (granted) {
                        lifecycleScope.launch {
                            appModule.geofenceManager.registerGeofence()
                        }
                    }
                }

                // Step 1: Foreground permissions launcher
                val fgPermissionLauncher = rememberLauncherForActivityResult(
                    ActivityResultContracts.RequestMultiplePermissions()
                ) { permissions ->
                    val fineGranted = permissions[Manifest.permission.ACCESS_FINE_LOCATION] == true
                    Log.d(TAG, "Fine location granted: $fineGranted")

                    if (fineGranted) {
                        // Now request background location separately (required on Android 11+)
                        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                            val bgGranted = ContextCompat.checkSelfPermission(
                                this@MainActivity,
                                Manifest.permission.ACCESS_BACKGROUND_LOCATION
                            ) == PackageManager.PERMISSION_GRANTED

                            if (!bgGranted) {
                                bgLocationLauncher.launch(Manifest.permission.ACCESS_BACKGROUND_LOCATION)
                            } else {
                                // Already have background — register geofence
                                lifecycleScope.launch {
                                    appModule.geofenceManager.registerGeofence()
                                }
                            }
                        } else {
                            // Pre-Q: foreground permission is enough for geofencing
                            lifecycleScope.launch {
                                appModule.geofenceManager.registerGeofence()
                            }
                        }
                    }
                }

                LaunchedEffect(Unit) {
                    // Check if we already have permissions
                    val hasFine = ContextCompat.checkSelfPermission(
                        this@MainActivity,
                        Manifest.permission.ACCESS_FINE_LOCATION
                    ) == PackageManager.PERMISSION_GRANTED

                    if (hasFine) {
                        val hasBg = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                            ContextCompat.checkSelfPermission(
                                this@MainActivity,
                                Manifest.permission.ACCESS_BACKGROUND_LOCATION
                            ) == PackageManager.PERMISSION_GRANTED
                        } else true

                        if (hasBg) {
                            // All good — register geofence
                            appModule.geofenceManager.registerGeofence()
                        } else {
                            bgLocationLauncher.launch(Manifest.permission.ACCESS_BACKGROUND_LOCATION)
                        }
                    } else {
                        // Request foreground permissions + notification permission
                        val permsToRequest = mutableListOf(
                            Manifest.permission.ACCESS_FINE_LOCATION,
                            Manifest.permission.ACCESS_COARSE_LOCATION
                        )
                        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                            permsToRequest.add(Manifest.permission.POST_NOTIFICATIONS)
                        }
                        fgPermissionLauncher.launch(permsToRequest.toTypedArray())
                    }
                }

                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    NavGraph(
                        dashboardViewModel = dashboardViewModel,
                        historyViewModel = historyViewModel,
                        settingsViewModel = settingsViewModel
                    )
                }
            }
        }
    }
}
