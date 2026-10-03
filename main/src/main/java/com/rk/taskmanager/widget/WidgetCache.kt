package com.rk.taskmanager.widget

import androidx.compose.runtime.Immutable
import com.rk.commons.settings.Settings
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlin.math.roundToInt

/** How long a killed row stays greyed out. */
private const val FLASH_MS = 2000L

/** Live CPU / RAM / GPU percentages for the vitals widget. `-1` means "not reported by the daemon". */
@Immutable
data class WidgetVitals(
    val connected: Boolean = false,
    val cpu: Int = -1,
    val ram: Int = -1,
    val ramUsed: Long = 0L,
    val ramTotal: Long = 0L,
    val gpu: Int = -1
)

/** One rendered row in the top-processes widget. */
@Immutable
data class WidgetProcRow(
    val pid: Int,
    val cmdLine: String,
    val label: String,
    val cpu: Float,
    val ramKb: Long,
    val isApp: Boolean
)

/** Sort order for the top-processes widget. Ids line up with [ProcessViewModel.Sortby]. */
enum class WidgetSort(val id: Int, val label: String) {
    RAM(0, "RAM"),
    CPU(1, "CPU"),
    NAME(2, "NAME");

    fun next(): WidgetSort = entries[(ordinal + 1) % entries.size]

    companion object {
        fun from(id: Int): WidgetSort = entries.firstOrNull { it.id == id } ?: RAM
    }
}

/**
 * In-memory state shared between [WidgetUpdateService] (the only writer) and the Glance
 * composables (read-only).
 *
 * Both live in the app process, so no persistence is needed: Glance's `provideGlance` runs in the
 * same process as the service. If the process dies the launcher keeps showing the last rendered
 * frame until the service restarts.
 */
object WidgetCache {

    private val _vitals = MutableStateFlow(WidgetVitals())
    val vitals: StateFlow<WidgetVitals> = _vitals.asStateFlow()

    private val _procs = MutableStateFlow<List<WidgetProcRow>>(emptyList())
    val procs: StateFlow<List<WidgetProcRow>> = _procs.asStateFlow()

    private val _sort = MutableStateFlow(WidgetSort.from(Settings.widgetProcSort))
    val sort: StateFlow<WidgetSort> = _sort.asStateFlow()

    /** Pids whose row should be greyed out to acknowledge a kill. */
    private val _killedPids = MutableStateFlow<Set<Int>>(emptySet())
    val killedPids: StateFlow<Set<Int>> = _killedPids.asStateFlow()

    fun updateConnected(connected: Boolean) {
        _vitals.value = _vitals.value.copy(connected = connected)
    }

    fun updateCpu(percent: Int) {
        _vitals.value = _vitals.value.copy(cpu = percent.coerceIn(0, 100))
    }

    fun updateGpu(percent: Int) {
        // The daemon returns -1 when no GPU busy node is readable on this device; keep that
        // distinct from a genuine 0%.
        _vitals.value = _vitals.value.copy(gpu = percent)
    }

    fun updateRam(percent: Int, used: Long, total: Long) {
        _vitals.value = _vitals.value.copy(
            ram = percent.coerceIn(0, 100),
            ramUsed = used,
            ramTotal = total
        )
    }

    fun updateProcs(rows: List<WidgetProcRow>) {
        _procs.value = rows
    }

    /** Cycles RAM -> CPU -> NAME -> RAM and persists the choice. */
    fun cycleSort(): WidgetSort {
        val next = _sort.value.next()
        Settings.widgetProcSort = next.id
        _sort.value = next
        return next
    }

    fun markKilled(pid: Int) {
        _killedPids.value = _killedPids.value + pid
    }

    fun clearKilled(pid: Int) {
        _killedPids.value = _killedPids.value - pid
    }

    /**
     * Greys a row out briefly to acknowledge a kill.
     *
     * The expiry runs on [flashScope] rather than the caller's scope: a Glance action session is
     * torn down shortly after `onAction` returns, and a cancelled coroutine would leave the row
     * greyed until the next process death.
     */
    fun flashKilled(pid: Int, ms: Long = FLASH_MS) {
        markKilled(pid)
        flashScope.launch {
            delay(ms)
            clearKilled(pid)
        }
    }

    private val flashScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
}

/**
 * Rows shown by the process widget.
 *
 * `RemoteViews` caps how many views a widget may inflate, so a longer list would risk
 * `TransactionTooLargeException` rather than simply scrolling.
 */
const val MAX_WIDGET_ROWS = 5

/** Formats a byte count as a compact size string, e.g. `1.2G`, `340M`. */
fun formatBytes(bytes: Long): String {
    if (bytes <= 0L) return "--"
    val mb = bytes / (1024.0 * 1024.0)
    val gb = bytes / (1024.0 * 1024.0 * 1024.0)
    return when {
        gb >= 1.0 -> "${(gb * 10).roundToInt() / 10.0}G"
        mb >= 1.0 -> "${mb.roundToInt()}M"
        else -> "${(bytes / 1024.0).roundToInt()}K"
    }
}

/** Formats a KiB figure (as reported by the daemon) as a compact size string. */
fun formatKb(kb: Long): String = formatBytes(kb * 1024L)

/**
 * Sorts and truncates to the rows the widget shows.
 *
 * Pure and free of Android dependencies so the ordering can be unit tested directly. The
 * comparison keys mirror the in-app process list: RAM by `memoryUsageKb`, CPU by `cpuUsage`, name
 * by lowercased label.
 */
fun selectTopRows(
    rows: List<WidgetProcRow>,
    sort: WidgetSort,
    limit: Int = MAX_WIDGET_ROWS
): List<WidgetProcRow> {
    val sorted = when (sort) {
        WidgetSort.CPU -> rows.sortedByDescending { it.cpu }
        WidgetSort.NAME -> rows.sortedBy { it.label.lowercase() }
        WidgetSort.RAM -> rows.sortedByDescending { it.ramKb }
    }
    return sorted.take(limit)
}