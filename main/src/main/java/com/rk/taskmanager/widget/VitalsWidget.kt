package com.rk.taskmanager.widget

import android.content.Context
import androidx.annotation.ColorRes
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.glance.GlanceId
import androidx.glance.GlanceModifier
import androidx.glance.appwidget.GlanceAppWidget
import androidx.glance.appwidget.LinearProgressIndicator
import androidx.glance.appwidget.SizeMode
import androidx.glance.appwidget.cornerRadius
import androidx.glance.appwidget.provideContent
import androidx.glance.background
import androidx.glance.layout.Alignment
import androidx.glance.layout.Column
import androidx.glance.layout.Row
import androidx.glance.layout.Spacer
import androidx.glance.layout.fillMaxSize
import androidx.glance.layout.fillMaxWidth
import androidx.glance.layout.height
import androidx.glance.layout.padding
import androidx.glance.text.FontWeight
import androidx.glance.text.Text
import androidx.glance.text.TextStyle

/**
 * CPU / RAM / GPU usage widget.
 *
 * Reads only [WidgetCache]; the daemon is never touched from the composition.
 */
class VitalsWidget : GlanceAppWidget() {

    override val sizeMode = SizeMode.Exact

    override suspend fun provideGlance(context: Context, id: GlanceId) {
        provideContent {
            val vitals = WidgetCache.vitals.collectAsState().value
            VitalsContent(vitals)
        }
    }
}

class VitalsWidgetReceiver : WidgetSyncReceiver() {
    override val glanceAppWidget: GlanceAppWidget = VitalsWidget()
}

@Composable
private fun VitalsContent(vitals: WidgetVitals) {
    val background = widgetColor(WidgetPalette.background)
    Column(
        modifier = GlanceModifier
            .fillMaxSize()
            .background(background)
            .cornerRadius(16.dp)
            .padding(12.dp)
    ) {
        MetricRow("CPU", vitals.cpu, WidgetPalette.fillCpu, unknownIsValid = false)
        Spacer(GlanceModifier.height(10.dp))
        MetricRow("RAM", vitals.ram, WidgetPalette.fillRam, unknownIsValid = false)
        Spacer(GlanceModifier.height(10.dp))
        MetricRow("GPU", vitals.gpu, WidgetPalette.fillGpu, unknownIsValid = true)
    }
}

@Composable
private fun MetricRow(
    label: String,
    value: Int,
    @ColorRes fillRes: Int,
    unknownIsValid: Boolean
) {
    val secondary = widgetColor(WidgetPalette.textSecondary)
    val primary = widgetColor(WidgetPalette.textPrimary)
    val track = widgetColor(WidgetPalette.track)
    val fill = widgetColor(fillRes)

    Column {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                text = label,
                style = TextStyle(fontSize = 12.sp, color = secondary)
            )
            Spacer(GlanceModifier.defaultWeight())
            Text(
                text = when {
                    value < 0 && unknownIsValid -> "n/a"
                    value < 0 -> "--"
                    else -> "$value%"
                },
                style = TextStyle(
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Bold,
                    color = primary
                )
            )
        }
        Spacer(GlanceModifier.height(4.dp))
        if (value >= 0) {
            // Glance takes progress in 0f..1f and scales it onto the RemoteViews progress bar.
            LinearProgressIndicator(
                progress = value.coerceIn(0, 100) / 100f,
                modifier = GlanceModifier
                    .fillMaxWidth()
                    .height(6.dp),
                color = fill,
                backgroundColor = track
            )
        } else {
            // GPU on devices with no readable busy node reports -1; keep the rows aligned.
            Spacer(
                GlanceModifier
                    .fillMaxWidth()
                    .height(6.dp)
            )
        }
    }
}