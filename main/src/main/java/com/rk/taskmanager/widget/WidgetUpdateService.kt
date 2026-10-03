package com.rk.taskmanager.widget

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.appwidget.AppWidgetManager
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.os.IBinder
import android.util.Log
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import androidx.glance.appwidget.updateAll
import com.rk.commons.settings.Settings
import com.rk.taskmanager.MainActivity
import com.rk.taskmanager.R
import com.rk.taskmanager.daemon.isConnected
import com.rk.taskmanager.daemon.send_daemon_messages
import com.rk.taskmanager.daemon.startDaemon
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import org.json.JSONObject

private const val TAG = "WidgetUpdateService"
private const val CHANNEL_ID = "widget_updates"
private const val NOTIF_ID = 4201

/**
 * Glance compositions stop being kept alive shortly after they render, so the widgets are
 * re-rendered on this cadence to refresh the RemoteViews snapshot. Between re-renders the
 * composables observe [WidgetCache] directly and stay live without any extra work.
 */
private const val KEEPALIVE_MS = 30_000L

/**
 * Keeps the daemon connected and samples it while a widget exists on the home screen.
 *
 * The daemon is owned here rather than by `MainActivity`, because a widget has to keep updating
 * after the activity is gone. Everything written here lands in [WidgetCache], which is the only
 * thing the Glance composables read.
 */
class WidgetUpdateService : Service() {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private var sampling: Job? = null

    override fun onCreate() {
        super.onCreate()
        createChannel()
        startForeground(NOTIF_ID, buildNotification())

        if (!Settings.widgetLive) {
            Log.i(TAG, "live widget updates disabled; stopping")
            stopSelf()
            return
        }
        startSampling()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        // Re-created by the system after a low-memory kill while a widget is still placed.
        return START_STICKY
    }

    override fun onDestroy() {
        scope.cancel()
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null

    private fun startSampling() {
        if (sampling?.isActive == true) return
        val app = applicationContext

        WidgetCollector.start(scope, app)

        sampling = scope.launch {
            ensureDaemon(app)
            while (isActive) {
                val connected = isConnected
                WidgetCache.updateConnected(connected)
                if (connected) {
                    // Same ordering as the in-app graph pinger, which spaces the burst so the
                    // daemon's own 100ms /proc/stat read does not overlap the next request.
                    ping("CPU_PING")
                    delay(16)
                    ping("SWAP_PING")
                    delay(15)
                    ping("GPU_PING")
                    WidgetCollector.readRam(app)
                } else {
                    // Shizuku may have been restarted underneath us; reconnect.
                    ensureDaemon(app)
                }
                delay(Settings.widgetMetricsInterval.coerceAtLeast(500).toLong())
            }
        }

        scope.launch {
            while (isActive) {
                if (isConnected) {
                    runCatching {
                        send_daemon_messages.emit(JSONObject().put("cmd", "LIST_PROCESS").toString())
                    }.onFailure { Log.w(TAG, "LIST_PROCESS failed: ${it.message}") }
                }
                delay(Settings.widgetProcInterval.coerceAtLeast(1000).toLong())
            }
        }

        scope.launch {
            while (isActive) {
                delay(KEEPALIVE_MS)
                runCatching { VitalsWidget().updateAll(app) }
                runCatching { TopProcessesWidget().updateAll(app) }
            }
        }
    }

    private suspend fun ensureDaemon(context: Context) {
        if (isConnected) return
        runCatching { startDaemon(context, Settings.workingMode) }
            .onFailure { Log.w(TAG, "startDaemon failed: ${it.message}") }
    }

    private suspend fun ping(cmd: String) {
        runCatching {
            send_daemon_messages.emit(JSONObject().put("cmd", cmd).toString())
        }.onFailure { Log.w(TAG, "$cmd failed: ${it.message}") }
    }

    private fun createChannel() {
        val channel = NotificationChannel(
            CHANNEL_ID,
            getString(R.string.widget_channel_name),
            NotificationManager.IMPORTANCE_LOW
        ).apply {
            description = getString(R.string.widget_channel_desc)
            setShowBadge(false)
        }
        (getSystemService(NOTIFICATION_SERVICE) as NotificationManager)
            .createNotificationChannel(channel)
    }

    private fun buildNotification(): Notification {
        val intent = PendingIntent.getActivity(
            this,
            0,
            Intent(this, MainActivity::class.java),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )
        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle(getString(R.string.app_name))
            .setContentText(getString(R.string.widget_notification_text))
            .setSmallIcon(R.drawable.ic_taskmanager_monochrome)
            .setContentIntent(intent)
            .setOngoing(true)
            .setShowWhen(false)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .build()
    }

    companion object {
        fun start(context: Context) {
            if (!Settings.widgetLive) return
            runCatching {
                ContextCompat.startForegroundService(context, Intent(context, WidgetUpdateService::class.java))
            }.onFailure { Log.w(TAG, "could not start service: ${it.message}") }
        }

        fun stop(context: Context) {
            runCatching { context.stopService(Intent(context, WidgetUpdateService::class.java)) }
        }

        /**
         * Starts or stops the service to match whether any widget is currently placed, so the
         * notification does not linger after the last widget is removed.
         */
        fun sync(context: Context) {
            val manager = AppWidgetManager.getInstance(context) ?: return
            val placed = manager
                .getAppWidgetIds(ComponentName(context, VitalsWidgetReceiver::class.java))
                .size +
                manager.getAppWidgetIds(ComponentName(context, TopProcessesWidgetReceiver::class.java))
                .size
            if (placed > 0 && Settings.widgetLive) start(context) else stop(context)
        }
    }
}