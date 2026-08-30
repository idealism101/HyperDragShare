package com.leaf.hyperdragshare.codex

import android.content.ClipData
import android.content.Context
import android.content.Intent
import android.net.Uri

internal object ShareLauncher {
    private const val TAG = "DragShare/Share"

    fun launch(
        context: Context,
        payload: CapturedContent,
        target: ShareTarget,
        stagedImage: Uri?,
        fallbackImage: Uri?,
        toast: DragShareToast?,
    ) {
        DragShareLog.i(
            TAG,
            "prepare target=" +
                target.component?.flattenToShortString() +
                " kind=" + (if (payload.isImage()) "image" else "text") +
                " mime=" + payload.mimeType(),
        )
        if (!payload.isImage()) {
            launchAttempt(context, payload, target, null, toast)
            return
        }
        if (stagedImage == null) {
            DragShareLog.w(TAG, "image URI is not ready", null)
            toast?.show("图片准备失败")
            return
        }
        val retryable = fallbackImage != null && fallbackImage != stagedImage
        if (launchAttempt(context, payload, target, stagedImage, if (retryable) null else toast)) {
            return
        }
        if (!retryable) {
            return
        }
        // The preferred URI is the shared MediaStore copy; a ROM that refuses to hand it over
        // still accepts the module's own authority.
        DragShareLog.i(TAG, "retrying with the private staged URI")
        launchAttempt(context, payload, target, fallbackImage, toast)
    }

    private fun launchAttempt(
        context: Context,
        payload: CapturedContent,
        target: ShareTarget,
        stagedImage: Uri?,
        toast: DragShareToast?,
    ): Boolean {
        try {
            val intent = Intent(Intent.ACTION_SEND)
            intent.type = payload.mimeType()
            intent.component = target.component
            intent.addCategory(Intent.CATEGORY_DEFAULT)
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)

            if (stagedImage == null) {
                intent.putExtra(Intent.EXTRA_TEXT, payload.text)
            } else {
                intent.setDataAndType(stagedImage, payload.mimeType())
                intent.putExtra(Intent.EXTRA_STREAM, stagedImage)
                intent.putExtra(Intent.EXTRA_TITLE, fileName(stagedImage))
                intent.clipData = ClipData.newUri(
                    context.contentResolver, "drag-share-image", stagedImage,
                )
                intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                var granted = false
                if (ImageStagingClient.isOwnAuthority(stagedImage)) {
                    ImageStagingClient.grantReadAccess(
                        context,
                        stagedImage,
                        target.component?.packageName,
                    )
                    granted = true
                }
                DragShareLog.i(
                    TAG,
                    "intent image source=" + describeSource(stagedImage) +
                        " data=" + describeUri(stagedImage) +
                        " flags=0x" + Integer.toHexString(intent.flags) +
                        " clip=true stream=true grant=" + granted,
                )
            }
            context.startActivity(intent)
            // startActivity only reports parameter errors. A background-launch veto and a
            // recipient that rejects the URI are both silent here, so this line must not claim
            // more than "the call returned".
            DragShareLog.i(
                TAG,
                "startActivity returned target=" + target.component?.flattenToShortString(),
            )
            ShareOutcomeProbe.verify(target, stagedImage)
            return true
        } catch (error: Throwable) {
            DragShareLog.w(
                TAG,
                "launch failed target=" + target.component?.flattenToShortString(),
                error,
            )
            if (stagedImage != null && ImageStagingClient.isOwnAuthority(stagedImage)) {
                try {
                    ImageStagingClient.revokeReadAccess(
                        context,
                        stagedImage,
                        target.component?.packageName,
                    )
                } catch (_: Throwable) {
                    // Preserve the original launch failure.
                }
            }
            toast?.show("无法打开 " + target.label)
            return false
        }
    }

    private fun fileName(uri: Uri): String =
        if (ShareUriToken.JPEG_SUFFIX == suffixOf(uri)) "drag-share.jpg" else "drag-share.png"

    private fun suffixOf(uri: Uri): String {
        val last = uri.lastPathSegment ?: return ShareUriToken.PNG_SUFFIX
        return if (last.endsWith(ShareUriToken.JPEG_SUFFIX)) {
            ShareUriToken.JPEG_SUFFIX
        } else {
            ShareUriToken.PNG_SUFFIX
        }
    }

    private fun describeSource(uri: Uri): String =
        if (SharedImagePublisher.isMediaStoreUri(uri)) "mediastore" else "module"

    private fun describeUri(uri: Uri?): String {
        if (uri == null) {
            return "null"
        }
        return uri.scheme + "://" + uri.authority +
            "/" + (if (uri.pathSegments.isEmpty()) "" else uri.pathSegments[0]) + "/<redacted>"
    }
}
