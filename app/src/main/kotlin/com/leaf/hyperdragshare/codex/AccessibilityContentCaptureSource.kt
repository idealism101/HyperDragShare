package com.leaf.hyperdragshare.codex

import android.graphics.Bitmap
import android.os.Handler
import android.os.Looper
import android.view.MotionEvent
import android.view.ViewConfiguration
import java.util.concurrent.ExecutorService
import kotlin.math.max

/** Coordinates Root pointer events, one node lookup, and optional one-shot screenshot capture. */
internal class AccessibilityContentCaptureSource(
    private val service: DragShareAccessibilityService,
    private val controller: DragShareController,
    private val rootTouchSource: RootTouchSource?,
    private val classifierExecutor: ExecutorService,
    private val screenshotter: AccessibilityScreenshotter,
) {
    private val mainHandler = Handler(Looper.getMainLooper())
    private val gestureDetector: LongPressGestureDetector

    init {
        val configuration = ViewConfiguration.get(service)
        val settings = DragShareSettings.readLocal(service)
        val tolerantTouchSlop = max(
            configuration.scaledTouchSlop * 2f,
            service.resources.displayMetrics.density * 16f,
        ) * settings.accessibilityTouchSlopMultiplier()
        gestureDetector = LongPressGestureDetector(
            settings.resolveAccessibilityLongPressTimeoutMillis(
                ViewConfiguration.getLongPressTimeout(),
            ).toLong(),
            tolerantTouchSlop,
            object : LongPressGestureDetector.Callback {
                override fun isLongPressAllowed(): Boolean =
                    service.isAccessibilityCaptureEnabled() &&
                        rootTouchSource != null &&
                        rootTouchSource.isReady()

                override fun onLongPress(gestureId: Long, stableX: Float, stableY: Float) {
                    service.trace(
                        "gesture=" + gestureId + " long press point=" +
                            Math.round(stableX) + "," + Math.round(stableY),
                    )
                    captureAt(gestureId, stableX, stableY, false)
                }

                override fun onDrag(action: Int, x: Float, y: Float, eventTime: Long) {
                    controller.acceptPointerEvent(action, x, y, eventTime, "root")
                }

                override fun onGestureCancelled(gestureId: Long) {
                    service.trace("gesture=" + gestureId + " cancelled")
                    screenshotter.cancelAll()
                    controller.cancelActiveSession()
                }
            },
        )
    }

    fun onPointerEvent(action: Int, x: Float, y: Float, eventTime: Long) {
        val before = gestureDetector.state()
        gestureDetector.onPointerEvent(action, x, y, eventTime)
        val after = gestureDetector.state()
        if (action == MotionEvent.ACTION_DOWN) {
            service.trace(
                "gesture=" + gestureDetector.currentGestureId() +
                    " pending=" + (after == LongPressGestureDetector.State.PENDING_LONG_PRESS),
            )
        } else if (before == LongPressGestureDetector.State.PENDING_LONG_PRESS &&
            after == LongPressGestureDetector.State.IGNORED_UNTIL_UP
        ) {
            service.trace(
                "gesture=" + gestureDetector.currentGestureId() +
                    " rejected movement point=" + Math.round(x) + "," + Math.round(y),
            )
        }
    }

    fun cancel() {
        gestureDetector.cancel()
        screenshotter.cancelAll()
        controller.cancelActiveSession()
    }

    private fun captureAt(gestureId: Long, stableX: Float, stableY: Float, retried: Boolean) {
        try {
            classifierExecutor.execute {
                if (!gestureDetector.isCapturing(gestureId)) {
                    return@execute
                }
                val selection = service.selectCandidateAt(
                    stableX,
                    stableY,
                    gestureId,
                )
                if (selection == null || selection.candidate == null) {
                    service.trace("gesture=" + gestureId + " candidate missing retry=" + !retried)
                    if (!retried) {
                        mainHandler.postDelayed(
                            { captureAt(gestureId, stableX, stableY, true) },
                            50L,
                        )
                    } else {
                        mainHandler.post { gestureDetector.captureFailed(gestureId) }
                    }
                    return@execute
                }
                val settings = DragShareSettings.readLocal(service)
                if (!settings.isAccessibilityCaptureMode() ||
                    !settings.isSharingEnabled(selection.isImage())
                ) {
                    service.trace("gesture=" + gestureId + " sharing disabled")
                    mainHandler.post { gestureDetector.captureFailed(gestureId) }
                    return@execute
                }
                val candidate = selection.candidate
                if (!selection.isImage()) {
                    service.trace("gesture=" + gestureId + " text captured")
                    val content = CapturedContent.text(
                        candidate.text,
                        candidate.sourcePackage,
                        candidate.bounds,
                    )
                    mainHandler.post { showCapturedContent(gestureId, content) }
                    return@execute
                }
                screenshotter.capture(
                    gestureId,
                    candidate.bounds,
                    object : AccessibilityScreenshotter.Callback {
                        override fun onSuccess(completedGestureId: Long, bitmap: Bitmap?) {
                            if (bitmap == null) {
                                mainHandler.post {
                                    gestureDetector.captureFailed(completedGestureId)
                                }
                                return
                            }
                            service.trace(
                                "gesture=" + completedGestureId + " screenshot captured size=" +
                                    bitmap.width + "x" + bitmap.height,
                            )
                            val content = CapturedContent.image(
                                bitmap,
                                candidate.sourcePackage,
                                candidate.bounds,
                                true,
                            )
                            mainHandler.post { showCapturedContent(completedGestureId, content) }
                        }

                        override fun onFailure(completedGestureId: Long, error: Throwable?) {
                            mainHandler.post {
                                DragShareLog.w(
                                    TAG,
                                    "gesture=" + completedGestureId + " image capture failed",
                                    error,
                                )
                                service.trace(
                                    "gesture=" + completedGestureId +
                                        " screenshot failed=" +
                                        (if (error == null) null else error.javaClass.simpleName),
                                )
                                gestureDetector.captureFailed(completedGestureId)
                            }
                        }
                    },
                )
            }
        } catch (error: RuntimeException) {
            DragShareLog.w(TAG, "gesture=" + gestureId + " classifier unavailable", error)
            mainHandler.post { gestureDetector.captureFailed(gestureId) }
        }
    }

    private fun showCapturedContent(gestureId: Long, content: CapturedContent?) {
        if (content == null || !service.isAccessibilityCaptureEnabled() ||
            !gestureDetector.beginDragging(gestureId)
        ) {
            recycleIfOwned(content)
            return
        }
        val settings = DragShareSettings.readLocal(service)
        controller.show(
            content,
            gestureDetector.latestX(),
            gestureDetector.latestY(),
            settings,
        )
        // beginDragging() only succeeds while the physical root gesture is
        // still down. Confirm that active root session synchronously so the
        // lock cannot be queued behind its eventual ACTION_UP.
        controller.onRootDragSessionStarted()
        service.trace("gesture=" + gestureId + " controller show requested kind=" + content.kind)
        DragShareLog.i(TAG, "gesture=" + gestureId + " controller shown kind=" + content.kind)
    }

    companion object {
        private const val TAG = "DragShare/Accessibility"

        private fun recycleIfOwned(content: CapturedContent?) {
            if (content != null && content.bitmapOwnedByDragShare && content.bitmap != null &&
                !content.bitmap.isRecycled
            ) {
                content.bitmap.recycle()
            }
        }
    }
}
