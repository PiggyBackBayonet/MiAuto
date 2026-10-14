package com.andrerinas.openheadunit.utils

import android.content.Context
import android.os.Build
import java.util.ArrayDeque

/** Small, shareable report. Never exports saved credentials or raw protocol payloads. */
object DiagnosticReport {
    fun build(context: Context): String {
        val settings = Settings(context)
        val ssid = settings.hotspotSsid
        val password = settings.hotspotPassword
        val lines = ArrayDeque<String>()
        fun accept(line: String) {
            DiagnosticReportRedactor.redact(line, ssid, password)?.let {
                if (lines.size == 6000) lines.removeFirst()
                lines.addLast(it)
            }
        }
        val history = DiagnosticJournal.snapshot()
        val source = if (settings.logSource == Settings.LogSource.APPLOG_FILE)
            AppLog.currentLogFile ?: AppLog.lastLogFile else null
        if (history.isNotEmpty()) {
            history.forEach(::accept)
        } else if (source?.isFile == true) {
            source.useLines { it.forEach(::accept) }
        } else {
            val process = ProcessBuilder("logcat", "-d", "-v", "threadtime", "OPENHU:V", "*:S")
                .redirectErrorStream(true).start()
            try {
                process.inputStream.bufferedReader().useLines { it.forEach(::accept) }
                check(process.waitFor() == 0) { "Could not read the app logs" }
            } finally { process.destroy() }
        }
        return buildString {
            appendLine("DiAuto ${context.packageManager.getPackageInfo(context.packageName, 0).versionName} · diagnostic report")
            appendLine("Android=${Build.VERSION.RELEASE}; API=${Build.VERSION.SDK_INT}")
            appendLine("Head unit: ${Build.MANUFACTURER} ${Build.MODEL}; board=${Build.BOARD}; build=${Build.DISPLAY}")
            appendLine("Wireless mode=${settings.wifiConnectionMode}; native transport=${settings.nativeApTransport}")
            appendLine("Hotspot auto-enable=${settings.autoEnableHotspot}; hotspot state=${SoftApStateReader.read(context)}")
            appendLine("Hotspot name saved=${settings.hotspotSsid.isNotBlank()}; password saved=${settings.hotspotPassword.isNotEmpty()}")
            appendLine("Logging level=${settings.exporterLogLevel}; source=${settings.logSource}")
            appendLine("Saved credentials, network addresses and protocol payloads are omitted.")
            appendLine("--- Recent app logs (up to 6000 lines) ---")
            if (lines.isEmpty()) appendLine("No recent app logs available. Reproduce the issue and save a new report.")
            lines.forEach { appendLine(it) }

            // The ICCOA SDK does its own logging under its own tags (com.easy.logger, com.share.connect,
            // WifiP2pManager, …). Those never reach our OPENHU tag, yet they are the only place the
            // SDK's own failure reasons ("卡在 10%" 之后到底哪一步断了) are written down. Both the SDK
            // and this app run in the same process, so a same-PID dump is both permitted and complete.
            appendLine("--- ICCOA SDK logs, same process, all tags (up to 4000 lines) ---")
            val sdkLogs = captureSameProcessLogs()
            if (sdkLogs.isEmpty()) appendLine("No SDK logs captured (logcat unavailable or empty).")
            sdkLogs.forEach { appendLine(it) }
        }
    }

    /**
     * Dumps everything this process wrote to logcat, whatever the tag.
     *
     * Prefers `--pid` (API 24+) and falls back to scanning a full dump for our PID, because a
     * missing `--pid` must degrade into "less detail", never into "no report". Lines already
     * captured above under the app's own tag are skipped so the report stays readable.
     */
    private fun captureSameProcessLogs(): List<String> {
        val pid = android.os.Process.myPid()
        val out = ArrayDeque<String>()
        fun accept(line: String) {
            if (line.contains("OPENHU")) return
            if (out.size == 4000) out.removeFirst()
            out.addLast(line)
        }
        val attempts = listOf(
            listOf("logcat", "-d", "-v", "threadtime", "--pid=$pid"),
            listOf("logcat", "-d", "-v", "threadtime"),
        )
        for (cmd in attempts) {
            val collected = ArrayDeque<String>()
            try {
                val process = ProcessBuilder(cmd).redirectErrorStream(true).start()
                try {
                    val wantsPidFilter = cmd.size == 5
                    val pidPattern = Regex("^\\d{2}-\\d{2} \\d{2}:\\d{2}:\\d{2}\\.\\d{3}\\s+$pid\\s")
                    process.inputStream.bufferedReader().useLines { seq ->
                        for (line in seq) {
                            if (wantsPidFilter || pidPattern.containsMatchIn(line)) {
                                if (collected.size == 4000) collected.removeFirst()
                                collected.addLast(line)
                            }
                        }
                    }
                    process.waitFor()
                } finally {
                    process.destroy()
                }
            } catch (_: Throwable) {
                continue
            }
            if (collected.isNotEmpty()) {
                collected.forEach(::accept)
                return out.toList()
            }
        }
        return out.toList()
    }
}
