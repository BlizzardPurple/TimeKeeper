package com.office.timekeeper.data.repository

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.MutablePreferences
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.preferencesOf
import com.office.timekeeper.data.datastore.SettingsDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows

/**
 * In-memory DataStore backed by a MutableStateFlow so we can exercise
 * [SettingsDataStore] (and therefore [SettingsRepository]) without
 * touching disk or needing an Android context.
 */
private class InMemoryDataStore : DataStore<Preferences> {

    private val _state = MutableStateFlow(preferencesOf())

    override val data: Flow<Preferences> = _state

    override suspend fun updateData(
        transform: suspend (Preferences) -> Preferences
    ): Preferences {
        var updated: Preferences? = null
        _state.update { current ->
            val mutable = current.toMutablePreferences()
            val result = transform(mutable)
            updated = result
            result
        }
        return updated!!
    }
}

@DisplayName("SettingsRepository")
class SettingsRepositoryTest {

    private lateinit var dataStore: InMemoryDataStore
    private lateinit var settingsDataStore: SettingsDataStore
    private lateinit var repo: SettingsRepository

    @BeforeEach
    fun setUp() {
        dataStore = InMemoryDataStore()
        settingsDataStore = SettingsDataStore(dataStore)
        repo = SettingsRepository(settingsDataStore)
    }

    // ──────────── Read flows (delegation) ────────────

    @Nested
    @DisplayName("Read flows — defaults")
    inner class ReadDefaults {

        @Test
        fun `targetDaysPerWeek emits default 3`() = runTest {
            assertEquals(3, repo.targetDaysPerWeek.first())
        }

        @Test
        fun `targetHoursPerDay emits default 8`() = runTest {
            assertEquals(8, repo.targetHoursPerDay.first())
        }

        @Test
        fun `geofenceRadiusMeters emits default 100f`() = runTest {
            assertEquals(100f, repo.geofenceRadiusMeters.first())
        }

        @Test
        fun `targetWeeklyHours emits default 24 (3 x 8)`() = runTest {
            assertEquals(24, repo.targetWeeklyHours.first())
        }
    }

    // ──────────── Write + read round-trip ────────────

    @Nested
    @DisplayName("Write — valid values persist and flows update")
    inner class WriteValid {

        @Test
        fun `setTargetDaysPerWeek persists and flow updates`() = runTest {
            repo.setTargetDaysPerWeek(5)
            assertEquals(5, repo.targetDaysPerWeek.first())
        }

        @Test
        fun `setTargetHoursPerDay persists and flow updates`() = runTest {
            repo.setTargetHoursPerDay(10)
            assertEquals(10, repo.targetHoursPerDay.first())
        }

        @Test
        fun `setGeofenceRadiusMeters persists and flow updates`() = runTest {
            repo.setGeofenceRadiusMeters(250f)
            assertEquals(250f, repo.geofenceRadiusMeters.first())
        }

        @Test
        fun `targetWeeklyHours updates after changing days`() = runTest {
            repo.setTargetDaysPerWeek(5)
            // 5 days * 8 hours = 40
            assertEquals(40, repo.targetWeeklyHours.first())
        }

        @Test
        fun `targetWeeklyHours updates after changing hours`() = runTest {
            repo.setTargetHoursPerDay(12)
            // 3 days * 12 hours = 36
            assertEquals(36, repo.targetWeeklyHours.first())
        }

        @Test
        fun `targetWeeklyHours updates after changing both`() = runTest {
            repo.setTargetDaysPerWeek(4)
            repo.setTargetHoursPerDay(6)
            // 4 * 6 = 24
            assertEquals(24, repo.targetWeeklyHours.first())
        }
    }

    // ──────────── Boundary values ────────────

    @Nested
    @DisplayName("Write — boundary values accepted")
    inner class WriteBoundary {

        @Test
        fun `setTargetDaysPerWeek accepts min 1`() = runTest {
            repo.setTargetDaysPerWeek(1)
            assertEquals(1, repo.targetDaysPerWeek.first())
        }

        @Test
        fun `setTargetDaysPerWeek accepts max 5`() = runTest {
            repo.setTargetDaysPerWeek(5)
            assertEquals(5, repo.targetDaysPerWeek.first())
        }

        @Test
        fun `setTargetHoursPerDay accepts min 1`() = runTest {
            repo.setTargetHoursPerDay(1)
            assertEquals(1, repo.targetHoursPerDay.first())
        }

        @Test
        fun `setTargetHoursPerDay accepts max 12`() = runTest {
            repo.setTargetHoursPerDay(12)
            assertEquals(12, repo.targetHoursPerDay.first())
        }

        @Test
        fun `setGeofenceRadiusMeters accepts min 50`() = runTest {
            repo.setGeofenceRadiusMeters(50f)
            assertEquals(50f, repo.geofenceRadiusMeters.first())
        }

        @Test
        fun `setGeofenceRadiusMeters accepts max 500`() = runTest {
            repo.setGeofenceRadiusMeters(500f)
            assertEquals(500f, repo.geofenceRadiusMeters.first())
        }
    }

    // ──────────── Validation — rejected values ────────────

    @Nested
    @DisplayName("Write — invalid values rejected with IllegalArgumentException")
    inner class WriteInvalid {

        @Test
        fun `setTargetDaysPerWeek rejects 0`() = runTest {
            assertThrows<IllegalArgumentException> {
                repo.setTargetDaysPerWeek(0)
            }
        }

        @Test
        fun `setTargetDaysPerWeek rejects 6`() = runTest {
            assertThrows<IllegalArgumentException> {
                repo.setTargetDaysPerWeek(6)
            }
        }

        @Test
        fun `setTargetDaysPerWeek rejects negative`() = runTest {
            assertThrows<IllegalArgumentException> {
                repo.setTargetDaysPerWeek(-1)
            }
        }

        @Test
        fun `setTargetHoursPerDay rejects 0`() = runTest {
            assertThrows<IllegalArgumentException> {
                repo.setTargetHoursPerDay(0)
            }
        }

        @Test
        fun `setTargetHoursPerDay rejects 13`() = runTest {
            assertThrows<IllegalArgumentException> {
                repo.setTargetHoursPerDay(13)
            }
        }

        @Test
        fun `setTargetHoursPerDay rejects negative`() = runTest {
            assertThrows<IllegalArgumentException> {
                repo.setTargetHoursPerDay(-5)
            }
        }

        @Test
        fun `setGeofenceRadiusMeters rejects 49`() = runTest {
            assertThrows<IllegalArgumentException> {
                repo.setGeofenceRadiusMeters(49f)
            }
        }

        @Test
        fun `setGeofenceRadiusMeters rejects 501`() = runTest {
            assertThrows<IllegalArgumentException> {
                repo.setGeofenceRadiusMeters(501f)
            }
        }

        @Test
        fun `setGeofenceRadiusMeters rejects 0`() = runTest {
            assertThrows<IllegalArgumentException> {
                repo.setGeofenceRadiusMeters(0f)
            }
        }

        @Test
        fun `setGeofenceRadiusMeters rejects negative`() = runTest {
            assertThrows<IllegalArgumentException> {
                repo.setGeofenceRadiusMeters(-100f)
            }
        }
    }

    // ──────────── Rejected values don't modify state ────────────

    @Nested
    @DisplayName("Rejected writes leave state unchanged")
    inner class RejectedPreservesState {

        @Test
        fun `rejected setTargetDaysPerWeek keeps previous value`() = runTest {
            repo.setTargetDaysPerWeek(4)
            runCatching { repo.setTargetDaysPerWeek(0) }
            assertEquals(4, repo.targetDaysPerWeek.first())
        }

        @Test
        fun `rejected setTargetHoursPerDay keeps previous value`() = runTest {
            repo.setTargetHoursPerDay(10)
            runCatching { repo.setTargetHoursPerDay(13) }
            assertEquals(10, repo.targetHoursPerDay.first())
        }

        @Test
        fun `rejected setGeofenceRadiusMeters keeps previous value`() = runTest {
            repo.setGeofenceRadiusMeters(200f)
            runCatching { repo.setGeofenceRadiusMeters(49f) }
            assertEquals(200f, repo.geofenceRadiusMeters.first())
        }
    }
}
