package com.leaf.hyperdragshare.codex;

import android.content.Context;
import android.content.SharedPreferences;
import android.os.Bundle;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.TimeUnit;

/** Current-build injection handshake and root availability checks for the settings UI. */
final class ModuleActivation {
    static final String METHOD_REPORT_INJECTED = "report_injected";
    static final String EXTRA_VERSION_CODE = "version_code";
    static final String EXTRA_PORTAL_ROOT_GRANTED = "portal_root_granted";

    private static final int MAX_OUTPUT_CHARS = 2048;
    private static final String PREFS_NAME = "module_activation";
    private static final String KEY_INJECTED_VERSION = "injected_version";
    private static final String KEY_PORTAL_ROOT_VERSION = "portal_root_version";
    private static final String KEY_PORTAL_ROOT_GRANTED = "portal_root_granted";
    private static final long COMMAND_TIMEOUT_SECONDS = 6L;
    /** A first grant has to wait for the root manager prompt, which outlives a command timeout. */
    private static final long ROOT_PROBE_TIMEOUT_SECONDS = 12L;
    private static final String PORTAL_SERVICE_COMMAND =
            "am startservice --user current "
                    + "-a miui.intent.action.TEXT_CONTENT_EXTENSION "
                    + "-n com.miui.contentextension/"
                    + "com.miui.contentextension.services.TextContentExtensionService";
    private static final String PORTAL_PROCESS_COMMAND = "pidof com.miui.contentextension";
    private static final String PORTAL_BLACKLIST_ACTIVITY_COMMAND =
            "am start --user current -n com.miui.contentextension/"
                    + "com.miui.contentextension.setting.whitelist.BlacklistSettingActivity";
    private static boolean portalRootProbeInFlight;

    private ModuleActivation() {}

    static boolean reportInjected(Context portalContext) {
        Bundle extras = new Bundle();
        extras.putLong(EXTRA_VERSION_CODE, BuildConfig.VERSION_CODE);
        return reportPortalStatus(portalContext, extras);
    }

    /** Runs in the injected portal process so the root manager evaluates the portal UID. */
    static void probePortalRootAccessAsync(Context portalContext) {
        if (portalContext == null) {
            return;
        }
        final Context reportContext = portalContext.getApplicationContext() == null
                ? portalContext
                : portalContext.getApplicationContext();
        synchronized (ModuleActivation.class) {
            if (portalRootProbeInFlight) {
                return;
            }
            portalRootProbeInFlight = true;
        }
        new Thread(() -> {
            try {
                reportPortalRootAccess(reportContext, hasRootAccess());
            } finally {
                synchronized (ModuleActivation.class) {
                    portalRootProbeInFlight = false;
                }
            }
        }, "DragShare-PortalRootCheck").start();
    }

    private static boolean reportPortalRootAccess(Context portalContext, boolean rootGranted) {
        Bundle extras = new Bundle();
        extras.putLong(EXTRA_VERSION_CODE, BuildConfig.VERSION_CODE);
        extras.putBoolean(EXTRA_PORTAL_ROOT_GRANTED, rootGranted);
        return reportPortalStatus(portalContext, extras);
    }

    private static boolean reportPortalStatus(Context portalContext, Bundle extras) {
        if (portalContext == null) {
            return false;
        }
        try {
            portalContext.getContentResolver().call(
                    ImageStagingClient.BASE_URI,
                    METHOD_REPORT_INJECTED,
                    null,
                    extras);
            return true;
        } catch (Throwable ignored) {
            return false;
        }
    }

    static void recordInjected(Context moduleContext, Bundle extras) {
        long reportedVersion = extras == null
                ? -1L
                : extras.getLong(EXTRA_VERSION_CODE, -1L);
        if (!matchesCurrentBuild(reportedVersion)) {
            return;
        }
        SharedPreferences.Editor editor = activationPreferences(moduleContext)
                .edit()
                .putLong(KEY_INJECTED_VERSION, reportedVersion);
        if (extras != null && extras.containsKey(EXTRA_PORTAL_ROOT_GRANTED)) {
            editor.putLong(KEY_PORTAL_ROOT_VERSION, reportedVersion)
                    .putBoolean(
                            KEY_PORTAL_ROOT_GRANTED,
                            extras.getBoolean(EXTRA_PORTAL_ROOT_GRANTED));
        }
        editor.apply();
    }

    /** The settings UI observes these preferences so a portal report needs no polling. */
    static SharedPreferences activationPreferences(Context context) {
        return context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
    }

    static boolean isCurrentBuildInjected(Context context) {
        if (context == null) {
            return false;
        }
        return matchesCurrentBuild(
                activationPreferences(context).getLong(KEY_INJECTED_VERSION, -1L));
    }

    static boolean isCurrentBuildPortalRootGranted(Context context) {
        if (context == null) {
            return false;
        }
        SharedPreferences preferences = activationPreferences(context);
        return matchesCurrentBuild(preferences.getLong(KEY_PORTAL_ROOT_VERSION, -1L))
                && preferences.getBoolean(KEY_PORTAL_ROOT_GRANTED, false);
    }

    static boolean hasRootAccess() {
        return runRootCommand("id -u", ROOT_PROBE_TIMEOUT_SECONDS);
    }

    /**
     * Starts Taplus' own exported service so an already-running service receives
     * onStartCommand and a stopped service loads the current LSPosed hook.
     */
    static boolean requestPortalInjectionHandshake() {
        return runRootCommand(PORTAL_SERVICE_COMMAND, COMMAND_TIMEOUT_SECONDS);
    }

    /** Whether a portal process is alive at all; only root may look at another app's processes. */
    static boolean isPortalRunning() {
        String pids = runRootCommandOutput(PORTAL_PROCESS_COMMAND, COMMAND_TIMEOUT_SECONDS);
        return pids != null && !pids.trim().isEmpty();
    }

    static String portalHandshakeCommand() {
        return PORTAL_SERVICE_COMMAND;
    }

    /** Starts Taplus' non-exported blacklist activity as root for the current Android user. */
    static boolean openPortalBlacklistSettings() {
        return runRootCommand(portalBlacklistCommand(), COMMAND_TIMEOUT_SECONDS);
    }

    static String portalBlacklistCommand() {
        return PORTAL_BLACKLIST_ACTIVITY_COMMAND;
    }

    private static boolean runRootCommand(String command, long timeoutSeconds) {
        return runRootCommandOutput(command, timeoutSeconds) != null;
    }

    /** Returns the merged command output, or null when the command failed or timed out. */
    private static String runRootCommandOutput(String command, long timeoutSeconds) {
        java.lang.Process process = null;
        try {
            process = new ProcessBuilder("su", "-c", command)
                    .redirectErrorStream(true)
                    .start();
            if (!process.waitFor(timeoutSeconds, TimeUnit.SECONDS)) {
                process.destroyForcibly();
                return null;
            }
            // These commands print a couple of lines at most, so the pipe never blocks the
            // process before it exits and the buffered output is read afterwards.
            String output = readOutput(process);
            return process.exitValue() == 0 ? output : null;
        } catch (IOException | InterruptedException ignored) {
            if (ignored instanceof InterruptedException) {
                Thread.currentThread().interrupt();
            }
            return null;
        } finally {
            if (process != null) {
                try {
                    process.getInputStream().close();
                } catch (IOException ignored) {
                    // Process cleanup only.
                }
                try {
                    process.getOutputStream().close();
                } catch (IOException ignored) {
                    // Process cleanup only.
                }
                process.destroy();
            }
        }
    }

    private static String readOutput(java.lang.Process process) {
        StringBuilder output = new StringBuilder();
        byte[] buffer = new byte[512];
        InputStream stream = process.getInputStream();
        try {
            int read;
            while (output.length() < MAX_OUTPUT_CHARS && (read = stream.read(buffer)) > 0) {
                output.append(new String(buffer, 0, read, StandardCharsets.UTF_8));
            }
        } catch (IOException ignored) {
            // A closed stream still leaves the exit code to decide the result.
        }
        return output.toString();
    }

    static boolean matchesCurrentBuild(long reportedVersion) {
        return reportedVersion == BuildConfig.VERSION_CODE;
    }
}
