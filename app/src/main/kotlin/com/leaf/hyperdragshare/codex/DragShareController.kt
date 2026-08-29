package com.leaf.hyperdragshare.codex

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
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

class DragShareController(
    private val context: Context,
    policy: OverlayWindowPolicy?,
) {
    private val windowManager: WindowManager =
        context.getSystemService(Context.WINDOW_SERVICE) as WindowManager
    private val backgroundTouchBlocker: BackgroundTouchBlocker = BackgroundTouchBlocker(context)
    private val windowPolicy: OverlayWindowPolicy = policy ?: OverlayWindowPolicy.portal()
    private val mainHandler = Handler(Looper.getMainLooper())
    private val dragShareToast: DragShareToast = DragShareToast(context, windowPolicy)
    private val edgeScrollRunnable: Runnable = object : Runnable {
        override fun run() {
            if (!active || !menuShown) {
                return
            }
            if (isCircleStyle()) {
                val circle = circleMenuView
                if (circle == null || circleScrollDirection == 0) {
                    return
                }
                circle.scrollWindow(circleScrollDirection)
                updateSelectedTarget(lastX, lastY)
                mainHandler.postDelayed(this, circleScrollIntervalMs())
                return
            }
            val modernMenu = modernMenuView
            val horizontalScroll = menuScroll
            if (edgeDirection == 0 || (horizontalScroll == null &&
                    menuVerticalScroll == null &&
                    modernMenu == null)
            ) {
                return
            }
            val now = SystemClock.uptimeMillis()
            val elapsed = if (lastEdgeScrollUptime == 0L) {
                16L
            } else {
                Math.min(48L, Math.max(1L, now - lastEdgeScrollUptime))
            }
            lastEdgeScrollUptime = now
            val distanceWithRemainder = dpFloat(settings.scrollSpeedDpPerSecond.toFloat()) *
                edgeScrollSpeedMultiplier() * elapsed / 1000f +
                edgeScrollRemainderPx
            val distance = distanceWithRemainder.toInt()
            edgeScrollRemainderPx = distanceWithRemainder - distance
            if (distance > 0) {
                if (modernMenu != null) {
                    modernMenu.scrollByPixels(edgeDirection * distance)
                } else if (horizontalScroll != null) {
                    horizontalScroll.scrollBy(edgeDirection * distance, 0)
                } else {
                    menuVerticalScroll?.scrollBy(0, edgeDirection * distance)
                }
                updateSelectedTarget(lastX, lastY)
            }
            mainHandler.postDelayed(this, 16L)
        }
    }
    private val circleExpandRunnable: Runnable = object : Runnable {
        override fun run() {
            if (!active || menuShown || circleMenuView == null ||
                circlePendingEdge == CircleMenuGeometry.EDGE_NONE
            ) {
                return
            }
            val edge = CircleMenuGeometry.nearestEdge(
                lastX,
                lastY,
                screenWidth,
                screenHeight,
                circleTriggerPx().toFloat(),
            )
            if (edge == circlePendingEdge) {
                circleEdge = edge
                showMenuOnMain()
            }
        }
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
    private var duplicateStartLogged = false
    private var backgroundBlockAttempted = false
    private var portalGlowStartedLogged = false
    private var portalGlowExpandedLogged = false
    private var lastEdgeScrollUptime = 0L
    private var edgeScrollRemainderPx = 0f
    private var circleEdge = CircleMenuGeometry.EDGE_NONE
    private var circlePendingEdge = CircleMenuGeometry.EDGE_NONE
    private var circleScrollDirection = 0
    private var screenWidth = 0
    private var screenHeight = 0
    private var topInset = 0
    private var bottomInset = 0
    private var menuTop = 0
    private var menuHeight = 0
    private var menuTriggerTop = 0
    private var menuLeft = 0
    private var menuWidth = 0
    private var simpleMenuPosition = DragShareSettings.SIMPLE_MENU_POSITION_BOTTOM
    private var configuredSimpleMenuPosition = DragShareSettings.SIMPLE_MENU_POSITION_BOTTOM
    private var nearHandSide = DragShareSettings.SIMPLE_MENU_POSITION_RIGHT
    private var nearHandSideLocked = false

    private var previewView: View? = null
    private var modernPreviewView: ModernPreviewOverlayView? = null
    private var modernPreviewWindow: ModernOverlayWindow? = null
    private var previewParams: WindowManager.LayoutParams? = null
    private var previewWidth = 0
    private var previewHeight = 0
    private var modernPreviewEnterStarted = false
    // Keep an exiting preview addressable until its animation completes so a new drag can
    // tear it down immediately instead of briefly stacking two overlay windows.
    private var previewExitGeneration = 0
    private var exitingPreviewView: View? = null
    private var exitingModernPreviewView: ModernPreviewOverlayView? = null
    private var exitingModernPreviewWindow: ModernOverlayWindow? = null

    private var glowView: PortalGlowView? = null
    private var glowParams: WindowManager.LayoutParams? = null

    private var menuView: View? = null
    private var modernMenuView: ModernMenuOverlayView? = null
    private var modernMenuWindow: ModernOverlayWindow? = null
    private var modernMenuDisposeRunnable: Runnable? = null
    private var linearMenuFadeGeneration = 0
    private var fadingLinearMenuView: View? = null
    private var menuParams: WindowManager.LayoutParams? = null
    private var menuScroll: HorizontalScrollView? = null
    private var menuVerticalScroll: ScrollView? = null
    private var menuRow: LinearLayout? = null
    private val menuItems: MutableList<View> = ArrayList()
    private var circleMenuView: CircleMenuOverlayView? = null
    private var circleMenuParams: WindowManager.LayoutParams? = null
    private var shareTargets: List<ShareTarget> = ArrayList()
    private var selectedTarget: ShareTarget? = null
    private var menuShown = false
    private var sensorManager: SensorManager? = null
    private var nearHandSensor: Sensor? = null
    private var nearHandSensorRegistered = false
    private val nearHandSensorListener: SensorEventListener = object : SensorEventListener {
        override fun onSensorChanged(event: SensorEvent?) {
            if (nearHandSideLocked || event == null ||
                event.values == null || event.values.size == 0
            ) {
                return
            }
            val tilt = if (event.sensor != null &&
                event.sensor.type == Sensor.TYPE_ROTATION_VECTOR
            ) {
                rotationVectorRoll(event.values)
            } else {
                event.values[0] / SensorManager.GRAVITY_EARTH
            }
            if (!tilt.isFinite() || Math.abs(tilt) < NEAR_HAND_TILT_THRESHOLD) {
                return
            }
            val side = if (GestureMath.nearHandMenuOnRight(tilt)) {
                DragShareSettings.SIMPLE_MENU_POSITION_RIGHT
            } else {
                DragShareSettings.SIMPLE_MENU_POSITION_LEFT
            }
            if (side != nearHandSide) {
                nearHandSide = side
                mainHandler.post { updateNearHandMenuPosition() }
            }
        }

        override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) {
            // No calibration state is needed for the coarse left/right decision.
        }
    }

    private var session: Session? = null
    private var settings: DragShareSettings = DragShareSettings.defaults()
    private var palette: OverlayColors = OverlayColors.light()

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
        DragShareLog.d(
            TAG,
            "pointer received source=" + source +
                " action=" + MotionEvent.actionToString(action) +
                " point=" + Math.round(x) + "," + Math.round(y) +
                " active=" + active +
                " time=" + eventTime,
        )
        lastObservedX = x
        lastObservedY = y
        lastObservedEventTime = eventTime
        mainHandler.post {
            if (active && !inputSourceLogged) {
                inputSourceLogged = true
                log(
                    "input source=" + source +
                        " action=" + MotionEvent.actionToString(action) +
                        " point=" + Math.round(x) + "," + Math.round(y),
                )
            }
            handleMotionOnMain(action, x, y, eventTime, source, beforeFinish)
        }
    }
    /** Called by a root-backed source after it has created an active drag session. */
    fun onRootDragSessionStarted() {
        runOnMain {
            if (active) {
                startBackgroundBlockerIfEnabled()
            }
        }
    }

    fun finishFromControlEvent() {
        mainHandler.post {
            if (!active) {
                return@post
            }
            handlePointerOnMain(lastObservedX, lastObservedY)
            // The fallback path does not have a physical root ACTION_UP callback.
            finishGestureOnMain(true)
        }
    }

    fun onHostTaskCancelled() {
        // Give the queued 257/control callback a chance to finish the drag first.
        mainHandler.postDelayed(
            {
                if (active) {
                    cancelGestureOnMain()
                } else {
                    backgroundTouchBlocker.stop()
                    if (!isPreviewExitPending()) {
                        removeGestureViews()
                    }
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
            stopEdgeScroll()
            backgroundTouchBlocker.stop()
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
            return
        }
        if (active) {
            pendingPortalHostFloatWindowSuppression = false
            if (!duplicateStartLogged) {
                duplicateStartLogged = true
                log("duplicate Taplus start ignored during active drag")
            }
            return
        }
        active = false
        inputSourceLogged = false
        duplicateStartLogged = false
        backgroundBlockAttempted = false
        portalGlowStartedLogged = false
        portalGlowExpandedLogged = false
        simpleMenuStartPointCaptured = false
        simpleMenuActivationQualified = false
        lastHandledEventTime = Long.MIN_VALUE
        lastHandledAction = -1
        settings = loadedSettings ?: DragShareSettings.defaults()
        configuredSimpleMenuPosition = settings.simpleMenuPosition
        nearHandSide = DragShareSettings.SIMPLE_MENU_POSITION_RIGHT
        nearHandSideLocked = false
        simpleMenuPosition = effectiveSimpleMenuPosition()
        palette = OverlayColors.from(settings)
        stopEdgeScroll()
        backgroundTouchBlocker.stop()
        removeGestureViews()

        if (!settings.isSharingEnabled(payload.isImage())) {
            pendingPortalHostFloatWindowSuppression = false
            log("sharing disabled kind=" + payload.kind)
            return
        }
        try {
            session = Session(payload)
            refreshDisplayGeometry()
            createPreview(payload)
            createGlow()
            val glow = glowView
            val glowLayout = glowParams
            if (glow != null && glowLayout != null) {
                windowManager.addView(glow, glowLayout)
                glow.start()
            }
            val previewWindow = modernPreviewWindow
            if (previewWindow != null) {
                previewWindow.show()
            } else {
                windowManager.addView(previewView, previewParams)
            }
            active = true
            pendingPortalHostFloatWindowSuppression = false
            registerNearHandSensorIfNeeded()
            traceAccessibility("overlay added kind=" + payload.kind)
        } catch (error: Throwable) {
            pendingPortalHostFloatWindowSuppression = false
            log("unable to add preview overlay", error)
            traceAccessibility(
                "overlay add failed=" + error.javaClass.simpleName +
                    ":" + error.message.toString(),
            )
            removeGestureViews()
            return
        }

        val hasRequestedInitialPoint = requestedInitialX.isFinite() &&
            requestedInitialX >= 0f &&
            requestedInitialY.isFinite() &&
            requestedInitialY >= 0f
        val initialX = if (requestedInitialX.isFinite() && requestedInitialX >= 0f) {
            requestedInitialX
        } else {
            screenWidth / 2f
        }
        val initialY = if (requestedInitialY.isFinite() && requestedInitialY >= 0f) {
            requestedInitialY
        } else {
            Math.max((topInset + dp(80)).toFloat(), screenHeight * 0.32f)
        }
        lastX = initialX
        lastY = initialY
        simpleMenuStartX = initialX
        simpleMenuStartY = initialY
        simpleMenuStartPointCaptured = hasRequestedInitialPoint
        updatePreviewPosition(initialX, initialY)
        updatePortalPullEffect(initialX, initialY)
        startPreviewEnterAnimation()
        // Querying package icons can be comparatively slow. The preview is
        // already visible before the bottom menu is assembled.
        shareTargets = safeQueryTargets(payload)
        createMenu()
        val circle = circleMenuView
        val circleParams = circleMenuParams
        if (isCircleStyle() && circle != null && circleParams != null) {
            try {
                circle.visibility = View.VISIBLE
                windowManager.addView(circle, circleParams)
                updateCirclePointer(initialX, initialY)
            } catch (error: Throwable) {
                log("unable to add circle menu overlay", error)
                circle.collapse()
                circleMenuView = null
                circleMenuParams = null
            }
        }
        log(
            "preview shown kind=" + payload.kind +
                " targets=" + shareTargets.size +
                " style=" + settings.uiStyle +
                " blockBackground=" + settings.blockBackgroundScroll +
                " at=" + Math.round(initialX) + "," + Math.round(initialY),
        )

        if (payload.isImage()) {
            val stagedSession = session
            ImageStagingClient.stage(
                context,
                payload.bitmap,
                object : ImageStagingClient.Callback {
                    override fun onStaged(uri: Uri?) {
                        mainHandler.post {
                            if (destroyed || stagedSession == null || stagedSession.cancelled) {
                                return@post
                            }
                            stagedSession.stagedUri = uri
                            if (stagedSession.pendingTarget != null) {
                                launchPendingShare(stagedSession)
                            }
                        }
                    }

                    override fun onFailure(error: Throwable?) {
                        mainHandler.post {
                            if (!destroyed && stagedSession != null && !stagedSession.cancelled) {
                                log("image staging failed", error)
                                if (stagedSession.pendingTarget != null) {
                                    showToast("图片准备失败")
                                }
                            }
                        }
                    }
                },
            )
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

    private fun createPreview(payload: CapturedContent) {
        if (isModernStyle()) {
            val side = ModernPreviewSizer.squareSidePx(
                payload,
                screenWidth,
                context.resources.displayMetrics.density,
            )
            previewWidth = side
            previewHeight = side
            val modernPreview = ModernPreviewOverlayView(context, payload, settings)
            modernPreviewView = modernPreview
            previewView = modernPreview
            val params = overlayParams(previewWidth, previewHeight, "DragShare modern preview")
            previewParams = params
            try {
                modernPreviewWindow = ModernOverlayWindow(
                    context,
                    windowManager,
                    modernPreview,
                    params,
                    dp(settings.modernBlurRadiusDp),
                )
            } catch (error: Throwable) {
                modernPreviewWindow = null
                log("unable to create native modern preview window", error)
            }
            return
        }
        val portalStyle = isPortalStyle()
        val circleStyle = isCircleStyle()
        val compactStyle = portalStyle || circleStyle
        val standardPreviewView = FrameLayout(context)
        previewView = standardPreviewView
        standardPreviewView.clipToOutline = true
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            standardPreviewView.isForceDarkAllowed = false
        }
        standardPreviewView.elevation = dp(if (compactStyle) 6 else 8).toFloat()
        standardPreviewView.background = roundDrawable(
            palette.previewBackground,
            if (compactStyle) 12 else 8,
        )

        if (compactStyle) {
            val sizeDp = if (!payload.isImage()) {
                PORTAL_PREVIEW_TEXT_SIZE_DP
            } else {
                PORTAL_PREVIEW_IMAGE_SIZE_DP
            }
            previewWidth = dp(sizeDp)
            previewHeight = dp(sizeDp)
            if (!payload.isImage()) {
                val text = TextView(context)
                var previewText: CharSequence? = payload.text
                if (previewText != null && previewText.length > 40) {
                    previewText = previewText.subSequence(0, 40)
                }
                text.text = previewText
                text.setTextColor(palette.primaryText)
                text.setTextSize(TypedValue.COMPLEX_UNIT_SP, 14f)
                text.gravity = Gravity.CENTER
                text.setPadding(dp(7), dp(7), dp(7), dp(7))
                text.maxLines = 4
                text.ellipsize = android.text.TextUtils.TruncateAt.END
                standardPreviewView.addView(
                    text,
                    FrameLayout.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT,
                        ViewGroup.LayoutParams.MATCH_PARENT,
                    ),
                )
            } else {
                val image = ImageView(context)
                image.setImageBitmap(payload.bitmap)
                image.scaleType = ImageView.ScaleType.CENTER_CROP
                image.setPadding(dp(3), dp(3), dp(3), dp(3))
                standardPreviewView.addView(
                    image,
                    FrameLayout.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT,
                        ViewGroup.LayoutParams.MATCH_PARENT,
                    ),
                )
            }
        } else if (!payload.isImage()) {
            previewWidth = dp(PREVIEW_TEXT_WIDTH_DP)
            previewHeight = dp(PREVIEW_TEXT_HEIGHT_DP)
            val text = TextView(context)
            text.text = payload.text
            text.setTextColor(palette.primaryText)
            text.setTextSize(TypedValue.COMPLEX_UNIT_SP, 14f)
            text.gravity = Gravity.CENTER_VERTICAL
            text.setPadding(dp(12), dp(10), dp(12), dp(10))
            text.maxLines = 4
            text.ellipsize = android.text.TextUtils.TruncateAt.END
            standardPreviewView.addView(
                text,
                FrameLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.MATCH_PARENT,
                ),
            )
        } else {
            previewWidth = dp(PREVIEW_IMAGE_SIZE_DP)
            previewHeight = dp(PREVIEW_IMAGE_SIZE_DP)
            val image = ImageView(context)
            image.setImageBitmap(payload.bitmap)
            image.scaleType = ImageView.ScaleType.FIT_CENTER
            image.setPadding(dp(6), dp(6), dp(6), dp(6))
            standardPreviewView.addView(
                image,
                FrameLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.MATCH_PARENT,
                ),
            )
        }

        previewParams = overlayParams(previewWidth, previewHeight, "DragShare preview")
    }

    private fun createGlow() {
        if (!isPortalStyle()) {
            glowView = null
            glowParams = null
            return
        }
        glowView = PortalGlowView(
            context,
            settings.colorMode == DragShareSettings.COLOR_DARK,
            bottomInset,
        )
        glowParams = overlayParams(screenWidth, screenHeight, "DragShare portal glow")
    }
    private fun startPreviewEnterAnimation() {
        if (isModernStyle()) {
            enableModernLocalBlurAndReveal()
            return
        }
        val preview = previewView
        if (preview == null || !isPortalStyle()) {
            return
        }
        preview.animate().cancel()
        preview.alpha = 0f
        preview.scaleX = 0.88f
        preview.scaleY = 0.88f
        preview.animate()
            .alpha(0.94f)
            .scaleX(1f)
            .scaleY(1f)
            .setDuration(200L)
            .start()
    }

    private fun enableModernLocalBlurAndReveal() {
        val preview = modernPreviewView ?: return
        modernPreviewEnterStarted = false
        val blurRadiusPx = dp(settings.modernBlurRadiusDp)
        val previewWindow = modernPreviewWindow
        val enabled = previewWindow != null && previewWindow.isNativeBackdropBlurEnabled()
        log(
            "modern Android background blur " + (if (enabled) "enabled" else "unavailable") +
                " radiusPx=" + blurRadiusPx,
        )
        revealModernPreview(preview)
    }

    private fun revealModernPreview(preview: ModernPreviewOverlayView?) {
        if (preview == null || modernPreviewEnterStarted) {
            return
        }
        modernPreviewEnterStarted = true
        val previewWindow = modernPreviewWindow
        if (previewWindow != null) {
            previewWindow.showAnimated()
        } else {
            preview.showAnimated()
        }
    }
    private fun createMenu() {
        if (isModernStyle()) {
            val icons: MutableMap<ShareTarget, Drawable> = LinkedHashMap()
            for (target in shareTargets) {
                val icon = iconForTarget(target)
                if (icon != null) {
                    icons[target] = icon
                }
            }
            val modernMenu = ModernMenuOverlayView(
                context,
                ArrayList(shareTargets),
                icons,
                settings,
                isVerticalSimpleMenu(),
            )
            modernMenuView = modernMenu
            menuView = modernMenu
            val params = overlayParams(menuWidth, menuHeight, "DragShare modern targets")
            params.x = menuLeft
            params.y = menuTop
            menuParams = params
            try {
                modernMenuWindow = ModernOverlayWindow(
                    context,
                    windowManager,
                    modernMenu,
                    params,
                    dp(settings.modernBlurRadiusDp),
                )
            } catch (error: Throwable) {
                modernMenuWindow = null
                log("unable to create native modern menu window", error)
            }
            return
        }
        if (isCircleStyle()) {
            val circle = CircleMenuOverlayView(
                context,
                screenWidth,
                screenHeight,
                topInset,
                bottomInset,
                settings.colorMode == DragShareSettings.COLOR_DARK,
                palette.accent,
                palette.primaryText,
                palette.selectedItemBackground,
                palette.selectedItemBorder,
                settings.iconOpacityPercent,
            )
            circleMenuView = circle
            circle.setTargets(shareTargets)
            circleMenuParams = overlayParams(
                screenWidth,
                screenHeight,
                "DragShare circle menu",
            )
            return
        }
        val portalStyle = isPortalStyle()
        val vertical = !portalStyle && isVerticalSimpleMenu()
        val linearMenuView = FrameLayout(context)
        menuView = linearMenuView
        linearMenuView.clipChildren = false
        linearMenuView.clipToPadding = false
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            linearMenuView.isForceDarkAllowed = false
        }
        val horizontalPadding = dp(if (portalStyle) 10 else 4)
        val verticalPadding = dp(if (portalStyle) 4 else 4)
        val bottomPadding = dp(if (portalStyle) 2 else 4)
        if (portalStyle) {
            linearMenuView.background = roundDrawable(Color.TRANSPARENT, 0)
        } else {
            addSimpleMenuBackground()
        }
        linearMenuView.elevation = dp(if (portalStyle) 0 else 10).toFloat()

        val menuContent = FrameLayout(context)
        menuContent.clipChildren = false
        menuContent.clipToPadding = false
        menuContent.setPadding(
            horizontalPadding,
            verticalPadding,
            horizontalPadding,
            bottomPadding,
        )
        linearMenuView.addView(
            menuContent,
            FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT,
            ),
        )

        val row = LinearLayout(context)
        menuRow = row
        row.orientation = if (vertical) LinearLayout.VERTICAL else LinearLayout.HORIZONTAL
        row.gravity = if (vertical) Gravity.CENTER_HORIZONTAL else Gravity.CENTER_VERTICAL
        row.clipChildren = false
        row.clipToPadding = false
        if (vertical) {
            val verticalScroll = ScrollView(context)
            menuVerticalScroll = verticalScroll
            verticalScroll.isVerticalScrollBarEnabled = false
            verticalScroll.overScrollMode = View.OVER_SCROLL_NEVER
            verticalScroll.clipToPadding = false
            verticalScroll.clipChildren = false
            verticalScroll.addView(
                row,
                ViewGroup.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.WRAP_CONTENT,
                ),
            )
            menuContent.addView(
                verticalScroll,
                FrameLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.MATCH_PARENT,
                ),
            )
        } else {
            val horizontalScroll = HorizontalScrollView(context)
            menuScroll = horizontalScroll
            horizontalScroll.isHorizontalScrollBarEnabled = false
            horizontalScroll.overScrollMode = View.OVER_SCROLL_NEVER
            horizontalScroll.clipToPadding = false
            horizontalScroll.clipChildren = false
            horizontalScroll.isFillViewport = false
            horizontalScroll.addView(
                row,
                ViewGroup.LayoutParams(
                    ViewGroup.LayoutParams.WRAP_CONTENT,
                    ViewGroup.LayoutParams.MATCH_PARENT,
                ),
            )
            menuContent.addView(
                horizontalScroll,
                FrameLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.MATCH_PARENT,
                ),
            )
        }
        menuItems.clear()
        if (shareTargets.isEmpty()) {
            val empty = TextView(context)
            empty.text = "没有可用的分享应用"
            empty.setTextColor(palette.secondaryText)
            empty.setTextSize(TypedValue.COMPLEX_UNIT_SP, 13f)
            empty.gravity = Gravity.CENTER
            if (vertical) {
                row.addView(
                    empty,
                    LinearLayout.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT,
                        ViewGroup.LayoutParams.WRAP_CONTENT,
                    ),
                )
            } else if (portalStyle) {
                row.addView(
                    empty,
                    LinearLayout.LayoutParams(
                        screenWidth,
                        ViewGroup.LayoutParams.MATCH_PARENT,
                    ),
                )
            } else {
                menuContent.addView(
                    empty,
                    FrameLayout.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT,
                        ViewGroup.LayoutParams.MATCH_PARENT,
                    ),
                )
            }
        } else {
            val itemWidth = dp(if (portalStyle) 78 else 76)
            val itemHeight = dp(if (portalStyle) 124 else 84)
            val iconSize = dp(if (portalStyle) 50 else 44)
            for (target in shareTargets) {
                val item = LinearLayout(context)
                item.orientation = LinearLayout.VERTICAL
                item.gravity = Gravity.CENTER_HORIZONTAL
                item.setPadding(
                    dp(if (portalStyle) 5 else 4),
                    dp(if (portalStyle) 8 else 3),
                    dp(if (portalStyle) 5 else 4),
                    dp(if (portalStyle) 2 else 3),
                )
                item.tag = target
                item.background = itemBackground(target, false)

                val icon = ImageView(context)
                icon.setImageDrawable(iconForTarget(target))
                icon.scaleType = ImageView.ScaleType.FIT_CENTER
                icon.alpha = settings.iconOpacityPercent / 100f
                item.addView(icon, LinearLayout.LayoutParams(iconSize, iconSize))

                val label = TextView(context)
                label.text = target.label
                label.setTextColor(palette.primaryText)
                label.setTextSize(TypedValue.COMPLEX_UNIT_SP, 11f)
                label.gravity = Gravity.CENTER
                label.maxLines = 1
                label.ellipsize = android.text.TextUtils.TruncateAt.END
                label.alpha = settings.iconOpacityPercent / 100f
                if (portalStyle) {
                    label.setShadowLayer(
                        dpFloat(2f),
                        0f,
                        dpFloat(1f),
                        if (settings.colorMode == DragShareSettings.COLOR_DARK) {
                            0xCC000000.toInt()
                        } else {
                            0x66000000
                        },
                    )
                }
                item.addView(
                    label,
                    LinearLayout.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT,
                        dp(if (portalStyle) 38 else 28),
                    ),
                )
                if (vertical) {
                    val width = Math.max(dp(72), menuWidth - dp(8))
                    row.addView(item, LinearLayout.LayoutParams(width, itemHeight))
                } else {
                    row.addView(item, LinearLayout.LayoutParams(itemWidth, itemHeight))
                }
                menuItems.add(item)
            }
        }

        val params = overlayParams(menuWidth, menuHeight, "DragShare targets")
        params.x = menuLeft
        params.y = menuTop
        menuParams = params
    }
    private fun addSimpleMenuBackground() {
        val linearMenuView = menuView as? FrameLayout ?: return
        val cornerRadiusDp = settings.simpleMenuCornerRadiusDp
        val background = View(context)
        background.background = roundDrawable(
            applyAbsoluteOpacity(palette.menuBackground, 100),
            cornerRadiusDp,
        )
        background.alpha = simpleMenuBackgroundOpacityFraction(
            settings.simpleMenuOpacityPercent,
        )
        linearMenuView.addView(
            background,
            FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT,
            ),
        )
        val cornerRadiusPx = dp(cornerRadiusDp)
        linearMenuView.outlineProvider = object : ViewOutlineProvider() {
            override fun getOutline(view: View, outline: Outline) {
                outline.setRoundRect(0, 0, view.width, view.height, cornerRadiusPx.toFloat())
            }
        }
        linearMenuView.clipToOutline = true
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
        // Only pilfer once the authoritative root stream has produced an
        // event. The MIUI fallback cannot safely consume its synthetic cancel.
        if ("root" == source &&
            (action == MotionEvent.ACTION_DOWN || action == MotionEvent.ACTION_MOVE)
        ) {
            startBackgroundBlockerIfEnabled()
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
        handlePointerOnMain(x, y)
        if (action == MotionEvent.ACTION_UP) {
            log(
                "gesture finished source=" + source +
                    " point=" + Math.round(x) + "," + Math.round(y) +
                    " menu=" + menuShown,
            )
            finishGestureOnMain(true, beforeFinish)
        }
    }
    private fun handlePointerOnMain(x: Float, y: Float) {
        if (!active || !x.isFinite() || !y.isFinite()) {
            return
        }
        lastX = x
        lastY = y
        updatePreviewPosition(x, y)
        updatePortalPullEffect(x, y)
        if (isCircleStyle()) {
            updateCirclePointer(x, y)
            if (menuShown) {
                updateSelectedTarget(x, y)
            }
            return
        }
        var menuOpenedNow = false
        if (!menuShown && shouldShowSimpleMenu(x, y) &&
            hasQualifiedSimpleMenuActivation(x, y)
        ) {
            showMenuOnMain()
            menuOpenedNow = menuShown
        }
        if (menuShown) {
            if (settings.closeMenuWhenPointerLeaves &&
                !menuOpenedNow &&
                !isPointerWithinSimpleMenuZone(x, y)
            ) {
                hideLinearMenu()
                return
            }
            updateSelectedTarget(x, y)
            val pointerInScrollableMenu = isPortalStyle() ||
                isPointerInsideSimpleMenu(x, y)
            var nextDirection = 0
            if (pointerInScrollableMenu) {
                val edgeAxisSize = edgeScrollAxisSize()
                val edgeWidth = edgeScrollWidthPx()
                nextDirection = GestureMath.edgeScrollDirection(
                    if (isVerticalSimpleMenu()) y else x,
                    edgeAxisSize,
                    edgeWidth,
                )
            }
            if (nextDirection != edgeDirection) {
                edgeDirection = nextDirection
                edgeScrollRemainderPx = 0f
                log("edge scroll direction=" + nextDirection)
                if (nextDirection == 0) {
                    stopEdgeScroll()
                } else {
                    lastEdgeScrollUptime = 0L
                    mainHandler.removeCallbacks(edgeScrollRunnable)
                    mainHandler.post(edgeScrollRunnable)
                }
            }
        }
    }
    private fun updateCirclePointer(x: Float, y: Float) {
        val circle = circleMenuView ?: return
        val trigger = circleTriggerPx()
        val nextEdge = CircleMenuGeometry.nearestEdge(
            x, y, screenWidth, screenHeight, trigger.toFloat(),
        )
        val progress = CircleMenuGeometry.edgeProgress(
            x, y, screenWidth, screenHeight,
            Math.max(trigger, dp(CIRCLE_EDGE_SOFT_DISTANCE_DP)).toFloat(),
        )
        if (menuShown) {
            circle.updatePointer(x, y)
            if (settings.closeMenuWhenPointerLeaves &&
                !circle.containsExpandedRegion(x, y)
            ) {
                hideCircleMenu()
                circle.setEdgeProgress(nextEdge, progress, x, y)
                updateCirclePendingEdge(nextEdge)
                return
            }
        } else {
            circle.setEdgeProgress(nextEdge, progress, x, y)
            updateCirclePendingEdge(nextEdge)
            return
        }

        val nextScrollDirection = circle.scrollDirectionForPointer(x, y)
        if (nextScrollDirection != circleScrollDirection) {
            circleScrollDirection = nextScrollDirection
            mainHandler.removeCallbacks(edgeScrollRunnable)
            if (nextScrollDirection != 0) {
                mainHandler.post(edgeScrollRunnable)
            }
        }
        // The reference side menu keeps the edge and vertical anchor captured
        // at expansion time. Pointer movement only changes hover/drop state.
    }

    private fun updateCirclePendingEdge(nextEdge: Int) {
        if (nextEdge == circlePendingEdge) {
            return
        }
        mainHandler.removeCallbacks(circleExpandRunnable)
        circlePendingEdge = nextEdge
        if (nextEdge != CircleMenuGeometry.EDGE_NONE) {
            mainHandler.postDelayed(circleExpandRunnable, CIRCLE_EDGE_OPEN_DELAY_MS)
        }
    }

    private fun circleTriggerPx(): Int {
        val maxTrigger = Math.max(1, Math.min(screenWidth, screenHeight) / 2)
        return Math.min(
            maxTrigger,
            Math.max(dp(CIRCLE_EDGE_TRIGGER_DP), dp(settings.edgeTriggerDp)),
        )
    }
    private fun effectiveSimpleMenuPosition(): Int {
        if (configuredSimpleMenuPosition == DragShareSettings.SIMPLE_MENU_POSITION_NEAR_HAND) {
            return if (nearHandSide == DragShareSettings.SIMPLE_MENU_POSITION_LEFT) {
                DragShareSettings.SIMPLE_MENU_POSITION_LEFT
            } else {
                DragShareSettings.SIMPLE_MENU_POSITION_RIGHT
            }
        }
        return if (configuredSimpleMenuPosition >= DragShareSettings.SIMPLE_MENU_POSITION_TOP &&
            configuredSimpleMenuPosition <= DragShareSettings.SIMPLE_MENU_POSITION_RIGHT
        ) {
            configuredSimpleMenuPosition
        } else {
            DragShareSettings.DEFAULT_SIMPLE_MENU_POSITION
        }
    }

    private fun isVerticalSimpleMenu(): Boolean =
        !isPortalStyle() &&
            (simpleMenuPosition == DragShareSettings.SIMPLE_MENU_POSITION_LEFT ||
                simpleMenuPosition == DragShareSettings.SIMPLE_MENU_POSITION_RIGHT)

    private fun edgeScrollAxisSize(): Int =
        if (isVerticalSimpleMenu()) screenHeight else screenWidth

    private fun edgeScrollWidthPx(): Int {
        val edgeAxisSize = edgeScrollAxisSize()
        val maxEdgeWidth = Math.max(1, (edgeAxisSize - 1) / 2)
        return Math.min(dp(settings.edgeTriggerDp), maxEdgeWidth)
    }

    private fun edgeScrollSpeedMultiplier(): Float {
        val vertical = isVerticalSimpleMenu()
        return GestureMath.edgeScrollSpeedMultiplier(
            if (vertical) lastY else lastX,
            edgeScrollAxisSize(),
            edgeScrollWidthPx(),
            dp(EDGE_SCROLL_FULL_SPEED_INSET_DP),
        )
    }

    private fun shouldShowSimpleMenu(x: Float, y: Float): Boolean {
        if (isPortalStyle()) {
            return GestureMath.shouldShowMenu(y, menuTriggerTop)
        }
        val trigger = Math.max(dp(settings.edgeTriggerDp), dp(MENU_TRIGGER_DP))
        return when (simpleMenuPosition) {
            DragShareSettings.SIMPLE_MENU_POSITION_TOP -> y <= menuTop + trigger
            DragShareSettings.SIMPLE_MENU_POSITION_LEFT -> x <= trigger
            DragShareSettings.SIMPLE_MENU_POSITION_RIGHT -> x >= screenWidth - trigger
            // SIMPLE_MENU_POSITION_BOTTOM and every unknown position trigger from the bottom.
            else -> y >= menuTriggerTop
        }
    }
    private fun hasQualifiedSimpleMenuActivation(x: Float, y: Float): Boolean {
        if (isPortalStyle() || isCircleStyle() || simpleMenuActivationQualified) {
            return true
        }
        if (!simpleMenuStartPointCaptured) {
            simpleMenuStartX = x
            simpleMenuStartY = y
            simpleMenuStartPointCaptured = true
            return false
        }
        simpleMenuActivationQualified = GestureMath.hasMovedTowardMenu(
            simpleMenuPosition,
            simpleMenuStartX,
            simpleMenuStartY,
            x,
            y,
            dp(SIMPLE_MENU_ACTIVATION_SLOP_DP).toFloat(),
        )
        return simpleMenuActivationQualified
    }

    private fun isPointerInsideSimpleMenu(x: Float, y: Float): Boolean =
        menuShown &&
            x >= menuLeft &&
            x < menuLeft + menuWidth &&
            y >= menuTop &&
            y < menuTop + menuHeight

    private fun isPointerWithinSimpleMenuZone(x: Float, y: Float): Boolean =
        isPointerInsideSimpleMenu(x, y) || shouldShowSimpleMenu(x, y)

    private fun hideLinearMenu() {
        if (!menuShown || isCircleStyle()) {
            return
        }
        stopEdgeScroll()
        menuShown = false
        val modernMenu = modernMenuView
        val currentMenuView = menuView
        if (isModernStyle() && modernMenu != null) {
            scheduleModernMenuDispose(modernMenu, modernMenuWindow)
        } else if (currentMenuView != null) {
            fadeOutLinearMenu(currentMenuView)
        }
        selectedTarget = null
        modernMenuView?.setSelectedTarget(null)
        val glow = glowView
        if (glow != null && isPortalStyle()) {
            glow.collapseMenu()
            updatePortalPullEffect(lastX, lastY)
        }
        updatePreviewPosition(lastX, lastY)
    }
    private fun hideCircleMenu() {
        val circle = circleMenuView
        if (!menuShown || !isCircleStyle() || circle == null) {
            return
        }
        stopEdgeScroll()
        mainHandler.removeCallbacks(circleExpandRunnable)
        circle.collapse()
        menuShown = false
        selectedTarget = null
        circleEdge = CircleMenuGeometry.EDGE_NONE
        circlePendingEdge = CircleMenuGeometry.EDGE_NONE
        updatePreviewPosition(lastX, lastY)
    }

    private fun updateNearHandMenuPosition() {
        if (!active || isPortalStyle() || isCircleStyle() ||
            nearHandSideLocked ||
            configuredSimpleMenuPosition != DragShareSettings.SIMPLE_MENU_POSITION_NEAR_HAND
        ) {
            return
        }
        val nextPosition = effectiveSimpleMenuPosition()
        if (nextPosition == simpleMenuPosition) {
            return
        }
        val wasShown = menuShown
        val previousMenuView = menuView
        if (wasShown) {
            hideLinearMenu()
            if (!isModernStyle()) {
                cancelLinearMenuFadeOut()
                removeLinearMenuImmediately(previousMenuView)
            }
        }
        if (!wasShown && modernMenuWindow != null) {
            modernMenuWindow?.dispose()
        }
        menuView = null
        modernMenuView = null
        modernMenuWindow = null
        menuParams = null
        menuScroll = null
        menuVerticalScroll = null
        menuRow = null
        simpleMenuPosition = nextPosition
        refreshDisplayGeometry()
        createMenu()
        if (wasShown) {
            showMenuOnMain()
        }
        updatePreviewPosition(lastX, lastY)
    }
    private fun registerNearHandSensorIfNeeded() {
        if (!active || isPortalStyle() || isCircleStyle() ||
            configuredSimpleMenuPosition != DragShareSettings.SIMPLE_MENU_POSITION_NEAR_HAND ||
            nearHandSensorRegistered
        ) {
            return
        }
        try {
            val manager = context.getSystemService(Context.SENSOR_SERVICE) as SensorManager?
            sensorManager = manager
            if (manager == null) {
                return
            }
            var sensor = manager.getDefaultSensor(Sensor.TYPE_ROTATION_VECTOR)
            if (sensor == null) {
                sensor = manager.getDefaultSensor(Sensor.TYPE_ACCELEROMETER)
            }
            nearHandSensor = sensor
            if (sensor != null &&
                manager.registerListener(
                    nearHandSensorListener,
                    sensor,
                    SensorManager.SENSOR_DELAY_GAME,
                )
            ) {
                nearHandSensorRegistered = true
                log("near-hand sensor registered type=" + sensor.type)
            }
        } catch (error: Throwable) {
            log("near-hand sensor unavailable", error)
            nearHandSensorRegistered = false
        }
    }

    private fun unregisterNearHandSensor() {
        val manager = sensorManager
        if (manager != null && nearHandSensorRegistered) {
            try {
                manager.unregisterListener(nearHandSensorListener)
            } catch (ignored: Throwable) {
                // Sensor service may already be shutting down.
            }
        }
        nearHandSensorRegistered = false
        nearHandSensor = null
        sensorManager = null
    }
    private fun circleScrollIntervalMs(): Long {
        val speed = Math.max(1f, settings.scrollSpeedDpPerSecond.toFloat())
        return Math.max(120L, Math.min(420L, Math.round(300f * 560f / speed).toLong()))
    }

    private fun showMenuOnMain() {
        if (menuShown) {
            return
        }
        if (isCircleStyle()) {
            val circle = circleMenuView
            if (circle == null || circlePendingEdge == CircleMenuGeometry.EDGE_NONE) {
                return
            }
            menuShown = true
            circleEdge = circlePendingEdge
            circleScrollDirection = 0
            circle.expand(circleEdge, lastX, lastY)
            updatePreviewPosition(lastX, lastY)
            startCirclePreviewAnimation()
            log(
                "circle menu shown edge=" + circleEdge +
                    " pointer=" + Math.round(lastX) + "," + Math.round(lastY),
            )
            mainHandler.post { updateSelectedTarget(lastX, lastY) }
            return
        }
        if (isModernStyle()) {
            cancelModernMenuDispose()
            // A menu that completed its exit animation is removed to guarantee that the
            // blurred overlay cannot remain on screen. Recreate it when the same drag re-enters
            // the trigger zone, preserving the public "leave and return" behavior.
            if (menuView == null || modernMenuView == null) {
                createMenu()
            }
        }
        val currentMenu = menuView ?: return
        lockNearHandMenuSide()
        if (!isModernStyle() && currentMenu.isAttachedToWindow) {
            cancelLinearMenuFadeOut(currentMenu)
            currentMenu.animate().cancel()
            menuShown = true
            currentMenu.animate()
                .alpha(1f)
                .translationX(0f)
                .translationY(0f)
                .setDuration(LINEAR_MENU_EXIT_DURATION_MS)
                .setInterpolator(DecelerateInterpolator(1.5f))
                .start()
            updatePreviewPosition(lastX, lastY)
            log(
                "linear menu restored pointerY=" + Math.round(lastY) +
                    " menuTop=" + menuTop,
            )
            currentMenu.post { updateSelectedTarget(lastX, lastY) }
            return
        }
        val modernMenu = modernMenuView
        val modernWindow = modernMenuWindow
        if (isModernStyle() && modernMenu != null &&
            ((modernWindow != null && modernWindow.isShowing()) || currentMenu.isAttachedToWindow)
        ) {
            menuShown = true
            if (modernWindow != null) {
                modernWindow.showAnimated()
            } else {
                modernMenu.showAnimated()
            }
            updatePreviewPosition(lastX, lastY)
            log("modern menu shown pointerY=" + Math.round(lastY) + " menuTop=" + menuTop)
            currentMenu.post { updateSelectedTarget(lastX, lastY) }
            return
        }
        try {
            if (isModernStyle() && modernWindow != null) {
                modernWindow.showAnimated()
            } else {
                windowManager.addView(currentMenu, menuParams)
            }
            menuShown = true
            updatePreviewPosition(lastX, lastY)
            log("menu shown pointerY=" + Math.round(lastY) + " menuTop=" + menuTop)
            if (isPortalStyle()) {
                val glow = glowView
                if (glow != null) {
                    glow.setPullProgress(1f, lastX / Math.max(1f, screenWidth.toFloat()))
                    glow.expandMenu()
                }
                if (!portalGlowExpandedLogged) {
                    portalGlowExpandedLogged = true
                    log("portal glow expanded with share tray")
                }
                startPortalMenuEnterAnimation()
            } else if (isModernStyle() && modernMenu != null) {
                if (modernWindow == null) {
                    modernMenu.showAnimated()
                }
            } else {
                currentMenu.alpha = 0f
                currentMenu.translationX = if (isVerticalSimpleMenu()) {
                    if (simpleMenuPosition == DragShareSettings.SIMPLE_MENU_POSITION_LEFT) {
                        -dp(12).toFloat()
                    } else {
                        dp(12).toFloat()
                    }
                } else {
                    0f
                }
                currentMenu.translationY = if (!isVerticalSimpleMenu()) {
                    if (simpleMenuPosition == DragShareSettings.SIMPLE_MENU_POSITION_TOP) {
                        -dp(12).toFloat()
                    } else {
                        dp(12).toFloat()
                    }
                } else {
                    0f
                }
                currentMenu.animate()
                    .alpha(1f)
                    .translationX(0f)
                    .translationY(0f)
                    .setDuration(120L)
                    .start()
            }
            currentMenu.post { updateSelectedTarget(lastX, lastY) }
        } catch (error: Throwable) {
            log("unable to add share menu", error)
        }
    }

    private fun lockNearHandMenuSide() {
        if (configuredSimpleMenuPosition != DragShareSettings.SIMPLE_MENU_POSITION_NEAR_HAND ||
            nearHandSideLocked
        ) {
            return
        }
        nearHandSideLocked = true
        unregisterNearHandSensor()
        log("near-hand menu side locked=" + simpleMenuPosition)
    }

    private fun startPortalMenuEnterAnimation() {
        val menu = menuView ?: return
        menu.alpha = 1f
        menu.translationY = 0f
        val springLike = OvershootInterpolator(0.78f)
        for (index in menuItems.indices) {
            val item = menuItems[index]
            item.animate().cancel()
            item.alpha = 0f
            item.translationY = dp(PORTAL_ITEM_ENTER_OFFSET_DP).toFloat()
            item.animate()
                .alpha(1f)
                .translationY(0f)
                .setStartDelay(Math.min(180L, index * 22L))
                .setDuration(430L)
                .setInterpolator(springLike)
                .start()
        }
    }

    private fun startCirclePreviewAnimation() {
        val preview = previewView ?: return
        preview.animate().cancel()
        preview.animate()
            .alpha(0.92f)
            .scaleX(0.88f)
            .scaleY(0.88f)
            .setDuration(500L)
            .start()
    }
    private fun updateSelectedTarget(x: Float, y: Float) {
        if (isCircleStyle()) {
            val circle = circleMenuView
            val hit = circle?.hitTest(x, y)
            selectedTarget = hit
            circle?.setSelectedTarget(hit)
            return
        }
        if (isModernStyle()) {
            val modern = modernMenuView
            val hit = if (menuShown && isPointerInsideSimpleMenu(x, y) && modern != null) {
                modern.hitTest(x, y)
            } else {
                null
            }
            selectedTarget = hit
            modern?.setSelectedTarget(hit)
            return
        }
        var hit: ShareTarget? = null
        if (menuShown && isPointerInsideSimpleMenu(x, y)) {
            for (i in menuItems.indices) {
                val item = menuItems[i]
                val location = IntArray(2)
                item.getLocationOnScreen(location)
                val inside: Boolean
                if (isPortalStyle() && item.width > 0 && item.height > 0) {
                    val centerX = location[0] + item.width / 2f
                    val centerY = location[1] + item.height / 2f
                    val scale = GestureMath.portalItemScale(x, centerX, item.width.toFloat())
                    inside = Math.abs(x - centerX) <= item.width * scale / 2f &&
                        Math.abs(y - centerY) <= item.height * scale / 2f
                } else {
                    inside = x >= location[0] && x < location[0] + item.width &&
                        y >= location[1] && y < location[1] + item.height
                }
                if (inside) {
                    val tag = item.tag
                    if (tag is ShareTarget) {
                        hit = tag
                    }
                    break
                }
            }
        }
        val selectionChanged = hit !== selectedTarget
        selectedTarget = hit
        if (isPortalStyle()) {
            val pointerInMenu = menuShown && isPointerInsideSimpleMenu(x, y)
            for (item in menuItems) {
                val location = IntArray(2)
                item.getLocationOnScreen(location)
                val scale = if (pointerInMenu && item.width > 0) {
                    GestureMath.portalItemScale(
                        x,
                        location[0] + item.width / 2f,
                        item.width.toFloat(),
                    )
                } else {
                    1f
                }
                item.scaleX = scale
                item.scaleY = scale
                if (selectionChanged) {
                    item.background = itemBackground(
                        item.tag as? ShareTarget,
                        item.tag === selectedTarget,
                    )
                }
            }
            return
        }
        if (!selectionChanged) {
            return
        }
        for (item in menuItems) {
            val selected = item.tag === selectedTarget
            item.background = itemBackground(item.tag as? ShareTarget, selected)
            item.animate().scaleX(if (selected) 1.04f else 1f)
                .scaleY(if (selected) 1.04f else 1f).setDuration(80L).start()
        }
    }

    private fun finishGestureOnMain(allowShare: Boolean) {
        finishGestureOnMain(allowShare, null)
    }

    private fun finishGestureOnMain(allowShare: Boolean, afterDeactivate: Runnable?) {
        if (!active) {
            return
        }
        val finished = session
        var target = selectedTarget
        if (allowShare && menuShown) {
            updateSelectedTarget(lastX, lastY)
            target = selectedTarget
        }

        if (settings.blockBackgroundScroll && !backgroundBlockAttempted) {
            log("background scroll lock skipped because root input was not active")
        }
        active = false
        stopEdgeScroll()
        backgroundTouchBlocker.stop()
        afterDeactivate?.run()

        // HyperOS can silently reject an AccessibilityService activity start after its last
        // accessibility overlay has been removed. Keep the passive overlay attached through
        // the user-selected launch, then clean it up in the same main-thread turn.
        if (allowShare && target != null && finished != null) {
            if (!finished.payload.isImage() ||
                target.isSaveToLocal() ||
                finished.stagedUri != null
            ) {
                launchShare(finished, target)
            } else {
                finished.pendingTarget = target
                showToast("正在准备图片")
            }
        } else if (finished != null && finished.pendingTarget == null) {
            finished.cancelled = true
        }
        removeGestureViews(true)
    }

    private fun cancelGestureOnMain() {
        if (!active) {
            return
        }
        active = false
        stopEdgeScroll()
        backgroundTouchBlocker.stop()
        removeGestureViews(true)
        val current = session
        if (current != null && current.pendingTarget == null) {
            current.cancelled = true
        }
    }

    private fun launchPendingShare(pendingSession: Session) {
        val target = pendingSession.pendingTarget
        if (target == null || (pendingSession.stagedUri == null && !target.isSaveToLocal())) {
            return
        }
        pendingSession.pendingTarget = null
        launchShare(pendingSession, target)
    }
    private fun launchShare(shareSession: Session, target: ShareTarget) {
        if (target.isSaveToLocal()) {
            saveImageLocally(shareSession.payload.bitmap)
            shareSession.cancelled = true
            return
        }
        if (target.isCopyToClipboard()) {
            copyToClipboard(shareSession.payload, shareSession.stagedUri)
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
            ShareLauncher.launch(
                context,
                shareSession.payload,
                target,
                shareSession.stagedUri,
                dragShareToast,
            )
        } catch (error: Throwable) {
            log("share launch failed", error)
        }
        shareSession.cancelled = true
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
    private fun updatePreviewPosition(x: Float, y: Float) {
        val params = previewParams ?: return
        val preview = previewView ?: return
        val circle = circleMenuView
        if (isCircleStyle() && menuShown && circle != null) {
            val avoid = circle.getAvoidRect()
            val margin = dp(12)
            var left = GestureMath.previewLeft(x, previewWidth, screenWidth, dp(8))
            val top = GestureMath.previewTop(
                y,
                previewHeight,
                dp(20),
                topInset + dp(8),
                screenHeight - bottomInset - previewHeight - dp(8),
            )
            when (circleEdge) {
                CircleMenuGeometry.EDGE_LEFT -> left = avoid.right + margin
                CircleMenuGeometry.EDGE_RIGHT -> left = avoid.left - previewWidth - margin
                else -> {}
            }
            params.x = GestureMath.clamp(
                left,
                dp(8),
                Math.max(dp(8), screenWidth - previewWidth - dp(8)),
            )
            params.y = GestureMath.clamp(
                top,
                topInset + dp(8),
                Math.max(
                    topInset + dp(8),
                    screenHeight - bottomInset - previewHeight - dp(8),
                ),
            )
            updateOverlayLayout(preview, modernPreviewWindow, params)
            return
        }
        val margin = dp(settings.simpleMenuEdgeDistanceDp)
        var minLeft = dp(8)
        var maxLeft = Math.max(minLeft, screenWidth - previewWidth - dp(8))
        var minTop = topInset + dp(8)
        var maxTop = Math.max(minTop, screenHeight - bottomInset - previewHeight - dp(8))
        if (menuShown) {
            if (isVerticalSimpleMenu()) {
                if (simpleMenuPosition == DragShareSettings.SIMPLE_MENU_POSITION_LEFT) {
                    minLeft = Math.min(maxLeft, menuLeft + menuWidth + margin)
                } else if (simpleMenuPosition == DragShareSettings.SIMPLE_MENU_POSITION_RIGHT) {
                    maxLeft = Math.max(minLeft, menuLeft - previewWidth - margin)
                }
            } else if (simpleMenuPosition == DragShareSettings.SIMPLE_MENU_POSITION_TOP) {
                minTop = Math.min(maxTop, menuTop + menuHeight + margin)
            } else {
                maxTop = Math.max(minTop, menuTop - previewHeight - margin)
            }
        }
        params.x = GestureMath.clamp(
            Math.round(x - previewWidth / 2f),
            minLeft,
            maxLeft,
        )
        params.y = GestureMath.clamp(
            Math.round(y - previewHeight - dp(20)),
            minTop,
            maxTop,
        )
        updateOverlayLayout(preview, modernPreviewWindow, params)
    }
    private fun updateOverlayLayout(
        view: View?,
        modernWindow: ModernOverlayWindow?,
        params: WindowManager.LayoutParams?,
    ) {
        if (view == null || params == null) {
            return
        }
        try {
            if (modernWindow != null) {
                modernWindow.updateLayout()
            } else {
                windowManager.updateViewLayout(view, params)
            }
        } catch (ignored: Throwable) {
            // The host may be tearing down its service at the same time.
        }
    }

    private fun updatePortalPullEffect(x: Float, y: Float) {
        val glow = glowView
        if (glow == null || !isPortalStyle()) {
            return
        }
        val progressStart = Math.max((topInset + dp(120)).toFloat(), screenHeight * 0.45f)
        val progress = if (menuShown) {
            1f
        } else {
            GestureMath.dragPullProgress(y, progressStart, menuTriggerTop.toFloat())
        }
        if (progress > 0.02f && !portalGlowStartedLogged) {
            portalGlowStartedLogged = true
            log("portal glow progress started")
        }
        glow.setPullProgress(progress, x / Math.max(1f, screenWidth.toFloat()))
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
        if (isCircleStyle()) {
            menuWidth = screenWidth
            menuHeight = dp(CircleMenuOverlayView.CONTAINER_SIZE_DP)
            menuLeft = 0
            menuTop = Math.max(topInset, screenHeight - bottomInset - menuHeight)
            menuTriggerTop = menuTop
            return
        }
        if (isPortalStyle()) {
            menuWidth = screenWidth
            menuHeight = dp(PORTAL_MENU_HEIGHT_DP)
            menuLeft = 0
            menuTop = Math.max(topInset, screenHeight - bottomInset - menuHeight)
            menuTriggerTop = Math.max(
                menuTop,
                screenHeight - bottomInset - dp(PORTAL_TRIGGER_FROM_BOTTOM_DP),
            )
            return
        }

        simpleMenuPosition = effectiveSimpleMenuPosition()
        val menuMargin = dp(settings.simpleMenuEdgeDistanceDp)
        if (isVerticalSimpleMenu()) {
            menuWidth = Math.min(dp(SIMPLE_SIDE_MENU_WIDTH_DP), Math.max(1, screenWidth / 2))
            menuHeight = Math.max(1, screenHeight - topInset - bottomInset)
            menuTop = topInset
            menuLeft = if (simpleMenuPosition == DragShareSettings.SIMPLE_MENU_POSITION_LEFT) {
                menuMargin
            } else {
                Math.max(0, screenWidth - menuWidth - menuMargin)
            }
            menuTriggerTop = menuTop
        } else {
            val horizontalInset = if (isModernStyle() &&
                simpleMenuPosition == DragShareSettings.SIMPLE_MENU_POSITION_BOTTOM
            ) {
                Math.min(
                    dp(MODERN_BOTTOM_MENU_SIDE_MARGIN_DP),
                    Math.max(0, (screenWidth - 1) / 2),
                )
            } else {
                0
            }
            menuWidth = Math.max(1, screenWidth - horizontalInset * 2)
            menuHeight = dp(MENU_HEIGHT_DP)
            menuLeft = horizontalInset
            if (simpleMenuPosition == DragShareSettings.SIMPLE_MENU_POSITION_TOP) {
                menuTop = topInset + menuMargin
                menuTriggerTop = menuTop + dp(MENU_TRIGGER_DP)
            } else {
                menuTop = Math.max(
                    topInset,
                    screenHeight - bottomInset - menuHeight - menuMargin,
                )
                menuTriggerTop = menuTop - dp(MENU_TRIGGER_DP)
            }
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

    private fun removeGestureViews(animatePreviewExit: Boolean = false) {
        unregisterNearHandSensor()
        cancelModernMenuDispose()
        cancelLinearMenuFadeOut()
        if (!animatePreviewExit) {
            removePendingPreviewExitImmediately()
        }
        val preview = previewView
        if (preview != null && !animatePreviewExit) {
            preview.animate().cancel()
        }
        menuView?.animate()?.cancel()
        for (item in menuItems) {
            item.animate().cancel()
        }
        if (animatePreviewExit && preview != null) {
            startPreviewExit(preview, modernPreviewView, modernPreviewWindow)
        } else {
            removeOverlayView(preview, modernPreviewView, modernPreviewWindow)
        }
        removeOverlayView(menuView, modernMenuView, modernMenuWindow)
        val glow = glowView
        if (glow != null) {
            glow.stop()
            try {
                windowManager.removeViewImmediate(glow)
            } catch (ignored: Throwable) {
                // Already removed or never attached.
            }
        }
        mainHandler.removeCallbacks(circleExpandRunnable)
        val circle = circleMenuView
        if (circle != null) {
            circle.collapse()
            try {
                windowManager.removeViewImmediate(circle)
            } catch (ignored: Throwable) {
                // Already removed or never attached.
            }
        }
        previewView = null
        modernPreviewView = null
        modernPreviewWindow = null
        previewParams = null
        glowView = null
        glowParams = null
        menuView = null
        modernMenuView = null
        modernMenuWindow = null
        modernMenuDisposeRunnable = null
        fadingLinearMenuView = null
        modernPreviewEnterStarted = false
        menuParams = null
        menuScroll = null
        menuVerticalScroll = null
        menuRow = null
        circleMenuView = null
        circleMenuParams = null
        menuItems.clear()
        selectedTarget = null
        menuShown = false
        edgeDirection = 0
        edgeScrollRemainderPx = 0f
        circleEdge = CircleMenuGeometry.EDGE_NONE
        circlePendingEdge = CircleMenuGeometry.EDGE_NONE
        circleScrollDirection = 0
        menuLeft = 0
        menuWidth = 0
    }
    private fun removeOverlayView(
        view: View?,
        modernOverlay: ModernOverlayComposeView?,
        modernWindow: ModernOverlayWindow?,
    ) {
        if (modernWindow != null) {
            modernWindow.dispose()
            return
        }
        if (view != null) {
            try {
                windowManager.removeViewImmediate(view)
            } catch (ignored: Throwable) {
                // Already removed or never attached.
            }
        }
        modernOverlay?.disposeOverlay()
    }

    private fun startPreviewExit(
        previewToFade: View,
        modernPreviewToFade: ModernPreviewOverlayView?,
        modernWindowToFade: ModernOverlayWindow?,
    ) {
        removePendingPreviewExitImmediately()
        val generation = ++previewExitGeneration
        exitingPreviewView = previewToFade
        exitingModernPreviewView = modernPreviewToFade
        exitingModernPreviewWindow = modernWindowToFade

        if (modernWindowToFade != null) {
            modernWindowToFade.hideAnimated(
                MODERN_OVERLAY_EXIT_DURATION_MS,
                Runnable {
                    finishPreviewExit(
                        generation,
                        previewToFade,
                        modernPreviewToFade,
                        modernWindowToFade,
                    )
                },
            )
            return
        }
        if (modernPreviewToFade != null) {
            modernPreviewToFade.hideAnimated()
            mainHandler.postDelayed(
                {
                    finishPreviewExit(
                        generation,
                        previewToFade,
                        modernPreviewToFade,
                        null,
                    )
                },
                MODERN_OVERLAY_EXIT_DURATION_MS,
            )
            return
        }

        previewToFade.animate().cancel()
        previewToFade.animate()
            .alpha(0f)
            .scaleX(Math.max(0f, previewToFade.scaleX * 0.96f))
            .scaleY(Math.max(0f, previewToFade.scaleY * 0.96f))
            .setDuration(PREVIEW_EXIT_DURATION_MS)
            .setInterpolator(DecelerateInterpolator(1.5f))
            .withEndAction {
                finishPreviewExit(
                    generation,
                    previewToFade,
                    null,
                    null,
                )
            }
            .start()
    }
    private fun finishPreviewExit(
        generation: Int,
        previewToRemove: View?,
        modernPreviewToRemove: ModernPreviewOverlayView?,
        modernWindowToRemove: ModernOverlayWindow?,
    ) {
        if (generation != previewExitGeneration ||
            exitingPreviewView !== previewToRemove ||
            exitingModernPreviewView !== modernPreviewToRemove ||
            exitingModernPreviewWindow !== modernWindowToRemove
        ) {
            return
        }
        exitingPreviewView = null
        exitingModernPreviewView = null
        exitingModernPreviewWindow = null
        removeOverlayView(previewToRemove, modernPreviewToRemove, modernWindowToRemove)
    }

    private fun isPreviewExitPending(): Boolean = exitingPreviewView != null

    private fun removePendingPreviewExitImmediately() {
        previewExitGeneration++
        val previewToRemove = exitingPreviewView
        val modernPreviewToRemove = exitingModernPreviewView
        val modernWindowToRemove = exitingModernPreviewWindow
        exitingPreviewView = null
        exitingModernPreviewView = null
        exitingModernPreviewWindow = null
        previewToRemove?.animate()?.cancel()
        removeOverlayView(previewToRemove, modernPreviewToRemove, modernWindowToRemove)
    }

    private fun scheduleModernMenuDispose(
        menuToDispose: ModernMenuOverlayView,
        windowToDispose: ModernOverlayWindow?,
    ) {
        cancelModernMenuDispose()
        val disposeRunnable = Runnable {
            if (menuShown || modernMenuView !== menuToDispose) {
                return@Runnable
            }
            if (windowToDispose != null) {
                windowToDispose.dispose()
            } else {
                try {
                    windowManager.removeViewImmediate(menuToDispose)
                } catch (ignored: Throwable) {
                    // The overlay can already be gone while the host tears down its window.
                }
                menuToDispose.disposeOverlay()
            }
            if (menuView === menuToDispose) {
                menuView = null
                modernMenuView = null
                modernMenuWindow = null
                menuParams = null
            }
        }
        modernMenuDisposeRunnable = disposeRunnable
        if (windowToDispose != null) {
            windowToDispose.hideAnimated(
                MODERN_OVERLAY_EXIT_DURATION_MS,
                Runnable {
                    if (modernMenuDisposeRunnable === disposeRunnable) {
                        disposeRunnable.run()
                    }
                },
            )
        } else {
            menuToDispose.hideAnimated()
            mainHandler.postDelayed(disposeRunnable, MODERN_OVERLAY_EXIT_DURATION_MS)
        }
    }

    private fun cancelModernMenuDispose() {
        val pending = modernMenuDisposeRunnable
        if (pending != null) {
            mainHandler.removeCallbacks(pending)
            modernMenuDisposeRunnable = null
        }
    }
    private fun fadeOutLinearMenu(menuToFade: View?) {
        if (menuToFade == null) {
            return
        }
        cancelLinearMenuFadeOut()
        val generation = ++linearMenuFadeGeneration
        fadingLinearMenuView = menuToFade
        menuToFade.animate().cancel()
        menuToFade.animate()
            .alpha(0f)
            .setDuration(LINEAR_MENU_EXIT_DURATION_MS)
            .setInterpolator(DecelerateInterpolator(1.5f))
            .withEndAction {
                if (generation == linearMenuFadeGeneration &&
                    fadingLinearMenuView === menuToFade
                ) {
                    fadingLinearMenuView = null
                    if (menuView !== menuToFade || !menuShown) {
                        removeLinearMenuImmediately(menuToFade)
                    }
                }
            }
            .start()
    }

    private fun cancelLinearMenuFadeOut(menuToRestore: View?) {
        if (fadingLinearMenuView === menuToRestore) {
            cancelLinearMenuFadeOut()
        }
    }

    private fun cancelLinearMenuFadeOut() {
        linearMenuFadeGeneration++
        val fading = fadingLinearMenuView
        fadingLinearMenuView = null
        fading?.animate()?.cancel()
    }

    private fun removeLinearMenuImmediately(menuToRemove: View?) {
        if (menuToRemove == null) {
            return
        }
        menuToRemove.animate().cancel()
        try {
            windowManager.removeViewImmediate(menuToRemove)
        } catch (ignored: Throwable) {
            // The overlay may already have been removed by the host.
        }
    }

    private fun stopEdgeScroll() {
        edgeDirection = 0
        circleScrollDirection = 0
        lastEdgeScrollUptime = 0L
        edgeScrollRemainderPx = 0f
        mainHandler.removeCallbacks(edgeScrollRunnable)
    }

    private fun isPortalStyle(): Boolean = settings.uiStyle == DragShareSettings.STYLE_PORTAL

    private fun isCircleStyle(): Boolean = settings.uiStyle == DragShareSettings.STYLE_CIRCLE

    private fun isModernStyle(): Boolean = settings.uiStyle == DragShareSettings.STYLE_MODERN

    private fun startBackgroundBlockerIfEnabled() {
        if (!settings.blockBackgroundScroll || backgroundBlockAttempted) {
            return
        }
        backgroundBlockAttempted = true
        if (!backgroundTouchBlocker.start()) {
            log("background scroll lock unavailable; continuing without it")
        }
    }

    private fun itemBackground(target: ShareTarget?, selected: Boolean): GradientDrawable {
        if (!selected) {
            return roundDrawable(Color.TRANSPARENT, if (isPortalStyle()) 28 else 8)
        }
        val drawable = roundDrawable(
            palette.selectedItemBackground,
            if (isPortalStyle()) 28 else 8,
        )
        if (isPortalStyle()) {
            drawable.setStroke(dp(1), palette.selectedItemBorder)
        }
        return drawable
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
        var stagedUri: Uri? = null
        var pendingTarget: ShareTarget? = null
        var cancelled: Boolean = false
    }

    private class OverlayColors private constructor(
        val previewBackground: Int,
        val menuBackground: Int,
        val primaryText: Int,
        val secondaryText: Int,
        val selectedItemBackground: Int,
        val selectedItemBorder: Int,
        val accent: Int,
    ) {
        companion object {
            fun light(): OverlayColors = OverlayColors(
                0xF7FFFFFF.toInt(),
                0xF4F8FAF9.toInt(),
                0xFF17201D.toInt(),
                0xFF55615C.toInt(),
                0x26137A5A,
                0x00000000,
                0xFF137A5A.toInt(),
            )

            fun from(settings: DragShareSettings?): OverlayColors {
                if (settings != null && settings.uiStyle == DragShareSettings.STYLE_CIRCLE) {
                    if (settings.colorMode == DragShareSettings.COLOR_DARK) {
                        return OverlayColors(
                            0xF72A2D32.toInt(),
                            0xE91E242B.toInt(),
                            0xFFF3F5F4.toInt(),
                            0xFFB7BFBB.toInt(),
                            0x4C4C9DFF,
                            0xAA7AC9FF.toInt(),
                            0xFF65C6E8.toInt(),
                        )
                    }
                    return OverlayColors(
                        0xFCFFFFFF.toInt(),
                        0xE9F7FAFC.toInt(),
                        0xFF17201D.toInt(),
                        0xFF65716B.toInt(),
                        0x3A5DABE8,
                        0xAA438CB6.toInt(),
                        0xFF2E9EB7.toInt(),
                    )
                }
                if (settings != null && settings.uiStyle == DragShareSettings.STYLE_PORTAL) {
                    if (settings.colorMode == DragShareSettings.COLOR_DARK) {
                        return OverlayColors(
                            0xF72A2D32.toInt(),
                            0x003A3E43,
                            0xFFF3F5F4.toInt(),
                            0xFFB7BFBB.toInt(),
                            0x384F8BFF,
                            0x887AC9FF.toInt(),
                            0xFF71DDEB.toInt(),
                        )
                    }
                    return OverlayColors(
                        0xFCFFFFFF.toInt(),
                        0x00FFFFFF,
                        0xFF17201D.toInt(),
                        0xFF65716B.toInt(),
                        0x306F9DFF,
                        0x8074B8FF.toInt(),
                        0xFF2CA9BD.toInt(),
                    )
                }
                if (settings != null && settings.colorMode == DragShareSettings.COLOR_DARK) {
                    return OverlayColors(
                        0xF725272A.toInt(),
                        0xF42F3135.toInt(),
                        0xFFF3F5F4.toInt(),
                        0xFFB7BFBB.toInt(),
                        0x3358B995,
                        0x6658B995,
                        0xFF58B995.toInt(),
                    )
                }
                return light()
            }
        }
    }
    companion object {
        private const val TAG = "DragShare/UI"
        private const val PREVIEW_TEXT_WIDTH_DP = 184
        private const val PREVIEW_TEXT_HEIGHT_DP = 112
        private const val PREVIEW_IMAGE_SIZE_DP = 148
        private const val PORTAL_PREVIEW_TEXT_SIZE_DP = 112
        private const val PORTAL_PREVIEW_IMAGE_SIZE_DP = 116
        private const val MENU_HEIGHT_DP = 96
        private const val SIMPLE_SIDE_MENU_WIDTH_DP = 112
        private const val PORTAL_MENU_HEIGHT_DP = 152
        private const val MENU_TRIGGER_DP = 72
        private const val SIMPLE_MENU_ACTIVATION_SLOP_DP = 16
        private const val EDGE_SCROLL_FULL_SPEED_INSET_DP = 8
        private const val PORTAL_TRIGGER_FROM_BOTTOM_DP = 96
        private const val PORTAL_ITEM_ENTER_OFFSET_DP = 168
        private const val MODERN_OVERLAY_EXIT_DURATION_MS = 220L
        private const val LINEAR_MENU_EXIT_DURATION_MS = 160L
        private const val PREVIEW_EXIT_DURATION_MS = 160L
        private const val MODERN_BOTTOM_MENU_SIDE_MARGIN_DP = 12
        private const val CIRCLE_EDGE_TRIGGER_DP = 76
        private const val CIRCLE_EDGE_SOFT_DISTANCE_DP = 180
        private const val CIRCLE_EDGE_OPEN_DELAY_MS = 200L
        private const val DUPLICATE_EVENT_WINDOW_MS = 2L
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
