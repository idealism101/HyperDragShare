package com.leaf.hyperdragshare.codex

import android.accessibilityservice.AccessibilityService
import android.graphics.Bitmap
import android.graphics.Rect
import android.hardware.HardwareBuffer
import android.os.Build
import android.util.DisplayMetrics
import android.view.Display
import android.view.WindowManager
import androidx.annotation.RequiresApi
import java.io.IOException
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import kotlin.math.max
import kotlin.math.roundToInt

/** One-at-a-time, cancellable region screenshot bridge for accessibility capture. */
internal class AccessibilityScreenshotter(
    private val service: AccessibilityService,
    worker: ExecutorService?,
    rootScreenshotter: RootScreenshotter? = null,
) {
    interface Callback {
        fun onSuccess(gestureId: Long, bitmap: Bitmap?)

        fun onFailure(gestureId: Long, error: Throwable?)
    }

    private val worker: ExecutorService = worker ?: Executors.newSingleThreadExecutor()
    private val rootScreenshotter: RootScreenshotter = rootScreenshotter ?: RootScreenshotter()
    private val timeoutExecutor = Executors.newSingleThreadScheduledExecutor { runnable ->
        val thread = Thread(runnable, "drag-share-screenshot-timeout")
        thread.isDaemon = true
        thread
    }
    private val lock = Any()
    private var nextRequestId = 0L
    private var activeRequestId = 0L
    private var closed = false
    fun capture(gestureId: Long, sourceBounds: Rect?, callback: Callback?) {
        if (sourceBounds == null || sourceBounds.width() <= 0 || sourceBounds.height() <= 0) {
            failImmediately(gestureId, callback, IllegalArgumentException("Invalid capture bounds"))
            return
        }
        val requestId: Long
        synchronized(lock) {
            if (closed) {
                failImmediately(gestureId, callback, IllegalStateException("Screenshotter is closed"))
                return
            }
            if (activeRequestId != 0L) {
                failImmediately(gestureId, callback, IllegalStateException("Screenshot already pending"))
                return
            }
            requestId = ++nextRequestId
            activeRequestId = requestId
        }
        val requestedBounds = Rect(sourceBounds)
        timeoutExecutor.schedule(
            Runnable { fail(requestId, gestureId, callback, IOException("Screenshot timed out")) },
            TIMEOUT_MILLIS,
            TimeUnit.MILLISECONDS,
        )
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            takeAccessibilityScreenshot(requestId, gestureId, requestedBounds, callback)
        } else {
            takeRootScreenshot(requestId, gestureId, requestedBounds, callback)
        }
    }

    fun cancelAll() {
        synchronized(lock) {
            activeRequestId = 0L
        }
    }

    fun close() {
        synchronized(lock) {
            closed = true
            activeRequestId = 0L
        }
        timeoutExecutor.shutdownNow()
    }
    @RequiresApi(Build.VERSION_CODES.R)
    private fun takeAccessibilityScreenshot(
        requestId: Long,
        gestureId: Long,
        sourceBounds: Rect,
        callback: Callback?,
    ) {
        try {
            service.takeScreenshot(
                Display.DEFAULT_DISPLAY,
                worker,
                object : AccessibilityService.TakeScreenshotCallback {
                    override fun onSuccess(result: AccessibilityService.ScreenshotResult) {
                        worker.execute {
                            handleAccessibilitySuccess(
                                requestId,
                                gestureId,
                                sourceBounds,
                                callback,
                                result,
                            )
                        }
                    }

                    override fun onFailure(errorCode: Int) {
                        if (errorCode ==
                            AccessibilityService.ERROR_TAKE_SCREENSHOT_INTERNAL_ERROR
                        ) {
                            takeRootScreenshot(requestId, gestureId, sourceBounds, callback)
                        } else {
                            fail(
                                requestId,
                                gestureId,
                                callback,
                                IOException("Accessibility screenshot failed code=" + errorCode),
                            )
                        }
                    }
                },
            )
        } catch (error: Throwable) {
            fail(requestId, gestureId, callback, error)
        }
    }
    @RequiresApi(Build.VERSION_CODES.R)
    private fun handleAccessibilitySuccess(
        requestId: Long,
        gestureId: Long,
        sourceBounds: Rect,
        callback: Callback?,
        result: AccessibilityService.ScreenshotResult?,
    ) {
        val buffer: HardwareBuffer? = result?.hardwareBuffer
        var full: Bitmap? = null
        try {
            if (result == null || buffer == null) {
                throw IOException("Accessibility screenshot returned no buffer")
            }
            full = Bitmap.wrapHardwareBuffer(buffer, result.colorSpace)
                ?: throw IOException("Unable to wrap screenshot buffer")
            val cropped = crop(full, sourceBounds)
                ?: throw IOException("Screenshot region is outside the display")
            deliver(requestId, gestureId, callback, cropped)
        } catch (error: Throwable) {
            fail(requestId, gestureId, callback, error)
        } finally {
            val wrapped = full
            if (wrapped != null && !wrapped.isRecycled) {
                wrapped.recycle()
            }
            buffer?.close()
        }
    }

    private fun takeRootScreenshot(
        requestId: Long,
        gestureId: Long,
        sourceBounds: Rect,
        callback: Callback?,
    ) {
        worker.execute {
            var full: Bitmap? = null
            try {
                full = rootScreenshotter.capture()
                val cropped = crop(full, sourceBounds)
                    ?: throw IOException("Screenshot region is outside the display")
                deliver(requestId, gestureId, callback, cropped)
            } catch (error: Throwable) {
                fail(requestId, gestureId, callback, error)
            } finally {
                val captured = full
                if (captured != null && !captured.isRecycled) {
                    captured.recycle()
                }
            }
        }
    }
    private fun crop(full: Bitmap, sourceBounds: Rect): Bitmap? {
        val displaySize = displaySize()
        val edge = max(1, service.resources.displayMetrics.density.roundToInt())
        val mapped = ScreenshotRectMapper.mapAndExpand(
            sourceBounds,
            displaySize[0],
            displaySize[1],
            full.width,
            full.height,
            edge,
        ) ?: return null
        val region = Bitmap.createBitmap(
            full,
            mapped.left,
            mapped.top,
            mapped.width(),
            mapped.height(),
        )
        val software = region.copy(Bitmap.Config.ARGB_8888, false)
        if (region !== full && !region.isRecycled) {
            region.recycle()
        }
        return software
    }

    private fun displaySize(): IntArray {
        try {
            val windowManager = service.getSystemService(
                android.content.Context.WINDOW_SERVICE,
            ) as WindowManager?
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R && windowManager != null) {
                val metrics = windowManager.maximumWindowMetrics
                return intArrayOf(metrics.bounds.width(), metrics.bounds.height())
            }
        } catch (_: Throwable) {
            // Use resources below on service contexts without WindowManager metrics.
        }
        val metrics: DisplayMetrics = service.resources.displayMetrics
        return intArrayOf(metrics.widthPixels, metrics.heightPixels)
    }
    private fun deliver(requestId: Long, gestureId: Long, callback: Callback?, bitmap: Bitmap?) {
        if (!complete(requestId)) {
            if (bitmap != null && !bitmap.isRecycled) {
                bitmap.recycle()
            }
            return
        }
        if (callback != null) {
            callback.onSuccess(gestureId, bitmap)
        } else if (bitmap != null && !bitmap.isRecycled) {
            bitmap.recycle()
        }
    }

    private fun fail(requestId: Long, gestureId: Long, callback: Callback?, error: Throwable?) {
        if (!complete(requestId)) {
            return
        }
        DragShareLog.w(TAG, "screenshot request failed", error)
        callback?.onFailure(gestureId, error)
    }

    private fun failImmediately(gestureId: Long, callback: Callback?, error: Throwable?) {
        callback?.onFailure(gestureId, error)
    }

    private fun complete(requestId: Long): Boolean {
        synchronized(lock) {
            if (closed || activeRequestId != requestId) {
                return false
            }
            activeRequestId = 0L
            return true
        }
    }

    companion object {
        private const val TAG = "DragShare/Screenshot"
        private const val TIMEOUT_MILLIS = 3_500L
    }
}
