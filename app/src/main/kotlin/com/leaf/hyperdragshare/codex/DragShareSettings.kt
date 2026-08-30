package com.leaf.hyperdragshare.codex

import android.content.Context
import android.content.SharedPreferences
import android.content.res.Configuration
import android.net.Uri
import android.os.Bundle
import java.util.Collections
import java.util.LinkedHashSet

/** Shared settings stored by the module and read by the injected portal process. */
internal class DragShareSettings(
    colorMode: Int,
    uiStyle: Int,
    edgeTriggerDp: Int,
    scrollSpeedDpPerSecond: Int,
    blockBackgroundScroll: Boolean,
    textSharingEnabled: Boolean,
    imageSharingEnabled: Boolean,
    simpleMenuPosition: Int,
    simpleMenuOpacityPercent: Int,
    simpleMenuCornerRadiusDp: Int,
    simpleMenuEdgeDistanceDp: Int,
    iconOpacityPercent: Int,
    closeMenuWhenPointerLeaves: Boolean,
    hiddenTargetKeys: Set<String>?,
    targetOrder: List<String>?,
    contentCaptureMode: Int,
    accessibilityLandscapeRecognitionEnabled: Boolean,
    accessibilityBlacklistedPackages: Set<String>?,
    accessibilityLongPressTimeoutMillis: Int,
    accessibilityRecognitionSensitivityPercent: Int,
    preloadTextSegmenter: Boolean,
    modernBlurRadiusDp: Int,
    modernGlassOpacityPercent: Int,
    logLevel: Int,
    logDestination: Int,
    sharedCopyLocation: Int,
) {
    val colorMode: Int

    val contentCaptureMode: Int

    val uiStyle: Int

    val edgeTriggerDp: Int

    val scrollSpeedDpPerSecond: Int

    val blockBackgroundScroll: Boolean

    val textSharingEnabled: Boolean

    val imageSharingEnabled: Boolean

    val preloadTextSegmenter: Boolean

    val simpleMenuPosition: Int

    val simpleMenuOpacityPercent: Int

    val simpleMenuCornerRadiusDp: Int

    val simpleMenuEdgeDistanceDp: Int

    val iconOpacityPercent: Int

    val modernBlurRadiusDp: Int

    val modernGlassOpacityPercent: Int

    val closeMenuWhenPointerLeaves: Boolean

    val hiddenTargetKeys: Set<String>

    val targetOrder: List<String>

    val accessibilityLandscapeRecognitionEnabled: Boolean

    val accessibilityBlacklistedPackages: Set<String>

    val accessibilityLongPressTimeoutMillis: Int

    val accessibilityRecognitionSensitivityPercent: Int

    val logLevel: Int

    val logDestination: Int

    val sharedCopyLocation: Int

    /** Full constructor including diagnostic logging configuration. */
    init {
        this.colorMode = if (colorMode == COLOR_DARK) COLOR_DARK else COLOR_LIGHT
        this.contentCaptureMode = normalizeContentCaptureMode(contentCaptureMode)
        this.uiStyle = if (uiStyle == STYLE_SIMPLE ||
            uiStyle == STYLE_PORTAL ||
            uiStyle == STYLE_CIRCLE ||
            uiStyle == STYLE_MODERN
        ) {
            uiStyle
        } else {
            DEFAULT_UI_STYLE
        }
        this.edgeTriggerDp = clamp(
            edgeTriggerDp,
            MIN_EDGE_TRIGGER_DP,
            MAX_EDGE_TRIGGER_DP,
        )
        this.scrollSpeedDpPerSecond = clamp(
            scrollSpeedDpPerSecond,
            MIN_SCROLL_SPEED_DP_PER_SECOND,
            MAX_SCROLL_SPEED_DP_PER_SECOND,
        )
        this.blockBackgroundScroll = blockBackgroundScroll
        this.textSharingEnabled = textSharingEnabled
        this.imageSharingEnabled = imageSharingEnabled
        this.preloadTextSegmenter = preloadTextSegmenter
        this.simpleMenuPosition = normalizeSimpleMenuPosition(simpleMenuPosition)
        this.simpleMenuOpacityPercent = clamp(
            simpleMenuOpacityPercent,
            MIN_SIMPLE_MENU_OPACITY_PERCENT,
            MAX_SIMPLE_MENU_OPACITY_PERCENT,
        )
        this.simpleMenuCornerRadiusDp = clamp(
            simpleMenuCornerRadiusDp,
            MIN_SIMPLE_MENU_CORNER_RADIUS_DP,
            MAX_SIMPLE_MENU_CORNER_RADIUS_DP,
        )
        this.simpleMenuEdgeDistanceDp = clamp(
            simpleMenuEdgeDistanceDp,
            MIN_SIMPLE_MENU_EDGE_DISTANCE_DP,
            MAX_SIMPLE_MENU_EDGE_DISTANCE_DP,
        )
        this.iconOpacityPercent = clamp(
            iconOpacityPercent,
            MIN_ICON_OPACITY_PERCENT,
            MAX_ICON_OPACITY_PERCENT,
        )
        this.modernBlurRadiusDp = clamp(
            modernBlurRadiusDp,
            MIN_MODERN_BLUR_RADIUS_DP,
            MAX_MODERN_BLUR_RADIUS_DP,
        )
        this.modernGlassOpacityPercent = clamp(
            modernGlassOpacityPercent,
            MIN_MODERN_GLASS_OPACITY_PERCENT,
            MAX_MODERN_GLASS_OPACITY_PERCENT,
        )
        this.closeMenuWhenPointerLeaves = closeMenuWhenPointerLeaves
        this.hiddenTargetKeys = immutableKeys(normalizeHiddenTargetKeys(hiddenTargetKeys))
        this.targetOrder = immutableKeysAsList(targetOrder)
        this.accessibilityLandscapeRecognitionEnabled =
            accessibilityLandscapeRecognitionEnabled
        this.accessibilityBlacklistedPackages = immutableKeys(accessibilityBlacklistedPackages)
        this.accessibilityLongPressTimeoutMillis = normalizeAccessibilityLongPressTimeout(
            accessibilityLongPressTimeoutMillis,
        )
        this.accessibilityRecognitionSensitivityPercent = clamp(
            accessibilityRecognitionSensitivityPercent,
            MIN_ACCESSIBILITY_RECOGNITION_SENSITIVITY_PERCENT,
            MAX_ACCESSIBILITY_RECOGNITION_SENSITIVITY_PERCENT,
        )
        this.logLevel = normalizeLogLevel(logLevel)
        this.logDestination = normalizeLogDestination(logDestination)
        this.sharedCopyLocation = normalizeSharedCopyLocation(sharedCopyLocation)
    }

    /** Backward-compatible constructor for callers using the original settings shape. */
    constructor(colorMode: Int, edgeTriggerDp: Int, scrollSpeedDpPerSecond: Int) : this(
        colorMode,
        DEFAULT_UI_STYLE,
        edgeTriggerDp,
        scrollSpeedDpPerSecond,
        DEFAULT_BLOCK_BACKGROUND_SCROLL,
        DEFAULT_TEXT_SHARING_ENABLED,
        DEFAULT_IMAGE_SHARING_ENABLED,
        DEFAULT_SIMPLE_MENU_POSITION,
        DEFAULT_SIMPLE_MENU_OPACITY_PERCENT,
        DEFAULT_SIMPLE_MENU_CORNER_RADIUS_DP,
        DEFAULT_SIMPLE_MENU_EDGE_DISTANCE_DP,
        DEFAULT_ICON_OPACITY_PERCENT,
        DEFAULT_CLOSE_MENU_WHEN_POINTER_LEAVES,
        emptySet(),
        emptyList(),
    )

    constructor(
        colorMode: Int,
        uiStyle: Int,
        edgeTriggerDp: Int,
        scrollSpeedDpPerSecond: Int,
        blockBackgroundScroll: Boolean,
    ) : this(
        colorMode,
        uiStyle,
        edgeTriggerDp,
        scrollSpeedDpPerSecond,
        blockBackgroundScroll,
        DEFAULT_TEXT_SHARING_ENABLED,
        DEFAULT_IMAGE_SHARING_ENABLED,
        DEFAULT_SIMPLE_MENU_POSITION,
        DEFAULT_SIMPLE_MENU_OPACITY_PERCENT,
        DEFAULT_SIMPLE_MENU_CORNER_RADIUS_DP,
        DEFAULT_SIMPLE_MENU_EDGE_DISTANCE_DP,
        DEFAULT_ICON_OPACITY_PERCENT,
        DEFAULT_CLOSE_MENU_WHEN_POINTER_LEAVES,
        emptySet(),
        emptyList(),
    )

    /** Backward-compatible constructor for callers that predate simple-menu options. */
    constructor(
        colorMode: Int,
        uiStyle: Int,
        edgeTriggerDp: Int,
        scrollSpeedDpPerSecond: Int,
        blockBackgroundScroll: Boolean,
        textSharingEnabled: Boolean,
        imageSharingEnabled: Boolean,
        hiddenTargetKeys: Set<String>?,
        targetOrder: List<String>?,
    ) : this(
        colorMode,
        uiStyle,
        edgeTriggerDp,
        scrollSpeedDpPerSecond,
        blockBackgroundScroll,
        textSharingEnabled,
        imageSharingEnabled,
        DEFAULT_SIMPLE_MENU_POSITION,
        DEFAULT_SIMPLE_MENU_OPACITY_PERCENT,
        DEFAULT_SIMPLE_MENU_CORNER_RADIUS_DP,
        DEFAULT_SIMPLE_MENU_EDGE_DISTANCE_DP,
        DEFAULT_ICON_OPACITY_PERCENT,
        DEFAULT_CLOSE_MENU_WHEN_POINTER_LEAVES,
        hiddenTargetKeys,
        targetOrder,
    )

    constructor(
        colorMode: Int,
        uiStyle: Int,
        edgeTriggerDp: Int,
        scrollSpeedDpPerSecond: Int,
        blockBackgroundScroll: Boolean,
        textSharingEnabled: Boolean,
        imageSharingEnabled: Boolean,
        simpleMenuPosition: Int,
        simpleMenuOpacityPercent: Int,
        simpleMenuCornerRadiusDp: Int,
        closeMenuWhenPointerLeaves: Boolean,
        hiddenTargetKeys: Set<String>?,
        targetOrder: List<String>?,
    ) : this(
        colorMode,
        uiStyle,
        edgeTriggerDp,
        scrollSpeedDpPerSecond,
        blockBackgroundScroll,
        textSharingEnabled,
        imageSharingEnabled,
        simpleMenuPosition,
        simpleMenuOpacityPercent,
        simpleMenuCornerRadiusDp,
        DEFAULT_SIMPLE_MENU_EDGE_DISTANCE_DP,
        DEFAULT_ICON_OPACITY_PERCENT,
        closeMenuWhenPointerLeaves,
        hiddenTargetKeys,
        targetOrder,
    )

    constructor(
        colorMode: Int,
        uiStyle: Int,
        edgeTriggerDp: Int,
        scrollSpeedDpPerSecond: Int,
        blockBackgroundScroll: Boolean,
        textSharingEnabled: Boolean,
        imageSharingEnabled: Boolean,
        simpleMenuPosition: Int,
        simpleMenuOpacityPercent: Int,
        simpleMenuCornerRadiusDp: Int,
        simpleMenuEdgeDistanceDp: Int,
        iconOpacityPercent: Int,
        closeMenuWhenPointerLeaves: Boolean,
        hiddenTargetKeys: Set<String>?,
        targetOrder: List<String>?,
    ) : this(
        colorMode,
        uiStyle,
        edgeTriggerDp,
        scrollSpeedDpPerSecond,
        blockBackgroundScroll,
        textSharingEnabled,
        imageSharingEnabled,
        simpleMenuPosition,
        simpleMenuOpacityPercent,
        simpleMenuCornerRadiusDp,
        simpleMenuEdgeDistanceDp,
        iconOpacityPercent,
        closeMenuWhenPointerLeaves,
        hiddenTargetKeys,
        targetOrder,
        DEFAULT_CONTENT_CAPTURE_MODE,
    )

    /** Full constructor. Invalid capture modes intentionally migrate to the portal default. */
    constructor(
        colorMode: Int,
        uiStyle: Int,
        edgeTriggerDp: Int,
        scrollSpeedDpPerSecond: Int,
        blockBackgroundScroll: Boolean,
        textSharingEnabled: Boolean,
        imageSharingEnabled: Boolean,
        simpleMenuPosition: Int,
        simpleMenuOpacityPercent: Int,
        simpleMenuCornerRadiusDp: Int,
        simpleMenuEdgeDistanceDp: Int,
        iconOpacityPercent: Int,
        closeMenuWhenPointerLeaves: Boolean,
        hiddenTargetKeys: Set<String>?,
        targetOrder: List<String>?,
        contentCaptureMode: Int,
    ) : this(
        colorMode,
        uiStyle,
        edgeTriggerDp,
        scrollSpeedDpPerSecond,
        blockBackgroundScroll,
        textSharingEnabled,
        imageSharingEnabled,
        simpleMenuPosition,
        simpleMenuOpacityPercent,
        simpleMenuCornerRadiusDp,
        simpleMenuEdgeDistanceDp,
        iconOpacityPercent,
        closeMenuWhenPointerLeaves,
        hiddenTargetKeys,
        targetOrder,
        contentCaptureMode,
        DEFAULT_ACCESSIBILITY_LANDSCAPE_RECOGNITION_ENABLED,
        emptySet(),
    )

    /** Full constructor including accessibility-only recognition settings. */
    constructor(
        colorMode: Int,
        uiStyle: Int,
        edgeTriggerDp: Int,
        scrollSpeedDpPerSecond: Int,
        blockBackgroundScroll: Boolean,
        textSharingEnabled: Boolean,
        imageSharingEnabled: Boolean,
        simpleMenuPosition: Int,
        simpleMenuOpacityPercent: Int,
        simpleMenuCornerRadiusDp: Int,
        simpleMenuEdgeDistanceDp: Int,
        iconOpacityPercent: Int,
        closeMenuWhenPointerLeaves: Boolean,
        hiddenTargetKeys: Set<String>?,
        targetOrder: List<String>?,
        contentCaptureMode: Int,
        accessibilityLandscapeRecognitionEnabled: Boolean,
        accessibilityBlacklistedPackages: Set<String>?,
    ) : this(
        colorMode,
        uiStyle,
        edgeTriggerDp,
        scrollSpeedDpPerSecond,
        blockBackgroundScroll,
        textSharingEnabled,
        imageSharingEnabled,
        simpleMenuPosition,
        simpleMenuOpacityPercent,
        simpleMenuCornerRadiusDp,
        simpleMenuEdgeDistanceDp,
        iconOpacityPercent,
        closeMenuWhenPointerLeaves,
        hiddenTargetKeys,
        targetOrder,
        contentCaptureMode,
        accessibilityLandscapeRecognitionEnabled,
        accessibilityBlacklistedPackages,
        DEFAULT_ACCESSIBILITY_LONG_PRESS_TIMEOUT_MILLIS,
        DEFAULT_ACCESSIBILITY_RECOGNITION_SENSITIVITY_PERCENT,
    )

    /** Full constructor including all accessibility-only recognition settings. */
    constructor(
        colorMode: Int,
        uiStyle: Int,
        edgeTriggerDp: Int,
        scrollSpeedDpPerSecond: Int,
        blockBackgroundScroll: Boolean,
        textSharingEnabled: Boolean,
        imageSharingEnabled: Boolean,
        simpleMenuPosition: Int,
        simpleMenuOpacityPercent: Int,
        simpleMenuCornerRadiusDp: Int,
        simpleMenuEdgeDistanceDp: Int,
        iconOpacityPercent: Int,
        closeMenuWhenPointerLeaves: Boolean,
        hiddenTargetKeys: Set<String>?,
        targetOrder: List<String>?,
        contentCaptureMode: Int,
        accessibilityLandscapeRecognitionEnabled: Boolean,
        accessibilityBlacklistedPackages: Set<String>?,
        accessibilityLongPressTimeoutMillis: Int,
        accessibilityRecognitionSensitivityPercent: Int,
    ) : this(
        colorMode,
        uiStyle,
        edgeTriggerDp,
        scrollSpeedDpPerSecond,
        blockBackgroundScroll,
        textSharingEnabled,
        imageSharingEnabled,
        simpleMenuPosition,
        simpleMenuOpacityPercent,
        simpleMenuCornerRadiusDp,
        simpleMenuEdgeDistanceDp,
        iconOpacityPercent,
        closeMenuWhenPointerLeaves,
        hiddenTargetKeys,
        targetOrder,
        contentCaptureMode,
        accessibilityLandscapeRecognitionEnabled,
        accessibilityBlacklistedPackages,
        accessibilityLongPressTimeoutMillis,
        accessibilityRecognitionSensitivityPercent,
        DEFAULT_PRELOAD_TEXT_SEGMENTER,
    )

    /** Full constructor including all accessibility settings and tokenizer warm-up preference. */
    constructor(
        colorMode: Int,
        uiStyle: Int,
        edgeTriggerDp: Int,
        scrollSpeedDpPerSecond: Int,
        blockBackgroundScroll: Boolean,
        textSharingEnabled: Boolean,
        imageSharingEnabled: Boolean,
        simpleMenuPosition: Int,
        simpleMenuOpacityPercent: Int,
        simpleMenuCornerRadiusDp: Int,
        simpleMenuEdgeDistanceDp: Int,
        iconOpacityPercent: Int,
        closeMenuWhenPointerLeaves: Boolean,
        hiddenTargetKeys: Set<String>?,
        targetOrder: List<String>?,
        contentCaptureMode: Int,
        accessibilityLandscapeRecognitionEnabled: Boolean,
        accessibilityBlacklistedPackages: Set<String>?,
        accessibilityLongPressTimeoutMillis: Int,
        accessibilityRecognitionSensitivityPercent: Int,
        preloadTextSegmenter: Boolean,
    ) : this(
        colorMode,
        uiStyle,
        edgeTriggerDp,
        scrollSpeedDpPerSecond,
        blockBackgroundScroll,
        textSharingEnabled,
        imageSharingEnabled,
        simpleMenuPosition,
        simpleMenuOpacityPercent,
        simpleMenuCornerRadiusDp,
        simpleMenuEdgeDistanceDp,
        iconOpacityPercent,
        closeMenuWhenPointerLeaves,
        hiddenTargetKeys,
        targetOrder,
        contentCaptureMode,
        accessibilityLandscapeRecognitionEnabled,
        accessibilityBlacklistedPackages,
        accessibilityLongPressTimeoutMillis,
        accessibilityRecognitionSensitivityPercent,
        preloadTextSegmenter,
        DEFAULT_MODERN_BLUR_RADIUS_DP,
        DEFAULT_MODERN_GLASS_OPACITY_PERCENT,
    )

    /**
     * Compatibility constructor for the per-payload copy switches used before copy actions
     * became independently visible menu targets.
     */
    constructor(
        colorMode: Int,
        uiStyle: Int,
        edgeTriggerDp: Int,
        scrollSpeedDpPerSecond: Int,
        blockBackgroundScroll: Boolean,
        textSharingEnabled: Boolean,
        imageSharingEnabled: Boolean,
        simpleMenuPosition: Int,
        simpleMenuOpacityPercent: Int,
        simpleMenuCornerRadiusDp: Int,
        simpleMenuEdgeDistanceDp: Int,
        iconOpacityPercent: Int,
        closeMenuWhenPointerLeaves: Boolean,
        hiddenTargetKeys: Set<String>?,
        targetOrder: List<String>?,
        contentCaptureMode: Int,
        accessibilityLandscapeRecognitionEnabled: Boolean,
        accessibilityBlacklistedPackages: Set<String>?,
        accessibilityLongPressTimeoutMillis: Int,
        accessibilityRecognitionSensitivityPercent: Int,
        preloadTextSegmenter: Boolean,
        textCopyEnabled: Boolean,
        imageCopyEnabled: Boolean,
    ) : this(
        colorMode,
        uiStyle,
        edgeTriggerDp,
        scrollSpeedDpPerSecond,
        blockBackgroundScroll,
        textSharingEnabled,
        imageSharingEnabled,
        simpleMenuPosition,
        simpleMenuOpacityPercent,
        simpleMenuCornerRadiusDp,
        simpleMenuEdgeDistanceDp,
        iconOpacityPercent,
        closeMenuWhenPointerLeaves,
        migrateLegacyCopyTargetVisibility(
            hiddenTargetKeys,
            textCopyEnabled,
            imageCopyEnabled,
        ),
        targetOrder,
        contentCaptureMode,
        accessibilityLandscapeRecognitionEnabled,
        accessibilityBlacklistedPackages,
        accessibilityLongPressTimeoutMillis,
        accessibilityRecognitionSensitivityPercent,
        preloadTextSegmenter,
        DEFAULT_MODERN_BLUR_RADIUS_DP,
        DEFAULT_MODERN_GLASS_OPACITY_PERCENT,
    )

    /** Full constructor including Miuix modern-overlay blur parameters. */
    constructor(
        colorMode: Int,
        uiStyle: Int,
        edgeTriggerDp: Int,
        scrollSpeedDpPerSecond: Int,
        blockBackgroundScroll: Boolean,
        textSharingEnabled: Boolean,
        imageSharingEnabled: Boolean,
        simpleMenuPosition: Int,
        simpleMenuOpacityPercent: Int,
        simpleMenuCornerRadiusDp: Int,
        simpleMenuEdgeDistanceDp: Int,
        iconOpacityPercent: Int,
        closeMenuWhenPointerLeaves: Boolean,
        hiddenTargetKeys: Set<String>?,
        targetOrder: List<String>?,
        contentCaptureMode: Int,
        accessibilityLandscapeRecognitionEnabled: Boolean,
        accessibilityBlacklistedPackages: Set<String>?,
        accessibilityLongPressTimeoutMillis: Int,
        accessibilityRecognitionSensitivityPercent: Int,
        preloadTextSegmenter: Boolean,
        modernBlurRadiusDp: Int,
        modernGlassOpacityPercent: Int,
    ) : this(
        colorMode,
        uiStyle,
        edgeTriggerDp,
        scrollSpeedDpPerSecond,
        blockBackgroundScroll,
        textSharingEnabled,
        imageSharingEnabled,
        simpleMenuPosition,
        simpleMenuOpacityPercent,
        simpleMenuCornerRadiusDp,
        simpleMenuEdgeDistanceDp,
        iconOpacityPercent,
        closeMenuWhenPointerLeaves,
        hiddenTargetKeys,
        targetOrder,
        contentCaptureMode,
        accessibilityLandscapeRecognitionEnabled,
        accessibilityBlacklistedPackages,
        accessibilityLongPressTimeoutMillis,
        accessibilityRecognitionSensitivityPercent,
        preloadTextSegmenter,
        modernBlurRadiusDp,
        modernGlassOpacityPercent,
        DEFAULT_LOG_LEVEL,
        DEFAULT_LOG_DESTINATION,
    )

    /** Backward-compatible constructor for callers that predate the shared-copy choice. */
    constructor(
    colorMode: Int,
    uiStyle: Int,
    edgeTriggerDp: Int,
    scrollSpeedDpPerSecond: Int,
    blockBackgroundScroll: Boolean,
    textSharingEnabled: Boolean,
    imageSharingEnabled: Boolean,
    simpleMenuPosition: Int,
    simpleMenuOpacityPercent: Int,
    simpleMenuCornerRadiusDp: Int,
    simpleMenuEdgeDistanceDp: Int,
    iconOpacityPercent: Int,
    closeMenuWhenPointerLeaves: Boolean,
    hiddenTargetKeys: Set<String>?,
    targetOrder: List<String>?,
    contentCaptureMode: Int,
    accessibilityLandscapeRecognitionEnabled: Boolean,
    accessibilityBlacklistedPackages: Set<String>?,
    accessibilityLongPressTimeoutMillis: Int,
    accessibilityRecognitionSensitivityPercent: Int,
    preloadTextSegmenter: Boolean,
    modernBlurRadiusDp: Int,
    modernGlassOpacityPercent: Int,
    logLevel: Int,
    logDestination: Int,
    ) : this(
        colorMode,
        uiStyle,
        edgeTriggerDp,
        scrollSpeedDpPerSecond,
        blockBackgroundScroll,
        textSharingEnabled,
        imageSharingEnabled,
        simpleMenuPosition,
        simpleMenuOpacityPercent,
        simpleMenuCornerRadiusDp,
        simpleMenuEdgeDistanceDp,
        iconOpacityPercent,
        closeMenuWhenPointerLeaves,
        hiddenTargetKeys,
        targetOrder,
        contentCaptureMode,
        accessibilityLandscapeRecognitionEnabled,
        accessibilityBlacklistedPackages,
        accessibilityLongPressTimeoutMillis,
        accessibilityRecognitionSensitivityPercent,
        preloadTextSegmenter,
        modernBlurRadiusDp,
        modernGlassOpacityPercent,
        logLevel,
        logDestination,
        DEFAULT_SHARED_COPY_LOCATION,
    )

    fun saveLocal(context: Context?) {
        if (context == null) {
            return
        }
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .edit()
            .putInt(KEY_COLOR_MODE, colorMode)
            .putInt(KEY_CONTENT_CAPTURE_MODE, contentCaptureMode)
            .putInt(KEY_UI_STYLE, uiStyle)
            .putInt(KEY_EDGE_TRIGGER_DP, edgeTriggerDp)
            .putInt(KEY_SCROLL_SPEED, scrollSpeedDpPerSecond)
            .putBoolean(KEY_BLOCK_BACKGROUND_SCROLL, blockBackgroundScroll)
            .putBoolean(KEY_TEXT_SHARING_ENABLED, textSharingEnabled)
            .putBoolean(KEY_IMAGE_SHARING_ENABLED, imageSharingEnabled)
            .remove(KEY_TEXT_COPY_ENABLED)
            .remove(KEY_IMAGE_COPY_ENABLED)
            .putBoolean(KEY_PRELOAD_TEXT_SEGMENTER, preloadTextSegmenter)
            .putInt(KEY_SIMPLE_MENU_POSITION, simpleMenuPosition)
            .putInt(KEY_SIMPLE_MENU_OPACITY, simpleMenuOpacityPercent)
            .putInt(KEY_SIMPLE_MENU_CORNER_RADIUS, simpleMenuCornerRadiusDp)
            .putInt(KEY_SIMPLE_MENU_EDGE_DISTANCE, simpleMenuEdgeDistanceDp)
            .putInt(KEY_ICON_OPACITY, iconOpacityPercent)
            .putInt(KEY_MODERN_BLUR_RADIUS, modernBlurRadiusDp)
            .putInt(KEY_MODERN_GLASS_OPACITY, modernGlassOpacityPercent)
            .putBoolean(
                KEY_CLOSE_MENU_WHEN_POINTER_LEAVES,
                closeMenuWhenPointerLeaves,
            )
            .putStringSet(KEY_HIDDEN_TARGETS, LinkedHashSet(hiddenTargetKeys))
            .putString(KEY_TARGET_ORDER, joinKeys(targetOrder))
            .putBoolean(
                KEY_ACCESSIBILITY_LANDSCAPE_RECOGNITION_ENABLED,
                accessibilityLandscapeRecognitionEnabled,
            )
            .putStringSet(
                KEY_ACCESSIBILITY_BLACKLISTED_PACKAGES,
                LinkedHashSet(accessibilityBlacklistedPackages),
            )
            .putInt(
                KEY_ACCESSIBILITY_LONG_PRESS_TIMEOUT_MILLIS,
                accessibilityLongPressTimeoutMillis,
            )
            .putInt(
                KEY_ACCESSIBILITY_RECOGNITION_SENSITIVITY_PERCENT,
                accessibilityRecognitionSensitivityPercent,
            )
            .putInt(KEY_LOG_LEVEL, logLevel)
            .putInt(KEY_LOG_DESTINATION, logDestination)
            .putInt(KEY_SHARED_COPY_LOCATION, sharedCopyLocation)
            .apply()
        DragShareLog.configure(this)
        DragShareLog.i(
            "DragShare/Settings",
            "logging configured level=" + logLevel + " destination=" + logDestination,
        )
        context.contentResolver.notifyChange(settingsUri(), null)
    }

    fun toBundle(): Bundle {
        val result = Bundle()
        result.putInt(KEY_COLOR_MODE, colorMode)
        result.putInt(KEY_CONTENT_CAPTURE_MODE, contentCaptureMode)
        result.putInt(KEY_UI_STYLE, uiStyle)
        result.putInt(KEY_EDGE_TRIGGER_DP, edgeTriggerDp)
        result.putInt(KEY_SCROLL_SPEED, scrollSpeedDpPerSecond)
        result.putBoolean(KEY_BLOCK_BACKGROUND_SCROLL, blockBackgroundScroll)
        result.putBoolean(KEY_TEXT_SHARING_ENABLED, textSharingEnabled)
        result.putBoolean(KEY_IMAGE_SHARING_ENABLED, imageSharingEnabled)
        // Keep an older injected process aligned until it reloads this module version.
        result.putBoolean(
            KEY_TEXT_COPY_ENABLED,
            isTargetVisible(TARGET_COPY_TEXT),
        )
        result.putBoolean(
            KEY_IMAGE_COPY_ENABLED,
            isTargetVisible(TARGET_COPY_IMAGE),
        )
        result.putBoolean(KEY_PRELOAD_TEXT_SEGMENTER, preloadTextSegmenter)
        result.putInt(KEY_SIMPLE_MENU_POSITION, simpleMenuPosition)
        result.putInt(KEY_SIMPLE_MENU_OPACITY, simpleMenuOpacityPercent)
        result.putInt(KEY_SIMPLE_MENU_CORNER_RADIUS, simpleMenuCornerRadiusDp)
        result.putInt(KEY_SIMPLE_MENU_EDGE_DISTANCE, simpleMenuEdgeDistanceDp)
        result.putInt(KEY_ICON_OPACITY, iconOpacityPercent)
        result.putInt(KEY_MODERN_BLUR_RADIUS, modernBlurRadiusDp)
        result.putInt(KEY_MODERN_GLASS_OPACITY, modernGlassOpacityPercent)
        result.putBoolean(KEY_CLOSE_MENU_WHEN_POINTER_LEAVES, closeMenuWhenPointerLeaves)
        result.putStringArrayList(KEY_HIDDEN_TARGETS, ArrayList(hiddenTargetKeys))
        result.putStringArrayList(KEY_TARGET_ORDER, ArrayList(targetOrder))
        result.putBoolean(
            KEY_ACCESSIBILITY_LANDSCAPE_RECOGNITION_ENABLED,
            accessibilityLandscapeRecognitionEnabled,
        )
        result.putStringArrayList(
            KEY_ACCESSIBILITY_BLACKLISTED_PACKAGES,
            ArrayList(accessibilityBlacklistedPackages),
        )
        result.putInt(
            KEY_ACCESSIBILITY_LONG_PRESS_TIMEOUT_MILLIS,
            accessibilityLongPressTimeoutMillis,
        )
        result.putInt(
            KEY_ACCESSIBILITY_RECOGNITION_SENSITIVITY_PERCENT,
            accessibilityRecognitionSensitivityPercent,
        )
        result.putInt(KEY_LOG_LEVEL, logLevel)
        result.putInt(KEY_LOG_DESTINATION, logDestination)
        result.putInt(KEY_SHARED_COPY_LOCATION, sharedCopyLocation)
        return result
    }

    fun isSharingEnabled(image: Boolean): Boolean =
        if (image) imageSharingEnabled else textSharingEnabled

    fun isPortalCaptureMode(): Boolean = contentCaptureMode == CONTENT_CAPTURE_PORTAL

    fun isAccessibilityCaptureMode(): Boolean =
        contentCaptureMode == CONTENT_CAPTURE_ACCESSIBILITY

    fun isModernStyle(): Boolean = uiStyle == STYLE_MODERN

    fun isAccessibilityPackageBlacklisted(packageName: String?): Boolean =
        packageName != null && accessibilityBlacklistedPackages.contains(packageName)

    fun isAccessibilityRecognitionEnabledForOrientation(orientation: Int): Boolean =
        accessibilityLandscapeRecognitionEnabled ||
            orientation != Configuration.ORIENTATION_LANDSCAPE

    fun resolveAccessibilityLongPressTimeoutMillis(systemTimeoutMillis: Int): Int {
        if (accessibilityLongPressTimeoutMillis !=
            DEFAULT_ACCESSIBILITY_LONG_PRESS_TIMEOUT_MILLIS
        ) {
            return accessibilityLongPressTimeoutMillis
        }
        return clamp(
            systemTimeoutMillis,
            MIN_ACCESSIBILITY_LONG_PRESS_TIMEOUT_MILLIS,
            MAX_ACCESSIBILITY_LONG_PRESS_TIMEOUT_MILLIS,
        )
    }

    fun accessibilityTouchSlopMultiplier(): Float =
        accessibilityRecognitionSensitivityPercent / 100f

    fun isTargetVisible(key: String?): Boolean = key != null && !hiddenTargetKeys.contains(key)

    companion object {
        const val COLOR_LIGHT = 0
        const val COLOR_DARK = 1

        const val CONTENT_CAPTURE_PORTAL = 0
        const val CONTENT_CAPTURE_ACCESSIBILITY = 1
        const val DEFAULT_CONTENT_CAPTURE_MODE = CONTENT_CAPTURE_PORTAL

        const val LOG_LEVEL_DISABLED = 0
        const val LOG_LEVEL_INFO = 1
        const val LOG_LEVEL_DEBUG = 2
        const val DEFAULT_LOG_LEVEL = LOG_LEVEL_INFO

        const val LOG_DESTINATION_SYSTEM = 0
        const val LOG_DESTINATION_FILE = 1
        const val DEFAULT_LOG_DESTINATION = LOG_DESTINATION_SYSTEM

        /**
         * Publishes a short-lived copy into the shared media collection when an image is handed to
         * another app, for the minority of devices where a recipient reports the image as missing.
         * The price is that galleries can see the copy while it exists, so this is opt-in.
         */
        const val SHARED_COPY_LOCATION_PUBLIC = 0

        /**
         * Keeps the image in the module's private cache — the default, because the private
         * capability URI works on the devices this module has been verified on and leaves nothing
         * in the gallery. A recipient that refuses a third-party authority cannot read it.
         */
        const val SHARED_COPY_LOCATION_MODULE = 1
        const val DEFAULT_SHARED_COPY_LOCATION = SHARED_COPY_LOCATION_MODULE

        /** Change notification only; settings values remain behind the trusted Provider RPC. */
        private const val SETTINGS_URI_VALUE =
            "content://com.leaf.hyperdragshare.codex.share/settings"

        /** Compact overlay retained from the original implementation. */
        const val STYLE_SIMPLE = 0

        /** Animated bottom-glow and spring tray modeled after Content Portal. */
        const val STYLE_PORTAL = 1

        /** Left/right semicircle menu modeled after Oplus ROM circlemenuview. */
        const val STYLE_CIRCLE = 2

        /** HyperOS View-blurred overlay with an adaptive square preview. */
        const val STYLE_MODERN = 3
        const val DEFAULT_UI_STYLE = STYLE_MODERN

        @Deprecated("Kept as a source-compatibility alias for pre-1.4 callers.")
        const val STYLE_CARD = STYLE_PORTAL

        const val MIN_EDGE_TRIGGER_DP = 24
        const val DEFAULT_EDGE_TRIGGER_DP = 56
        const val MAX_EDGE_TRIGGER_DP = 200

        const val MIN_SCROLL_SPEED_DP_PER_SECOND = 120
        const val DEFAULT_SCROLL_SPEED_DP_PER_SECOND = 560
        const val MAX_SCROLL_SPEED_DP_PER_SECOND = 1200

        const val SIMPLE_MENU_POSITION_TOP = 0
        const val SIMPLE_MENU_POSITION_BOTTOM = 1
        const val SIMPLE_MENU_POSITION_LEFT = 2
        const val SIMPLE_MENU_POSITION_RIGHT = 3
        const val SIMPLE_MENU_POSITION_NEAR_HAND = 4
        const val DEFAULT_SIMPLE_MENU_POSITION = SIMPLE_MENU_POSITION_BOTTOM

        const val MIN_SIMPLE_MENU_OPACITY_PERCENT = 20
        const val DEFAULT_SIMPLE_MENU_OPACITY_PERCENT = 100
        const val MAX_SIMPLE_MENU_OPACITY_PERCENT = 100

        const val MIN_SIMPLE_MENU_CORNER_RADIUS_DP = 0
        const val DEFAULT_SIMPLE_MENU_CORNER_RADIUS_DP = 8
        const val MAX_SIMPLE_MENU_CORNER_RADIUS_DP = 32

        const val MIN_SIMPLE_MENU_EDGE_DISTANCE_DP = 0
        const val DEFAULT_SIMPLE_MENU_EDGE_DISTANCE_DP = 8
        const val MAX_SIMPLE_MENU_EDGE_DISTANCE_DP = 64

        const val MIN_ICON_OPACITY_PERCENT = 0
        const val DEFAULT_ICON_OPACITY_PERCENT = 100
        const val MAX_ICON_OPACITY_PERCENT = 100

        const val MIN_MODERN_BLUR_RADIUS_DP = 0
        const val DEFAULT_MODERN_BLUR_RADIUS_DP = 60
        const val MAX_MODERN_BLUR_RADIUS_DP = 150

        const val MIN_MODERN_GLASS_OPACITY_PERCENT = 0
        const val DEFAULT_MODERN_GLASS_OPACITY_PERCENT = 36
        const val MAX_MODERN_GLASS_OPACITY_PERCENT = 90

        const val DEFAULT_BLOCK_BACKGROUND_SCROLL = false
        const val DEFAULT_TEXT_SHARING_ENABLED = true
        const val DEFAULT_IMAGE_SHARING_ENABLED = true
        const val DEFAULT_PRELOAD_TEXT_SEGMENTER = true
        const val DEFAULT_CLOSE_MENU_WHEN_POINTER_LEAVES = true
        const val DEFAULT_ACCESSIBILITY_LANDSCAPE_RECOGNITION_ENABLED = false
        const val MIN_ACCESSIBILITY_LONG_PRESS_TIMEOUT_MILLIS = 250

        /** Uses the platform long-press delay until the user moves the setting slider. */
        const val DEFAULT_ACCESSIBILITY_LONG_PRESS_TIMEOUT_MILLIS = 0
        const val MAX_ACCESSIBILITY_LONG_PRESS_TIMEOUT_MILLIS = 1200
        const val MIN_ACCESSIBILITY_RECOGNITION_SENSITIVITY_PERCENT = 50
        const val DEFAULT_ACCESSIBILITY_RECOGNITION_SENSITIVITY_PERCENT = 100
        const val MAX_ACCESSIBILITY_RECOGNITION_SENSITIVITY_PERCENT = 200

        /** Stable key for the built-in text copy action. It is kept ahead of app targets. */
        const val TARGET_COPY_TEXT = "builtin:copy_text"

        /** Stable key for the built-in image copy action. It is kept ahead of app targets. */
        const val TARGET_COPY_IMAGE = "builtin:copy_image"

        /** Legacy key used by versions that exposed one shared copy action. */
        const val TARGET_COPY = "builtin:copy"

        /** Stable key for the built-in image action. It is kept ahead of app targets. */
        const val TARGET_SAVE_LOCAL = "builtin:save_local"

        /** Stable key for the built-in text action. It is kept ahead of app targets. */
        const val TARGET_TEXT_SEGMENTATION = "builtin:text_segmentation"

        const val METHOD_GET_SETTINGS = "get_settings"

        private const val PREFS_NAME = "drag_share_settings"
        private const val KEY_COLOR_MODE = "color_mode"
        private const val KEY_CONTENT_CAPTURE_MODE = "content_capture_mode"
        private const val KEY_UI_STYLE = "ui_style"
        private const val KEY_EDGE_TRIGGER_DP = "edge_trigger_dp"
        private const val KEY_SCROLL_SPEED = "scroll_speed_dp_per_second"
        private const val KEY_BLOCK_BACKGROUND_SCROLL = "block_background_scroll"
        private const val KEY_TEXT_SHARING_ENABLED = "text_sharing_enabled"
        private const val KEY_IMAGE_SHARING_ENABLED = "image_sharing_enabled"
        private const val KEY_TEXT_COPY_ENABLED = "text_copy_enabled"
        private const val KEY_IMAGE_COPY_ENABLED = "image_copy_enabled"
        private const val KEY_PRELOAD_TEXT_SEGMENTER = "preload_text_segmenter"
        private const val KEY_SIMPLE_MENU_POSITION = "simple_menu_position"
        private const val KEY_SIMPLE_MENU_OPACITY = "simple_menu_opacity_percent"
        private const val KEY_SIMPLE_MENU_CORNER_RADIUS = "simple_menu_corner_radius_dp"
        private const val KEY_SIMPLE_MENU_EDGE_DISTANCE = "simple_menu_edge_distance_dp"
        private const val KEY_ICON_OPACITY = "icon_opacity_percent"
        private const val KEY_MODERN_BLUR_RADIUS = "modern_blur_radius_dp"
        private const val KEY_MODERN_GLASS_OPACITY = "modern_glass_opacity_percent"
        private const val KEY_CLOSE_MENU_WHEN_POINTER_LEAVES = "close_menu_when_pointer_leaves"
        private const val KEY_HIDDEN_TARGETS = "hidden_targets"
        private const val KEY_TARGET_ORDER = "target_order"
        private const val KEY_ACCESSIBILITY_LANDSCAPE_RECOGNITION_ENABLED =
            "accessibility_landscape_recognition_enabled"
        private const val KEY_ACCESSIBILITY_BLACKLISTED_PACKAGES =
            "accessibility_blacklisted_packages"
        private const val KEY_ACCESSIBILITY_LONG_PRESS_TIMEOUT_MILLIS =
            "accessibility_long_press_timeout_millis"
        private const val KEY_ACCESSIBILITY_RECOGNITION_SENSITIVITY_PERCENT =
            "accessibility_recognition_sensitivity_percent"
        private const val KEY_LOG_LEVEL = "log_level"
        private const val KEY_LOG_DESTINATION = "log_destination"
        private const val KEY_SHARED_COPY_LOCATION = "shared_copy_location"


        fun defaults(): DragShareSettings = DragShareSettings(
            COLOR_LIGHT,
            DEFAULT_UI_STYLE,
            DEFAULT_EDGE_TRIGGER_DP,
            DEFAULT_SCROLL_SPEED_DP_PER_SECOND,
            DEFAULT_BLOCK_BACKGROUND_SCROLL,
            DEFAULT_TEXT_SHARING_ENABLED,
            DEFAULT_IMAGE_SHARING_ENABLED,
            DEFAULT_SIMPLE_MENU_POSITION,
            DEFAULT_SIMPLE_MENU_OPACITY_PERCENT,
            DEFAULT_SIMPLE_MENU_CORNER_RADIUS_DP,
            DEFAULT_SIMPLE_MENU_EDGE_DISTANCE_DP,
            DEFAULT_ICON_OPACITY_PERCENT,
            DEFAULT_CLOSE_MENU_WHEN_POINTER_LEAVES,
            emptySet(),
            emptyList(),
            DEFAULT_CONTENT_CAPTURE_MODE,
        )

        fun readLocal(context: Context?): DragShareSettings {
            if (context == null) {
                return defaults()
            }
            val preferences: SharedPreferences = context.getSharedPreferences(
                PREFS_NAME,
                Context.MODE_PRIVATE,
            )
            val hiddenTargetKeys = migrateLegacyCopyTargetVisibility(
                preferences.getStringSet(KEY_HIDDEN_TARGETS, emptySet()),
                preferences.getBoolean(KEY_TEXT_COPY_ENABLED, true),
                preferences.getBoolean(KEY_IMAGE_COPY_ENABLED, true),
            )
            return DragShareSettings(
                preferences.getInt(KEY_COLOR_MODE, COLOR_LIGHT),
                preferences.getInt(KEY_UI_STYLE, DEFAULT_UI_STYLE),
                preferences.getInt(KEY_EDGE_TRIGGER_DP, DEFAULT_EDGE_TRIGGER_DP),
                preferences.getInt(
                    KEY_SCROLL_SPEED,
                    DEFAULT_SCROLL_SPEED_DP_PER_SECOND,
                ),
                preferences.getBoolean(
                    KEY_BLOCK_BACKGROUND_SCROLL,
                    DEFAULT_BLOCK_BACKGROUND_SCROLL,
                ),
                preferences.getBoolean(
                    KEY_TEXT_SHARING_ENABLED,
                    DEFAULT_TEXT_SHARING_ENABLED,
                ),
                preferences.getBoolean(
                    KEY_IMAGE_SHARING_ENABLED,
                    DEFAULT_IMAGE_SHARING_ENABLED,
                ),
                preferences.getInt(
                    KEY_SIMPLE_MENU_POSITION,
                    DEFAULT_SIMPLE_MENU_POSITION,
                ),
                preferences.getInt(
                    KEY_SIMPLE_MENU_OPACITY,
                    DEFAULT_SIMPLE_MENU_OPACITY_PERCENT,
                ),
                preferences.getInt(
                    KEY_SIMPLE_MENU_CORNER_RADIUS,
                    DEFAULT_SIMPLE_MENU_CORNER_RADIUS_DP,
                ),
                preferences.getInt(
                    KEY_SIMPLE_MENU_EDGE_DISTANCE,
                    DEFAULT_SIMPLE_MENU_EDGE_DISTANCE_DP,
                ),
                preferences.getInt(
                    KEY_ICON_OPACITY,
                    DEFAULT_ICON_OPACITY_PERCENT,
                ),
                preferences.getBoolean(
                    KEY_CLOSE_MENU_WHEN_POINTER_LEAVES,
                    DEFAULT_CLOSE_MENU_WHEN_POINTER_LEAVES,
                ),
                hiddenTargetKeys,
                parseKeys(preferences.getString(KEY_TARGET_ORDER, "")),
                preferences.getInt(
                    KEY_CONTENT_CAPTURE_MODE,
                    DEFAULT_CONTENT_CAPTURE_MODE,
                ),
                preferences.getBoolean(
                    KEY_ACCESSIBILITY_LANDSCAPE_RECOGNITION_ENABLED,
                    DEFAULT_ACCESSIBILITY_LANDSCAPE_RECOGNITION_ENABLED,
                ),
                preferences.getStringSet(
                    KEY_ACCESSIBILITY_BLACKLISTED_PACKAGES,
                    emptySet(),
                ),
                preferences.getInt(
                    KEY_ACCESSIBILITY_LONG_PRESS_TIMEOUT_MILLIS,
                    DEFAULT_ACCESSIBILITY_LONG_PRESS_TIMEOUT_MILLIS,
                ),
                preferences.getInt(
                    KEY_ACCESSIBILITY_RECOGNITION_SENSITIVITY_PERCENT,
                    DEFAULT_ACCESSIBILITY_RECOGNITION_SENSITIVITY_PERCENT,
                ),
                preferences.getBoolean(
                    KEY_PRELOAD_TEXT_SEGMENTER,
                    DEFAULT_PRELOAD_TEXT_SEGMENTER,
                ),
                preferences.getInt(
                    KEY_MODERN_BLUR_RADIUS,
                    DEFAULT_MODERN_BLUR_RADIUS_DP,
                ),
                preferences.getInt(
                    KEY_MODERN_GLASS_OPACITY,
                    DEFAULT_MODERN_GLASS_OPACITY_PERCENT,
                ),
                preferences.getInt(KEY_LOG_LEVEL, DEFAULT_LOG_LEVEL),
                preferences.getInt(KEY_LOG_DESTINATION, DEFAULT_LOG_DESTINATION),
                preferences.getInt(
                    KEY_SHARED_COPY_LOCATION,
                    DEFAULT_SHARED_COPY_LOCATION,
                ),
            )
        }

        fun readFromProvider(portalContext: Context?): DragShareSettings {
            if (portalContext == null) {
                return defaults()
            }
            return try {
                val result = portalContext.contentResolver.call(
                    ImageStagingClient.BASE_URI,
                    METHOD_GET_SETTINGS,
                    null,
                    null,
                )
                fromBundle(result)
            } catch (ignored: Throwable) {
                defaults()
            }
        }

        fun fromBundle(bundle: Bundle?): DragShareSettings {
            if (bundle == null) {
                return defaults()
            }
            val hiddenValues = bundle.getStringArrayList(KEY_HIDDEN_TARGETS)
            val orderValues = bundle.getStringArrayList(KEY_TARGET_ORDER)
            val accessibilityBlacklistValues = bundle.getStringArrayList(
                KEY_ACCESSIBILITY_BLACKLISTED_PACKAGES,
            )
            val hiddenTargetKeys = migrateLegacyCopyTargetVisibility(
                if (hiddenValues == null) emptySet() else LinkedHashSet(hiddenValues),
                bundle.getBoolean(KEY_TEXT_COPY_ENABLED, true),
                bundle.getBoolean(KEY_IMAGE_COPY_ENABLED, true),
            )
            return DragShareSettings(
                bundle.getInt(KEY_COLOR_MODE, COLOR_LIGHT),
                bundle.getInt(KEY_UI_STYLE, DEFAULT_UI_STYLE),
                bundle.getInt(KEY_EDGE_TRIGGER_DP, DEFAULT_EDGE_TRIGGER_DP),
                bundle.getInt(
                    KEY_SCROLL_SPEED,
                    DEFAULT_SCROLL_SPEED_DP_PER_SECOND,
                ),
                bundle.getBoolean(
                    KEY_BLOCK_BACKGROUND_SCROLL,
                    DEFAULT_BLOCK_BACKGROUND_SCROLL,
                ),
                bundle.getBoolean(
                    KEY_TEXT_SHARING_ENABLED,
                    DEFAULT_TEXT_SHARING_ENABLED,
                ),
                bundle.getBoolean(
                    KEY_IMAGE_SHARING_ENABLED,
                    DEFAULT_IMAGE_SHARING_ENABLED,
                ),
                bundle.getInt(
                    KEY_SIMPLE_MENU_POSITION,
                    DEFAULT_SIMPLE_MENU_POSITION,
                ),
                bundle.getInt(
                    KEY_SIMPLE_MENU_OPACITY,
                    DEFAULT_SIMPLE_MENU_OPACITY_PERCENT,
                ),
                bundle.getInt(
                    KEY_SIMPLE_MENU_CORNER_RADIUS,
                    DEFAULT_SIMPLE_MENU_CORNER_RADIUS_DP,
                ),
                bundle.getInt(
                    KEY_SIMPLE_MENU_EDGE_DISTANCE,
                    DEFAULT_SIMPLE_MENU_EDGE_DISTANCE_DP,
                ),
                bundle.getInt(
                    KEY_ICON_OPACITY,
                    DEFAULT_ICON_OPACITY_PERCENT,
                ),
                bundle.getBoolean(
                    KEY_CLOSE_MENU_WHEN_POINTER_LEAVES,
                    DEFAULT_CLOSE_MENU_WHEN_POINTER_LEAVES,
                ),
                hiddenTargetKeys,
                if (orderValues == null) emptyList() else orderValues,
                bundle.getInt(
                    KEY_CONTENT_CAPTURE_MODE,
                    DEFAULT_CONTENT_CAPTURE_MODE,
                ),
                bundle.getBoolean(
                    KEY_ACCESSIBILITY_LANDSCAPE_RECOGNITION_ENABLED,
                    DEFAULT_ACCESSIBILITY_LANDSCAPE_RECOGNITION_ENABLED,
                ),
                if (accessibilityBlacklistValues == null) {
                    emptySet()
                } else {
                    LinkedHashSet(accessibilityBlacklistValues)
                },
                bundle.getInt(
                    KEY_ACCESSIBILITY_LONG_PRESS_TIMEOUT_MILLIS,
                    DEFAULT_ACCESSIBILITY_LONG_PRESS_TIMEOUT_MILLIS,
                ),
                bundle.getInt(
                    KEY_ACCESSIBILITY_RECOGNITION_SENSITIVITY_PERCENT,
                    DEFAULT_ACCESSIBILITY_RECOGNITION_SENSITIVITY_PERCENT,
                ),
                bundle.getBoolean(
                    KEY_PRELOAD_TEXT_SEGMENTER,
                    DEFAULT_PRELOAD_TEXT_SEGMENTER,
                ),
                bundle.getInt(
                    KEY_MODERN_BLUR_RADIUS,
                    DEFAULT_MODERN_BLUR_RADIUS_DP,
                ),
                bundle.getInt(
                    KEY_MODERN_GLASS_OPACITY,
                    DEFAULT_MODERN_GLASS_OPACITY_PERCENT,
                ),
                bundle.getInt(KEY_LOG_LEVEL, DEFAULT_LOG_LEVEL),
                bundle.getInt(KEY_LOG_DESTINATION, DEFAULT_LOG_DESTINATION),
                bundle.getInt(
                    KEY_SHARED_COPY_LOCATION,
                    DEFAULT_SHARED_COPY_LOCATION,
                ),
            )
        }

        fun settingsUri(): Uri = Uri.parse(SETTINGS_URI_VALUE)

        private fun migrateLegacyCopyTargetVisibility(
            hiddenTargetKeys: Set<String>?,
            textCopyEnabled: Boolean,
            imageCopyEnabled: Boolean,
        ): Set<String> {
            val hidden = LinkedHashSet(
                normalizeHiddenTargetKeys(hiddenTargetKeys),
            )
            if (!textCopyEnabled) {
                hidden.add(TARGET_COPY_TEXT)
            }
            if (!imageCopyEnabled) {
                hidden.add(TARGET_COPY_IMAGE)
            }
            return hidden
        }

        private fun normalizeHiddenTargetKeys(hiddenTargetKeys: Set<String>?): Set<String> {
            val hidden = LinkedHashSet<String>()
            if (hiddenTargetKeys != null) {
                hidden.addAll(hiddenTargetKeys)
            }
            if (hidden.remove(TARGET_COPY)) {
                hidden.add(TARGET_COPY_TEXT)
                hidden.add(TARGET_COPY_IMAGE)
            }
            return hidden
        }

        private fun immutableKeys(values: Collection<String?>?): Set<String> {
            val result = LinkedHashSet<String>()
            if (values != null) {
                for (value in values) {
                    if (value != null && value.trim { it <= ' ' }.isNotEmpty()) {
                        result.add(value)
                    }
                }
            }
            return Collections.unmodifiableSet(result)
        }

        private fun immutableKeysAsList(values: Collection<String?>?): List<String> {
            val result = ArrayList<String>()
            if (values != null) {
                for (value in values) {
                    if (value != null &&
                        value.trim { it <= ' ' }.isNotEmpty() &&
                        !result.contains(value)
                    ) {
                        result.add(value)
                    }
                }
            }
            return Collections.unmodifiableList(result)
        }

        private fun joinKeys(values: List<String?>?): String {
            val result = StringBuilder()
            if (values == null) {
                return ""
            }
            for (value in values) {
                if (value == null || value.trim { it <= ' ' }.isEmpty() ||
                    value.indexOf('\n') >= 0
                ) {
                    continue
                }
                if (result.isNotEmpty()) {
                    result.append('\n')
                }
                result.append(value)
            }
            return result.toString()
        }

        private fun parseKeys(encoded: String?): List<String> {
            val result = ArrayList<String>()
            if (encoded == null || encoded.isEmpty()) {
                return result
            }
            val values = encoded.split("\n").dropLastWhile { it.isEmpty() }
            result.addAll(values)
            return result
        }

        private fun clamp(value: Int, minimum: Int, maximum: Int): Int =
            Math.max(minimum, Math.min(maximum, value))

        private fun normalizeSimpleMenuPosition(position: Int): Int =
            if (position >= SIMPLE_MENU_POSITION_TOP &&
                position <= SIMPLE_MENU_POSITION_NEAR_HAND
            ) {
                position
            } else {
                DEFAULT_SIMPLE_MENU_POSITION
            }

        private fun normalizeContentCaptureMode(mode: Int): Int =
            if (mode == CONTENT_CAPTURE_ACCESSIBILITY) {
                CONTENT_CAPTURE_ACCESSIBILITY
            } else {
                CONTENT_CAPTURE_PORTAL
            }

        private fun normalizeAccessibilityLongPressTimeout(timeoutMillis: Int): Int {
            if (timeoutMillis == DEFAULT_ACCESSIBILITY_LONG_PRESS_TIMEOUT_MILLIS) {
                return timeoutMillis
            }
            return clamp(
                timeoutMillis,
                MIN_ACCESSIBILITY_LONG_PRESS_TIMEOUT_MILLIS,
                MAX_ACCESSIBILITY_LONG_PRESS_TIMEOUT_MILLIS,
            )
        }

        private fun normalizeLogLevel(level: Int): Int {
            if (level == LOG_LEVEL_DISABLED || level == LOG_LEVEL_DEBUG) {
                return level
            }
            return LOG_LEVEL_INFO
        }

        private fun normalizeSharedCopyLocation(location: Int): Int =
            if (location == SHARED_COPY_LOCATION_PUBLIC) {
                SHARED_COPY_LOCATION_PUBLIC
            } else {
                SHARED_COPY_LOCATION_MODULE
            }

        private fun normalizeLogDestination(destination: Int): Int =
            if (destination == LOG_DESTINATION_FILE) {
                LOG_DESTINATION_FILE
            } else {
                LOG_DESTINATION_SYSTEM
            }
    }
}
