package com.office.timekeeper.widget

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.preferencesDataStore

/**
 * Single DataStore delegate for the widget package.
 *
 * The `preferencesDataStore` property delegate must be declared at the
 * top level (or as an extension property) — it can only be initialized once
 * per file name. This extension is the single source used by both
 * [TimeKeeperWidgetStateDefinition] and [ToggleCheckInAction].
 */
internal val Context.widgetSettingsDataStore: DataStore<Preferences> by preferencesDataStore(
    name = "settings"
)
