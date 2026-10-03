package com.rk.taskmanager.widget

import android.appwidget.AppWidgetManager
import android.content.Context
import androidx.glance.appwidget.GlanceAppWidget
import androidx.glance.appwidget.GlanceAppWidgetReceiver

/**
 * Keeps [WidgetUpdateService] in step with whether any widget is placed.
 *
 * Without this the foreground service would either never start (widgets added while the app is
 * closed) or linger forever after the last widget is removed.
 */
abstract class WidgetSyncReceiver : GlanceAppWidgetReceiver() {

    abstract override val glanceAppWidget: GlanceAppWidget

    override fun onEnabled(context: Context) {
        super.onEnabled(context)
        WidgetUpdateService.sync(context)
    }

    override fun onUpdate(context: Context, appWidgetManager: AppWidgetManager, appWidgetIds: IntArray) {
        super.onUpdate(context, appWidgetManager, appWidgetIds)
        // Also covers the case where the service was killed while a widget stayed on screen.
        WidgetUpdateService.sync(context)
    }

    override fun onDisabled(context: Context) {
        super.onDisabled(context)
        WidgetUpdateService.sync(context)
    }
}