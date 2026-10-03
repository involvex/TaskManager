package com.rk.taskmanager.widget

import android.content.Context
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.glance.ColorFilter
import androidx.glance.GlanceId
import androidx.glance.GlanceModifier
import androidx.glance.Image
import androidx.glance.ImageProvider
import androidx.glance.action.actionParametersOf
import androidx.glance.action.clickable
import androidx.glance.appwidget.GlanceAppWidget
import androidx.glance.appwidget.SizeMode
import androidx.glance.appwidget.action.actionRunCallback
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
import androidx.glance.layout.size
import androidx.glance.layout.width
import androidx.glance.text.FontWeight
import androidx.glance.text.Text
import androidx.glance.text.TextStyle
import androidx.glance.unit.ColorProvider
import com.rk.taskmanager.R
import kotlin.math.roundToInt

/**
 * Top-5 processes widget, sortable by RAM / CPU / name, with a kill button per row.
 *
 * Row count is fixed at [MAX_WIDGET_ROWS]: `RemoteViews` caps how many views a widget may inflate,
 * and a longer list would risk `TransactionTooLargeException` rather than simply scrolling.
 */
class TopProcessesWidget : GlanceAppWidget() {

    override val sizeMode = SizeMode.Exact

    override suspend fun provideGlance(context: Context, id: GlanceId) {
        provideContent { ProcessesContent() }
    }
}

class TopProcessesWidgetReceiver : WidgetSyncReceiver() {
    override val glanceAppWidget: GlanceAppWidget = TopProcessesWidget()
}

@Composable
private fun ProcessesContent() {
    val rows = WidgetCache.procs.collectAsState().value
    val sort = WidgetCache.sort.collectAsState().value
    val killed = WidgetCache.killedPids.collectAsState().value
    val connected = WidgetCache.vitals.collectAsState().value.connected

    val background = widgetColor(WidgetPalette.background)
    val primary = widgetColor(WidgetPalette.textPrimary)
    val secondary = widgetColor(WidgetPalette.textSecondary)
    val disabled = widgetColor(WidgetPalette.textDisabled)

    Column(
        modifier = GlanceModifier
            .fillMaxSize()
            .background(background)
            .cornerRadius(16.dp)
            .padding(12.dp)
    ) {
        Row(
            modifier = GlanceModifier
                .fillMaxWidth()
                .clickable(actionRunCallback<CycleSortAction>())
                .padding(vertical = 2.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "Top 5",
                style = TextStyle(fontSize = 12.sp, color = secondary)
            )
            Spacer(GlanceModifier.defaultWeight())
            Text(
                text = sort.label,
                style = TextStyle(
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Bold,
                    color = primary
                )
            )
        }

        Spacer(GlanceModifier.height(6.dp))

        when {
            !connected -> Message("Waiting for daemon...", secondary)
            rows.isEmpty() -> Message("Collecting processes...", secondary)
            else -> rows.forEach { ProcRow(it, it.pid in killed, primary, disabled) }
        }
    }
}

@Composable
private fun Message(text: String, color: ColorProvider) {
    Text(text = text, style = TextStyle(fontSize = 12.sp, color = color))
}

@Composable
private fun ProcRow(
    row: WidgetProcRow,
    isKilled: Boolean,
    primary: ColorProvider,
    disabled: ColorProvider
) {
    val bodyColor = if (isKilled) disabled else primary

    Row(
        modifier = GlanceModifier
            .fillMaxWidth()
            .padding(vertical = 3.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = row.label,
            style = TextStyle(fontSize = 12.sp, color = bodyColor),
            modifier = GlanceModifier.defaultWeight()
        )
        Spacer(GlanceModifier.width(6.dp))
        Text(
            text = "${row.cpu.coerceAtLeast(0f).roundToInt()}%",
            style = TextStyle(fontSize = 11.sp, color = bodyColor),
            modifier = GlanceModifier.width(40.dp)
        )
        Text(
            text = formatKb(row.ramKb),
            style = TextStyle(fontSize = 11.sp, color = bodyColor),
            modifier = GlanceModifier.width(52.dp)
        )
        Image(
            provider = ImageProvider(R.drawable.ic_widget_close),
            contentDescription = "Kill ${row.label}",
            colorFilter = ColorFilter.tint(bodyColor),
            modifier = GlanceModifier
                .padding(start = 8.dp)
                .size(16.dp)
                .clickable(
                    actionRunCallback<KillProcessAction>(
                        actionParametersOf(
                            KillProcessAction.KEY_PID to row.pid,
                            KillProcessAction.KEY_CMDLINE to row.cmdLine,
                            KillProcessAction.KEY_IS_APP to row.isApp
                        )
                    )
                )
        )
    }
}