package com.leaf.hyperdragshare.codex

import android.content.Context
import android.content.res.Configuration
import android.os.Bundle
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

/**
 * 覆盖 `DragShareSettings` 仍然保留的设置项：归一化、bundle 往返、本地持久化与查询辅助函数。
 *
 * 旧拖拽样式相关字段（uiStyle / simpleMenu* / 图标不透明度 / 滚动参数 / 阻止背景滑动 /
 * 手指移开时关闭菜单 等）已随样式删除一并移除，对应用例同步删除。
 */
@RunWith(RobolectricTestRunner::class)
@Config(manifest = Config.NONE)
class DragShareSettingsTest {
    @Test
    fun defaultsMatchDocumentedValues() {
        val settings = DragShareSettings.defaults()

        assertEquals(DragShareSettings.COLOR_LIGHT, settings.colorMode)
        assertEquals(DragShareSettings.CONTENT_CAPTURE_PORTAL, settings.contentCaptureMode)
        assertEquals(DragShareSettings.DEFAULT_TEXT_SHARING_ENABLED, settings.textSharingEnabled)
        assertEquals(DragShareSettings.DEFAULT_IMAGE_SHARING_ENABLED, settings.imageSharingEnabled)
        assertEquals(DragShareSettings.DEFAULT_PRELOAD_TEXT_SEGMENTER, settings.preloadTextSegmenter)
        assertEquals(DragShareSettings.DEFAULT_LOG_LEVEL, settings.logLevel)
        assertEquals(DragShareSettings.DEFAULT_LOG_DESTINATION, settings.logDestination)
        assertEquals(DragShareSettings.DEFAULT_SHARED_COPY_LOCATION, settings.sharedCopyLocation)
        assertEquals(
            DragShareSettings.DEFAULT_ACCESSIBILITY_LANDSCAPE_RECOGNITION_ENABLED,
            settings.accessibilityLandscapeRecognitionEnabled,
        )
        assertEquals(
            DragShareSettings.DEFAULT_ACCESSIBILITY_RECOGNITION_SENSITIVITY_PERCENT,
            settings.accessibilityRecognitionSensitivityPercent,
        )
        assertTrue(settings.hiddenTargetKeys.isEmpty())
        assertTrue(settings.targetOrder.isEmpty())
        assertTrue(settings.accessibilityBlacklistedPackages.isEmpty())
    }

    @Test
    fun emptyBundleFallsBackToDefaults() {
        val fromBundle = DragShareSettings.fromBundle(Bundle())
        val defaults = DragShareSettings.defaults()

        assertEquals(defaults.colorMode, fromBundle.colorMode)
        assertEquals(defaults.contentCaptureMode, fromBundle.contentCaptureMode)
        assertEquals(defaults.logLevel, fromBundle.logLevel)
        assertEquals(defaults.logDestination, fromBundle.logDestination)
        assertEquals(defaults.sharedCopyLocation, fromBundle.sharedCopyLocation)
        assertTrue(DragShareSettings.fromBundle(null).isPortalCaptureMode())
    }

    @Test
    fun bundleRoundTripKeepsEverySurvivingField() {
        val settings = settings(
            colorMode = DragShareSettings.COLOR_DARK,
            contentCaptureMode = DragShareSettings.CONTENT_CAPTURE_ACCESSIBILITY,
            textSharingEnabled = false,
            imageSharingEnabled = true,
            hiddenTargetKeys = setOf("pkg.hidden", DragShareSettings.TARGET_COPY),
            targetOrder = listOf("pkg.first", "pkg.second"),
            accessibilityLandscapeRecognitionEnabled = true,
            accessibilityBlacklistedPackages = setOf("pkg.blocked"),
            accessibilityLongPressTimeoutMillis = 500,
            accessibilityRecognitionSensitivityPercent = 150,
            preloadTextSegmenter = false,
            logLevel = DragShareSettings.LOG_LEVEL_DEBUG,
            logDestination = DragShareSettings.LOG_DESTINATION_FILE,
            sharedCopyLocation = DragShareSettings.SHARED_COPY_LOCATION_PUBLIC,
        )

        val restored = DragShareSettings.fromBundle(settings.toBundle())

        assertEquals(settings.colorMode, restored.colorMode)
        assertEquals(settings.contentCaptureMode, restored.contentCaptureMode)
        assertEquals(settings.textSharingEnabled, restored.textSharingEnabled)
        assertEquals(settings.imageSharingEnabled, restored.imageSharingEnabled)
        assertEquals(settings.hiddenTargetKeys, restored.hiddenTargetKeys)
        assertEquals(settings.targetOrder, restored.targetOrder)
        assertEquals(
            settings.accessibilityLandscapeRecognitionEnabled,
            restored.accessibilityLandscapeRecognitionEnabled,
        )
        assertEquals(
            settings.accessibilityBlacklistedPackages,
            restored.accessibilityBlacklistedPackages,
        )
        assertEquals(
            settings.accessibilityLongPressTimeoutMillis,
            restored.accessibilityLongPressTimeoutMillis,
        )
        assertEquals(
            settings.accessibilityRecognitionSensitivityPercent,
            restored.accessibilityRecognitionSensitivityPercent,
        )
        assertEquals(settings.preloadTextSegmenter, restored.preloadTextSegmenter)
        assertEquals(settings.logLevel, restored.logLevel)
        assertEquals(settings.logDestination, restored.logDestination)
        assertEquals(settings.sharedCopyLocation, restored.sharedCopyLocation)
    }

    @Test
    fun colorModeNormalizesUnknownValuesToLight() {
        assertEquals(DragShareSettings.COLOR_LIGHT, settings(colorMode = 7).colorMode)
        assertEquals(
            DragShareSettings.COLOR_DARK,
            settings(colorMode = DragShareSettings.COLOR_DARK).colorMode,
        )
    }

    @Test
    fun captureModeNormalizesUnknownValuesToPortal() {
        val settings = settings(contentCaptureMode = 42)

        assertTrue(settings.isPortalCaptureMode())
        assertFalse(settings.isAccessibilityCaptureMode())
        assertTrue(
            settings(contentCaptureMode = DragShareSettings.CONTENT_CAPTURE_ACCESSIBILITY)
                .isAccessibilityCaptureMode(),
        )
    }

    @Test
    fun logLevelAndDestinationNormalizeUnknownValues() {
        val settings = settings(logLevel = 99, logDestination = 99)

        assertEquals(DragShareSettings.LOG_LEVEL_INFO, settings.logLevel)
        assertEquals(DragShareSettings.LOG_DESTINATION_SYSTEM, settings.logDestination)
        assertEquals(
            DragShareSettings.LOG_LEVEL_DISABLED,
            settings(logLevel = DragShareSettings.LOG_LEVEL_DISABLED).logLevel,
        )
        assertEquals(
            DragShareSettings.LOG_DESTINATION_FILE,
            settings(logDestination = DragShareSettings.LOG_DESTINATION_FILE).logDestination,
        )
    }

    @Test
    fun sharedCopyLocationOnlyKeepsPublicOrModule() {
        assertEquals(
            DragShareSettings.SHARED_COPY_LOCATION_PUBLIC,
            settings(
                sharedCopyLocation = DragShareSettings.SHARED_COPY_LOCATION_PUBLIC,
            ).sharedCopyLocation,
        )
        assertEquals(
            DragShareSettings.SHARED_COPY_LOCATION_MODULE,
            settings(sharedCopyLocation = 77).sharedCopyLocation,
        )
    }

    @Test
    fun accessibilityLongPressTimeoutClampsAndFollowsSystem() {
        // 0（默认）表示跟随系统，并把系统值夹到允许区间内。
        assertEquals(
            DragShareSettings.MIN_ACCESSIBILITY_LONG_PRESS_TIMEOUT_MILLIS,
            settings(accessibilityLongPressTimeoutMillis = 0)
                .resolveAccessibilityLongPressTimeoutMillis(10),
        )
        assertEquals(
            DragShareSettings.MAX_ACCESSIBILITY_LONG_PRESS_TIMEOUT_MILLIS,
            settings(accessibilityLongPressTimeoutMillis = 0)
                .resolveAccessibilityLongPressTimeoutMillis(99_999),
        )
        assertEquals(
            DragShareSettings.MAX_ACCESSIBILITY_LONG_PRESS_TIMEOUT_MILLIS,
            settings(accessibilityLongPressTimeoutMillis = 99_999)
                .accessibilityLongPressTimeoutMillis,
        )
        assertEquals(
            600,
            settings(accessibilityLongPressTimeoutMillis = 600)
                .resolveAccessibilityLongPressTimeoutMillis(300),
        )
    }

    @Test
    fun recognitionSensitivityBecomesTouchSlopMultiplier() {
        assertEquals(
            1f,
            settings(accessibilityRecognitionSensitivityPercent = 100)
                .accessibilityTouchSlopMultiplier(),
            0.0001f,
        )
        assertEquals(
            1.5f,
            settings(accessibilityRecognitionSensitivityPercent = 150)
                .accessibilityTouchSlopMultiplier(),
            0.0001f,
        )
        assertEquals(
            DragShareSettings.MAX_ACCESSIBILITY_RECOGNITION_SENSITIVITY_PERCENT,
            settings(accessibilityRecognitionSensitivityPercent = 99_999)
                .accessibilityRecognitionSensitivityPercent,
        )
    }

    @Test
    fun orientationRecognitionHonoursEnabledFlag() {
        val disabled = settings(accessibilityLandscapeRecognitionEnabled = false)
        assertTrue(
            disabled.isAccessibilityRecognitionEnabledForOrientation(
                Configuration.ORIENTATION_PORTRAIT,
            ),
        )
        assertFalse(
            disabled.isAccessibilityRecognitionEnabledForOrientation(
                Configuration.ORIENTATION_LANDSCAPE,
            ),
        )

        val enabled = settings(accessibilityLandscapeRecognitionEnabled = true)
        assertTrue(
            enabled.isAccessibilityRecognitionEnabledForOrientation(
                Configuration.ORIENTATION_LANDSCAPE,
            ),
        )
    }

    @Test
    fun hiddenTargetsDriveVisibilityAndPackageBlacklist() {
        val settings = settings(
            hiddenTargetKeys = setOf("pkg.hidden"),
            accessibilityBlacklistedPackages = setOf("pkg.blocked"),
        )

        assertFalse(settings.isTargetVisible("pkg.hidden"))
        assertTrue(settings.isTargetVisible("pkg.visible"))
        assertFalse(settings.isTargetVisible(null))
        assertTrue(settings.isAccessibilityPackageBlacklisted("pkg.blocked"))
        assertFalse(settings.isAccessibilityPackageBlacklisted("pkg.other"))
        assertFalse(settings.isAccessibilityPackageBlacklisted(null))
    }

    @Test
    fun sharingTogglesSelectTextOrImage() {
        val settings = settings(textSharingEnabled = false, imageSharingEnabled = true)

        assertFalse(settings.isSharingEnabled(image = false))
        assertTrue(settings.isSharingEnabled(image = true))
    }

    @Test
    fun localPersistenceRoundTrip() {
        val context: Context = RuntimeEnvironment.getApplication()
        val settings = settings(
            colorMode = DragShareSettings.COLOR_DARK,
            contentCaptureMode = DragShareSettings.CONTENT_CAPTURE_ACCESSIBILITY,
            targetOrder = listOf("pkg.a", "pkg.b"),
            logLevel = DragShareSettings.LOG_LEVEL_DEBUG,
            sharedCopyLocation = DragShareSettings.SHARED_COPY_LOCATION_PUBLIC,
        )

        settings.saveLocal(context)
        val restored = DragShareSettings.readLocal(context)

        assertEquals(settings.colorMode, restored.colorMode)
        assertEquals(settings.contentCaptureMode, restored.contentCaptureMode)
        assertEquals(settings.targetOrder, restored.targetOrder)
        assertEquals(settings.logLevel, restored.logLevel)
        assertEquals(settings.sharedCopyLocation, restored.sharedCopyLocation)

        // 复原默认值，避免影响同一进程内的其它用例。
        DragShareSettings.defaults().saveLocal(context)
    }

    private fun settings(
        colorMode: Int = DragShareSettings.COLOR_LIGHT,
        textSharingEnabled: Boolean = true,
        imageSharingEnabled: Boolean = true,
        hiddenTargetKeys: Set<String> = emptySet(),
        targetOrder: List<String> = emptyList(),
        contentCaptureMode: Int = DragShareSettings.CONTENT_CAPTURE_PORTAL,
        accessibilityLandscapeRecognitionEnabled: Boolean = false,
        accessibilityBlacklistedPackages: Set<String> = emptySet(),
        accessibilityLongPressTimeoutMillis: Int = 0,
        accessibilityRecognitionSensitivityPercent: Int =
            DragShareSettings.DEFAULT_ACCESSIBILITY_RECOGNITION_SENSITIVITY_PERCENT,
        preloadTextSegmenter: Boolean = true,
        logLevel: Int = DragShareSettings.DEFAULT_LOG_LEVEL,
        logDestination: Int = DragShareSettings.DEFAULT_LOG_DESTINATION,
        sharedCopyLocation: Int = DragShareSettings.DEFAULT_SHARED_COPY_LOCATION,
        frostedPlateAlphaPercent: Int = DragShareSettings.DEFAULT_FROSTED_PLATE_ALPHA_PERCENT,
        frostedBlurRadiusDp: Int = DragShareSettings.DEFAULT_FROSTED_BLUR_RADIUS_DP,
        frostedDarknessPercent: Int = DragShareSettings.DEFAULT_FROSTED_DARKNESS_PERCENT,
        translateAppPackage: String = DragShareSettings.DEFAULT_TRANSLATE_APP_PACKAGE,
    ): DragShareSettings = DragShareSettings(
        colorMode,
        textSharingEnabled,
        imageSharingEnabled,
        hiddenTargetKeys,
        targetOrder,
        contentCaptureMode,
        accessibilityLandscapeRecognitionEnabled,
        accessibilityBlacklistedPackages,
        accessibilityLongPressTimeoutMillis,
        accessibilityRecognitionSensitivityPercent,
        preloadTextSegmenter,
        logLevel,
        logDestination,
        sharedCopyLocation,
        frostedPlateAlphaPercent,
        frostedBlurRadiusDp,
        frostedDarknessPercent,
        translateAppPackage,
    )
}
