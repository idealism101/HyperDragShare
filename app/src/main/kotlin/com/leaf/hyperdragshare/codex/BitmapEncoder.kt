package com.leaf.hyperdragshare.codex

import android.graphics.Bitmap
import java.io.IOException
import java.io.OutputStream

object BitmapEncoder {
    /** Writes the source pixels losslessly without applying density scaling. */
    @Throws(IOException::class)
    fun writePng(source: Bitmap?, output: OutputStream?) {
        if (source == null || source.isRecycled || output == null) {
            throw IOException("Bitmap is unavailable")
        }
        var work: Bitmap? = source
        var ownsWork = false
        try {
            if (isHardwareBitmap(source)) {
                work = source.copy(Bitmap.Config.ARGB_8888, false)
                ownsWork = true
            }
            if (work == null || work.isRecycled
                || !work.compress(Bitmap.CompressFormat.PNG, 100, output)
            ) {
                throw IOException("Unable to encode image as PNG")
            }
        } finally {
            val finished = work
            if (ownsWork && finished != null && !finished.isRecycled) {
                finished.recycle()
            }
        }
    }

    private fun isHardwareBitmap(bitmap: Bitmap?): Boolean {
        // Avoid linking Bitmap.Config.HARDWARE on older Robolectric Android shadows.
        return bitmap != null && "HARDWARE" == bitmap.config?.toString()
    }
}
