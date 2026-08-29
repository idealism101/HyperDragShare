package com.leaf.hyperdragshare.codex

import android.content.Context
import android.os.SystemClock
import android.util.DisplayMetrics
import android.view.MotionEvent
import android.view.WindowManager
import java.io.BufferedReader
import java.io.FileInputStream
import java.io.IOException
import java.io.InputStream
import java.io.InputStreamReader
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.util.Locale
import java.util.regex.Pattern

internal class RootTouchSource(private val context: Context, private val listener: Listener) {
    fun interface Listener {
        fun onPointerEvent(action: Int, x: Float, y: Float, eventTime: Long)
    }

    private val parser: EvdevTouchParser = EvdevTouchParser(this::onRawFrame)

    @Volatile
    private var running = false

    @Volatile
    private var ready = false

    @Volatile
    private var process: Process? = null

    @Volatile
    private var inputStream: InputStream? = null
    private var thread: Thread? = null

    private var rawMaxX = 0
    private var rawMaxY = 0
    private var firstEventLogged = false
    private var discoveredDevices = ""
    private var rawEventsInFrame = 0
    private var rawFrameCount = 0L

    @Synchronized
    fun start() {
        if (running) {
            return
        }
        ready = false
        running = true
        firstEventLogged = false
        discoveredDevices = ""
        rawEventsInFrame = 0
        rawFrameCount = 0L
        DragShareLog.d(TAG, "root input start requested")
        val started = Thread({ runLoop() }, "drag-share-root-input")
        started.isDaemon = true
        thread = started
        started.start()
    }

    @Synchronized
    fun stop() {
        running = false
        ready = false
        parser.cancel()
        closeQuietly(inputStream)
        inputStream = null
        val currentProcess = process
        process = null
        currentProcess?.destroy()
        val currentThread = thread
        thread = null
        currentThread?.interrupt()
    }

    fun isReady(): Boolean = ready

    private fun runLoop() {
        try {
            DragShareLog.d(TAG, "starting touchscreen discovery")
            val devicePath = findTouchDevice()
            if (devicePath == null) {
                log("no direct touchscreen device found")
                DragShareDiagnostics.captureInputFailure(
                    context,
                    "no touchscreen candidate",
                    discoveredDevices,
                    null,
                )
                return
            }
            val ranges = readCoordinateRanges(devicePath)
            rawMaxX = ranges[0]
            rawMaxY = ranges[1]
            if (rawMaxX <= 0 || rawMaxY <= 0) {
                log("invalid coordinate range for " + devicePath)
                DragShareDiagnostics.captureInputFailure(
                    context,
                    "invalid coordinate range for " + devicePath,
                    discoveredDevices,
                    null,
                )
                return
            }

            val stream = openDevice(devicePath)
            inputStream = stream
            ready = true
            val metrics = currentDisplayMetrics()
            log(
                "ready device=" + devicePath +
                    " raw=" + rawMaxX + "x" + rawMaxY +
                    " screen=" + metrics.widthPixels + "x" + metrics.heightPixels +
                    " eventSize=" + inputEventSize(),
            )
            DragShareLog.d(TAG, "starting evdev read loop path=" + devicePath)
            readEvents(stream)
        } catch (error: Throwable) {
            if (running) {
                log("input loop failed", error)
                DragShareDiagnostics.captureInputFailure(
                    context,
                    "input loop failed:" + error.javaClass.simpleName,
                    discoveredDevices,
                    null,
                )
            }
        } finally {
            if (running && !firstEventLogged) {
                DragShareLog.d(TAG, "input stream ended before a decoded pointer action")
                DragShareDiagnostics.captureInputFailure(
                    context,
                    "stream ended without decoded pointer action",
                    discoveredDevices,
                    null,
                )
            }
            parser.cancel()
            ready = false
            closeQuietly(inputStream)
            inputStream = null
            val currentProcess = process
            process = null
            currentProcess?.destroy()
            running = false
        }
    }

    private fun findTouchDevice(): String? {
        var devices = ""
        try {
            FileInputStream("/proc/bus/input/devices").use { stream ->
                devices = readText(stream)
            }
        } catch (_: IOException) {
            // Some Android builds deny the read; others return an empty filtered view.
        }
        discoveredDevices = devices
        DragShareLog.d(TAG, "host /proc/bus/input/devices:\n" + devices)

        val devicePath = findTouchDevicePath(devices)
        if (devicePath != null) {
            return devicePath
        }

        log("touchscreen hidden from host process; retrying device discovery as root")
        try {
            devices = runRootTextCommand("cat /proc/bus/input/devices")
        } catch (interrupted: InterruptedException) {
            Thread.currentThread().interrupt()
            throw IOException("Interrupted while finding touchscreen", interrupted)
        }
        discoveredDevices = devices
        DragShareLog.d(TAG, "root /proc/bus/input/devices:\n" + devices)
        return findTouchDevicePath(devices)
    }

    private fun readCoordinateRanges(devicePath: String): IntArray {
        val output = runRootTextCommand("getevent -lp " + devicePath)
        DragShareLog.d(TAG, "input capability path=" + devicePath + ":\n" + output)
        DragShareDiagnostics.captureInputInventory(
            context,
            "root input range probe " + devicePath,
            discoveredDevices,
            output,
        )
        return intArrayOf(parseMaximum(MAX_X, output), parseMaximum(MAX_Y, output))
    }

    private fun parseMaximum(pattern: Pattern, value: CharSequence): Int {
        val matcher = pattern.matcher(value)
        if (!matcher.find()) {
            return -1
        }
        val maximum = matcher.group(1)
        return if (maximum == null) -1 else Integer.parseInt(maximum)
    }

    private fun openDevice(devicePath: String): InputStream {
        try {
            val stream = FileInputStream(devicePath)
            DragShareLog.d(TAG, "opened input device directly path=" + devicePath)
            return stream
        } catch (directFailure: IOException) {
            DragShareLog.d(
                TAG,
                "direct input open failed path=" + devicePath +
                    " error=" + directFailure.javaClass.simpleName +
                    ':' + directFailure.message +
                    "; retrying through root",
            )
            val rootProcess = ProcessBuilder("su", "-c", "exec cat " + devicePath).start()
            process = rootProcess
            DragShareLog.d(TAG, "opened input device through root cat path=" + devicePath)
            return rootProcess.inputStream
        }
    }

    private fun readEvents(stream: InputStream) {
        val eventSize = inputEventSize()
        val event = ByteArray(eventSize)
        while (running && readFully(stream, event)) {
            val buffer = ByteBuffer.wrap(event).order(ByteOrder.LITTLE_ENDIAN)
            if (eventSize == 24) {
                buffer.position(16)
            } else {
                buffer.position(8)
            }
            val type = buffer.short.toInt() and 0xffff
            val code = buffer.short.toInt() and 0xffff
            val value = buffer.int
            consumeEvent(type, code, value)
        }
    }

    private fun inputEventSize(): Int = if (android.os.Process.is64Bit()) 24 else 16

    private fun readFully(stream: InputStream, buffer: ByteArray): Boolean {
        var offset = 0
        while (running && offset < buffer.size) {
            val count = stream.read(buffer, offset, buffer.size - offset)
            if (count < 0) {
                return false
            }
            offset += count
        }
        return offset == buffer.size
    }

    private fun consumeEvent(type: Int, code: Int, value: Int) {
        if (DragShareLog.isDebugEnabled()) {
            rawEventsInFrame++
            if (rawFrameCount < 12L) {
                DragShareLog.d(
                    TAG,
                    "raw evdev event type=" + type + " code=" + code + " value=" + value,
                )
            }
            if (type == EvdevTouchParser.EV_SYN && code == EvdevTouchParser.SYN_REPORT) {
                rawFrameCount++
                DragShareLog.d(
                    TAG,
                    "raw evdev frame=" + rawFrameCount + " events=" + rawEventsInFrame,
                )
                rawEventsInFrame = 0
            }
        }
        parser.consume(type, code, value)
    }

    private fun onRawFrame(action: Int, rawX: Float, rawY: Float) {
        if (rawMaxX <= 0 || rawMaxY <= 0) {
            return
        }
        val screenPoint = toScreenCoordinates(rawX, rawY)
        DragShareLog.d(
            TAG,
            "decoded " + MotionEvent.actionToString(action) +
                " raw=" + Math.round(rawX) + ',' + Math.round(rawY) +
                " mapped=" + Math.round(screenPoint[0]) + ',' + Math.round(screenPoint[1]),
        )
        emit(action, screenPoint[0], screenPoint[1])
    }

    private fun emit(action: Int, x: Float, y: Float) {
        if (!firstEventLogged) {
            firstEventLogged = true
            log(
                "first event action=" + MotionEvent.actionToString(action) +
                    " point=" + Math.round(x) + "," + Math.round(y),
            )
        }
        listener.onPointerEvent(action, x, y, SystemClock.uptimeMillis())
    }

    private fun toScreenCoordinates(rawX: Float, rawY: Float): FloatArray {
        val windowManager = context.getSystemService(Context.WINDOW_SERVICE) as WindowManager
        val metrics = currentDisplayMetrics()
        val rotation = windowManager.defaultDisplay.rotation
        return GestureMath.mapRawPoint(
            rawX,
            rawY,
            rawMaxX,
            rawMaxY,
            metrics.widthPixels,
            metrics.heightPixels,
            rotation,
        )
    }

    private fun currentDisplayMetrics(): DisplayMetrics {
        val windowManager = context.getSystemService(Context.WINDOW_SERVICE) as WindowManager
        val metrics = DisplayMetrics()
        windowManager.defaultDisplay.getRealMetrics(metrics)
        return metrics
    }

    private fun runRootTextCommand(command: String): String {
        val commandProcess = ProcessBuilder("su", "-c", command)
            .redirectErrorStream(true)
            .start()
        val output: String = commandProcess.inputStream.use { stream ->
            readText(stream)
        }
        val exitCode = commandProcess.waitFor()
        if (exitCode != 0) {
            throw IOException("Command failed (" + exitCode + "): " + output)
        }
        return output
    }

    private fun readText(stream: InputStream): String {
        val output = StringBuilder()
        BufferedReader(InputStreamReader(stream)).use { reader ->
            var line = reader.readLine()
            while (line != null) {
                output.append(line).append('\n')
                line = reader.readLine()
            }
        }
        return output.toString()
    }

    private fun closeQuietly(stream: InputStream?) {
        if (stream == null) {
            return
        }
        try {
            stream.close()
        } catch (_: IOException) {
            // Ignore shutdown races.
        }
    }

    companion object {
        private const val TAG = "DragShare/RootInput"
        private val EVENT_HANDLER: Pattern = Pattern.compile("\\b(event\\d+)\\b")
        private val MAX_X: Pattern = Pattern.compile("ABS_MT_POSITION_X\\s*:.*?max\\s+(\\d+)")
        private val MAX_Y: Pattern = Pattern.compile("ABS_MT_POSITION_Y\\s*:.*?max\\s+(\\d+)")

        fun findTouchDevicePath(devices: CharSequence?): String? {
            if (devices == null) {
                return null
            }
            for (block in devices.toString().split(Regex("(?:\\r?\\n){2,}"))) {
                val event = eventFromBlock(block)
                if (event != null) {
                    return "/dev/input/" + event
                }
            }
            return null
        }

        private fun eventFromBlock(block: String): String? {
            val direct = block.contains("B: PROP=2") || block.contains("INPUT_PROP_DIRECT")
            val touchNamed = block.lowercase(Locale.ROOT).contains("touch")
            val hasAbsoluteAxes = block.contains("B: ABS=")
            if (!hasAbsoluteAxes || (!direct && !touchNamed)) {
                return null
            }
            for (line in block.split("\n")) {
                if (!line.startsWith("H: Handlers=")) {
                    continue
                }
                val matcher = EVENT_HANDLER.matcher(line)
                if (matcher.find()) {
                    return matcher.group(1)
                }
            }
            return null
        }

        private fun log(message: String) {
            DragShareLog.i(TAG, message)
        }

        private fun log(message: String, error: Throwable?) {
            DragShareLog.w(TAG, message, error)
        }
    }
}
