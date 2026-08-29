package com.leaf.hyperdragshare.codex

import android.content.ClipData
import android.content.Context
import android.content.Intent
import android.net.Uri

internal object ShareLauncher {
    fun launch(
        context: Context,
        payload: CapturedContent,
        target: ShareTarget,
        stagedImage: Uri?,
        toast: DragShareToast?,
    ) {
        try {
            val intent = Intent(Intent.ACTION_SEND)
            intent.type = payload.mimeType()
            intent.component = target.component
            intent.addCategory(Intent.CATEGORY_DEFAULT)
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)

            DragShareLog.i(
                "DragShare/Share",
                "prepare target=" +
                    target.component?.flattenToShortString() +
                    " kind=" + (if (payload.isImage()) "image" else "text") +
                    " mime=" + payload.mimeType(),
            )

            if (!payload.isImage()) {
                intent.putExtra(Intent.EXTRA_TEXT, payload.text)
            } else {
                if (stagedImage == null) {
                    throw IllegalArgumentException("Image URI is not ready")
                }
                intent.setDataAndType(stagedImage, payload.mimeType())
                intent.putExtra(Intent.EXTRA_STREAM, stagedImage)
                intent.putExtra(Intent.EXTRA_TITLE, "drag-share.png")
                intent.clipData = ClipData.newUri(
                    context.contentResolver, "drag-share-image", stagedImage,
                )
                intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                ImageStagingClient.grantReadAccess(
                    context,
                    stagedImage,
                    target.component?.packageName,
                )
                DragShareLog.i(
                    "DragShare/Share",
                    "intent image data=" +
                        describeUri(stagedImage) +
                        " flags=0x" + Integer.toHexString(intent.flags) +
                        " clip=true stream=true grant=true",
                )
            }
            context.startActivity(intent)
            DragShareLog.i(
                "DragShare/Share",
                "startActivity succeeded target=" + target.component?.flattenToShortString(),
            )
        } catch (error: Throwable) {
            DragShareLog.w(
                "DragShare/Share",
                "launch failed target=" + target.component?.flattenToShortString(),
                error,
            )
            if (stagedImage != null) {
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
        }
    }

    private fun describeUri(uri: Uri?): String {
        if (uri == null) {
            return "null"
        }
        return uri.scheme + "://" + uri.authority +
            "/" + (if (uri.pathSegments.isEmpty()) "" else uri.pathSegments[0]) + "/<redacted>"
    }
}
