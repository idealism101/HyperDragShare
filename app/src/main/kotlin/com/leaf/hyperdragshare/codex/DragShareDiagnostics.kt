package com.leaf.hyperdragshare.codex

import android.app.Application
import android.content.Context
import android.os.Build
import java.io.ByteArrayOutputStream
import java.io.IOException
import java.io.InputStream
import java.util.Arrays
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.atomic.AtomicReference
import kotlin.math.min

/** Debug-only environment and input diagnostics. It deliberately excludes captured content. */
object DragShareDiagnostics {
    private const val TAG = "DragShare/Diagnostics"
    private const val MAX_COMMAND_OUTPUT_CHARS = 48 * 1024
    private const val COMMAND_TIMEOUT_MS = 5_000L
    private val EXECUTOR: ExecutorService = Executors.newSingleThreadExecutor { runnable ->
        val thread = Thread(runnable, "drag-share-diagnostics")
        thread.isDaemon = true
        thread
    }
    private val RUNTIME_CAPTURED_DESTINATION = AtomicInteger(Integer.MIN_VALUE)
    private val INPUT_INVENTORY_CAPTURED_DESTINATION = AtomicInteger(Integer.MIN_VALUE)

    @JvmStatic
    fun captureRuntimeOnce(context: Context?, reason: String?, frameworkInfo: String?) {
        if (context == null || !DragShareLog.isDebugEnabled() ||
            !markRuntimeDestinationCaptured()
        ) {
            return
        }
        val applicationContext = context.applicationContext ?: context
        EXECUTOR.execute { captureRuntime(applicationContext, reason, frameworkInfo) }
    }

    @JvmStatic
    fun captureInputInventory(
        context: Context?,
        reason: String?,
        discoveredDevices: String?,
        selectedDeviceInfo: String?,
    ) {
        if (context == null || !DragShareLog.isDebugEnabled() ||
            !markInputInventoryDestinationCaptured()
        ) {
            return
        }
        val devices = trimForLog(discoveredDevices)
        val selected = trimForLog(selectedDeviceInfo)
        EXECUTOR.execute {
            DragShareLog.d(TAG, "input inventory begin reason=" + safe(reason))
            if (devices.isNotEmpty()) {
                DragShareLog.d(TAG, "input devices discovered by caller:\n" + devices)
            }
            if (selected.isNotEmpty()) {
                DragShareLog.d(TAG, "selected input capability dump:\n" + selected)
            }
            DragShareLog.d(
                TAG,
                "root ls -l /dev/input:\n" +
                    runRootCommand("ls -ld /dev/input; ls -l /dev/input"),
            )
            DragShareLog.d(
                TAG,
                "root /proc/bus/input/devices:\n" +
                    runRootCommand("cat /proc/bus/input/devices"),
            )
            if (selected.isEmpty()) {
                DragShareLog.d(TAG, "root getevent -lp:\n" + runRootCommand("getevent -lp"))
            }
            DragShareLog.d(TAG, "input inventory end")
        }
    }

    @JvmStatic
    fun captureInputFailure(
        context: Context?,
        reason: String?,
        discoveredDevices: String?,
        selectedDeviceInfo: String?,
    ) {
        if (context == null || !DragShareLog.isDebugEnabled()) {
            return
        }
        val devices = trimForLog(discoveredDevices)
        val selected = trimForLog(selectedDeviceInfo)
        EXECUTOR.execute {
            DragShareLog.d(TAG, "input failure reason=" + safe(reason))
            if (devices.isNotEmpty()) {
                DragShareLog.d(TAG, "input devices at failure:\n" + devices)
            }
            if (selected.isNotEmpty()) {
                DragShareLog.d(TAG, "selected device at failure:\n" + selected)
            }
            DragShareLog.d(
                TAG,
                "root input capabilities at failure:\n" + runRootCommand("getevent -lp"),
            )
        }
    }

    private fun captureRuntime(context: Context, reason: String?, frameworkInfo: String?) {
        DragShareLog.d(TAG, "runtime diagnostic begin reason=" + safe(reason))
        DragShareLog.d(
            TAG,
            "module=" + BuildConfig.VERSION_NAME + " (" +
                BuildConfig.VERSION_CODE + ") package=" + context.packageName +
                " process=" + Application.getProcessName() +
                " uid=" + android.os.Process.myUid(),
        )
        DragShareLog.d(
            TAG,
            "device manufacturer=" + safe(Build.MANUFACTURER) +
                " brand=" + safe(Build.BRAND) +
                " model=" + safe(Build.MODEL) +
                " device=" + safe(Build.DEVICE) +
                " product=" + safe(Build.PRODUCT),
        )
        DragShareLog.d(
            TAG,
            "system release=" + safe(Build.VERSION.RELEASE) +
                " sdk=" + Build.VERSION.SDK_INT +
                " incremental=" + safe(Build.VERSION.INCREMENTAL) +
                " fingerprint=" + safe(Build.FINGERPRINT) +
                " abis=" + Arrays.toString(Build.SUPPORTED_ABIS),
        )
        DragShareLog.d(
            TAG,
            "target portal=" + DragShareModule.TAPLUS_PACKAGE +
                " version=" + packageVersion(context, DragShareModule.TAPLUS_PACKAGE),
        )
        DragShareLog.d(
            TAG,
            "LSPosed Manager package=" + packageVersion(context, "org.lsposed.manager"),
        )
        if (frameworkInfo != null && frameworkInfo.trim().isNotEmpty()) {
            DragShareLog.d(TAG, "Xposed framework=" + frameworkInfo)
        }
        DragShareLog.d(
            TAG,
            "root environment:\n" +
                runRootCommand("id; command -v su; su -v; getprop ro.mi.os.version.name"),
        )
        DragShareLog.d(TAG, "runtime diagnostic end")
    }

    private fun packageVersion(context: Context, packageName: String): String {
        return try {
            val info = context.packageManager.getPackageInfo(packageName, 0)
            safe(info.versionName) + " (" + info.longVersionCode + ")"
        } catch (error: Throwable) {
            "unavailable:" + error.javaClass.simpleName
        }
    }

    private fun runRootCommand(command: String): String {
        var process: java.lang.Process? = null
        try {
            process = ProcessBuilder("su", "-c", command)
                .redirectErrorStream(true)
                .start()
            val commandProcess: java.lang.Process = process
            val output = AtomicReference("")
            val readFailure = AtomicReference<Throwable?>()
            val reader = Thread({
                try {
                    commandProcess.inputStream.use { input ->
                        output.set(readBounded(input, MAX_COMMAND_OUTPUT_CHARS))
                    }
                } catch (error: Throwable) {
                    readFailure.compareAndSet(null, error)
                }
            }, "drag-share-diagnostic-command")
            reader.isDaemon = true
            reader.start()
            if (!process.waitFor(COMMAND_TIMEOUT_MS, TimeUnit.MILLISECONDS)) {
                process.destroyForcibly()
                awaitCommandReader(reader, 1_000L)
                return "timeout after " + COMMAND_TIMEOUT_MS + "ms"
            }
            if (!awaitCommandReader(reader, COMMAND_TIMEOUT_MS)) {
                return "output drain timeout"
            }
            val failure = readFailure.get()
            if (failure != null) {
                return "output read failed:" + failure.javaClass.simpleName +
                    ':' + safe(failure.message)
            }
            return "exit=" + process.exitValue() + '\n' + output.get()
        } catch (error: Throwable) {
            return "failed:" + error.javaClass.simpleName + ':' + safe(error.message)
        } finally {
            process?.destroy()
        }
    }

    private fun markRuntimeDestinationCaptured(): Boolean {
        val destination = DragShareLog.configuredDestination()
        while (true) {
            val previous = RUNTIME_CAPTURED_DESTINATION.get()
            if (previous == destination) {
                return false
            }
            if (RUNTIME_CAPTURED_DESTINATION.compareAndSet(previous, destination)) {
                return true
            }
        }
    }

    private fun markInputInventoryDestinationCaptured(): Boolean {
        val destination = DragShareLog.configuredDestination()
        while (true) {
            val previous = INPUT_INVENTORY_CAPTURED_DESTINATION.get()
            if (previous == destination) {
                return false
            }
            if (INPUT_INVENTORY_CAPTURED_DESTINATION.compareAndSet(previous, destination)) {
                return true
            }
        }
    }

    private fun awaitCommandReader(reader: Thread, timeoutMillis: Long): Boolean {
        try {
            reader.join(timeoutMillis)
            if (!reader.isAlive) {
                return true
            }
            reader.interrupt()
            return false
        } catch (interrupted: InterruptedException) {
            Thread.currentThread().interrupt()
            return false
        }
    }

    @Throws(IOException::class)
    private fun readBounded(input: InputStream, maximumChars: Int): String {
        ByteArrayOutputStream().use { output ->
            val buffer = ByteArray(2 * 1024)
            while (true) {
                val count = input.read(buffer)
                if (count < 0) {
                    break
                }
                val remaining = maximumChars - output.size()
                if (remaining > 0) {
                    output.write(buffer, 0, min(remaining, count))
                }
            }
            val text = output.toString("UTF-8")
            return if (text.length >= maximumChars) text + "\n[truncated]" else text
        }
    }

    private fun trimForLog(value: String?): String {
        if (value == null || value.isEmpty()) {
            return ""
        }
        return if (value.length > MAX_COMMAND_OUTPUT_CHARS) {
            value.substring(0, MAX_COMMAND_OUTPUT_CHARS) + "\n[truncated]"
        } else {
            value
        }
    }

    private fun safe(value: String?): String = value?.replace('\u0000', '?') ?: ""
}
