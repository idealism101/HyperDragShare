package com.leaf.hyperdragshare.codex

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.provider.Settings
import java.util.Collections
import java.util.LinkedHashMap
import java.util.LinkedHashSet

/** Resolves dynamic, non-editable accessibility exclusions and applies user exclusions. */
internal object AccessibilityBlacklist {
    private const val REASON_LAUNCHER = "系统当前启动器"
    private const val REASON_INPUT_METHOD = "当前输入法"

    fun isBlocked(
        context: Context?,
        settings: DragShareSettings?,
        packageName: String?,
    ): Boolean = isBlockedByPackages(
        packageName,
        if (settings == null) emptySet() else settings.accessibilityBlacklistedPackages,
        builtInPackages(context),
    )

    fun isBlockedByPackages(
        packageName: String?,
        userBlacklistedPackages: Set<String>?,
        builtInBlacklistedPackages: Set<String>?,
    ): Boolean {
        if (packageName == null || packageName.trim().isEmpty()) {
            return false
        }
        return (userBlacklistedPackages != null && userBlacklistedPackages.contains(packageName)) ||
            (
                builtInBlacklistedPackages != null &&
                    builtInBlacklistedPackages.contains(packageName)
                )
    }

    fun builtInPackages(context: Context?): Set<String> =
        LinkedHashSet(builtInReasons(context).keys)

    fun builtInReasons(context: Context?): Map<String, String> {
        if (context == null) {
            return emptyMap()
        }
        val result = LinkedHashMap<String, String>()
        val packageManager = context.packageManager
        try {
            val homeIntent = Intent(Intent.ACTION_MAIN)
                .addCategory(Intent.CATEGORY_HOME)
            val resolved = packageManager.resolveActivity(
                homeIntent,
                PackageManager.MATCH_DEFAULT_ONLY,
            )
            if (resolved?.activityInfo != null) {
                addReason(result, resolved.activityInfo.packageName, REASON_LAUNCHER)
            }
        } catch (_: Throwable) {
            // The user blacklist remains effective when a ROM restricts this lookup.
        }
        try {
            val value = Settings.Secure.getString(
                context.contentResolver,
                Settings.Secure.DEFAULT_INPUT_METHOD,
            )
            val component = ComponentName.unflattenFromString(value)
            if (component != null) {
                addReason(result, component.packageName, REASON_INPUT_METHOD)
            }
        } catch (_: Throwable) {
            // Some managed profiles do not expose the active input method.
        }
        return Collections.unmodifiableMap(result)
    }

    private fun addReason(
        reasons: MutableMap<String, String>,
        packageName: String?,
        reason: String,
    ) {
        if (packageName == null || packageName.trim().isEmpty()) {
            return
        }
        val existing = reasons[packageName]
        reasons[packageName] = if (existing == null) reason else existing + "、" + reason
    }
}
