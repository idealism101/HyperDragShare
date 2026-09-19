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
    textSharingEnabled: Boolean,
    imageSharingEnabled: Boolean,
    hiddenTargetKeys: Set<String>?,
    targetOrder: List<String>?,
    contentCaptureMode: Int,
    accessibilityLandscapeRecognitionEnabled: Boolean,
    accessibilityBlacklistedPackages: Set<String>?,
    accessibilityLongPressTimeoutMillis: Int,
    accessibilityRecognitionSensitivityPercent: Int,
    preloadTextSegmenter: Boolean,
    logLevel: Int,
    logDestination: Int,
    sharedCopyLocation: Int,
    frostedPlateAlphaPercent: Int,
    frostedBlurRadiusDp: Int,
    frostedDarknessPercent: Int,
    translateAppPackage: String?,
) {
    val colorMode: Int

    val contentCaptureMode: Int

    val textSharingEnabled: Boolean

    val imageSharingEnabled: Boolean

    val preloadTextSegmenter: Boolean

    val hiddenTargetKeys: Set<String>

    val targetOrder: List<String>

    val accessibilityLandscapeRecognitionEnabled: Boolean

    val accessibilityBlacklistedPackages: Set<String>

    val accessibilityLongPressTimeoutMillis: Int

    val accessibilityRecognitionSensitivityPercent: Int

    val logLevel: Int

    val logDestination: Int

    val sharedCopyLocation: Int

    /** 磨砂面板背板不透明度（%）：越低越透出背后的模糊内容。 */
    val frostedPlateAlphaPercent: Int

    /** 磨砂程度（背景模糊半径 dp）：0 = 不模糊。 */
    val frostedBlurRadiusDp: Int

    /** 磨砂面板暗黑程度（%）：0 = 白玻璃，100 = 纯黑。 */
    val frostedDarknessPercent: Int

    /**
     * 翻译按钮要交给哪个应用（包名）。空串 = 不指定，保持"复制文字 + 提示"的原有行为。
     */
    val translateAppPackage: String

    /** Full constructor including diagnostic logging configuration. */
    init {
        this.colorMode = if (colorMode == COLOR_DARK) COLOR_DARK else COLOR_LIGHT
        this.contentCaptureMode = normalizeContentCaptureMode(contentCaptureMode)
        this.textSharingEnabled = textSharingEnabled
        this.imageSharingEnabled = imageSharingEnabled
        this.preloadTextSegmenter = preloadTextSegmenter
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
        this.frostedPlateAlphaPercent = clamp(
            frostedPlateAlphaPercent,
            MIN_FROSTED_PLATE_ALPHA_PERCENT,
            MAX_FROSTED_PLATE_ALPHA_PERCENT,
        )
        this.frostedBlurRadiusDp = clamp(
            frostedBlurRadiusDp,
            MIN_FROSTED_BLUR_RADIUS_DP,
            MAX_FROSTED_BLUR_RADIUS_DP,
        )
        this.frostedDarknessPercent = clamp(
            frostedDarknessPercent,
            MIN_FROSTED_DARKNESS_PERCENT,
            MAX_FROSTED_DARKNESS_PERCENT,
        )
        this.translateAppPackage = translateAppPackage?.trim().orEmpty()
    }

    fun saveLocal(context: Context?) {
        if (context == null) {
            return
        }
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .edit()
            .putInt(KEY_COLOR_MODE, colorMode)
            .putInt(KEY_CONTENT_CAPTURE_MODE, contentCaptureMode)
            .putBoolean(KEY_TEXT_SHARING_ENABLED, textSharingEnabled)
            .putBoolean(KEY_IMAGE_SHARING_ENABLED, imageSharingEnabled)
            .remove(KEY_TEXT_COPY_ENABLED)
            .remove(KEY_IMAGE_COPY_ENABLED)
            .putBoolean(KEY_PRELOAD_TEXT_SEGMENTER, preloadTextSegmenter)
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
            .putInt(KEY_FROSTED_PLATE_ALPHA, frostedPlateAlphaPercent)
            .putInt(KEY_FROSTED_BLUR_RADIUS, frostedBlurRadiusDp)
            .putInt(KEY_FROSTED_DARKNESS, frostedDarknessPercent)
            .putString(KEY_TRANSLATE_APP_PACKAGE, translateAppPackage)

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
        result.putInt(KEY_FROSTED_PLATE_ALPHA, frostedPlateAlphaPercent)
        result.putInt(KEY_FROSTED_BLUR_RADIUS, frostedBlurRadiusDp)
        result.putInt(KEY_FROSTED_DARKNESS, frostedDarknessPercent)
        result.putString(KEY_TRANSLATE_APP_PACKAGE, translateAppPackage)
        return result
    }

    fun isSharingEnabled(image: Boolean): Boolean =
        if (image) imageSharingEnabled else textSharingEnabled

    fun isPortalCaptureMode(): Boolean = contentCaptureMode == CONTENT_CAPTURE_PORTAL

    fun isAccessibilityCaptureMode(): Boolean =
        contentCaptureMode == CONTENT_CAPTURE_ACCESSIBILITY


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

        // 磨砂面板外观：默认对齐当前观感（55% 不透明 + 纯黑 + 28dp 模糊）。
        const val MIN_FROSTED_PLATE_ALPHA_PERCENT = 0
        const val DEFAULT_FROSTED_PLATE_ALPHA_PERCENT = 55
        const val MAX_FROSTED_PLATE_ALPHA_PERCENT = 100

        const val MIN_FROSTED_BLUR_RADIUS_DP = 0
        const val DEFAULT_FROSTED_BLUR_RADIUS_DP = 28
        const val MAX_FROSTED_BLUR_RADIUS_DP = 60

        const val MIN_FROSTED_DARKNESS_PERCENT = 0
        const val DEFAULT_FROSTED_DARKNESS_PERCENT = 40
        const val MAX_FROSTED_DARKNESS_PERCENT = 100

        /** 翻译应用：默认不指定（空串），保持"复制文字 + 提示"的原有行为。 */
        const val DEFAULT_TRANSLATE_APP_PACKAGE = ""

        /** Change notification only; settings values remain behind the trusted Provider RPC. */
        private const val SETTINGS_URI_VALUE =
            "content://com.leaf.hyperdragshare.codex.share/settings"

        /** Compact overlay retained from the original implementation. */

        /** Animated bottom-glow and spring tray modeled after Content Portal. */

        /** Left/right semicircle menu modeled after Oplus ROM circlemenuview. */

        /** HyperOS View-blurred overlay with an adaptive square preview. */

        @Deprecated("Kept as a source-compatibility alias for pre-1.4 callers.")










        const val DEFAULT_TEXT_SHARING_ENABLED = true
        const val DEFAULT_IMAGE_SHARING_ENABLED = true
        const val DEFAULT_PRELOAD_TEXT_SEGMENTER = true
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
        private const val KEY_TEXT_SHARING_ENABLED = "text_sharing_enabled"
        private const val KEY_IMAGE_SHARING_ENABLED = "image_sharing_enabled"
        private const val KEY_TEXT_COPY_ENABLED = "text_copy_enabled"
        private const val KEY_IMAGE_COPY_ENABLED = "image_copy_enabled"
        private const val KEY_PRELOAD_TEXT_SEGMENTER = "preload_text_segmenter"
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
        private const val KEY_FROSTED_PLATE_ALPHA = "frosted_plate_alpha_percent"
        private const val KEY_FROSTED_BLUR_RADIUS = "frosted_blur_radius_dp"
        private const val KEY_FROSTED_DARKNESS = "frosted_darkness_percent"
        private const val KEY_TRANSLATE_APP_PACKAGE = "translate_app_package"


        fun defaults(): DragShareSettings = DragShareSettings(
            COLOR_LIGHT,
            DEFAULT_TEXT_SHARING_ENABLED,
            DEFAULT_IMAGE_SHARING_ENABLED,
            emptySet(),
            emptyList(),
            DEFAULT_CONTENT_CAPTURE_MODE,
            DEFAULT_ACCESSIBILITY_LANDSCAPE_RECOGNITION_ENABLED,
            emptySet(),
            DEFAULT_ACCESSIBILITY_LONG_PRESS_TIMEOUT_MILLIS,
            DEFAULT_ACCESSIBILITY_RECOGNITION_SENSITIVITY_PERCENT,
            DEFAULT_PRELOAD_TEXT_SEGMENTER,
            DEFAULT_LOG_LEVEL,
            DEFAULT_LOG_DESTINATION,
            DEFAULT_SHARED_COPY_LOCATION,
            DEFAULT_FROSTED_PLATE_ALPHA_PERCENT,
            DEFAULT_FROSTED_BLUR_RADIUS_DP,
            DEFAULT_FROSTED_DARKNESS_PERCENT,
            DEFAULT_TRANSLATE_APP_PACKAGE,
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
                preferences.getBoolean(
                    KEY_TEXT_SHARING_ENABLED,
                    DEFAULT_TEXT_SHARING_ENABLED,
                ),
                preferences.getBoolean(
                    KEY_IMAGE_SHARING_ENABLED,
                    DEFAULT_IMAGE_SHARING_ENABLED,
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
                preferences.getInt(KEY_LOG_LEVEL, DEFAULT_LOG_LEVEL),
                preferences.getInt(KEY_LOG_DESTINATION, DEFAULT_LOG_DESTINATION),
                preferences.getInt(
                    KEY_SHARED_COPY_LOCATION,
                    DEFAULT_SHARED_COPY_LOCATION,
                ),
                preferences.getInt(
                    KEY_FROSTED_PLATE_ALPHA,
                    DEFAULT_FROSTED_PLATE_ALPHA_PERCENT,
                ),
                preferences.getInt(
                    KEY_FROSTED_BLUR_RADIUS,
                    DEFAULT_FROSTED_BLUR_RADIUS_DP,
                ),
                preferences.getInt(
                    KEY_FROSTED_DARKNESS,
                    DEFAULT_FROSTED_DARKNESS_PERCENT,
                ),
                preferences.getString(
                    KEY_TRANSLATE_APP_PACKAGE,
                    DEFAULT_TRANSLATE_APP_PACKAGE,
                ) ?: DEFAULT_TRANSLATE_APP_PACKAGE,
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
                bundle.getBoolean(
                    KEY_TEXT_SHARING_ENABLED,
                    DEFAULT_TEXT_SHARING_ENABLED,
                ),
                bundle.getBoolean(
                    KEY_IMAGE_SHARING_ENABLED,
                    DEFAULT_IMAGE_SHARING_ENABLED,
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
                bundle.getInt(KEY_LOG_LEVEL, DEFAULT_LOG_LEVEL),
                bundle.getInt(KEY_LOG_DESTINATION, DEFAULT_LOG_DESTINATION),
                bundle.getInt(
                    KEY_SHARED_COPY_LOCATION,
                    DEFAULT_SHARED_COPY_LOCATION,
                ),
                bundle.getInt(
                    KEY_FROSTED_PLATE_ALPHA,
                    DEFAULT_FROSTED_PLATE_ALPHA_PERCENT,
                ),
                bundle.getInt(
                    KEY_FROSTED_BLUR_RADIUS,
                    DEFAULT_FROSTED_BLUR_RADIUS_DP,
                ),
                bundle.getInt(
                    KEY_FROSTED_DARKNESS,
                    DEFAULT_FROSTED_DARKNESS_PERCENT,
                ),
                bundle.getString(
                    KEY_TRANSLATE_APP_PACKAGE,
                    DEFAULT_TRANSLATE_APP_PACKAGE,
                ) ?: DEFAULT_TRANSLATE_APP_PACKAGE,
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
