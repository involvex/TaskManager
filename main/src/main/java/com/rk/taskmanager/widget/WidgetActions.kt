package com.rk.taskmanager.widget

import android.content.Context
import android.util.Log
import androidx.glance.GlanceId
import androidx.glance.action.ActionParameters
import androidx.glance.appwidget.action.ActionCallback
import androidx.glance.appwidget.updateAll
import com.rk.commons.settings.Settings
import com.rk.taskmanager.daemon.isConnected
import com.rk.taskmanager.daemon.killByPidOrPackage
import com.rk.taskmanager.daemon.send_daemon_messages
import org.json.JSONObject

private const val TAG = "WidgetActions"

/**
 * Kills the process on the tapped row.
 *
 * `killByPidOrPackage` correlates the daemon reply by request id, so several taps landing close
 * together resolve against their own replies instead of racing for the first `KILL_RESULT`.
 */
class KillProcessAction : ActionCallback {

    override suspend fun onAction(
        context: Context,
        glanceId: GlanceId,
        parameters: ActionParameters
    ) {
        val pid = parameters[KEY_PID] ?: return
        val cmdLine = parameters[KEY_CMDLINE] ?: return
        val isApp = parameters[KEY_IS_APP] ?: false

        val killed = runCatching { killByPidOrPackage(pid, cmdLine, isApp) }
            .onFailure { Log.w(TAG, "kill of pid=$pid failed: ${it.message}") }
            .getOrDefault(false)

        if (killed) {
            Settings.kills++
            WidgetCache.flashKilled(pid)
        } else {
            Log.w(TAG, "daemon refused to kill pid=$pid ($cmdLine)")
        }

        // Re-render from the action itself; waiting for the next LIST_PROCESS tick would leave the
        // killed row on screen for a few seconds.
        runCatching { TopProcessesWidget().updateAll(context) }
    }

    companion object {
        val KEY_PID = ActionParameters.Key<Int>("pid")
        val KEY_CMDLINE = ActionParameters.Key<String>("cmdLine")
        val KEY_IS_APP = ActionParameters.Key<Boolean>("isApp")
    }
}

/** Tapping the header cycles RAM -> CPU -> NAME, matching the in-app sort options. */
class CycleSortAction : ActionCallback {

    override suspend fun onAction(
        context: Context,
        glanceId: GlanceId,
        parameters: ActionParameters
    ) {
        val next = WidgetCache.cycleSort()
        Log.i(TAG, "widget sort cycled to ${next.name}")

        // Ask for a fresh list so the new order appears immediately rather than on the next tick.
        if (isConnected) {
            runCatching {
                send_daemon_messages.emit(JSONObject().put("cmd", "LIST_PROCESS").toString())
            }
        }
        runCatching { TopProcessesWidget().updateAll(context) }
    }
}