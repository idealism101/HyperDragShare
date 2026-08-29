package com.leaf.hyperdragshare.codex

import android.content.ContentResolver
import android.content.Context
import android.net.Uri
import android.util.Log
import java.io.FileNotFoundException
import java.io.IOException
import java.io.InputStream
import java.io.OutputStream
import java.nio.charset.StandardCharsets
import java.text.SimpleDateFormat
import java.util.ArrayDeque
import java.util.Date
import java.util.Locale
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicReference

/** Logging boundary shared by the module process and the injected portal process. */
internal object DragShareLog {
    const val LOG_DIRECTORY = "/data/local/tmp/HyperDragShare"
    const val LOG_FILE_PATH = LOG_DIRECTORY + "/hyperdragshare.log"

    private const val LOG_BACKUP_FILE_PATH = LOG_DIRECTORY + "/hyperdragshare.1.log"
    private const val TAG = "DragShare/Log"
    private const val MAX_FILE_BYTES = 4 * 1024 * 1024
    private const val MAX_PENDING_FILE_LINES = 1_024
    private const val ROOT_COMMAND_TIMEOUT_MS = 8_000L
    private const val STREAM_CLOSE_TIMEOUT_MS = 1_000L
    private val FILE_LOCK = Any()
    private val PENDING_FILE_LINES = ArrayDeque<String>()
    private val FILE_EXECUTOR = Executors.newSingleThreadExecutor { runnable ->
        val thread = Thread(runnable, "drag-share-log-file")
        thread.isDaemon = true
        thread
    }
    private val TIMESTAMP_FORMAT: ThreadLocal<SimpleDateFormat> = ThreadLocal.withInitial {
        SimpleDateFormat("yyyy-MM-dd HH:mm:ss.SSS", Locale.US)
    }

    @Volatile
    private var configuredLevel = DragShareSettings.DEFAULT_LOG_LEVEL

    @Volatile
    private var configuredDestination = DragShareSettings.DEFAULT_LOG_DESTINATION
    private var fileDrainScheduled = false
    private var rotateOnNextOpen = false
    private var droppedFileLines = 0
    private var fileSink: RootFileSink? = null
    fun configure(settings: DragShareSettings?) {
        if (settings == null) {
            return
        }
        val nextLevel = settings.logLevel
        val nextDestination = settings.logDestination
        val closeFileSink = configuredDestination == DragShareSettings.LOG_DESTINATION_FILE &&
            nextDestination != DragShareSettings.LOG_DESTINATION_FILE
        configuredLevel = nextLevel
        configuredDestination = nextDestination
        if (closeFileSink) {
            synchronized(FILE_LOCK) {
                PENDING_FILE_LINES.clear()
                rotateOnNextOpen = false
            }
            FILE_EXECUTOR.execute { closeFileSink() }
        }
    }

    fun isDebugEnabled(): Boolean = configuredLevel == DragShareSettings.LOG_LEVEL_DEBUG

    fun isFileDestination(): Boolean =
        configuredDestination == DragShareSettings.LOG_DESTINATION_FILE

    fun configuredDestination(): Int = configuredDestination

    fun d(tag: String?, message: String?) {
        emit(DragShareSettings.LOG_LEVEL_DEBUG, Log.DEBUG, tag, message, null)
    }

    fun i(tag: String?, message: String?) {
        emit(DragShareSettings.LOG_LEVEL_INFO, Log.INFO, tag, message, null)
    }

    fun w(tag: String?, message: String?, error: Throwable? = null) {
        emit(DragShareSettings.LOG_LEVEL_INFO, Log.WARN, tag, message, error)
    }

    fun e(tag: String?, message: String?, error: Throwable? = null) {
        emit(DragShareSettings.LOG_LEVEL_INFO, Log.ERROR, tag, message, error)
    }

    fun exportFileName(): String = "HyperDragShare-" +
        SimpleDateFormat("yyyyMMdd-HHmmss", Locale.US).format(Date()) + ".log"
    /** Copies the root-owned diagnostic file to a user-selected document URI. */
    @Throws(IOException::class)
    fun exportTo(context: Context?, destination: Uri?) {
        if (context == null || destination == null) {
            throw IOException("Missing export destination")
        }
        awaitQueuedFileWrites()
        var process: Process? = null
        var output: OutputStream? = null
        var copyThread: Thread? = null
        val copyFailure = AtomicReference<Throwable>()
        try {
            process = ProcessBuilder(
                "su",
                "-c",
                "if [ -r " + LOG_FILE_PATH + " ]; then cat " + LOG_FILE_PATH +
                    "; else exit 3; fi",
            )
                .redirectErrorStream(true)
                .start()
            val exportProcess = process
            output = openExportOutput(context.contentResolver, destination)
            val exportOutput = output
            output = null
            copyThread = Thread({
                try {
                    exportProcess.inputStream.use { input ->
                        exportOutput.use { stream ->
                            copy(input, stream)
                            stream.flush()
                        }
                    }
                } catch (error: Throwable) {
                    copyFailure.compareAndSet(null, error)
                }
            }, "drag-share-log-export")
            copyThread.isDaemon = true
            copyThread.start()
            val completed = try {
                process.waitFor(ROOT_COMMAND_TIMEOUT_MS, TimeUnit.MILLISECONDS)
            } catch (interrupted: InterruptedException) {
                Thread.currentThread().interrupt()
                throw IOException("Interrupted while exporting diagnostic log", interrupted)
            }
            if (!completed) {
                process.destroyForcibly()
                awaitExportCopy(copyThread, STREAM_CLOSE_TIMEOUT_MS)
                throw IOException("Timed out while reading the diagnostic log")
            }
            awaitExportCopy(copyThread, ROOT_COMMAND_TIMEOUT_MS)
            val failure = copyFailure.get()
            if (failure != null) {
                throw IOException("Unable to write exported diagnostic log", failure)
            }
            if (process.exitValue() != 0) {
                throw IOException("Diagnostic log is unavailable or root could not read it")
            }
        } finally {
            if (copyThread == null && output != null) {
                try {
                    output.close()
                } catch (_: IOException) {
                    // A failed document stream is already represented by the export exception.
                }
            }
            process?.destroy()
        }
    }

    @Throws(IOException::class)
    private fun awaitExportCopy(copyThread: Thread, timeoutMillis: Long) {
        try {
            copyThread.join(timeoutMillis)
        } catch (interrupted: InterruptedException) {
            Thread.currentThread().interrupt()
            throw IOException("Interrupted while exporting diagnostic log", interrupted)
        }
        if (copyThread.isAlive) {
            copyThread.interrupt()
            throw IOException("Timed out while copying the diagnostic log")
        }
    }
    @Throws(FileNotFoundException::class)
    private fun openExportOutput(resolver: ContentResolver, destination: Uri): OutputStream =
        resolver.openOutputStream(destination, "w") ?: throw FileNotFoundException(
            destination.toString(),
        )

    private fun emit(
        minimumLevel: Int,
        priority: Int,
        tag: String?,
        message: String?,
        error: Throwable?,
    ) {
        if (!shouldEmit(minimumLevel)) {
            return
        }
        val safeTag = if (tag == null || tag.trim().isEmpty()) TAG else tag
        val safeMessage = message ?: ""
        if (configuredDestination == DragShareSettings.LOG_DESTINATION_FILE) {
            enqueueFileLine(formatFileLine(priority, safeTag, safeMessage, error))
        } else {
            emitToLogcat(priority, safeTag, safeMessage, error)
        }
    }

    private fun shouldEmit(level: Int): Boolean {
        val configured = configuredLevel
        return configured == DragShareSettings.LOG_LEVEL_DEBUG ||
            (
                configured == DragShareSettings.LOG_LEVEL_INFO &&
                    level >= DragShareSettings.LOG_LEVEL_INFO
                )
    }

    private fun emitToLogcat(priority: Int, tag: String, message: String, error: Throwable?) {
        if (priority == Log.DEBUG) {
            Log.d(tag, message)
        } else if (priority == Log.ERROR && error != null) {
            Log.e(tag, message, error)
        } else if (priority == Log.ERROR) {
            Log.e(tag, message)
        } else if (priority == Log.WARN && error != null) {
            Log.w(tag, message, error)
        } else if (priority == Log.WARN) {
            Log.w(tag, message)
        } else {
            Log.i(tag, message)
        }
    }
    private fun formatFileLine(
        priority: Int,
        tag: String,
        message: String,
        error: Throwable?,
    ): String {
        val line = StringBuilder()
        line.append(TIMESTAMP_FORMAT.get()?.format(Date()))
            .append(' ')
            .append(priorityLabel(priority))
            .append(" pid=").append(android.os.Process.myPid())
            .append(" uid=").append(android.os.Process.myUid())
            .append(" thread=").append(Thread.currentThread().name)
            .append(" ").append(tag)
            .append(": ").append(message)
        if (error != null) {
            line.append('\n').append(Log.getStackTraceString(error))
        }
        if (line.isEmpty() || line[line.length - 1] != '\n') {
            line.append('\n')
        }
        return line.toString()
    }

    private fun priorityLabel(priority: Int): Char {
        if (priority == Log.DEBUG) {
            return 'D'
        }
        if (priority == Log.WARN) {
            return 'W'
        }
        if (priority == Log.ERROR) {
            return 'E'
        }
        return 'I'
    }

    private fun enqueueFileLine(line: String) {
        synchronized(FILE_LOCK) {
            if (PENDING_FILE_LINES.size >= MAX_PENDING_FILE_LINES) {
                droppedFileLines++
                if (droppedFileLines == 1) {
                    Log.w(TAG, "diagnostic file queue is full; dropping further log lines")
                }
                return
            }
            PENDING_FILE_LINES.addLast(line)
            if (fileDrainScheduled) {
                return
            }
            fileDrainScheduled = true
            FILE_EXECUTOR.execute { drainFileQueue() }
        }
    }
    private fun drainFileQueue() {
        while (true) {
            val line = synchronized(FILE_LOCK) {
                val next = PENDING_FILE_LINES.pollFirst()
                if (next == null) {
                    fileDrainScheduled = false
                }
                next
            } ?: return
            try {
                appendToFile(line)
            } catch (error: IOException) {
                Log.w(TAG, "unable to append diagnostic log file", error)
                synchronized(FILE_LOCK) {
                    PENDING_FILE_LINES.clear()
                    fileDrainScheduled = false
                }
                closeFileSink()
                return
            }
        }
    }

    @Throws(IOException::class)
    private fun appendToFile(line: String) {
        val bytes = line.toByteArray(StandardCharsets.UTF_8)
        var sink = fileSink
        if (sink == null || sink.bytesWritten + bytes.size > MAX_FILE_BYTES) {
            if (sink != null) {
                closeFileSink()
                rotateOnNextOpen = true
            }
            sink = RootFileSink.open(rotateOnNextOpen)
            fileSink = sink
            rotateOnNextOpen = false
        }
        sink.append(bytes)
    }

    private fun closeFileSink() {
        val sink = fileSink
        fileSink = null
        sink?.close()
    }

    @Throws(IOException::class)
    private fun awaitQueuedFileWrites() {
        try {
            val barrier = FILE_EXECUTOR.submit(Runnable { })
            barrier.get(ROOT_COMMAND_TIMEOUT_MS, TimeUnit.MILLISECONDS)
        } catch (error: Throwable) {
            throw IOException("Unable to flush diagnostic log", error)
        }
    }
    @Throws(IOException::class)
    private fun copy(input: InputStream, output: OutputStream) {
        val buffer = ByteArray(16 * 1024)
        while (true) {
            val count = input.read(buffer)
            if (count < 0) {
                return
            }
            output.write(buffer, 0, count)
        }
    }

    private class RootFileSink private constructor(private val process: Process) {
        private val output: OutputStream = process.outputStream

        var bytesWritten = 0
            private set

        @Throws(IOException::class)
        fun append(bytes: ByteArray) {
            output.write(bytes)
            output.flush()
            bytesWritten += bytes.size
        }

        fun close() {
            try {
                output.close()
            } catch (_: IOException) {
                // The root shell may already have stopped.
            }
            process.destroy()
        }

        companion object {
            @Throws(IOException::class)
            fun open(rotate: Boolean): RootFileSink {
                val command = "mkdir -p " + LOG_DIRECTORY +
                    " && chmod 700 " + LOG_DIRECTORY +
                    (
                        if (rotate) {
                            " && if [ -f " + LOG_FILE_PATH + " ]; then mv -f " +
                                LOG_FILE_PATH + " " + LOG_BACKUP_FILE_PATH + "; fi"
                        } else {
                            ""
                        }
                        ) +
                    " && touch " + LOG_FILE_PATH +
                    " && chmod 600 " + LOG_FILE_PATH +
                    " && exec sh -c 'cat >> " + LOG_FILE_PATH + "'"
                return RootFileSink(
                    ProcessBuilder("su", "-c", command)
                        .redirectErrorStream(true)
                        .start(),
                )
            }
        }
    }
}
