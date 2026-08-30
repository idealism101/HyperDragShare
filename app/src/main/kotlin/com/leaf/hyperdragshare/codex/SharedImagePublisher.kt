package com.leaf.hyperdragshare.codex

import android.content.ContentValues
import android.content.Context
import android.net.Uri
import android.os.Environment
import android.provider.MediaStore
import java.io.File
import java.io.FileInputStream
import java.io.OutputStream

/**
 * Mirrors a staged image into the shared MediaStore collection.
 *
 * On some devices a recipient reports "资源不存在" for an image staged behind the module's own
 * authority, without ever calling into [ShareImageProvider] — no `query`, no `openFile`. Why those
 * devices differ from the ones this module is developed on is not known. A MediaStore row is backed
 * by a real shared path and readable by every app holding `READ_MEDIA_IMAGES`, so it is the fallback
 * offered to them; it is opt-in (`shared_copy_location`), not the default path.
 *
 * That readability is also why the copy cannot be hidden while it exists. `IS_PENDING` and
 * `IS_TRASHED` both take the row out of gallery listings, but they take it out of every
 * non-owner read as well; a `.nomedia` or dot-prefixed directory is never indexed, so there is
 * no media URI and no path a recipient may open; and `Download`/`Documents` do not help because
 * MediaProvider derives `MEDIA_TYPE` from the MIME type, so gallery queries still return the
 * row. What can be controlled is how long the copy exists and how often one is made at all —
 * hence [publish] runs only when the user has both turned the public location on and committed to
 * handing the image to another app — never for a preview, a cancelled drag, or a local-only action
 * — and every published row gets a
 * [SharedImageCleanupJob] deadline so the retention window is an upper bound rather than the
 * minimum age of whatever the next drag happens to sweep up.
 *
 * Runs in the module process only: MediaStore rows are owned by the inserting package, which is
 * what lets [sweep] delete them again without any storage permission.
 */
internal object SharedImagePublisher {
    private const val TAG = "DragShareProvider"
    private const val NAME_PREFIX = "dragshare_"
    /** Read deadline handed to the recipient; also the sweep cutoff and the job latency. */
    const val RETENTION_MS = 10L * 60L * 1000L

    private val RELATIVE_PATH =
        Environment.DIRECTORY_PICTURES + File.separator + "HyperDragShare" + File.separator

    /** Also matches the `HyperDragShare/share/` sub-directory earlier builds published into. */
    private val SWEEP_PATH_PREFIX = RELATIVE_PATH + "%"

    /** True when [uri] came from [publish] rather than from the module's own authority. */
    fun isMediaStoreUri(uri: Uri?): Boolean =
        uri != null && MediaStore.AUTHORITY == uri.authority

    /** Best effort: returns null when the shared collection is unavailable. */
    fun publish(context: Context, source: File, mimeType: String, suffix: String): Uri? {
        val resolver = context.contentResolver
        val values = ContentValues()
        values.put(MediaStore.Images.Media.DISPLAY_NAME, displayName(suffix))
        values.put(MediaStore.Images.Media.MIME_TYPE, mimeType)
        values.put(MediaStore.Images.Media.RELATIVE_PATH, RELATIVE_PATH)
        values.put(MediaStore.Images.Media.IS_PENDING, 1)
        var uri: Uri? = null
        try {
            uri = resolver.insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, values)
                ?: throw IllegalStateException("MediaStore insert returned null")
            resolver.openOutputStream(uri).use { output ->
                if (output == null) {
                    throw IllegalStateException("MediaStore entry is not writable")
                }
                copy(source, output)
            }
            val ready = ContentValues()
            ready.put(MediaStore.Images.Media.IS_PENDING, 0)
            resolver.update(uri, ready, null, null)
            SharedImageCleanupJob.schedule(context)
            return uri
        } catch (error: Throwable) {
            release(context, uri)
            DragShareLog.w(TAG, "media publish failed", error)
            return null
        }
    }

    /** Drops a single published row; the private staged copy keeps serving the fallback URI. */
    fun release(context: Context, uri: Uri?) {
        if (uri == null) {
            return
        }
        try {
            context.contentResolver.delete(uri, null, null)
        } catch (error: Throwable) {
            DragShareLog.w(TAG, "media release failed", error)
        }
    }

    /**
     * Removes rows this module published more than ten minutes ago and returns how many younger
     * ones are still there. Unprivileged MediaStore queries only see the caller's own
     * contributions, so this can never touch user photos.
     *
     * Ten minutes is a recipient-side deadline, not a display window: an app that only reads
     * `EXTRA_STREAM` when the user finally taps 发送 must still find the file there. The count is
     * what lets [SharedImageCleanupJob] re-arm instead of leaving a young row behind forever.
     */
    fun sweep(context: Context): Int {
        val resolver = context.contentResolver
        val cutoffSeconds = (System.currentTimeMillis() - RETENTION_MS) / 1000L
        var removed = 0
        var remaining = 0
        try {
            resolver.query(
                MediaStore.Images.Media.EXTERNAL_CONTENT_URI,
                arrayOf(MediaStore.Images.Media._ID, MediaStore.Images.Media.DATE_ADDED),
                MediaStore.Images.Media.RELATIVE_PATH + " LIKE ? AND " +
                    MediaStore.Images.Media.DISPLAY_NAME + " LIKE ?",
                arrayOf(SWEEP_PATH_PREFIX, NAME_PREFIX + "%"),
                null,
            )?.use { cursor ->
                while (cursor.moveToNext()) {
                    if (cursor.getLong(1) >= cutoffSeconds) {
                        remaining++
                        continue
                    }
                    val row = MediaStore.Images.Media.EXTERNAL_CONTENT_URI
                        .buildUpon()
                        .appendPath(cursor.getLong(0).toString())
                        .build()
                    val deleted = try {
                        resolver.delete(row, null, null)
                    } catch (_: Throwable) {
                        // Another process can already have removed the row.
                        0
                    }
                    if (deleted > 0) {
                        removed += deleted
                    } else {
                        remaining++
                    }
                }
            }
        } catch (error: Throwable) {
            DragShareLog.w(TAG, "media sweep failed", error)
            return 0
        }
        if (removed > 0) {
            DragShareLog.i(TAG, "media sweep removed=" + removed + " remaining=" + remaining)
        }
        return remaining
    }

    private fun displayName(suffix: String): String =
        NAME_PREFIX + System.currentTimeMillis() + suffix

    private fun copy(source: File, output: OutputStream) {
        FileInputStream(source).use { input ->
            val buffer = ByteArray(64 * 1024)
            while (true) {
                val read = input.read(buffer)
                if (read < 0) {
                    break
                }
                output.write(buffer, 0, read)
            }
        }
        output.flush()
    }
}
