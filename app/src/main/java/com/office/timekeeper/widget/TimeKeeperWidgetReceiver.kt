package com.office.timekeeper.widget

import androidx.glance.appwidget.GlanceAppWidget
import androidx.glance.appwidget.GlanceAppWidgetReceiver

/**
 * [GlanceAppWidgetReceiver] that wires the system's AppWidget lifecycle
 * events to [TimeKeeperWidget].
 *
 * Registration in AndroidManifest is handled in task 10.3.
 */
class TimeKeeperWidgetReceiver : GlanceAppWidgetReceiver() {
    override val glanceAppWidget: GlanceAppWidget = TimeKeeperWidget()
}
