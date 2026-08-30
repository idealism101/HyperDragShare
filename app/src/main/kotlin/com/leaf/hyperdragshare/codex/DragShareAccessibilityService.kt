package com.leaf.hyperdragshare.codex

import android.accessibilityservice.AccessibilityService
import android.app.KeyguardManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.database.ContentObserver
import android.graphics.Rect
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.os.PowerManager
import android.os.SystemClock
import android.view.Display
import android.view.MotionEvent
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo
import android.view.accessibility.AccessibilityWindowInfo
import androidx.core.content.ContextCompat
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors

/** Accessibility-mode runtime owner. Events only update window state; capture starts after a long press. */
class DragShareAccessibilityService : AccessibilityService() {
    private val mainHandler = Handler(Looper.getMainLooper())
    private val rootReadyReporter: Runnable = object : Runnable {
        override fun run() {
            val source = rootTouchSource
            val ready = runtimeStarted && source != null && source.isReady()
            AccessibilityRuntimeStatus.setRootInputReady(ready)
            if (ready != lastReportedRootReady) {
                lastReportedRootReady = ready
                trace("root ready=" + ready)
            }
            if (!runtimeStarted) {
                return
            }
            if (ready) {
                rootNotReadySince = 0L
                rootRestartCount = 0
            } else {
                val now = SystemClock.uptimeMillis()
                if (rootNotReadySince == 0L) {
                    rootNotReadySince = now
                } else if (now - rootNotReadySince >= ROOT_RETRY_DELAY_MILLIS &&
                    rootRestartCount < MAX_ROOT_RESTARTS &&
                    source != null
                ) {
                    rootRestartCount++
                    rootNotReadySince = now
                    DragShareLog.w(
                        TAG,
                        "restarting unavailable Root input attempt=" + rootRestartCount,
                    )
                    source.stop()
                    mainHandler.postDelayed({
                        if (runtimeStarted && rootTouchSource === source && !source.isReady()) {
                            source.start()
                        }
                    }, 250L)
                }
            }
            mainHandler.postDelayed(this, if (ready) 1_000L else 400L)
        }
    }

    private var settingsObserver: ContentObserver? = null
    private var screenReceiver: BroadcastReceiver? = null

    @Volatile
    private var screenInteractive = true

    @Volatile
    private var foregroundPackage: String? = null

    @Volatile
    private var windowGeneration = 0
    private var rootNotReadySince = 0L
    private var rootRestartCount = 0
    private var lastReportedRootReady = false
    private var runtimeStarted = false
    private var runtimeLongPressTimeoutMillis = Int.MIN_VALUE
    private var runtimeRecognitionSensitivityPercent = Int.MIN_VALUE
    private var rootTouchSource: RootTouchSource? = null
    private var controller: DragShareController? = null
    private var captureSource: AccessibilityContentCaptureSource? = null
    private var classifierExecutor: ExecutorService? = null
    private var screenshotter: AccessibilityScreenshotter? = null

    override fun onServiceConnected() {
        super.onServiceConnected()
        AccessibilityTrace.reset(this)
        AccessibilityRuntimeStatus.setConnected(true)
        screenInteractive = isScreenInteractive()
        trace(
            "service connected mode=" +
                DragShareSettings.readLocal(this).contentCaptureMode +
                " interactive=" + screenInteractive +
                " locked=" + isDeviceLocked() +
                " sdk=" + Build.VERSION.SDK_INT,
        )
        registerSettingsObserver()
        registerScreenReceiver()
        applyMode()
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        if (event == null) {
            return
        }
        val packageName = event.packageName
        foregroundPackage = if (packageName == null) null else packageName.toString()
        val type = event.eventType
        if (type == AccessibilityEvent.TYPE_WINDOWS_CHANGED ||
            type == AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED ||
            type == AccessibilityEvent.TYPE_WINDOW_CONTENT_CHANGED ||
            type == AccessibilityEvent.TYPE_VIEW_SCROLLED
        ) {
            windowGeneration++
        }
    }

    override fun onInterrupt() {
        val source = captureSource
        source?.cancel()
        val activeScreenshotter = screenshotter
        activeScreenshotter?.cancelAll()
    }

    override fun onDestroy() {
        unregisterSettingsObserver()
        unregisterScreenReceiver()
        stopRuntime()
        AccessibilityRuntimeStatus.setConnected(false)
        super.onDestroy()
    }

    fun isAccessibilityCaptureEnabled(): Boolean {
        val settings = DragShareSettings.readLocal(this)
        return isAccessibilityRuntimeEnabled(settings) &&
            settings.isAccessibilityRecognitionEnabledForOrientation(
                resources.configuration.orientation,
            )
    }

    private fun isAccessibilityRuntimeEnabled(): Boolean =
        isAccessibilityRuntimeEnabled(DragShareSettings.readLocal(this))

    private fun isAccessibilityRuntimeEnabled(settings: DragShareSettings): Boolean =
        screenInteractive &&
            !isDeviceLocked() &&
            settings.isAccessibilityCaptureMode()

    internal fun selectCandidateAt(
        x: Float,
        y: Float,
        gestureId: Long,
    ): AccessibilityCandidateSelector.Selection? {
        if (!isAccessibilityCaptureEnabled()) {
            trace("gesture=" + gestureId + " selection skipped disabled")
            return null
        }
        val settings = DragShareSettings.readLocal(this)
        val roots = rootsAtPoint(x, y, settings)
        trace("gesture=" + gestureId + " roots=" + roots.size)
        for (windowRoot in roots) {
            try {
                val snapshots = snapshotTree(windowRoot.root, windowRoot.layer)
                val screen = screenSize()
                val classifier = AccessibilityNodeClassifier(
                    resources.displayMetrics.density,
                    screen[0],
                    screen[1],
                )
                val buckets = classifier.classify(snapshots)
                val selection = AccessibilityCandidateSelector.select(buckets, x, y)
                DragShareLog.i(
                    TAG,
                    "gesture=" + gestureId +
                        " nodes=" + snapshots.size +
                        " candidates=" + buckets.candidateCount(),
                )
                if (selection != null) {
                    val candidate = selection.candidate
                    if (AccessibilityBlacklist.isBlocked(this, settings, candidate?.sourcePackage)) {
                        trace("gesture=" + gestureId + " candidate skipped blacklist")
                        continue
                    }
                    trace(
                        "gesture=" + gestureId + " selected=" +
                            candidate?.kind +
                            " bounds=" + candidate?.bounds?.flattenToString(),
                    )
                    return selection
                }
            } catch (error: Throwable) {
                DragShareLog.w(TAG, "gesture=" + gestureId + " window snapshot failed", error)
            } finally {
                recycleNode(windowRoot.root)
            }
        }
        trace("gesture=" + gestureId + " no candidate")
        return null
    }

    private fun applyMode() {
        if (isAccessibilityRuntimeEnabled()) {
            startRuntime()
        } else {
            stopRuntime()
        }
    }

    private fun startRuntime() {
        if (runtimeStarted) {
            return
        }
        val settings = DragShareSettings.readLocal(this)
        runtimeStarted = true
        runtimeLongPressTimeoutMillis = settings.accessibilityLongPressTimeoutMillis
        runtimeRecognitionSensitivityPercent = settings.accessibilityRecognitionSensitivityPercent
        rootNotReadySince = 0L
        rootRestartCount = 0
        lastReportedRootReady = false
        val executor = Executors.newSingleThreadExecutor { runnable ->
            val thread = Thread(runnable, "drag-share-accessibility-classifier")
            thread.isDaemon = true
            thread
        }
        classifierExecutor = executor
        val activeController = DragShareController(
            this,
            OverlayWindowPolicy.accessibility(),
        )
        controller = activeController
        val activeScreenshotter = AccessibilityScreenshotter(this, executor)
        screenshotter = activeScreenshotter
        val input = RootTouchSource(
            applicationContext,
        ) { action, x, y, eventTime ->
            if (action == MotionEvent.ACTION_DOWN ||
                action == MotionEvent.ACTION_UP ||
                action == MotionEvent.ACTION_CANCEL
            ) {
                trace("root action=" + MotionEvent.actionToString(action))
            }
            val source = captureSource
            source?.onPointerEvent(action, x, y, eventTime)
        }
        rootTouchSource = input
        captureSource = AccessibilityContentCaptureSource(
            this,
            activeController,
            input,
            executor,
            activeScreenshotter,
        )
        input.start()
        mainHandler.removeCallbacks(rootReadyReporter)
        mainHandler.post(rootReadyReporter)
        DragShareLog.i(TAG, "accessibility runtime started")
        trace("runtime started")
    }

    private fun stopRuntime() {
        if (!runtimeStarted && controller == null && rootTouchSource == null) {
            AccessibilityRuntimeStatus.setRootInputReady(false)
            return
        }
        runtimeStarted = false
        runtimeLongPressTimeoutMillis = Int.MIN_VALUE
        runtimeRecognitionSensitivityPercent = Int.MIN_VALUE
        rootNotReadySince = 0L
        rootRestartCount = 0
        lastReportedRootReady = false
        mainHandler.removeCallbacks(rootReadyReporter)
        AccessibilityRuntimeStatus.setRootInputReady(false)
        val source = captureSource
        captureSource = null
        source?.cancel()
        val activeScreenshotter = screenshotter
        screenshotter = null
        activeScreenshotter?.close()
        val input = rootTouchSource
        rootTouchSource = null
        input?.stop()
        val executor = classifierExecutor
        classifierExecutor = null
        executor?.shutdownNow()
        val activeController = controller
        controller = null
        activeController?.destroy()
        DragShareLog.i(TAG, "accessibility runtime stopped")
        trace("runtime stopped")
    }

    private fun rootsAtPoint(
        x: Float,
        y: Float,
        settings: DragShareSettings,
    ): List<WindowRoot> {
        val result = ArrayList<WindowRoot>()
        val seen = HashSet<String>()
        var activeRoot: AccessibilityNodeInfo? = null
        try {
            activeRoot = rootInActiveWindow
            addRootIfUsable(result, seen, activeRoot, Int.MAX_VALUE, settings)
        } catch (ignored: Throwable) {
            recycleNode(activeRoot)
        }
        var windows: List<AccessibilityWindowInfo?>?
        try {
            windows = getWindows()
        } catch (ignored: Throwable) {
            windows = null
        }
        if (windows != null) {
            for (window in windows) {
                var root: AccessibilityNodeInfo? = null
                try {
                    if (window == null ||
                        window.type == AccessibilityWindowInfo.TYPE_ACCESSIBILITY_OVERLAY
                    ) {
                        continue
                    }
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R &&
                        window.displayId != Display.DEFAULT_DISPLAY
                    ) {
                        continue
                    }
                    val bounds = Rect()
                    window.getBoundsInScreen(bounds)
                    if (!contains(bounds, x, y)) {
                        continue
                    }
                    root = window.root
                    var layer = window.layer
                    if (window.isActive) layer += 1_000_000
                    if (window.isFocused) layer += 100_000
                    addRootIfUsable(result, seen, root, layer, settings)
                    root = null
                } catch (ignored: Throwable) {
                    // One broken window must not cancel the full capture.
                } finally {
                    recycleNode(root)
                    recycleWindow(window)
                }
            }
        }
        sortWindowRoots(result)
        return result
    }

    private fun addRootIfUsable(
        roots: MutableList<WindowRoot>,
        seen: MutableSet<String>,
        root: AccessibilityNodeInfo?,
        layer: Int,
        settings: DragShareSettings,
    ) {
        if (root == null) {
            return
        }
        val bounds = Rect()
        root.getBoundsInScreen(bounds)
        val packageName = asString(root.packageName)
        // Window bounds were already checked when available. Some ROMs expose an
        // empty or inset root bounds while its descendants still have valid bounds.
        if (getPackageName() == packageName) {
            recycleNode(root)
            return
        }
        if (AccessibilityBlacklist.isBlocked(this, settings, packageName)) {
            recycleNode(root)
            return
        }
        val key = packageName + ":" + bounds.flattenToString()
        if (!seen.add(key)) {
            recycleNode(root)
            return
        }
        roots.add(WindowRoot(root, layer))
    }

    private fun snapshotTree(
        root: AccessibilityNodeInfo,
        layer: Int,
    ): List<AccessibilityNodeSnapshot> {
        val snapshots = ArrayList<AccessibilityNodeSnapshot>()
        val budget = TraversalBudget()
        traverse(root, 0, false, layer, snapshots, budget)
        if (budget.exhausted) {
            DragShareLog.w(TAG, "node traversal budget exhausted nodes=" + budget.nodeCount)
        }
        return snapshots
    }

    private fun traverse(
        node: AccessibilityNodeInfo?,
        depth: Int,
        inheritedWebView: Boolean,
        layer: Int,
        snapshots: MutableList<AccessibilityNodeSnapshot>,
        budget: TraversalBudget,
    ) {
        if (node == null || budget.exhausted || depth > MAX_NODE_DEPTH) {
            return
        }
        budget.nodeCount++
        if (budget.nodeCount > MAX_NODE_COUNT ||
            SystemClock.uptimeMillis() - budget.startedAt > NODE_BUDGET_MILLIS
        ) {
            budget.exhausted = true
            return
        }
        val bounds = Rect()
        node.getBoundsInScreen(bounds)
        val className = asString(node.className)
        val insideWebView = inheritedWebView || "android.webkit.WebView" == className
        val childCount = node.childCount
        snapshots.add(
            AccessibilityNodeSnapshot(
                bounds = bounds,
                packageName = asString(node.packageName),
                className = className,
                viewId = node.viewIdResourceName,
                text = asString(node.text),
                contentDescription = asString(node.contentDescription),
                visible = node.isVisibleToUser,
                editable = node.isEditable,
                password = node.isPassword,
                clickable = node.isClickable,
                longClickable = node.isLongClickable,
                important = node.isImportantForAccessibility,
                leaf = childCount == 0,
                insideWebView = insideWebView,
                depth = depth,
                windowLayer = layer,
                traversalOrder = budget.nodeCount,
            ),
        )
        var index = 0
        while (index < childCount && !budget.exhausted) {
            var child: AccessibilityNodeInfo? = null
            try {
                child = node.getChild(index)
                traverse(child, depth + 1, insideWebView, layer, snapshots, budget)
            } finally {
                recycleNode(child)
            }
            index++
        }
    }

    private fun screenSize(): IntArray {
        val metrics = resources.displayMetrics
        return intArrayOf(metrics.widthPixels, metrics.heightPixels)
    }

    private fun registerSettingsObserver() {
        unregisterSettingsObserver()
        val observer: ContentObserver = object : ContentObserver(mainHandler) {
            override fun onChange(selfChange: Boolean) {
                val settings = DragShareSettings.readLocal(
                    this@DragShareAccessibilityService,
                )
                if (runtimeStarted &&
                    (
                        settings.accessibilityLongPressTimeoutMillis !=
                            runtimeLongPressTimeoutMillis ||
                            settings.accessibilityRecognitionSensitivityPercent !=
                            runtimeRecognitionSensitivityPercent
                        )
                ) {
                    stopRuntime()
                }
                applyMode()
            }
        }
        settingsObserver = observer
        contentResolver.registerContentObserver(
            DragShareSettings.settingsUri(),
            false,
            observer,
        )
    }

    private fun unregisterSettingsObserver() {
        val observer = settingsObserver ?: return
        try {
            contentResolver.unregisterContentObserver(observer)
        } catch (ignored: Throwable) {
            // Service teardown can race the resolver.
        }
        settingsObserver = null
    }

    private fun registerScreenReceiver() {
        if (screenReceiver != null) {
            return
        }
        val receiver: BroadcastReceiver = object : BroadcastReceiver() {
            override fun onReceive(context: Context?, intent: Intent?) {
                val action = if (intent == null) null else intent.action
                if (Intent.ACTION_SCREEN_OFF == action) {
                    screenInteractive = false
                    stopRuntime()
                } else if (Intent.ACTION_USER_PRESENT == action ||
                    Intent.ACTION_SCREEN_ON == action
                ) {
                    screenInteractive = isScreenInteractive()
                    applyMode()
                }
            }
        }
        screenReceiver = receiver
        val filter = IntentFilter()
        filter.addAction(Intent.ACTION_SCREEN_OFF)
        filter.addAction(Intent.ACTION_SCREEN_ON)
        filter.addAction(Intent.ACTION_USER_PRESENT)
        ContextCompat.registerReceiver(
            this,
            receiver,
            filter,
            ContextCompat.RECEIVER_NOT_EXPORTED,
        )
    }

    private fun unregisterScreenReceiver() {
        val receiver = screenReceiver ?: return
        try {
            unregisterReceiver(receiver)
        } catch (ignored: Throwable) {
            // Already unregistered during platform shutdown.
        }
        screenReceiver = null
    }

    private fun isScreenInteractive(): Boolean {
        val manager = getSystemService(Context.POWER_SERVICE) as PowerManager?
        return manager == null || manager.isInteractive
    }

    private fun isDeviceLocked(): Boolean {
        val manager = getSystemService(Context.KEYGUARD_SERVICE) as KeyguardManager?
        return manager != null && manager.isKeyguardLocked
    }

    fun trace(message: String) {
        AccessibilityTrace.record(this, message)
    }

    private class WindowRoot(
        val root: AccessibilityNodeInfo,
        val layer: Int,
    )

    private class TraversalBudget {
        val startedAt: Long = SystemClock.uptimeMillis()

        var nodeCount: Int = 0

        var exhausted: Boolean = false
    }

    companion object {
        private const val TAG = "DragShare/Accessibility"
        private const val MAX_NODE_COUNT = 4_000
        private const val MAX_NODE_DEPTH = 64
        private const val NODE_BUDGET_MILLIS = 120L
        private const val ROOT_RETRY_DELAY_MILLIS = 5_000L
        private const val MAX_ROOT_RESTARTS = 3

        private fun sortWindowRoots(roots: MutableList<WindowRoot>) {
            roots.sortWith(
                object : Comparator<WindowRoot> {
                    override fun compare(first: WindowRoot, second: WindowRoot): Int =
                        Integer.compare(second.layer, first.layer)
                },
            )
        }

        private fun contains(bounds: Rect?, x: Float, y: Float): Boolean =
            bounds != null && x >= bounds.left && x <= bounds.right &&
                y >= bounds.top && y <= bounds.bottom

        private fun asString(value: CharSequence?): String? =
            if (value == null) null else value.toString()

        @Suppress("DEPRECATION")
        private fun recycleNode(node: AccessibilityNodeInfo?) {
            if (node != null) {
                node.recycle()
            }
        }

        @Suppress("DEPRECATION")
        private fun recycleWindow(window: AccessibilityWindowInfo?) {
            if (window != null) {
                window.recycle()
            }
        }
    }
}
