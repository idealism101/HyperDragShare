package com.leaf.hyperdragshare.codex

import android.content.ClipData
import android.content.ComponentName
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.Color
import android.graphics.Insets
import android.graphics.Outline
import android.graphics.PixelFormat
import android.graphics.Rect
import android.graphics.drawable.Drawable
import android.graphics.drawable.GradientDrawable
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.net.Uri
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.util.DisplayMetrics
import android.util.TypedValue
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import android.view.ViewOutlineProvider
import android.view.WindowInsets
import android.view.WindowManager
import android.view.WindowMetrics
import android.view.animation.DecelerateInterpolator
import android.view.animation.OvershootInterpolator
import android.widget.FrameLayout
import android.widget.HorizontalScrollView
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView

internal class DragShareController(
    private val context: Context,
    policy: OverlayWindowPolicy?,
) {
    private val windowManager: WindowManager =
        context.getSystemService(Context.WINDOW_SERVICE) as WindowManager
    private val windowPolicy: OverlayWindowPolicy = policy ?: OverlayWindowPolicy.portal()
    private val mainHandler = Handler(Looper.getMainLooper())
    private val dragShareToast: DragShareToast = DragShareToast(context, windowPolicy)
    private val pendingLaunchTimeout: Runnable = Runnable {
        val stalled = session
        if (stalled != null && stalled.pendingTarget != null) {
            stalled.pendingTarget = null
            stalled.cancelled = true
            log("pending share timed out before the image was ready")
            showToast("图片准备失败")
        }
        discardPendingLaunch()
    }

    @Volatile private var active = false
    @Volatile private var destroyed = false
    // startPick*Task can create Taplus' full-screen float view before this controller reaches
    // the main thread. Keep its window suppressed during that small handoff.
    @Volatile private var pendingPortalHostFloatWindowSuppression = false
    @Volatile private var lastObservedX = -1f
    @Volatile private var lastObservedY = -1f
    @Volatile private var lastObservedEventTime = 0L

    private var lastX = 0f
    private var lastY = 0f
    private var simpleMenuStartX = 0f
    private var simpleMenuStartY = 0f
    private var simpleMenuStartPointCaptured = false
    private var simpleMenuActivationQualified = false
    private var lastHandledEventTime = Long.MIN_VALUE
    private var lastHandledAction = -1
    private var edgeDirection = 0
    private var inputSourceLogged = false
    private var gestureSource: String? = null
    // HyperOS lets a module start an activity while one of its overlays is still attached. When
    // the drop lands before the PNG finished staging, the gesture windows are gone by the time
    // the pending share launches, so a 1x1 transparent window is parked in their place.
    private var pendingLaunchAnchor: View? = null
    private var duplicateStartLogged = false
    private var screenWidth = 0
    private var screenHeight = 0
    private var topInset = 0
    private var bottomInset = 0
    // ---------- 磨砂手势状态机 ----------
    // 生命周期（只有一个样式：长按 → 环形填充 → 点环 → 磨砂菜单）：
    //   showOnMain            建 Session + 进度环，active=true（根拖拽期间压制/延迟宿主调用）；
    //   手指抬起(onFrostedGestureUp)
    //     ├─ 环未填满         → 取消：清 session、移除视图；
    //     └─ 环已填满         → active=false、宿主调用已重放，环留在原位
    //                            等待点按（ringAwaitingTap=true）。
    //   点环                  → 移除环、弹磨砂菜单（Dialog）；
    //   点菜单以外            → 菜单 Dialog 的 ACTION_OUTSIDE → dismissFrostedOnMain；
    //   点功能/目标           → 执行后 removeGestureViews 收尾。
    // 关键不变量：
    //   * ringAwaitingTap=true 时，环收到 ACTION_OUTSIDE 只做静默清理 —— 绝不重放
    //     宿主调用（那会把下一次长按刚入队的调用提前重放，毒化 Taplus 任务状态）；
    //   * dismissFrostedOnMain 仅在 wasActive 时补重放，理由同上；
    //   * 所有移除路径都经 removeGestureViews / removeFrostedViews 统一复位标志。
    private var progressRingView: ProgressRingView? = null
    private var frostedMenuView: FrostedMenuOverlayView? = null
    private var frostedMenuWindow: FrostedMenuWindow? = null
    private var ringFilled = false

    /** 环已填满、正在等待用户点按（此时抬手已收尾，环的 outside 只做静默清理）。 */
    private var ringAwaitingTap = false
    private var ringFillStartUptime = 0L
    private val ringFillRunnable = Runnable { runRingFillTick() }
    private var shareTargets: List<ShareTarget> = ArrayList()
    private var session: Session? = null
    private var settings: DragShareSettings = DragShareSettings.defaults()

    constructor(context: Context) : this(context, OverlayWindowPolicy.portal())

    fun isActive(): Boolean = active

    fun reservePortalHostFloatWindowSuppression() {
        pendingPortalHostFloatWindowSuppression = true
    }

    fun shouldSuppressPortalHostFloatWindow(): Boolean =
        active || pendingPortalHostFloatWindowSuppression
    fun show(
        content: CapturedContent?,
        initialX: Float,
        initialY: Float,
        loadedSettings: DragShareSettings?,
    ) {
        if (content == null) {
            return
        }
        runOnMain { showOnMain(content, initialX, initialY, loadedSettings) }
    }

    fun cancelActiveSession() {
        runOnMain { cancelGestureOnMain() }
    }

    fun latestPointerX(): Float = lastObservedX

    fun latestPointerY(): Float = lastObservedY

    fun acceptMotionEvent(event: MotionEvent?, beforeFinish: Runnable? = null) {
        if (event == null) {
            return
        }
        val action = event.actionMasked
        val x = event.rawX
        val y = event.rawY
        val eventTime = event.eventTime
        event.recycle()
        acceptPointerEvent(action, x, y, eventTime, "miui", beforeFinish)
    }

    fun acceptPointerEvent(
        action: Int,
        x: Float,
        y: Float,
        eventTime: Long,
        source: String?,
        beforeFinish: Runnable? = null,
    ) {
        lastObservedX = x
        lastObservedY = y
        lastObservedEventTime = eventTime
        mainHandler.post {
            if (active) {
                gestureSource = source
                session?.pointerEvents = (session?.pointerEvents ?: 0) + 1
                if (!inputSourceLogged) {
                    inputSourceLogged = true
                    log(
                        "input source=" + source +
                            " first=" + MotionEvent.actionToString(action),
                    )
                }
            }
            handleMotionOnMain(action, x, y, eventTime, source, beforeFinish)
        }
    }
    /** Called by a root-backed source after it has created an active drag session. */
    fun onRootDragSessionStarted() {
        // 原先在这里启动"阻止背景滑动"的输入拦截；该功能已按需求整体移除。
    }

    fun finishFromControlEvent() {
        // 只剩磨砂一种样式，没有"拖拽落点分享"：宿主自己的结束信号只当旁听，不驱动收尾。
    }

    fun onHostTaskCancelled() {
        // Give the queued 257/control callback a chance to finish the drag first.
        mainHandler.postDelayed(
            {
                if (active) {
                    cancelGestureOnMain()
                } else {
                    removeGestureViews()
                }
            },
            32L,
        )
    }

    fun destroy() {
        destroyed = true
        pendingPortalHostFloatWindowSuppression = false
        runOnMain {
            active = false
            session?.cancelled = true
            discardPendingLaunch()
            removeGestureViews()
            dragShareToast.close()
        }
    }
    private fun showOnMain(
        payload: CapturedContent,
        requestedInitialX: Float,
        requestedInitialY: Float,
        loadedSettings: DragShareSettings?,
    ) {
        if (destroyed) {
            pendingPortalHostFloatWindowSuppression = false
            log("showOnMain skipped: destroyed")
            return
        }
        log("showOnMain enter kind=" + payload.kind + " active=" + active)
        if (active) {
            // 自愈：磨砂模式下若环和菜单都已不在，却仍 active，说明上一轮收了没收尾干净，
            // 直接把状态复位继续，而不是把这次长按吞掉。
            if (FROSTED_RING_MENU && frostedMenuWindow == null && frostedMenuView == null &&
                progressRingView == null
            ) {
                log("stale frosted state reset on new gesture")
                active = false
            } else {
                pendingPortalHostFloatWindowSuppression = false
                log(
                    "showOnMain skipped: active with live views" +
                        " menu=" + (frostedMenuView != null) +
                        " ring=" + (progressRingView != null),
                )
                if (!duplicateStartLogged) {
                    duplicateStartLogged = true
                    log("duplicate Taplus start ignored during active drag")
                }
                return
            }
        }
        active = false
        inputSourceLogged = false
        gestureSource = null
        duplicateStartLogged = false
        lastHandledEventTime = Long.MIN_VALUE
        lastHandledAction = -1
        settings = loadedSettings ?: DragShareSettings.defaults()
        discardPendingLaunch()
        removeGestureViews()

        if (!settings.isSharingEnabled(payload.isImage())) {
            pendingPortalHostFloatWindowSuppression = false
            log("sharing disabled kind=" + payload.kind)
            return
        }
        try {
            session = Session(payload)
            refreshDisplayGeometry()
            // 只有一种样式：长按 → 环形填充 → 点环弹出磨砂菜单。
            pendingPortalHostFloatWindowSuppression = false
            startFrostedModeOnMain(payload, requestedInitialX, requestedInitialY)
        } catch (error: Throwable) {
            pendingPortalHostFloatWindowSuppression = false
            log("unable to start frosted mode", error)
            traceAccessibility(
                "overlay add failed=" + error.javaClass.simpleName +
                    ":" + error.message.toString(),
            )
            removeGestureViews()
        }
    }

    private fun safeQueryTargets(payload: CapturedContent?): List<ShareTarget> {
        return try {
            val queried = ShareTargetRepository.query(context, payload)
            ShareTargetRepository.applySettings(
                context,
                queried,
                settings,
                payload != null && payload.isImage(),
            )
        } catch (error: Throwable) {
            log("share target query failed", error)
            ArrayList()
        }
    }


    private fun handleMotionOnMain(
        action: Int,
        x: Float,
        y: Float,
        eventTime: Long,
        source: String?,
        beforeFinish: Runnable?,
    ) {
        if (!active) {
            return
        }
        if (eventTime == lastHandledEventTime && action == lastHandledAction &&
            eventTime != 0L && eventTime > lastObservedEventTime - DUPLICATE_EVENT_WINDOW_MS
        ) {
            return
        }
        lastHandledEventTime = eventTime
        lastHandledAction = action
        lastX = x
        lastY = y

        if (action == MotionEvent.ACTION_CANCEL) {
            log("gesture cancelled source=" + source)
            cancelGestureOnMain()
            return
        }
        if (action == MotionEvent.ACTION_UP) {
            log("gesture finished source=" + source)
            onFrostedGestureUp(beforeFinish)
        }
    }
    private fun keepOverlayForPendingLaunch() {
        discardPendingLaunch()
        try {
            val anchor = View(context)
            windowManager.addView(anchor, overlayParams(1, 1, "drag-share-pending"))
            pendingLaunchAnchor = anchor
        } catch (error: Throwable) {
            log("unable to park an overlay for the pending share", error)
        }
        mainHandler.postDelayed(pendingLaunchTimeout, PENDING_LAUNCH_TIMEOUT_MS)
    }

    private fun discardPendingLaunch() {
        mainHandler.removeCallbacks(pendingLaunchTimeout)
        val anchor = pendingLaunchAnchor
        pendingLaunchAnchor = null
        if (anchor != null) {
            try {
                windowManager.removeViewImmediate(anchor)
            } catch (ignored: Throwable) {
                // Already removed or never attached.
            }
        }
    }

    private fun cancelGestureOnMain() {
        if (!active) {
            return
        }
        active = false
        // 手势被系统取消同样"本次拖拽已结束"，补一次宿主延迟调用重放，避免队列残留。
        PortalHooks.flushDeferredHostCalls()
        removeGestureViews(true)
        val current = session
        if (current != null && current.pendingTarget == null) {
            current.cancelled = true
        }
    }

    // ---------------------------------------------------------------------
    // 按钮触发模式
    //
    // 长按识别后只在手指旁探出一个小按钮，手势期间不跟手、不展开菜单；
    // 抬手（ACTION_UP）后按钮才转为可触摸，点按它才展开分享菜单，
    // 再点按菜单里的目标完成分享。Android 一次手势在 ACTION_DOWN 时就定死了
    // 接收窗口，所以按钮必须等抬手后才能接管触摸，这正好也省掉了逐帧布局。
    // ---------------------------------------------------------------------

    private fun startFrostedModeOnMain(payload: CapturedContent, x: Float, y: Float) {
        // 先清掉可能残留的上一轮环/菜单（如切任务中心后未收到 ACTION_OUTSIDE 的旧菜单），
        // 否则旧菜单会挡住新会话，表现为"长按有时出不来"。
        removeFrostedViews()
        val hasPoint = x.isFinite() && x >= 0f && y.isFinite() && y >= 0f
        val initialX = if (hasPoint) x else screenWidth / 2f
        val initialY = if (hasPoint) {
            y
        } else {
            Math.max((topInset + dp(80)).toFloat(), screenHeight * 0.32f)
        }
        lastX = initialX
        lastY = initialY
        active = true
        ringFilled = false
        shareTargets = safeQueryTargets(payload)
        createProgressRing(initialX, initialY)
        if (payload.isImage()) {
            val stagedSession = session
            ImageStagingClient.stage(
                context,
                payload.bitmap,
                object : ImageStagingClient.Callback {
                    override fun onStaged(staged: Uri?) {
                        mainHandler.post {
                            if (destroyed || stagedSession == null || stagedSession.cancelled) {
                                return@post
                            }
                            stagedSession.stagedUri = staged
                            if (stagedSession.pendingTarget != null) {
                                launchPendingShare(stagedSession)
                            }
                        }
                    }

                    override fun onFailure(error: Throwable?) {
                        mainHandler.post {
                            if (!destroyed && stagedSession != null && !stagedSession.cancelled) {
                                log("frosted image staging failed", error)
                            }
                        }
                    }
                },
            )
        }
        ringFillStartUptime = SystemClock.uptimeMillis()
        mainHandler.post(ringFillRunnable)
        log("frosted ring mode started targets=" + shareTargets.size)
    }

    private fun createProgressRing(x: Float, y: Float) {
        val size = dp(RING_SIZE_DP)
        val ring = ProgressRingView(context)
        ring.setProgress(0f)
        val params = overlayParams(size, size, "DragShare frosted ring")
        // 环从创建起就"可点"：填满后由用户点它才弹菜单（点环外则取消）。
        // 触摸流在 ACTION_DOWN 就绑定原窗口，所以这个窗口即使一开始就可触摸，
        // 也不会抢走用户这次长按的事件。
        params.flags = WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
            WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL or
            WindowManager.LayoutParams.FLAG_WATCH_OUTSIDE_TOUCH or
            WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN
        params.x = Math.round(x - size / 2f).coerceIn(0, Math.max(0, screenWidth - size))
        // 环整体放在按压点上方，避免被手指遮住；中心比手指高 (size/2 + 24dp)。
        params.y = Math.round(y - size - dp(24)).coerceIn(0, Math.max(0, screenHeight - size))
        ring.setOnClickListener { onRingTappedOnMain() }
        ring.setOnTouchListener { _, event ->
            if (event.actionMasked == MotionEvent.ACTION_OUTSIDE) {
                if (ringAwaitingTap) {
                    // 抬手时手势已收尾（宿主调用已重放）：这里只做静默清理。
                    // 若走 dismissFrostedOnMain 会 flushDeferredHostCalls，
                    // 把下一次长按刚挂起的宿主调用提前重放，导致后续长按全部失灵。
                    log("ring ACTION_OUTSIDE -> quiet cleanup (awaiting tap)")
                    ringAwaitingTap = false
                    removeGestureViews()
                } else {
                    // 点环以外 = 取消
                    log("ring ACTION_OUTSIDE -> cancel")
                    dismissFrostedOnMain()
                }
                true
            } else {
                false
            }
        }
        try {
            windowManager.addView(ring, params)
            progressRingView = ring
        } catch (error: Throwable) {
            progressRingView = null
            log("unable to add frosted ring", error)
        }
    }

    private fun runRingFillTick() {
        if (!active || ringFilled) {
            return
        }
        val elapsed = SystemClock.uptimeMillis() - ringFillStartUptime
        val p = (elapsed.toFloat() / RING_FILL_MS).coerceIn(0f, 1f)
        progressRingView?.setProgress(p)
        if (p >= 1f) {
            ringFilled = true
            onRingFull()
        } else {
            mainHandler.postDelayed(ringFillRunnable, RING_FILL_TICK_MS)
        }
    }

    /**
     * 环填满：**不自动弹菜单**，环留在原位等用户点它（见 [onRingTappedOnMain]）。
     * 不做超时：只有点环（出菜单）或点环以外（取消）才会消失。
     */
    private fun onRingFull() {
        ringAwaitingTap = true
        log("frosted ring filled; waiting for tap")
    }

    /** 用户点了已填满的环 → 收起环并弹出菜单。 */
    private fun onRingTappedOnMain() {
        if (!ringFilled) {
            log("ring tapped before filled; ignored")
            return
        }
        log("frosted ring tapped -> show menu")
        ringAwaitingTap = false
        removeProgressRing()
        showFrostedMenuOnMain()
    }

    private fun removeProgressRing() {
        val ring = progressRingView
        if (ring != null) {
            try {
                windowManager.removeViewImmediate(ring)
            } catch (ignored: Throwable) {
                // Already removed.
            }
            progressRingView = null
        }
    }

    private fun showFrostedMenuOnMain() {
        if (frostedMenuView != null) {
            // 残留旧菜单：先拆掉再建新的（注意 ringFilled 会被清，需恢复，
            // 否则抬手会被当成"未填满取消"）。
            removeFrostedViews()
            ringFilled = true
        }
        val payload = session?.payload ?: return
        val view = FrostedMenuOverlayView(
            context,
            shareTargets,
            { iconForTarget(it) },
            { handleFrostedFunction(it) },
            { launchFrostedTarget(it) },
            settings.frostedPlateAlphaPercent,
            settings.frostedDarknessPercent,
        )
        frostedMenuView = view
        val sideMargin = dp(RING_MENU_SIDE_MARGIN_DP)
        val menuW = (screenWidth - 2 * sideMargin).coerceAtLeast(1)
        val menuH = dp(RING_MENU_HEIGHT_DP).coerceAtLeast(1)
        val params = overlayParams(menuW, menuH, "DragShare frosted menu")
        params.x = sideMargin
        params.y = Math.max(topInset, ((screenHeight - menuH) / 2f).toInt())
        // 本地模糊窗口：Dialog + window.setBackgroundBlurRadius，只糊背板范围、四周不压暗。
        // （不用 FLAG_BLUR_BEHIND —— 它会糊窗口下方全部内容，HyperOS 上表现为整屏模糊。）
        try {
            val window = FrostedMenuWindow(
                context,
                windowManager,
                view,
                params,
                dp(settings.frostedBlurRadiusDp),
            ) { dismissFrostedOnMain() }
            frostedMenuWindow = window
            window.show()
            log("frosted menu shown targets=" + shareTargets.size)
        } catch (error: Throwable) {
            log("unable to show frosted menu", error)
            frostedMenuView = null
            frostedMenuWindow = null
        }
    }

    private fun onFrostedGestureUp(afterDeactivate: Runnable?) {
        // 抬手即"本次拖拽结束"：重放被压制的宿主调用（cancelOffset 等），让宿主把流水线跑完。
        active = false
        log(
            "frosted gesture up ringFilled=" + ringFilled +
                " replay=" + (afterDeactivate != null),
        )
        afterDeactivate?.run()
        if (!ringFilled) {
            // 没填满就松手 = 取消
            val cur = session
            if (cur != null && cur.pendingTarget == null) {
                cur.cancelled = true
            }
            removeGestureViews(true)
            return
        }
        // 已填满：环留在原处等用户点它（点环才出菜单，见 onRingTappedOnMain）。
        log("frosted ring ready; waiting for user tap")
    }

    private fun dismissFrostedOnMain() {
        // 关键：彻底结束磨砂手势态。面板/环是被"点外部/超时"收起的，此前不会经过抬手分支，
        // 若不在这里复位 active，showOnMain 的 `if (active) return` 会把之后所有长按都吞掉。
        log("dismissFrostedOnMain (outside/timeout)")
        val wasActive = active
        active = false
        ringAwaitingTap = false
        mainHandler.removeCallbacks(ringFillRunnable)
        // 兜底：仅当手势仍在进行（宿主延迟调用还没重放）时才补重放；
        // 否则会把下一次长按刚挂起的宿主调用提前重放，导致其失灵。
        if (wasActive) {
            PortalHooks.flushDeferredHostCalls()
        }
        val cur = session
        if (cur != null && cur.pendingTarget == null) {
            cur.cancelled = true
        }
        removeGestureViews(true)
    }

    private fun launchFrostedTarget(target: ShareTarget) {
        val finished = session
        if (finished == null) {
            removeGestureViews(true)
            return
        }
        if (!finished.payload.isImage() || target.isSaveToLocal() || finished.stagedUri != null) {
            launchShare(finished, target)
        } else {
            finished.pendingTarget = target
            keepOverlayForPendingLaunch()
            showToast("正在准备图片")
        }
        removeGestureViews(true)
    }

    private fun handleFrostedFunction(fn: FrostedFunction) {
        val finished = session
        val payload = finished?.payload
        when (fn) {
            FrostedFunction.COPY -> copyToClipboard(payload, finished?.let { publishSharedCopy(it) })
            FrostedFunction.SAVE -> {
                if (payload?.isImage() == true) {
                    saveImageLocally(payload.bitmap)
                } else {
                    copyToClipboard(payload, finished?.let { publishSharedCopy(it) })
                    showToast("已保存到剪贴板")
                }
            }
            FrostedFunction.SEGMENT -> openTextSegmentation(payload?.text)
            FrostedFunction.SHARE -> openSystemShare(payload)
            FrostedFunction.TRANSLATE -> {
                val app = settings.translateAppPackage
                val text = payload?.text
                val usable = !app.isNullOrEmpty() && !text.isNullOrEmpty()
                if (usable) {
                    // 保底：先把文字放进剪贴板。部分翻译应用（如小爱翻译）不消费外部传入的文字，
                    // 打开后长按输入框即可粘贴。
                    copyToClipboard(payload, finished?.let { publishSharedCopy(it) })
                }
                if (usable && launchTranslateApp(app.orEmpty(), text.orEmpty())) {
                    showToast("已打开翻译应用，文字已复制，长按输入框可粘贴")
                } else {
                    showToast(
                        if (usable) {
                            "已复制文字，可自行打开翻译应用粘贴"
                        } else {
                            "已复制文字（可在设置里指定翻译应用）"
                        },
                    )
                }
            }
        }
        if (finished != null && finished.pendingTarget == null) {
            finished.cancelled = true
        }
        removeGestureViews(true)
    }

    /**
     * 把选中的文字交给指定翻译应用。依次尝试：分享文本 → 文本处理(PROCESS_TEXT) → 系统翻译，
     * 都不行就退到"复制后启动它的主界面"。成功返回 true。
     */
    private fun launchTranslateApp(packageName: String, text: String): Boolean {
        logTranslateAppEntryPoints(packageName)
        // 后台把该应用在系统里真实注册的入口写进日志（dumpsys，非阻塞）。
        TranslateAppDiagnostics.logEntryPoints(packageName)
        // 0) 本进程内已经试成功过的组合，直接用（避免重复试探造成闪屏）。
        val cached = cachedTranslateRecipe
        if (cached != null && cached.first == packageName) {
            if (startTranslateIntent(
                    intent = translateIntent(cached.second, text),
                    packageName = packageName,
                    component = ComponentName(packageName, cached.third),
                    action = cached.second,
                )
            ) {
                return true
            }
            cachedTranslateRecipe = null
        }
        // 1) 常规隐式启动（只用包名过滤）。
        for ((action, _) in TRANSLATE_PAYLOADS) {
            val intent = applyTranslateExtras(Intent(action).setType("text/plain"), text)
            if (startTranslateIntent(intent, packageName, null, action)) {
                return true
            }
        }
        // 2) 显式启动：把这包里所有导出的 Activity 按"翻译相关"优先排序，逐个试。
        //    MIUI 的入口（如 IntentAiTranslateServiceActivity / WordsTransActivity）不注册标准 action，
        //    只能靠显式 ComponentName 拉起；显式启动不校验 intent-filter，只看 exported。
        for ((index, activity) in translateCandidateActivities(packageName).withIndex()) {
            val component = ComponentName(packageName, activity)
            for ((action, _) in TRANSLATE_PAYLOADS) {
                val intent = applyTranslateExtras(Intent(action).setType("text/plain"), text)
                if (startTranslateIntent(intent, packageName, component, action)) {
                    rememberTranslateRecipe(packageName, activity, action)
                    return true
                }
            }
            // 最可能的入口再猜几个 MIUI 风格 action（标准 action 之外的私有约定）。
            if (index == 0) {
                for (action in TRANSLATE_EXTRA_ACTIONS) {
                    val intent = applyTranslateExtras(Intent(action).setType("text/plain"), text)
                    if (startTranslateIntent(intent, packageName, component, action)) {
                        rememberTranslateRecipe(packageName, activity, action)
                        return true
                    }
                }
            }
            // 少数入口只吃"裸数据"，不认 action 也不需要 type。
            val bare = applyTranslateExtras(Intent(Intent.ACTION_VIEW).setType("text/plain"), text)
            if (startTranslateIntent(bare, packageName, component, "VIEW")) {
                rememberTranslateRecipe(packageName, activity, "VIEW")
                return true
            }
        }
        // 3) 启动器入口 / 主界面（文字已在剪贴板）。
        val launcherIntent = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER)
        if (startTranslateIntent(launcherIntent, packageName, null, "LAUNCHER")) {
            return true
        }
        try {
            val launch = context.packageManager.getLaunchIntentForPackage(packageName)
                ?: throw IllegalStateException("no launcher entry")
            if (startTranslateIntent(launch, packageName, null, "LAUNCHER_EXPLICIT")) {
                return true
            }
        } catch (error: Throwable) {
            log("unable to open translate app package=" + packageName, error)
        }
        // 4) 最后兜底：交给系统自己弹"文本处理"选择器（不指定包名），
        //    MIUI 的翻译/问小爱入口通常就在这里，由系统去拉起它，不需要我们跨应用启动。
        val chooser = applyTranslateExtras(Intent(Intent.ACTION_PROCESS_TEXT).setType("text/plain"), text)
        if (startTranslateIntent(chooser, null, null, "PROCESS_TEXT_CHOOSER")) {
            log("translate fell back to system text-processing chooser")
            return true
        }
        return false
    }

    private fun translateIntent(action: String, text: String): Intent =
        applyTranslateExtras(Intent(action).setType("text/plain"), text)

    /** 把文字塞进所有常见 extra key（见 [TRANSLATE_EXTRA_KEYS]）。 */
    private fun applyTranslateExtras(intent: Intent, text: String): Intent {
        TRANSLATE_EXTRA_KEYS.forEach { key ->
            intent.putExtra(key, text)
        }
        return intent
    }

    private fun rememberTranslateRecipe(packageName: String, activity: String, action: String) {
        cachedTranslateRecipe = Triple(packageName, action, activity)
        log("translate recipe learned component=" + activity + " action=" + action)
    }

    private fun startTranslateIntent(
        intent: Intent,
        packageName: String?,
        component: ComponentName?,
        action: String,
    ): Boolean = try {
        when {
            component != null -> intent.component = component
            packageName != null -> intent.setPackage(packageName)
        }
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        context.startActivity(intent)
        log(
            "translate launched action=" + action +
                " target=" + (component?.flattenToShortString() ?: packageName ?: "(system chooser)"),
        )
        true
    } catch (error: Throwable) {
        log(
            "translate attempt failed action=" + action +
                " target=" + (component?.flattenToShortString() ?: packageName ?: "(system chooser)"),
            error,
        )
        false
    }

    /**
     * 该包里所有"可能用于翻译"的导出 Activity，按相关度排序。
     *
     * 不再依赖 intent-filter 探测（MIUI 的翻译入口不注册标准 action），而是直接列组件的
     * exported 状态；显式启动只看 exported，所以这样能找到它们。
     */
    private fun translateCandidateActivities(packageName: String): List<String> {
        val packageManager = context.packageManager
        val activities = try {
            val info = if (Build.VERSION.SDK_INT >= 33) {
                packageManager.getPackageInfo(
                    packageName,
                    PackageManager.PackageInfoFlags.of(PackageManager.GET_ACTIVITIES.toLong()),
                )
            } else {
                @Suppress("DEPRECATION")
                packageManager.getPackageInfo(packageName, PackageManager.GET_ACTIVITIES)
            }
            info.activities ?: return emptyList()
        } catch (error: Throwable) {
            log("unable to list translate activities", error)
            return emptyList()
        }
        return activities
            .asSequence()
            .filter { it.exported && it.enabled }
            .map { it.name }
            .filter { name ->
                val lower = name.lowercase()
                IGNORED_TRANSLATE_ACTIVITY_HINTS.none { lower.contains(it) }
            }
            .sortedBy { name ->
                val lower = name.lowercase()
                val index = TRANSLATE_ACTIVITY_HINTS.indexOfFirst { lower.contains(it) }
                if (index >= 0) index else TRANSLATE_ACTIVITY_HINTS.size
            }
            .take(MAX_TRANSLATE_ACTIVITY_ATTEMPTS)
            .toList()
    }

    /** 把目标应用的入口与启动器入口写进日志，便于定位"打不开翻译应用"的原因。 */
    private fun logTranslateAppEntryPoints(packageName: String) {
        try {
            val packageManager = context.packageManager
            val info = if (Build.VERSION.SDK_INT >= 33) {
                packageManager.getPackageInfo(
                    packageName,
                    PackageManager.PackageInfoFlags.of(
                        PackageManager.GET_ACTIVITIES.toLong(),
                    ),
                )
            } else {
                @Suppress("DEPRECATION")
                packageManager.getPackageInfo(packageName, PackageManager.GET_ACTIVITIES)
            }
            log("translate app inspect package=" + packageName)
            info.activities?.forEach { activity ->
                log(
                    "  entry name=" + activity.name +
                        " exported=" + activity.exported +
                        " enabled=" + activity.enabled,
                )
            }
            log(
                "  launcher entry=" + (
                    packageManager.getLaunchIntentForPackage(packageName)
                        ?.component?.flattenToShortString() ?: "none"
                    ),
            )
        } catch (error: Throwable) {
            log("unable to inspect translate app " + packageName, error)
        }
    }

    private fun openSystemShare(payload: CapturedContent?) {
        if (payload == null) {
            return
        }
        try {
            val intent = Intent(Intent.ACTION_SEND)
            if (payload.isImage()) {
                val uri = session?.stagedUri ?: session?.let { publishSharedCopy(it) }
                if (uri == null) {
                    showToast("图片未就绪")
                    return
                }
                intent.type = "image/*"
                intent.putExtra(Intent.EXTRA_STREAM, uri)
                intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_ACTIVITY_NEW_TASK)
            } else {
                val text = payload.text
                if (text.isNullOrBlank()) {
                    showToast("没有可分享的文字")
                    return
                }
                intent.type = "text/plain"
                intent.putExtra(Intent.EXTRA_TEXT, text)
                intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            context.startActivity(Intent.createChooser(intent, "分享到"))
            log("opened system share")
        } catch (error: Throwable) {
            log("system share failed", error)
            showToast("分享失败")
        }
    }

    private fun removeFrostedViews() {
        if (frostedMenuWindow != null || frostedMenuView != null || progressRingView != null) {
            log(
                "removeFrostedViews menu=" + (frostedMenuWindow != null) +
                    " ring=" + (progressRingView != null),
            )
        }
        // 任何一条磨砂收尾路径都要复位手势态，避免 active 卡死导致之后长按无效。
        active = false
        mainHandler.removeCallbacks(ringFillRunnable)
        val ring = progressRingView
        if (ring != null) {
            try {
                windowManager.removeViewImmediate(ring)
            } catch (ignored: Throwable) {
                // Already removed.
            }
            progressRingView = null
        }
        val menu = frostedMenuWindow
        if (menu != null) {
            menu.dispose()
            frostedMenuWindow = null
        }
        frostedMenuView = null
        ringFilled = false
    }

    private fun launchPendingShare(pendingSession: Session) {
        val target = pendingSession.pendingTarget
        if (target == null || (pendingSession.stagedUri == null && !target.isSaveToLocal())) {
            return
        }
        pendingSession.pendingTarget = null
        log(
            "pending share launching afterMs=" +
                (SystemClock.uptimeMillis() - pendingSession.startedAtUptime),
        )
        try {
            launchShare(pendingSession, target)
        } finally {
            discardPendingLaunch()
        }
    }
    private fun launchShare(shareSession: Session, target: ShareTarget) {
        if (target.isSaveToLocal()) {
            saveImageLocally(shareSession.payload.bitmap)
            shareSession.cancelled = true
            return
        }
        if (target.isCopyToClipboard()) {
            // The clipboard hands the image to whichever app the user pastes into, so it needs
            // the same shared copy an explicit share does.
            copyToClipboard(
                shareSession.payload,
                publishSharedCopy(shareSession) ?: shareSession.stagedUri,
            )
            shareSession.cancelled = true
            return
        }
        if (target.isTextSegmentation()) {
            openTextSegmentation(shareSession.payload.text)
            shareSession.cancelled = true
            return
        }
        try {
            log("drop target=" + target.component?.flattenToShortString())
            val staged = shareSession.stagedUri
            val shared = publishSharedCopy(shareSession)
            ShareLauncher.launch(
                context,
                shareSession.payload,
                target,
                shared ?: staged,
                staged,
                dragShareToast,
            )
        } catch (error: Throwable) {
            log("share launch failed", error)
        }
        shareSession.cancelled = true
    }

    /**
     * Publishes the shared media copy of an already staged image, or returns null when there is
     * nothing to publish and when the shared collection is unavailable.
     *
     * A shared copy is a real file in the user's `Pictures` tree and shows up in gallery apps
     * while it exists, so it is created here — once the user has actually dropped the image on a
     * recipient — instead of for every image the preview stages.
     */
    private fun publishSharedCopy(shareSession: Session): Uri? {
        val staged = shareSession.stagedUri ?: return null
        return ImageStagingClient.publishShared(context, staged)
    }

    private fun copyToClipboard(payload: CapturedContent?, stagedImage: Uri?) {
        val image = payload != null && payload.isImage()
        try {
            val clipboard = context.getSystemService(ClipboardManager::class.java)
                ?: throw IllegalStateException("Clipboard service is unavailable")
            val clip: ClipData
            if (image) {
                if (stagedImage == null) {
                    throw IllegalArgumentException("Image URI is not ready")
                }
                clip = ClipData.newUri(
                    context.contentResolver,
                    "drag-share-image",
                    stagedImage,
                )
            } else {
                val text = payload?.text
                if (text == null || text.trim { it <= ' ' }.isEmpty()) {
                    throw IllegalArgumentException("Text is empty")
                }
                clip = ClipData.newPlainText("drag-share-text", text)
            }
            clipboard.setPrimaryClip(clip)
            log("copied " + (if (image) "image" else "text") + " to clipboard")
            showToast(if (image) "图片已复制到剪贴板" else "文字已复制到剪贴板")
        } catch (error: Throwable) {
            log("clipboard copy failed", error)
            showToast(if (image) "复制图片失败" else "复制文字失败")
        }
    }

    private fun openTextSegmentation(text: String?) {
        if (text == null || text.trim { it <= ' ' }.isEmpty()) {
            showToast("没有可分词的文字")
            return
        }
        try {
            val intent = TextSegmentationActivity.createIntent(
                text,
                Math.round(lastX),
                Math.round(lastY),
            )
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            context.startActivity(intent)
            log("opened text segmentation")
        } catch (error: Throwable) {
            log("open text segmentation failed", error)
            showToast("无法打开文本分词")
        }
    }

    private fun saveImageLocally(bitmap: Bitmap?) {
        if (bitmap == null || bitmap.isRecycled) {
            showToast("保存图片失败")
            return
        }
        val worker = Thread({
            try {
                LocalImageSaver.save(context, bitmap)
                log("saved image as PNG")
                traceAccessibility("local save succeeded")
                mainHandler.post { showToast("已保存到本地", android.widget.Toast.LENGTH_LONG) }
            } catch (error: Throwable) {
                log("local image save failed", error)
                traceAccessibility("local save failed=" + error.javaClass.simpleName)
                mainHandler.post { showToast("保存图片失败", android.widget.Toast.LENGTH_LONG) }
            }
        }, "drag-share-local-save")
        worker.start()
    }
    private fun refreshDisplayGeometry() {
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                val metrics = windowManager.currentWindowMetrics
                screenWidth = metrics.bounds.width()
                screenHeight = metrics.bounds.height()
                val insets = metrics.windowInsets.getInsetsIgnoringVisibility(
                    WindowInsets.Type.systemBars() or WindowInsets.Type.displayCutout(),
                )
                topInset = insets.top
                bottomInset = insets.bottom
            } else {
                throw UnsupportedOperationException("legacy display metrics")
            }
        } catch (ignored: Throwable) {
            val metrics = DisplayMetrics()
            @Suppress("DEPRECATION")
            windowManager.defaultDisplay.getRealMetrics(metrics)
            screenWidth = metrics.widthPixels
            screenHeight = metrics.heightPixels
            topInset = 0
            bottomInset = 0
        }
    }

    private fun overlayParams(width: Int, height: Int, title: String): WindowManager.LayoutParams {
        val params = WindowManager.LayoutParams()
        params.type = windowPolicy.windowType
        params.format = PixelFormat.TRANSLUCENT
        params.flags = WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
            WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE or
            WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN
        params.gravity = Gravity.TOP or Gravity.START
        params.width = width
        params.height = height
        params.x = 0
        params.y = 0
        params.title = title
        params.layoutInDisplayCutoutMode =
            WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_SHORT_EDGES
        return params
    }

    /** 只剩磨砂一种样式：收尾就是拆掉环与菜单。 */
    private fun removeGestureViews(@Suppress("UNUSED_PARAMETER") animatePreviewExit: Boolean = false) {
        removeFrostedViews()
        ringAwaitingTap = false
    }
    private fun iconForTarget(target: ShareTarget): Drawable? =
        ShareTargetRepository.iconForDisplay(target)

    private fun roundDrawable(color: Int, radiusDp: Int): GradientDrawable {
        val drawable = GradientDrawable()
        drawable.setColor(color)
        drawable.cornerRadius = dp(radiusDp).toFloat()
        return drawable
    }

    private fun dp(value: Int): Int = Math.round(
        TypedValue.applyDimension(
            TypedValue.COMPLEX_UNIT_DIP,
            value.toFloat(),
            context.resources.displayMetrics,
        ),
    )

    private fun dpFloat(value: Float): Float = TypedValue.applyDimension(
        TypedValue.COMPLEX_UNIT_DIP,
        value,
        context.resources.displayMetrics,
    )

    private fun showToast(message: String) {
        dragShareToast.show(message)
    }

    private fun showToast(message: String, duration: Int) {
        dragShareToast.show(message, duration)
    }

    private fun runOnMain(runnable: Runnable) {
        if (Looper.myLooper() === Looper.getMainLooper()) {
            runnable.run()
        } else {
            mainHandler.post(runnable)
        }
    }

    private fun traceAccessibility(message: String) {
        if ("accessibility" == windowPolicy.sourceName) {
            AccessibilityTrace.record(context, message)
        }
    }
    private class Session(val payload: CapturedContent) {
        val startedAtUptime: Long = SystemClock.uptimeMillis()
        var stagedUri: Uri? = null
        var stagedAtUptime: Long = 0L
        var pointerEvents: Int = 0
        var pendingTarget: ShareTarget? = null
        var cancelled: Boolean = false
    }

    companion object {
        /** 翻译按钮把文字交给目标应用时，依次尝试的 (action, extraKey)。 */
        private val TRANSLATE_PAYLOADS = listOf(
            Intent.ACTION_SEND to Intent.EXTRA_TEXT,
            Intent.ACTION_PROCESS_TEXT to Intent.EXTRA_PROCESS_TEXT,
            Intent.ACTION_TRANSLATE to Intent.EXTRA_TEXT,
        )

        /** Activity 名里出现这些词就优先试（越靠前越优先）。 */
        private val TRANSLATE_ACTIVITY_HINTS = listOf(
            "intentaitranslate",
            "intenttrans",
            "wordstrans",
            "wordstranslate",
            "texttranslate",
            "translate",
            "translation",
            "trans",
        )

        /** Activity 名里出现这些词就跳过（设置/关于/隐私/语音/字幕等显然不是文字翻译入口）。 */
        private val IGNORED_TRANSLATE_ACTIVITY_HINTS = listOf(
            "setting",
            "about",
            "guide",
            "privacy",
            "subtitle",
            "voice",
            "phrase",
            "sns",
            "feedback",
            "debug",
        )

        /**
         * 一次把文字塞进所有常见 extra key：不同 ROM/应用读的键不一样
         * （标准是 EXTRA_TEXT / EXTRA_PROCESS_TEXT，MIUI 系还有自定义键）。
         * 多塞几个没有副作用，能显著提高"自动填入输入框"的成功率。
         */
        private val TRANSLATE_EXTRA_KEYS = listOf(
            Intent.EXTRA_TEXT,
            Intent.EXTRA_PROCESS_TEXT,
            Intent.EXTRA_TITLE,
            "text",
            "content",
            "translate_text",
            "translate_content",
            "intent_ai_translate_text",
            "intent_text",
            "extra_text",
            "src_text",
            "source_text",
            "query",
        )

        /** 对最可能的入口额外猜几个 MIUI 风格 action（标准 action 之外的）。 */
        private val TRANSLATE_EXTRA_ACTIONS = listOf(
            "com.xiaomi.aiasst.vision.action.TRANSLATE",
            "com.xiaomi.aiasst.action.TRANSLATE",
            "miui.intent.action.TRANSLATE",
        )

        private const val MAX_TRANSLATE_ACTIVITY_ATTEMPTS = 4

        /** 试成功过的 (packageName, action, activity)，仅本进程内缓存。 */
        @Volatile
        private var cachedTranslateRecipe: Triple<String, String, String>? = null

        private const val TAG = "DragShare/UI"
        private const val PENDING_LAUNCH_TIMEOUT_MS = 8_000L
        private const val DUPLICATE_EVENT_WINDOW_MS = 2L
        // 磨砂进度环 + 点环弹出磨砂菜单（唯一保留的样式）。
        private const val FROSTED_RING_MENU = true
        private const val RING_FILL_MS = 420L
        private const val RING_FILL_TICK_MS = 16L
        private const val RING_SIZE_DP = 48
        private const val RING_MENU_SIDE_MARGIN_DP = 24
        private const val RING_MENU_HEIGHT_DP = 460
        private const val FROSTED_BLUR_RADIUS_DP = 28
        private const val TRIGGER_BUTTON_SIZE_DP = 48
        private const val TRIGGER_BUTTON_TIMEOUT_MS = 10_000L
        private const val TRIGGER_BUTTON_LABEL = "分享"
        private const val NEAR_HAND_TILT_THRESHOLD = 0.14f

        private fun rotationVectorRoll(values: FloatArray): Float {
            return try {
                val rotationMatrix = FloatArray(9)
                val orientation = FloatArray(3)
                SensorManager.getRotationMatrixFromVector(rotationMatrix, values)
                SensorManager.getOrientation(rotationMatrix, orientation)
                orientation[2]
            } catch (ignored: Throwable) {
                Float.NaN
            }
        }

        private fun applyAbsoluteOpacity(color: Int, percent: Int): Int {
            val alpha = Math.round(255f * (Math.max(0, Math.min(100, percent)) / 100f))
            return (color and 0x00FFFFFF) or (alpha shl 24)
        }

        fun simpleMenuBackgroundOpacityFraction(percent: Int): Float =
            Math.max(0, Math.min(100, percent)) / 100f

        private fun log(message: String) {
            DragShareLog.i(TAG, message)
        }

        private fun log(message: String, error: Throwable?) {
            DragShareLog.w(TAG, message, error)
        }
    }
}
