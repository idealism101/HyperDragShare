package com.leaf.hyperdragshare.codex

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import java.io.ByteArrayOutputStream
import java.io.IOException
import java.io.InputStream
import java.util.concurrent.TimeUnit

/** In-memory Root screenshot fallback for Android 9 and 10. */
internal class RootScreenshotter {
    @Throws(IOException::class)
    fun capture(): Bitmap {
        var process: Process? = null
        try {
            process = ProcessBuilder("su", "-c", "/system/bin/screencap -p").start()
            val bytes = process.inputStream.use { input -> readAll(input) }
            if (!process.waitFor(COMMAND_TIMEOUT_SECONDS, TimeUnit.SECONDS)) {
                process.destroyForcibly()
                throw IOException("Root screencap timed out")
            }
            if (process.exitValue() != 0 || bytes.isEmpty()) {
                throw IOException("Root screencap failed")
            }
            return BitmapFactory.decodeByteArray(bytes, 0, bytes.size)
                ?: throw IOException("Root screencap returned invalid PNG")
        } catch (interrupted: InterruptedException) {
            Thread.currentThread().interrupt()
            throw IOException("Root screencap interrupted", interrupted)
        } finally {
            if (process != null) {
                try {
                    process.errorStream.close()
                } catch (_: IOException) {
                    // Process teardown only.
                }
                process.destroy()
            }
        }
    }

    companion object {
        private const val COMMAND_TIMEOUT_SECONDS = 4L

        private fun readAll(input: InputStream): ByteArray {
            val output = ByteArrayOutputStream()
            val buffer = ByteArray(64 * 1024)
            while (true) {
                val read = input.read(buffer)
                if (read < 0) {
                    return output.toByteArray()
                }
                if (read > 0) {
                    output.write(buffer, 0, read)
                }
            }
        }
    }
}
