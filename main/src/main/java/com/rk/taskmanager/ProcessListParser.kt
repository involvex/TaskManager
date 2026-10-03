package com.rk.taskmanager

import org.json.JSONArray

/**
 * Parses a `PROCESS_LIST` payload from the native daemon into [ProcessViewModel.Process] models.
 *
 * Shared by [ProcessViewModel] and the home screen widget collector so both agree on how a daemon
 * payload maps to a model. Keeping one copy avoids the two paths drifting apart.
 */
object ProcessListParser {

    fun parse(jsonArray: JSONArray, myPkg: String): List<ProcessViewModel.Process> {
        val out = ArrayList<ProcessViewModel.Process>(jsonArray.length())
        for (i in 0 until jsonArray.length()) {
            val obj = jsonArray.getJSONObject(i)
            val cmdLine = obj.optString("cmdLine", "")
            if (cmdLine == myPkg) continue
            out.add(
                ProcessViewModel.Process(
                    name = obj.optString("name", ""),
                    nice = obj.optInt("nice", 0),
                    pid = obj.optInt("pid", 0),
                    uid = obj.optInt("uid", 0),
                    cpuUsage = obj.optDouble("cpuUsage", 0.0).toFloat(),
                    parentPid = obj.optInt("parentPid", 0),
                    isForeground = obj.optBoolean("isForeground", false),
                    memoryUsageKb = obj.optLong("memoryUsageKb", 0L),
                    cmdLine = cmdLine,
                    state = obj.optString("state", ""),
                    threads = obj.optInt("threads", 0),
                    startTime = obj.optLong("startTime", 0L),
                    elapsedTime = obj.optDouble("elapsedTime", 0.0).toFloat(),
                    residentSetSizeKb = obj.optLong("residentSetSizeKb", 0L),
                    virtualMemoryKb = obj.optLong("virtualMemoryKb", 0L),
                    cgroup = obj.optString("cgroup", ""),
                    executablePath = obj.optString("executablePath", "")
                )
            )
        }
        return out
    }
}