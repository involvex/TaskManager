package com.rk.taskmanager.daemon

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import org.json.JSONObject
import java.util.concurrent.atomic.AtomicInteger
import kotlin.time.Duration.Companion.milliseconds

private val reqCounter = AtomicInteger(1)

/** Monotonic request id used to correlate a daemon reply with the request that caused it. */
fun nextReqId(): Int = reqCounter.getAndIncrement()

/**
 * Sends [cmd] and waits for the daemon's reply of [replyType].
 *
 * Replies are matched on the echoed `reqId` rather than on arrival order. Several requests can be
 * in flight at once (the process widget fires kills from independent taps), so matching on type
 * alone would let one caller consume another caller's reply.
 *
 * Returns null if the daemon is unreachable or does not answer within [timeoutMs].
 */
suspend fun requestDaemon(
    cmd: JSONObject,
    replyType: String,
    timeoutMs: Long = 3000L
): JSONObject? = withContext(Dispatchers.IO) {
    runCatching {
        withTimeout(timeoutMs.milliseconds) {
            val reqId = cmd.optInt("reqId", 0)
            val waiter = async {
                daemon_messages.first { message ->
                    runCatching {
                        val json = JSONObject(message)
                        json.optString("type") == replyType &&
                            (reqId == 0 || json.optInt("reqId", 0) == reqId)
                    }.getOrDefault(false)
                }
            }
            send_daemon_messages.emit(cmd.toString())
            JSONObject(waiter.await())
        }
    }.onFailure {
        android.util.Log.w("DaemonRequest", "request '$replyType' failed: ${it.message}")
    }.getOrNull()
}

/**
 * Kills a process, using `am force-stop` when it belongs to an installed package (matching the
 * behaviour of the in-app kill) and a raw `SIGKILL` otherwise.
 */
suspend fun killByPidOrPackage(pid: Int, packageName: String, isApp: Boolean): Boolean {
    val cmd = JSONObject().apply {
        put("reqId", nextReqId())
        if (isApp) {
            put("cmd", "FORCE_STOP")
            put("pkg", packageName)
        } else {
            put("cmd", "KILL")
            put("pid", pid)
        }
    }
    return requestDaemon(cmd, "KILL_RESULT")?.optBoolean("success", false) ?: false
}