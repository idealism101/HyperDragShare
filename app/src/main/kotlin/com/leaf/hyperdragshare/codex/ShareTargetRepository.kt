package com.leaf.hyperdragshare.codex

import android.annotation.SuppressLint
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.drawable.Drawable
import java.util.LinkedHashMap

object ShareTargetRepository {
    private const val MODULE_PACKAGE = "com.leaf.hyperdragshare.codex"
    const val BUILT_IN_ACTION_TILE_COLOR = 0xFF3482FF.toInt()

    @JvmStatic
    @SuppressLint("QueryPermissionsNeeded")
    fun query(context: Context, payload: CapturedContent?): List<ShareTarget> =
        query(context, if (payload == null) "text/plain" else payload.mimeType())

    @JvmStatic
    @SuppressLint("QueryPermissionsNeeded")
    fun query(context: Context, mimeType: String?): List<ShareTarget> {
        val packageManager = context.packageManager
        val prototype = Intent(Intent.ACTION_SEND).setType(mimeType)
        val resolveInfos = packageManager.queryIntentActivities(
            prototype, PackageManager.MATCH_DEFAULT_ONLY,
        )
        val result = ArrayList<ShareTarget>()
        val seen = HashSet<String>()

        for (info in resolveInfos) {
            val activity = info.activityInfo
            if (activity == null || !activity.exported) {
                continue
            }
            val component = ComponentName(activity.packageName, activity.name)
            if (!seen.add(component.flattenToString())) {
                continue
            }

            val label: CharSequence? = try {
                info.loadLabel(packageManager)
            } catch (_: Throwable) {
                activity.applicationInfo.loadLabel(packageManager)
            }
            val icon: Drawable? = try {
                info.loadIcon(packageManager)
            } catch (_: Throwable) {
                activity.applicationInfo.loadIcon(packageManager)
            }
            result.add(ShareTarget(component, label, icon))
        }
        return result
    }

    /** Returns the union used by the settings pages, without built-in actions. */
    @JvmStatic
    fun queryAll(context: Context): List<ShareTarget> {
        val byKey = LinkedHashMap<String, ShareTarget>()
        try {
            for (target in query(context, "text/plain")) {
                byKey[target.key() ?: continue] = target
            }
        } catch (_: Throwable) {
            // Keep image targets available if a ROM rejects one MIME query.
        }
        try {
            for (target in query(context, "image/*")) {
                byKey.putIfAbsent(target.key() ?: continue, target)
            }
        } catch (_: Throwable) {
            // The settings page can still manage the targets from the first query.
        }
        return ArrayList(byKey.values)
    }

    /**
     * Applies visibility and the user-defined order to a runtime menu. Built-in actions are
     * deliberately inserted before package targets; each payload type has its own copy action.
     */
    @JvmStatic
    fun applySettings(
        context: Context?,
        queried: List<ShareTarget>?,
        settings: DragShareSettings?,
        imagePayload: Boolean,
    ): List<ShareTarget> {
        val effective = settings ?: DragShareSettings.defaults()
        val visible = LinkedHashMap<String, ShareTarget>()
        val copyKey = if (imagePayload) {
            DragShareSettings.TARGET_COPY_IMAGE
        } else {
            DragShareSettings.TARGET_COPY_TEXT
        }
        if (effective.isTargetVisible(copyKey)) {
            visible[copyKey] = if (imagePayload) {
                ShareTarget.copyImageToClipboard(loadCopyIcon(context))
            } else {
                ShareTarget.copyTextToClipboard(loadCopyIcon(context))
            }
        }
        if (imagePayload && effective.isTargetVisible(DragShareSettings.TARGET_SAVE_LOCAL)) {
            visible[DragShareSettings.TARGET_SAVE_LOCAL] =
                ShareTarget.saveToLocal(loadSaveIcon(context))
        } else if (!imagePayload &&
            effective.isTargetVisible(DragShareSettings.TARGET_TEXT_SEGMENTATION)
        ) {
            visible[DragShareSettings.TARGET_TEXT_SEGMENTATION] =
                ShareTarget.textSegmentation(loadTextSegmentationIcon(context))
        }
        if (queried != null) {
            for (target in queried) {
                val key = target.key()
                if (key == null || !effective.isTargetVisible(key)) {
                    continue
                }
                visible.putIfAbsent(key, target)
            }
        }

        val result = ArrayList<ShareTarget>()
        val copy = visible.remove(copyKey)
        if (copy != null) {
            result.add(copy)
        }
        if (imagePayload) {
            val save = visible.remove(DragShareSettings.TARGET_SAVE_LOCAL)
            if (save != null) {
                result.add(save)
            }
        } else {
            val textSegmentation = visible.remove(DragShareSettings.TARGET_TEXT_SEGMENTATION)
            if (textSegmentation != null) {
                result.add(textSegmentation)
            }
        }
        for (key in effective.targetOrder) {
            val target = visible.remove(key)
            if (target != null) {
                result.add(target)
            }
        }
        result.addAll(visible.values)
        return result
    }

    /** Orders all installed targets for the settings screen, preserving newly discovered apps. */
    @JvmStatic
    fun orderForSettings(
        queried: List<ShareTarget>?,
        settings: DragShareSettings?,
    ): List<ShareTarget> {
        if (queried == null || queried.isEmpty()) {
            return ArrayList()
        }
        val effective = settings ?: DragShareSettings.defaults()
        val remaining = LinkedHashMap<String, ShareTarget>()
        for (target in queried) {
            val key = target.key()
            if (key != null && effective.isTargetVisible(key)) {
                remaining.putIfAbsent(key, target)
            }
        }
        val result = ArrayList<ShareTarget>()
        for (key in effective.targetOrder) {
            val target = remaining.remove(key)
            if (target != null) {
                result.add(target)
            }
        }
        result.addAll(remaining.values)
        return result
    }

    @JvmStatic
    fun loadSaveIcon(context: Context?): Drawable? = loadBuiltInIcon(context, R.drawable.ic_download)

    @JvmStatic
    fun loadCopyIcon(context: Context?): Drawable? = loadBuiltInIcon(context, R.drawable.ic_copy)

    @JvmStatic
    fun loadTextSegmentationIcon(context: Context?): Drawable? =
        loadBuiltInIcon(context, R.drawable.ic_text_segment)

    /** Returns a visually consistent icon for every menu and settings surface. */
    @JvmStatic
    fun iconForDisplay(target: ShareTarget?): Drawable? {
        if (target == null) {
            return null
        }
        if (target.isCopyToClipboard()) {
            return CopyTargetIconDrawable(target.icon, BUILT_IN_ACTION_TILE_COLOR)
        }
        if (target.isSaveToLocal()) {
            return SaveTargetIconDrawable(target.icon, BUILT_IN_ACTION_TILE_COLOR)
        }
        if (target.isTextSegmentation()) {
            return TextSegmentationTargetIconDrawable(target.icon, BUILT_IN_ACTION_TILE_COLOR)
        }
        return target.icon
    }

    private fun loadBuiltInIcon(context: Context?, resourceId: Int): Drawable? {
        if (context == null) {
            return null
        }
        return try {
            val resourceContext = if (MODULE_PACKAGE == context.packageName) {
                context
            } else {
                context.createPackageContext(MODULE_PACKAGE, Context.CONTEXT_IGNORE_SECURITY)
            }
            resourceContext.getDrawable(resourceId)
        } catch (_: Throwable) {
            null
        }
    }
}
