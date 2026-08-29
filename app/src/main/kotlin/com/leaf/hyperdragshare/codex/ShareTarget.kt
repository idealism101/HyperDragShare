package com.leaf.hyperdragshare.codex

import android.content.ComponentName
import android.graphics.drawable.Drawable

class ShareTarget private constructor(
    @JvmField val component: ComponentName?,
    @JvmField val label: CharSequence?,
    @JvmField val icon: Drawable?,
    private val key: String?,
    private val packageName: String,
    private val builtIn: Boolean,
) {
    constructor(component: ComponentName?, label: CharSequence?, icon: Drawable?) : this(
        component,
        label,
        icon,
        component?.flattenToString(),
        if (component == null) "" else component.packageName,
        false,
    )

    fun key(): String? = key

    fun packageName(): String = packageName

    fun isBuiltIn(): Boolean = builtIn

    fun isSaveToLocal(): Boolean = builtIn && DragShareSettings.TARGET_SAVE_LOCAL == key

    fun isCopyTextToClipboard(): Boolean = builtIn && DragShareSettings.TARGET_COPY_TEXT == key

    fun isCopyImageToClipboard(): Boolean = builtIn && DragShareSettings.TARGET_COPY_IMAGE == key

    fun isCopyToClipboard(): Boolean = isCopyTextToClipboard() || isCopyImageToClipboard()

    fun isTextSegmentation(): Boolean =
        builtIn && DragShareSettings.TARGET_TEXT_SEGMENTATION == key

    companion object {
        @JvmStatic
        fun saveToLocal(icon: Drawable?): ShareTarget = ShareTarget(
            null,
            "保存到本地",
            icon,
            DragShareSettings.TARGET_SAVE_LOCAL,
            "builtin",
            true,
        )

        @JvmStatic
        fun copyTextToClipboard(icon: Drawable?): ShareTarget = ShareTarget(
            null,
            "复制文本",
            icon,
            DragShareSettings.TARGET_COPY_TEXT,
            "builtin",
            true,
        )

        @JvmStatic
        fun copyImageToClipboard(icon: Drawable?): ShareTarget = ShareTarget(
            null,
            "复制图片",
            icon,
            DragShareSettings.TARGET_COPY_IMAGE,
            "builtin",
            true,
        )

        @JvmStatic
        fun textSegmentation(icon: Drawable?): ShareTarget = ShareTarget(
            null,
            "文本分词",
            icon,
            DragShareSettings.TARGET_TEXT_SEGMENTATION,
            "builtin",
            true,
        )
    }
}
