package com.leaf.hyperdragshare.codex

import android.content.Context
import android.content.SharedPreferences
import android.os.Bundle
import java.io.IOException
import java.nio.charset.StandardCharsets
import java.util.concurrent.TimeUnit

/** Current-build injection handshake and root availability checks for the settings UI. */
internal object ModuleActivation {
    const val METHOD_REPORT_INJECTED = "report_injected"
    const val EXTRA_VERSION_CODE = "version_code"
    const val EXTRA_PORTAL_ROOT_GRANTED = "portal_root_granted"

    private const val MAX_OUTPUT_CHARS = 2048
    private const val PREFS_NAME = "module_activation"
    private const val KEY_INJECTED_VERSION = "injected_version"
    private const val KEY_PORTAL_ROOT_VERSION = "portal_root_version"
    private const val KEY_PORTAL_ROOT_GRANTED = "portal_root_granted"
    private const val COMMAND_TIMEOUT_SECONDS = 6L

    /** A first grant has to wait for the root manager prompt, which outlives a command timeout. */
    private const val ROOT_PROBE_TIMEOUT_SECONDS = 12L
    private const val PORTAL_SERVICE_COMMAND =
        "am startservice --user current " +
            "-a miui.intent.action.TEXT_CONTENT_EXTENSION " +
            "-n com.miui.contentextension/" +
            "com.miui.contentextension.services.TextContentExtensionService"
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
        if (!matchesCurrentBuild(reportedVersion)) {
            return
        }
        val editor = activationPreferences(moduleContext)
            .edit()
            .putLong(KEY_INJECTED_VERSION, reportedVersion)
        if (extras != null && extras.containsKey(EXTRA_PORTAL_ROOT_GRANTED)) {
            editor.putLong(KEY_PORTAL_ROOT_VERSION, reportedVersion)
                .putBoolean(
                    KEY_PORTAL_ROOT_GRANTED,
                    extras.getBoolean(EXTRA_PORTAL_ROOT_GRANTED),
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
     * Starts Taplus' own exported service so an already-running service receives
     * onStartCommand and a stopped service loads the current LSPosed hook.
     */
    fun requestPortalInjectionHandshake(): Boolean =
        runRootCommand(PORTAL_SERVICE_COMMAND, COMMAND_TIMEOUT_SECONDS)

    /** Whether a portal process is alive at all; only root may look at another app's processes. */
    fun isPortalRunning(): Boolean {
        val pids = runRootCommandOutput(PORTAL_PROCESS_COMMAND, COMMAND_TIMEOUT_SECONDS)
        return pids != null && pids.trim().isNotEmpty()
    }

    fun portalHandshakeCommand(): String = PORTAL_SERVICE_COMMAND

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
