package com.leaf.hyperdragshare.codex

import android.content.res.Configuration
import android.os.Bundle
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.util.LinkedHashSet

@RunWith(RobolectricTestRunner::class)
@Config(manifest = Config.NONE)
class DragShareSettingsTest {
    @Test
    fun defaultsUseModernStyleAndFullIconOpacity() {
        val settings = DragShareSettings.defaults()

        assertEquals(DragShareSettings.COLOR_LIGHT, settings.colorMode)
        assertEquals(
            DragShareSettings.CONTENT_CAPTURE_PORTAL,
            settings.contentCaptureMode,
        )
        assertEquals(DragShareSettings.STYLE_MODERN, settings.uiStyle)
        assertEquals(DragShareSettings.STYLE_MODERN, DragShareSettings.DEFAULT_UI_STYLE)
        assertEquals(
            DragShareSettings.STYLE_MODERN,
            DragShareSettings.fromBundle(Bundle()).uiStyle,
        )
        assertEquals(DragShareSettings.DEFAULT_EDGE_TRIGGER_DP, settings.edgeTriggerDp)
        assertEquals(
            DragShareSettings.DEFAULT_SCROLL_SPEED_DP_PER_SECOND,
            settings.scrollSpeedDpPerSecond,
        )
        assertEquals(
            DragShareSettings.DEFAULT_BLOCK_BACKGROUND_SCROLL,
            settings.blockBackgroundScroll,
        )
        assertEquals(
            DragShareSettings.DEFAULT_TEXT_SHARING_ENABLED,
            settings.textSharingEnabled,
        )
        assertEquals(
            DragShareSettings.DEFAULT_IMAGE_SHARING_ENABLED,
            settings.imageSharingEnabled,
        )
        assertTrue(settings.isTargetVisible(DragShareSettings.TARGET_COPY_TEXT))
        assertTrue(settings.isTargetVisible(DragShareSettings.TARGET_COPY_IMAGE))
        assertTrue(settings.preloadTextSegmenter)
        assertEquals(
            DragShareSettings.DEFAULT_SIMPLE_MENU_POSITION,
            settings.simpleMenuPosition,
        )
        assertEquals(
            DragShareSettings.DEFAULT_SIMPLE_MENU_OPACITY_PERCENT,
            settings.simpleMenuOpacityPercent,
        )
        assertEquals(
            DragShareSettings.DEFAULT_SIMPLE_MENU_CORNER_RADIUS_DP,
            settings.simpleMenuCornerRadiusDp,
        )
        assertEquals(
            DragShareSettings.DEFAULT_SIMPLE_MENU_EDGE_DISTANCE_DP,
            settings.simpleMenuEdgeDistanceDp,
        )
        assertEquals(
            DragShareSettings.DEFAULT_ICON_OPACITY_PERCENT,
            settings.iconOpacityPercent,
        )
        assertEquals(100, DragShareSettings.DEFAULT_ICON_OPACITY_PERCENT)
        assertEquals(100, settings.iconOpacityPercent)
        assertEquals(
            DragShareSettings.DEFAULT_MODERN_BLUR_RADIUS_DP,
            settings.modernBlurRadiusDp,
        )
        assertEquals(
            DragShareSettings.DEFAULT_MODERN_GLASS_OPACITY_PERCENT,
            settings.modernGlassOpacityPercent,
        )
        assertTrue(settings.closeMenuWhenPointerLeaves)
        assertTrue(settings.hiddenTargetKeys.isEmpty())
        assertTrue(settings.targetOrder.isEmpty())
        assertFalse(settings.accessibilityLandscapeRecognitionEnabled)
        assertTrue(settings.accessibilityBlacklistedPackages.isEmpty())
        assertEquals(
            DragShareSettings.DEFAULT_ACCESSIBILITY_LONG_PRESS_TIMEOUT_MILLIS,
            settings.accessibilityLongPressTimeoutMillis,
        )
        assertEquals(
            DragShareSettings.DEFAULT_ACCESSIBILITY_RECOGNITION_SENSITIVITY_PERCENT,
            settings.accessibilityRecognitionSensitivityPercent,
        )
        assertEquals(DragShareSettings.LOG_LEVEL_INFO, settings.logLevel)
        assertEquals(DragShareSettings.LOG_DESTINATION_SYSTEM, settings.logDestination)
        assertEquals(650, settings.resolveAccessibilityLongPressTimeoutMillis(650))
        assertEquals(1f, settings.accessibilityTouchSlopMultiplier(), 0f)
        assertFalse(
            settings.isAccessibilityRecognitionEnabledForOrientation(
                Configuration.ORIENTATION_LANDSCAPE,
            ),
        )
        assertTrue(
            settings.isAccessibilityRecognitionEnabledForOrientation(
                Configuration.ORIENTATION_PORTRAIT,
            ),
        )
    }

    @Test
    fun diagnosticLoggingSettingsAreClampedAndRoundTripThroughProviderBundle() {
        val settings = DragShareSettings(
            DragShareSettings.COLOR_LIGHT,
            DragShareSettings.STYLE_SIMPLE,
            DragShareSettings.DEFAULT_EDGE_TRIGGER_DP,
            DragShareSettings.DEFAULT_SCROLL_SPEED_DP_PER_SECOND,
            false,
            true,
            true,
            DragShareSettings.DEFAULT_SIMPLE_MENU_POSITION,
            DragShareSettings.DEFAULT_SIMPLE_MENU_OPACITY_PERCENT,
            DragShareSettings.DEFAULT_SIMPLE_MENU_CORNER_RADIUS_DP,
            DragShareSettings.DEFAULT_SIMPLE_MENU_EDGE_DISTANCE_DP,
            DragShareSettings.DEFAULT_ICON_OPACITY_PERCENT,
            true,
            LinkedHashSet<String>(),
            emptyList(),
            DragShareSettings.CONTENT_CAPTURE_PORTAL,
            false,
            LinkedHashSet<String>(),
            DragShareSettings.DEFAULT_ACCESSIBILITY_LONG_PRESS_TIMEOUT_MILLIS,
            DragShareSettings.DEFAULT_ACCESSIBILITY_RECOGNITION_SENSITIVITY_PERCENT,
            true,
            DragShareSettings.DEFAULT_MODERN_BLUR_RADIUS_DP,
            DragShareSettings.DEFAULT_MODERN_GLASS_OPACITY_PERCENT,
            DragShareSettings.LOG_LEVEL_DEBUG,
            DragShareSettings.LOG_DESTINATION_FILE,
        )
        val roundTripped = DragShareSettings.fromBundle(settings.toBundle())
        assertEquals(DragShareSettings.LOG_LEVEL_DEBUG, roundTripped.logLevel)
        assertEquals(DragShareSettings.LOG_DESTINATION_FILE, roundTripped.logDestination)

        val invalid = DragShareSettings(
            DragShareSettings.COLOR_LIGHT,
            DragShareSettings.STYLE_SIMPLE,
            DragShareSettings.DEFAULT_EDGE_TRIGGER_DP,
            DragShareSettings.DEFAULT_SCROLL_SPEED_DP_PER_SECOND,
            false,
            true,
            true,
            DragShareSettings.DEFAULT_SIMPLE_MENU_POSITION,
            DragShareSettings.DEFAULT_SIMPLE_MENU_OPACITY_PERCENT,
            DragShareSettings.DEFAULT_SIMPLE_MENU_CORNER_RADIUS_DP,
            DragShareSettings.DEFAULT_SIMPLE_MENU_EDGE_DISTANCE_DP,
            DragShareSettings.DEFAULT_ICON_OPACITY_PERCENT,
            true,
            LinkedHashSet<String>(),
            emptyList(),
            DragShareSettings.CONTENT_CAPTURE_PORTAL,
            false,
            LinkedHashSet<String>(),
            DragShareSettings.DEFAULT_ACCESSIBILITY_LONG_PRESS_TIMEOUT_MILLIS,
            DragShareSettings.DEFAULT_ACCESSIBILITY_RECOGNITION_SENSITIVITY_PERCENT,
            true,
            DragShareSettings.DEFAULT_MODERN_BLUR_RADIUS_DP,
            DragShareSettings.DEFAULT_MODERN_GLASS_OPACITY_PERCENT,
            99,
            99,
        )
        assertEquals(DragShareSettings.LOG_LEVEL_INFO, invalid.logLevel)
        assertEquals(DragShareSettings.LOG_DESTINATION_SYSTEM, invalid.logDestination)
    }

    @Test
    fun invalidValuesAreClampedToSupportedRanges() {
        val settings = DragShareSettings(
            99,
            Int.MIN_VALUE,
            Int.MAX_VALUE,
        )

        assertEquals(DragShareSettings.COLOR_LIGHT, settings.colorMode)
        assertEquals(DragShareSettings.DEFAULT_UI_STYLE, settings.uiStyle)
        assertEquals(DragShareSettings.MIN_EDGE_TRIGGER_DP, settings.edgeTriggerDp)
        assertEquals(
            DragShareSettings.MAX_SCROLL_SPEED_DP_PER_SECOND,
            settings.scrollSpeedDpPerSecond,
        )
    }

    @Test
    fun darkModeAndUpperEdgeValuesAreRetained() {
        val settings = DragShareSettings(
            DragShareSettings.COLOR_DARK,
            DragShareSettings.STYLE_PORTAL,
            DragShareSettings.MAX_EDGE_TRIGGER_DP,
            DragShareSettings.MAX_SCROLL_SPEED_DP_PER_SECOND,
            true,
        )

        assertEquals(DragShareSettings.COLOR_DARK, settings.colorMode)
        assertEquals(DragShareSettings.STYLE_PORTAL, settings.uiStyle)
        assertEquals(DragShareSettings.MAX_EDGE_TRIGGER_DP, settings.edgeTriggerDp)
        assertEquals(
            DragShareSettings.MAX_SCROLL_SPEED_DP_PER_SECOND,
            settings.scrollSpeedDpPerSecond,
        )
        assertEquals(true, settings.blockBackgroundScroll)
    }

    @Test
    fun circleStyleIsAcceptedAndPreserved() {
        val settings = DragShareSettings(
            DragShareSettings.COLOR_LIGHT,
            DragShareSettings.STYLE_CIRCLE,
            DragShareSettings.DEFAULT_EDGE_TRIGGER_DP,
            DragShareSettings.DEFAULT_SCROLL_SPEED_DP_PER_SECOND,
            false,
        )

        assertEquals(DragShareSettings.STYLE_CIRCLE, settings.uiStyle)
    }

    @Test
    fun modernStyleAndBlurParametersAreClampedAndRoundTrip() {
        val settings = DragShareSettings(
            DragShareSettings.COLOR_DARK,
            DragShareSettings.STYLE_MODERN,
            DragShareSettings.DEFAULT_EDGE_TRIGGER_DP,
            DragShareSettings.DEFAULT_SCROLL_SPEED_DP_PER_SECOND,
            false,
            true,
            true,
            DragShareSettings.DEFAULT_SIMPLE_MENU_POSITION,
            DragShareSettings.DEFAULT_SIMPLE_MENU_OPACITY_PERCENT,
            DragShareSettings.DEFAULT_SIMPLE_MENU_CORNER_RADIUS_DP,
            DragShareSettings.DEFAULT_SIMPLE_MENU_EDGE_DISTANCE_DP,
            DragShareSettings.DEFAULT_ICON_OPACITY_PERCENT,
            true,
            LinkedHashSet<String>(),
            emptyList(),
            DragShareSettings.CONTENT_CAPTURE_PORTAL,
            false,
            LinkedHashSet<String>(),
            DragShareSettings.DEFAULT_ACCESSIBILITY_LONG_PRESS_TIMEOUT_MILLIS,
            DragShareSettings.DEFAULT_ACCESSIBILITY_RECOGNITION_SENSITIVITY_PERCENT,
            true,
            Int.MAX_VALUE,
            Int.MIN_VALUE,
        )

        assertTrue(settings.isModernStyle())
        assertEquals(
            DragShareSettings.MAX_MODERN_BLUR_RADIUS_DP,
            settings.modernBlurRadiusDp,
        )
        assertEquals(
            DragShareSettings.MIN_MODERN_GLASS_OPACITY_PERCENT,
            settings.modernGlassOpacityPercent,
        )
        val fromBundle = DragShareSettings.fromBundle(settings.toBundle())
        assertEquals(DragShareSettings.STYLE_MODERN, fromBundle.uiStyle)
        assertEquals(settings.modernBlurRadiusDp, fromBundle.modernBlurRadiusDp)
        assertEquals(settings.modernGlassOpacityPercent, fromBundle.modernGlassOpacityPercent)
    }

    @Test
    fun sharingSwitchesAndTargetRulesAreRetained() {
        val settings = DragShareSettings(
            DragShareSettings.COLOR_LIGHT,
            DragShareSettings.STYLE_SIMPLE,
            DragShareSettings.DEFAULT_EDGE_TRIGGER_DP,
            DragShareSettings.DEFAULT_SCROLL_SPEED_DP_PER_SECOND,
            false,
            false,
            true,
            LinkedHashSet(listOf("pkg/.Hidden")),
            listOf("pkg/.Second", "pkg/.First"),
        )

        assertFalse(settings.isSharingEnabled(false))
        assertTrue(settings.isSharingEnabled(true))
        assertFalse(settings.isTargetVisible("pkg/.Hidden"))
        assertTrue(settings.isTargetVisible("pkg/.Visible"))
        assertEquals(
            listOf("pkg/.Second", "pkg/.First"),
            settings.targetOrder,
        )
    }

    @Test
    fun simpleMenuOptionsAreClamped() {
        val settings = DragShareSettings(
            DragShareSettings.COLOR_LIGHT,
            DragShareSettings.STYLE_SIMPLE,
            DragShareSettings.DEFAULT_EDGE_TRIGGER_DP,
            DragShareSettings.DEFAULT_SCROLL_SPEED_DP_PER_SECOND,
            false,
            true,
            true,
            99,
            Int.MIN_VALUE,
            Int.MAX_VALUE,
            false,
            LinkedHashSet<String>(),
            emptyList(),
        )

        assertEquals(DragShareSettings.DEFAULT_SIMPLE_MENU_POSITION, settings.simpleMenuPosition)
        assertEquals(
            DragShareSettings.MIN_SIMPLE_MENU_OPACITY_PERCENT,
            settings.simpleMenuOpacityPercent,
        )
        assertEquals(
            DragShareSettings.MAX_SIMPLE_MENU_CORNER_RADIUS_DP,
            settings.simpleMenuCornerRadiusDp,
        )
        assertFalse(settings.closeMenuWhenPointerLeaves)
    }

    @Test
    fun portalSettingsRetainSimpleMenuBackgroundOpacity() {
        val settings = DragShareSettings(
            DragShareSettings.COLOR_LIGHT,
            DragShareSettings.STYLE_SIMPLE,
            DragShareSettings.DEFAULT_EDGE_TRIGGER_DP,
            DragShareSettings.DEFAULT_SCROLL_SPEED_DP_PER_SECOND,
            false,
            true,
            true,
            DragShareSettings.DEFAULT_SIMPLE_MENU_POSITION,
            DragShareSettings.MIN_SIMPLE_MENU_OPACITY_PERCENT,
            DragShareSettings.DEFAULT_SIMPLE_MENU_CORNER_RADIUS_DP,
            DragShareSettings.DEFAULT_SIMPLE_MENU_EDGE_DISTANCE_DP,
            DragShareSettings.DEFAULT_ICON_OPACITY_PERCENT,
            true,
            LinkedHashSet<String>(),
            emptyList(),
            DragShareSettings.CONTENT_CAPTURE_PORTAL,
        )

        val portalSettings = DragShareSettings.fromBundle(settings.toBundle())

        assertTrue(portalSettings.isPortalCaptureMode())
        assertEquals(
            DragShareSettings.MIN_SIMPLE_MENU_OPACITY_PERCENT,
            portalSettings.simpleMenuOpacityPercent,
        )
        assertEquals(
            0.2f,
            DragShareController.simpleMenuBackgroundOpacityFraction(
                portalSettings.simpleMenuOpacityPercent,
            ),
            0f,
        )
    }

    @Test
    fun edgeDistanceAndIconOpacityAreClamped() {
        val settings = DragShareSettings(
            DragShareSettings.COLOR_LIGHT,
            DragShareSettings.STYLE_SIMPLE,
            DragShareSettings.DEFAULT_EDGE_TRIGGER_DP,
            DragShareSettings.DEFAULT_SCROLL_SPEED_DP_PER_SECOND,
            false,
            true,
            true,
            DragShareSettings.DEFAULT_SIMPLE_MENU_POSITION,
            DragShareSettings.DEFAULT_SIMPLE_MENU_OPACITY_PERCENT,
            DragShareSettings.DEFAULT_SIMPLE_MENU_CORNER_RADIUS_DP,
            Int.MAX_VALUE,
            Int.MIN_VALUE,
            true,
            LinkedHashSet<String>(),
            emptyList(),
        )

        assertEquals(
            DragShareSettings.MAX_SIMPLE_MENU_EDGE_DISTANCE_DP,
            settings.simpleMenuEdgeDistanceDp,
        )
        assertEquals(
            DragShareSettings.MIN_ICON_OPACITY_PERCENT,
            settings.iconOpacityPercent,
        )
    }

    @Test
    fun hiddenTargetsDoNotEnterSettingsOrder() {
        val hidden = ShareTarget.saveToLocal(null)
        val settings = DragShareSettings(
            DragShareSettings.COLOR_LIGHT,
            DragShareSettings.STYLE_SIMPLE,
            DragShareSettings.DEFAULT_EDGE_TRIGGER_DP,
            DragShareSettings.DEFAULT_SCROLL_SPEED_DP_PER_SECOND,
            false,
            true,
            true,
            LinkedHashSet(listOfNotNull(hidden.key())),
            listOfNotNull(hidden.key()),
        )

        val ordered = ShareTargetRepository.orderForSettings(
            listOf(hidden),
            settings,
        )
        assertTrue(ordered.isEmpty())
    }

    @Test
    fun builtInActionsAppearForMatchingPayloads() {
        val settings = DragShareSettings.defaults()

        val textTargets = ShareTargetRepository.applySettings(
            null,
            emptyList(),
            settings,
            false,
        )
        val imageTargets = ShareTargetRepository.applySettings(
            null,
            emptyList(),
            settings,
            true,
        )

        assertEquals(2, textTargets.size)
        assertTrue(textTargets[0].isCopyTextToClipboard())
        assertTrue(textTargets[1].isTextSegmentation())
        assertEquals(2, imageTargets.size)
        assertTrue(imageTargets[0].isCopyImageToClipboard())
        assertTrue(imageTargets[1].isSaveToLocal())
    }

    @Test
    fun accessibilityCaptureModeRoundTripsThroughBundle() {
        val settings = DragShareSettings(
            DragShareSettings.COLOR_LIGHT,
            DragShareSettings.STYLE_SIMPLE,
            DragShareSettings.DEFAULT_EDGE_TRIGGER_DP,
            DragShareSettings.DEFAULT_SCROLL_SPEED_DP_PER_SECOND,
            false,
            true,
            true,
            DragShareSettings.DEFAULT_SIMPLE_MENU_POSITION,
            DragShareSettings.DEFAULT_SIMPLE_MENU_OPACITY_PERCENT,
            DragShareSettings.DEFAULT_SIMPLE_MENU_CORNER_RADIUS_DP,
            DragShareSettings.DEFAULT_SIMPLE_MENU_EDGE_DISTANCE_DP,
            DragShareSettings.DEFAULT_ICON_OPACITY_PERCENT,
            true,
            LinkedHashSet<String>(),
            emptyList(),
            DragShareSettings.CONTENT_CAPTURE_ACCESSIBILITY,
            true,
            LinkedHashSet(listOf("pkg.accessibility.blacklisted")),
            700,
            150,
        )

        assertTrue(settings.isAccessibilityCaptureMode())
        assertTrue(settings.accessibilityLandscapeRecognitionEnabled)
        assertTrue(
            settings.isAccessibilityRecognitionEnabledForOrientation(
                Configuration.ORIENTATION_LANDSCAPE,
            ),
        )
        assertTrue(settings.isAccessibilityPackageBlacklisted("pkg.accessibility.blacklisted"))
        assertEquals(700, settings.accessibilityLongPressTimeoutMillis)
        assertEquals(150, settings.accessibilityRecognitionSensitivityPercent)
        assertEquals(1.5f, settings.accessibilityTouchSlopMultiplier(), 0f)
        assertEquals(
            DragShareSettings.CONTENT_CAPTURE_ACCESSIBILITY,
            DragShareSettings.fromBundle(settings.toBundle()).contentCaptureMode,
        )
        assertTrue(
            DragShareSettings.fromBundle(settings.toBundle())
                .accessibilityLandscapeRecognitionEnabled,
        )
        assertTrue(
            DragShareSettings.fromBundle(settings.toBundle())
                .isAccessibilityPackageBlacklisted("pkg.accessibility.blacklisted"),
        )
        assertEquals(
            700,
            DragShareSettings.fromBundle(settings.toBundle())
                .accessibilityLongPressTimeoutMillis,
        )
        assertEquals(
            150,
            DragShareSettings.fromBundle(settings.toBundle())
                .accessibilityRecognitionSensitivityPercent,
        )
    }

    @Test
    fun legacyCopyPreferencesMigrateToSeparateVisibleTargets() {
        val settings = DragShareSettings(
            DragShareSettings.COLOR_LIGHT,
            DragShareSettings.STYLE_SIMPLE,
            DragShareSettings.DEFAULT_EDGE_TRIGGER_DP,
            DragShareSettings.DEFAULT_SCROLL_SPEED_DP_PER_SECOND,
            false,
            true,
            true,
            DragShareSettings.DEFAULT_SIMPLE_MENU_POSITION,
            DragShareSettings.DEFAULT_SIMPLE_MENU_OPACITY_PERCENT,
            DragShareSettings.DEFAULT_SIMPLE_MENU_CORNER_RADIUS_DP,
            DragShareSettings.DEFAULT_SIMPLE_MENU_EDGE_DISTANCE_DP,
            DragShareSettings.DEFAULT_ICON_OPACITY_PERCENT,
            true,
            LinkedHashSet<String>(),
            emptyList(),
            DragShareSettings.CONTENT_CAPTURE_PORTAL,
            false,
            LinkedHashSet<String>(),
            DragShareSettings.DEFAULT_ACCESSIBILITY_LONG_PRESS_TIMEOUT_MILLIS,
            DragShareSettings.DEFAULT_ACCESSIBILITY_RECOGNITION_SENSITIVITY_PERCENT,
            false,
            false,
            true,
        )

        assertFalse(settings.preloadTextSegmenter)
        assertFalse(settings.isTargetVisible(DragShareSettings.TARGET_COPY_TEXT))
        assertTrue(settings.isTargetVisible(DragShareSettings.TARGET_COPY_IMAGE))
        val textTargets = ShareTargetRepository.applySettings(
            null,
            emptyList(),
            settings,
            false,
        )
        val imageTargets = ShareTargetRepository.applySettings(
            null,
            emptyList(),
            settings,
            true,
        )
        assertEquals(1, textTargets.size)
        assertTrue(textTargets[0].isTextSegmentation())
        assertEquals(2, imageTargets.size)
        assertTrue(imageTargets[0].isCopyImageToClipboard())
        assertTrue(imageTargets[1].isSaveToLocal())
        assertFalse(DragShareSettings.fromBundle(settings.toBundle()).preloadTextSegmenter)
        assertFalse(
            DragShareSettings.fromBundle(settings.toBundle())
                .isTargetVisible(DragShareSettings.TARGET_COPY_TEXT),
        )
        assertTrue(
            DragShareSettings.fromBundle(settings.toBundle())
                .isTargetVisible(DragShareSettings.TARGET_COPY_IMAGE),
        )
        assertTrue(DragShareSettings.fromBundle(Bundle()).preloadTextSegmenter)
        assertTrue(
            DragShareSettings.fromBundle(Bundle())
                .isTargetVisible(DragShareSettings.TARGET_COPY_TEXT),
        )
        assertTrue(
            DragShareSettings.fromBundle(Bundle())
                .isTargetVisible(DragShareSettings.TARGET_COPY_IMAGE),
        )

        val sharedCopyHidden = DragShareSettings(
            DragShareSettings.COLOR_LIGHT,
            DragShareSettings.STYLE_SIMPLE,
            DragShareSettings.DEFAULT_EDGE_TRIGGER_DP,
            DragShareSettings.DEFAULT_SCROLL_SPEED_DP_PER_SECOND,
            false,
            true,
            true,
            LinkedHashSet(listOf(DragShareSettings.TARGET_COPY)),
            emptyList(),
        )
        assertFalse(sharedCopyHidden.isTargetVisible(DragShareSettings.TARGET_COPY_TEXT))
        assertFalse(sharedCopyHidden.isTargetVisible(DragShareSettings.TARGET_COPY_IMAGE))
    }

    @Test
    fun accessibilityGestureValuesAreClamped() {
        val settings = DragShareSettings(
            DragShareSettings.COLOR_LIGHT,
            DragShareSettings.STYLE_SIMPLE,
            DragShareSettings.DEFAULT_EDGE_TRIGGER_DP,
            DragShareSettings.DEFAULT_SCROLL_SPEED_DP_PER_SECOND,
            false,
            true,
            true,
            DragShareSettings.DEFAULT_SIMPLE_MENU_POSITION,
            DragShareSettings.DEFAULT_SIMPLE_MENU_OPACITY_PERCENT,
            DragShareSettings.DEFAULT_SIMPLE_MENU_CORNER_RADIUS_DP,
            DragShareSettings.DEFAULT_SIMPLE_MENU_EDGE_DISTANCE_DP,
            DragShareSettings.DEFAULT_ICON_OPACITY_PERCENT,
            true,
            LinkedHashSet<String>(),
            emptyList(),
            DragShareSettings.CONTENT_CAPTURE_ACCESSIBILITY,
            false,
            LinkedHashSet<String>(),
            Int.MIN_VALUE,
            Int.MAX_VALUE,
        )

        assertEquals(
            DragShareSettings.MIN_ACCESSIBILITY_LONG_PRESS_TIMEOUT_MILLIS,
            settings.accessibilityLongPressTimeoutMillis,
        )
        assertEquals(
            DragShareSettings.MAX_ACCESSIBILITY_RECOGNITION_SENSITIVITY_PERCENT,
            settings.accessibilityRecognitionSensitivityPercent,
        )
    }

    @Test
    fun invalidCaptureModesMigrateToPortal() {
        val settings = DragShareSettings(
            DragShareSettings.COLOR_LIGHT,
            DragShareSettings.STYLE_SIMPLE,
            DragShareSettings.DEFAULT_EDGE_TRIGGER_DP,
            DragShareSettings.DEFAULT_SCROLL_SPEED_DP_PER_SECOND,
            false,
            true,
            true,
            DragShareSettings.DEFAULT_SIMPLE_MENU_POSITION,
            DragShareSettings.DEFAULT_SIMPLE_MENU_OPACITY_PERCENT,
            DragShareSettings.DEFAULT_SIMPLE_MENU_CORNER_RADIUS_DP,
            DragShareSettings.DEFAULT_SIMPLE_MENU_EDGE_DISTANCE_DP,
            DragShareSettings.DEFAULT_ICON_OPACITY_PERCENT,
            true,
            LinkedHashSet<String>(),
            emptyList(),
            99,
        )
        assertTrue(settings.isPortalCaptureMode())

        val oldBundle = Bundle()
        assertTrue(DragShareSettings.fromBundle(oldBundle).isPortalCaptureMode())
    }

    @Test
    fun backgroundScrollSettingRoundTripsThroughPortalBundle() {
        val settings = DragShareSettings(
            DragShareSettings.COLOR_LIGHT,
            DragShareSettings.STYLE_SIMPLE,
            DragShareSettings.DEFAULT_EDGE_TRIGGER_DP,
            DragShareSettings.DEFAULT_SCROLL_SPEED_DP_PER_SECOND,
            true,
        )

        assertTrue(settings.blockBackgroundScroll)
        assertTrue(DragShareSettings.fromBundle(settings.toBundle()).blockBackgroundScroll)
    }

    @Test
    fun accessibilityBlacklistCombinesUserAndBuiltInPackages() {
        assertTrue(
            AccessibilityBlacklist.isBlockedByPackages(
                "pkg.user",
                LinkedHashSet(listOf("pkg.user")),
                LinkedHashSet<String>(),
            ),
        )
        assertTrue(
            AccessibilityBlacklist.isBlockedByPackages(
                "pkg.builtin",
                LinkedHashSet<String>(),
                LinkedHashSet(listOf("pkg.builtin")),
            ),
        )
        assertFalse(
            AccessibilityBlacklist.isBlockedByPackages(
                "pkg.allowed",
                LinkedHashSet(listOf("pkg.user")),
                LinkedHashSet(listOf("pkg.builtin")),
            ),
        )
    }
}
