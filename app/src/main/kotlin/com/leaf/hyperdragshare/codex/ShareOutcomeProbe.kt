package com.leaf.hyperdragshare.codex

import android.net.Uri
import java.io.BufferedReader
import java.io.InputStreamReader
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean

/**
 * `startActivity` returns normally even when the launch is vetoed in the background, and a
 * recipient that rejects the image URI shows its own error instead of telling the module. This
 * probe answers both questions afterwards by asking the system which activity is actually
 * resumed, so a debug log finally distinguishes "never launched" from "launched and refused".
 *
 * Diagnostics only, and only while debug logging is on.
 */
internal object ShareOutcomeProbe {
    private const val TAG = "DragShare/Share"
    private const val PROBE_DELAY_MS = 1_200L
    private const val PROBE_TIMEOUT_MS = 2_500L
    private const val COMMAND =
        "dumpsys activity activities | grep -m2 -E 'topResumedActivity|mResumedActivity'"

    private val pending = AtomicBoolean(false)
    private val executor = Executors.newSingleThreadExecutor { runnable ->
        val thread = Thread(runnable, "drag-share-share-probe")
        thread.isDaemon = true
        thread
    }

    fun verify(target: ShareTarget, stagedImage: Uri?) {
        if (!DragShareLog.isDebugEnabled()) {
            return
        }
        val expected = target.component?.flattenToShortString() ?: return
        if (!pending.compareAndSet(false, true)) {
            return
        }
        val source = if (stagedImage == null) {
            "text"
        } else if (SharedImagePublisher.isMediaStoreUri(stagedImage)) {
            "mediastore"
        } else {
            "module"
        }
        executor.execute {
            try {
                Thread.sleep(PROBE_DELAY_MS)
                val resumed = resumedActivity()
                DragShareLog.d(
                    TAG,
                    "share outcome expected=" + expected +
                        " source=" + source +
                        " foreground=" + describeForeground(resumed, expected) +
                        " resumed=" + resumed,
                )
            } catch (interrupted: InterruptedException) {
                Thread.currentThread().interrupt()
            } catch (error: Throwable) {
                DragShareLog.d(
                    TAG,
                    "share outcome probe unavailable=" + error.javaClass.simpleName,
                )
            } finally {
                pending.set(false)
            }
        }
    }

    private fun describeForeground(resumed: String, expected: String): String {
        if (resumed.isEmpty()) {
            return "unknown"
        }
        val packageName = expected.substringBefore('/')
        return if (resumed.contains(packageName)) "yes" else "no"
    }

    private fun resumedActivity(): String {
        val process = ProcessBuilder("su", "-c", COMMAND)
            .redirectErrorStream(true)
            .start()
        val output = StringBuilder()
        try {
            BufferedReader(InputStreamReader(process.inputStream)).use { reader ->
                var line = reader.readLine()
                while (line != null) {
                    if (output.isNotEmpty()) {
                        output.append(" | ")
                    }
                    output.append(line.trim())
                    line = reader.readLine()
                }
            }
            if (!process.waitFor(PROBE_TIMEOUT_MS, TimeUnit.MILLISECONDS)) {
                process.destroy()
            }
        } finally {
            process.destroy()
        }
        return output.toString()
    }
}
