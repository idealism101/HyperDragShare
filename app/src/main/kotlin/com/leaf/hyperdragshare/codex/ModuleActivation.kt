package com.leaf.hyperdragshare.codex

import android.content.Context
import android.content.SharedPreferences
import android.content.pm.PackageManager
import android.os.Bundle
import java.io.IOException
import java.nio.charset.StandardCharsets
import java.util.concurrent.TimeUnit

/** Current-build injection handshake and root availability checks for the settings UI. */
internal object ModuleActivation {
    const val METHOD_REPORT_INJECTED = "report_injected"
    const val EXTRA_VERSION_CODE = "version_code"
    const val EXTRA_PORTAL_ROOT_GRANTED = "portal_root_granted"
    const val EXTRA_PORTAL_PID = "portal_pid"

    private const val MAX_OUTPUT_CHARS = 2048
    private const val PREFS_NAME = "module_activation"
    private const val KEY_INJECTED_VERSION = "injected_version"

    /**
     * The last report from any portal process, matching build or not. A report for another build
     * is not proof of injection, but it does name the process that is still running that build.
     */
    private const val KEY_LAST_REPORT_VERSION = "last_report_version"
    private const val KEY_LAST_REPORT_PID = "last_report_pid"
    private const val KEY_PORTAL_ROOT_VERSION = "portal_root_version"
    private const val KEY_PORTAL_ROOT_GRANTED = "portal_root_granted"

    /** Bumped by every root report, so a caller can tell a fresh answer from the stored one. */
    private const val KEY_PORTAL_ROOT_SEQUENCE = "portal_root_sequence"
    private const val COMMAND_TIMEOUT_SECONDS = 6L

    /** A first grant has to wait for the root manager prompt, which outlives a command timeout. */
    private const val ROOT_PROBE_TIMEOUT_SECONDS = 12L
    private const val PORTAL_SERVICE_COMMAND =
        "am startservice --user current " +
            "-a miui.intent.action.TEXT_CONTENT_EXTENSION " +
            "-n com.miui.contentextension/" +
            "com.miui.contentextension.services.TextContentExtensionService"

    /**
     * HyperOS applies the background start rule to a root shell as well, so `am startservice`
     * answers `Error: app is in background` (exit 255) whenever the portal has no process -- which
     * is exactly when the handshake is needed. Reading the portal's own switch provider starts the
     * process without that rule and without any UI: publishing a provider runs the portal
     * Application, and `Instrumentation.callApplicationOnCreate` is where the hook reports the
     * build it loaded. The query itself reads nothing of interest and changes no portal state.
     */
    private const val PORTAL_PROVIDER_COMMAND =
        "content query --uri content://com.miui.contentextension.provider.switchcontrolprovider"
    private const val PORTAL_PROCESS_COMMAND = "pidof com.miui.contentextension"
    private const val PORTAL_BLACKLIST_ACTIVITY_COMMAND =
        "am start --user current -n com.miui.contentextension/" +
            "com.miui.contentextension.setting.whitelist.BlacklistSettingActivity"

    private val lock = Any()
    private var portalRootProbeInFlight = false

    fun reportInjected(portalContext: Context?): Boolean {
        val extras = Bundle()
        extras.putLong(EXTRA_VERSION_CODE, BuildConfig.VERSION_CODE.toLong())
        return reportPortalStatus(portalContext, extras)
    }

    /** Runs in the injected portal process so the root manager evaluates the portal UID. */
    fun probePortalRootAccessAsync(portalContext: Context?) {
        if (portalContext == null) {
            return
        }
        val reportContext = portalContext.applicationContext ?: portalContext
        synchronized(lock) {
            if (portalRootProbeInFlight) {
                return
            }
            portalRootProbeInFlight = true
        }
        Thread({
            try {
                reportPortalRootAccess(reportContext, hasRootAccess())
            } finally {
                synchronized(lock) {
                    portalRootProbeInFlight = false
                }
            }
        }, "DragShare-PortalRootCheck").start()
    }

    private fun reportPortalRootAccess(portalContext: Context?, rootGranted: Boolean): Boolean {
        val extras = Bundle()
        extras.putLong(EXTRA_VERSION_CODE, BuildConfig.VERSION_CODE.toLong())
        extras.putBoolean(EXTRA_PORTAL_ROOT_GRANTED, rootGranted)
        return reportPortalStatus(portalContext, extras)
    }

    private fun reportPortalStatus(portalContext: Context?, extras: Bundle): Boolean {
        if (portalContext == null) {
            return false
        }
        // Every report names the process it came from, including the root one: the module needs it
        // to tell a portal that is still running an older build from one that is not running.
        extras.putInt(EXTRA_PORTAL_PID, android.os.Process.myPid())
        return try {
            portalContext.contentResolver.call(
                ImageStagingClient.BASE_URI,
                METHOD_REPORT_INJECTED,
                null,
                extras,
            )
            true
        } catch (_: Throwable) {
            false
        }
    }

    fun recordInjected(moduleContext: Context, extras: Bundle?) {
        val reportedVersion = extras?.getLong(EXTRA_VERSION_CODE, -1L) ?: -1L
        if (reportedVersion > 0L) {
            // Kept for every report: a portal still running an older build is the difference
            // between 旧版本仍在运行 and 未注入, and only the report names its process.
            activationPreferences(moduleContext)
                .edit()
                .putLong(KEY_LAST_REPORT_VERSION, reportedVersion)
                .putInt(KEY_LAST_REPORT_PID, extras?.getInt(EXTRA_PORTAL_PID, -1) ?: -1)
                .apply()
        }
        if (!matchesCurrentBuild(reportedVersion)) {
            return
        }
        val editor = activationPreferences(moduleContext)
            .edit()
            .putLong(KEY_INJECTED_VERSION, reportedVersion)
        if (extras != null && extras.containsKey(EXTRA_PORTAL_ROOT_GRANTED)) {
            val preferences = activationPreferences(moduleContext)
            editor.putLong(KEY_PORTAL_ROOT_VERSION, reportedVersion)
                .putBoolean(
                    KEY_PORTAL_ROOT_GRANTED,
                    extras.getBoolean(EXTRA_PORTAL_ROOT_GRANTED),
                )
                .putLong(
                    KEY_PORTAL_ROOT_SEQUENCE,
                    preferences.getLong(KEY_PORTAL_ROOT_SEQUENCE, 0L) + 1L,
                )
        }
        editor.apply()
    }

    /** The settings UI observes these preferences so a portal report needs no polling. */
    fun activationPreferences(context: Context): SharedPreferences =
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    fun isCurrentBuildInjected(context: Context?): Boolean {
        if (context == null) {
            return false
        }
        return matchesCurrentBuild(
            activationPreferences(context).getLong(KEY_INJECTED_VERSION, -1L),
        )
    }

    /**
     * Whether the portal answered the root question for this build at all, whatever the answer.
     * Only the portal process can probe its own grant, so a denied grant is a real answer that
     * the UI must not keep waiting on.
     */
    /**
     * Identifies the latest root report. The grant lives in the root manager and the user may
     * change it at any time, so the answer is only known to be current while this value keeps
     * moving; comparing it around a re-probe request tells a fresh report from the stored one.
     */
    fun portalRootReportSequence(context: Context?): Long {
        if (context == null) {
            return 0L
        }
        return activationPreferences(context).getLong(KEY_PORTAL_ROOT_SEQUENCE, 0L)
    }

    /**
     * Asks a live portal process to probe its root grant again. The portal already observes the
     * settings URI, and only the portal itself can be told apart from the module by the root
     * manager, so this notification is the whole request; the answer arrives as another report.
     */
    fun requestPortalRootReprobe(context: Context): Boolean = try {
        context.contentResolver.notifyChange(DragShareSettings.settingsUri(), null)
        true
    } catch (_: Throwable) {
        false
    }

    fun hasCurrentBuildPortalRootReport(context: Context?): Boolean {
        if (context == null) {
            return false
        }
        return matchesCurrentBuild(
            activationPreferences(context).getLong(KEY_PORTAL_ROOT_VERSION, -1L),
        )
    }

    fun isCurrentBuildPortalRootGranted(context: Context?): Boolean {
        if (context == null) {
            return false
        }
        val preferences = activationPreferences(context)
        return matchesCurrentBuild(preferences.getLong(KEY_PORTAL_ROOT_VERSION, -1L)) &&
            preferences.getBoolean(KEY_PORTAL_ROOT_GRANTED, false)
    }

    fun hasRootAccess(): Boolean = runRootCommand("id -u", ROOT_PROBE_TIMEOUT_SECONDS)

    /**
     * Whether the root manager grants the portal's UID, asked from this process by dropping to
     * that UID and letting `su` decide there. The grant belongs to the UID, so the answer is the
     * same one the portal would get, but it needs no portal process and no report round trip.
     *
     * Returns null when the answer would not mean anything -- the UID is unknown, root is not
     * granted here, or the drop itself did not happen because this root solution spells `su` for
     * another user differently -- so the caller can fall back to the portal's own report.
     */
    fun probePortalRootGrant(context: Context?): Boolean? {
        val uid = portalUid(context) ?: return null
        // `|| true` keeps a refused inner `su` from failing the whole command: a refusal is the
        // answer, not an error. The echoed UID proves the drop happened before it.
        val output = runRootCommandOutput(
            "su $uid -c 'id -u; su -c id -u || true'",
            COMMAND_TIMEOUT_SECONDS,
        ) ?: return null
        val lines = output.lineSequence().map { it.trim() }.filter { it.isNotEmpty() }.toList()
        if (lines.firstOrNull() != uid.toString()) {
            return null
        }
        return lines.drop(1).contains("0")
    }

    private fun portalUid(context: Context?): Int? {
        if (context == null) {
            return null
        }
        return try {
            context.packageManager.getApplicationInfo(PORTAL_PACKAGE, 0).uid
        } catch (_: PackageManager.NameNotFoundException) {
            null
        }
    }

    /**
     * Makes sure a portal process exists so its hook can report the loaded build. Returns false
     * when neither way in was allowed, which means waiting for a report would time out for
     * nothing. The service start is kept as the second attempt for ROMs that answer the provider
     * query but not the background service start, or the other way round.
     */
    fun requestPortalInjectionHandshake(): Boolean =
        runRootCommand(PORTAL_PROVIDER_COMMAND, COMMAND_TIMEOUT_SECONDS) ||
            runRootCommand(PORTAL_SERVICE_COMMAND, COMMAND_TIMEOUT_SECONDS)

    /** Whether a portal process is alive at all; only root may look at another app's processes. */
    fun isPortalRunning(): Boolean = portalPids().isNotEmpty()

    /** The live portal process ids, empty when the portal is not running or root refused. */
    fun portalPids(): List<Int> {
        val pids = runRootCommandOutput(PORTAL_PROCESS_COMMAND, COMMAND_TIMEOUT_SECONDS)
            ?: return emptyList()
        return pids.split(Regex("\\s+"))
            .mapNotNull { it.trim().toIntOrNull() }
    }

    /**
     * The process id of a portal that reported a build other than the running one, or null when
     * the last report was for this build or there was none. A portal process loads the module its
     * process started with, so such a process stays on the old code until it is restarted.
     */
    fun staleReportPid(context: Context?): Int? {
        if (context == null) {
            return null
        }
        val preferences = activationPreferences(context)
        val version = preferences.getLong(KEY_LAST_REPORT_VERSION, -1L)
        if (version <= 0L || matchesCurrentBuild(version)) {
            return null
        }
        return preferences.getInt(KEY_LAST_REPORT_PID, -1).takeIf { it > 0 }
    }

    fun portalHandshakeCommand(): String = PORTAL_SERVICE_COMMAND

    fun portalSpawnCommand(): String = PORTAL_PROVIDER_COMMAND

    /** Starts Taplus' non-exported blacklist activity as root for the current Android user. */
    fun openPortalBlacklistSettings(): Boolean =
        runRootCommand(portalBlacklistCommand(), COMMAND_TIMEOUT_SECONDS)

    fun portalBlacklistCommand(): String = PORTAL_BLACKLIST_ACTIVITY_COMMAND

    private fun runRootCommand(command: String, timeoutSeconds: Long): Boolean =
        runRootCommandOutput(command, timeoutSeconds) != null

    /** Returns the merged command output, or null when the command failed or timed out. */
    private fun runRootCommandOutput(command: String, timeoutSeconds: Long): String? {
        var process: Process? = null
        try {
            process = ProcessBuilder("su", "-c", command)
                .redirectErrorStream(true)
                .start()
            if (!process.waitFor(timeoutSeconds, TimeUnit.SECONDS)) {
                process.destroyForcibly()
                return null
            }
            // These commands print a couple of lines at most, so the pipe never blocks the
            // process before it exits and the buffered output is read afterwards.
            val output = readOutput(process)
            return if (process.exitValue() == 0) output else null
        } catch (_: IOException) {
            return null
        } catch (_: InterruptedException) {
            Thread.currentThread().interrupt()
            return null
        } finally {
            val started = process
            if (started != null) {
                try {
                    started.inputStream.close()
                } catch (_: IOException) {
                    // Process cleanup only.
                }
                try {
                    started.outputStream.close()
                } catch (_: IOException) {
                    // Process cleanup only.
                }
                started.destroy()
            }
        }
    }

    private fun readOutput(process: Process): String {
        val output = StringBuilder()
        val buffer = ByteArray(512)
        val stream = process.inputStream
        try {
            while (output.length < MAX_OUTPUT_CHARS) {
                val read = stream.read(buffer)
                if (read <= 0) {
                    break
                }
                output.append(String(buffer, 0, read, StandardCharsets.UTF_8))
            }
        } catch (_: IOException) {
            // A closed stream still leaves the exit code to decide the result.
        }
        return output.toString()
    }

    fun matchesCurrentBuild(reportedVersion: Long): Boolean =
        reportedVersion == BuildConfig.VERSION_CODE.toLong()
}
