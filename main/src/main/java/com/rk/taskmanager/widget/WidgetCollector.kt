package com.rk.taskmanager.widget

import android.app.ActivityManager
import android.content.Context
import android.util.Log
import com.rk.taskmanager.ProcessListParser
import com.rk.taskmanager.daemon.daemon_messages
import com.rk.taskmanager.screens.getApkNameFromPackage
import com.rk.taskmanager.screens.isAppInstalled
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import org.json.JSONArray
import java.util.concurrent.ConcurrentHashMap

private const val TAG = "WidgetCollector"

/**
 * Upper bound on the pid -> label cache.
 *
 * Labels are re-resolved for every process on each poll, so this only needs to be large enough
 * that the wholesale reset in [resolve] is rare. Pids churn constantly, hence the bound, but a low
 * ceiling would mean re-resolving every label every few seconds on a busy device.
 */
private const val LABEL_CACHE_LIMIT = 2000

/**
 * Translates daemon messages into [WidgetCache] state for the home screen widgets.
 *
 * This is an additional collector on the shared `daemon_messages` flow alongside the in-app ones.
 * SharedFlow broadcasts to every collector, so this does not interfere with the graphs or the
 * in-app process list.
 */
object WidgetCollector {

    private data class Resolved(val label: String, val isApp: Boolean)

    private val labelCache = ConcurrentHashMap<String, Resolved>()

    fun start(scope: CoroutineScope, context: Context) {
        val app = context.applicationContext
        scope.launch(Dispatchers.IO) {
            daemon_messages.collect { message ->
                runCatching { handle(scope, app, message) }
                    .onFailure { Log.w(TAG, "dropping daemon message: ${it.message}") }
            }
        }
    }

    private suspend fun handle(scope: CoroutineScope, context: Context, message: String) {
        val json = org.json.JSONObject(message)
        when (json.optString("type")) {
            "CPU_USAGE" -> WidgetCache.updateCpu(json.optInt("usage", -1))
            "GPU_USAGE" -> WidgetCache.updateGpu(json.optInt("usage", -1))
            // PROCESS_LIST is large and label resolution hits the package manager, so it runs off
            // the collector's critical path and cannot stall the cheap metric updates behind it.
            "PROCESS_LIST" -> scope.launch(Dispatchers.IO) {
                runCatching {
                    WidgetCache.updateProcs(topRows(context, json.getJSONArray("processes")))
                }.onFailure { Log.w(TAG, "failed to build top rows: ${it.message}") }
            }
        }
    }

    private suspend fun topRows(context: Context, jsonArray: JSONArray): List<WidgetProcRow> {
        val rows = ProcessListParser.parse(jsonArray, context.packageName)
            // Drop kernel threads and init: they have no cmdline worth showing and would otherwise
            // dominate a RAM sort with 0-name entries.
            .filter { it.pid > 1 && it.cmdLine.isNotBlank() && it.pid != it.parentPid }
            .map { proc ->
                val resolved = resolve(context, proc.cmdLine, proc.name)
                WidgetProcRow(
                    pid = proc.pid,
                    cmdLine = proc.cmdLine,
                    label = resolved.label,
                    cpu = proc.cpuUsage,
                    ramKb = proc.memoryUsageKb,
                    isApp = resolved.isApp
                )
            }

        return selectTopRows(rows, WidgetCache.sort.value)
    }

    private fun resolve(context: Context, key: String, fallback: String): Resolved {
        labelCache[key]?.let { return it }

        val resolved = runCatching {
            val installed = isAppInstalled(context, key)
            val label = if (installed) getApkNameFromPackage(context, key) ?: fallback else fallback
            Resolved(label, installed)
        }.getOrElse { Resolved(fallback, false) }

        // Pids churn constantly, so bound the cache rather than letting it grow without limit.
        if (labelCache.size >= LABEL_CACHE_LIMIT) labelCache.clear()
        labelCache[key] = resolved
        return resolved
    }

    /**
     * Reads RAM from [ActivityManager], mirroring the in-app path. Kept separate from
     * `getSystemRamUsage` so the widget does not depend on the screen-local Compose globals.
     */
    fun readRam(context: Context) {
        runCatching {
            val am = context.getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager
            val info = ActivityManager.MemoryInfo()
            am.getMemoryInfo(info)
            val total = info.totalMem
            val used = total - info.availMem
            val pct = if (total > 0L) ((used.toDouble() / total.toDouble()) * 100).toInt() else 0
            WidgetCache.updateRam(pct, used, total)
        }.onFailure { Log.w(TAG, "failed to read ram: ${it.message}") }
    }
}