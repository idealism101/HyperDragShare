package com.leaf.hyperdragshare.codex

import android.content.ContentProvider
import android.content.ContentValues
import android.content.Intent
import android.database.Cursor
import android.database.MatrixCursor
import android.net.Uri
import android.os.Binder
import android.os.Bundle
import android.os.ParcelFileDescriptor
import android.os.Process
import android.provider.MediaStore
import android.provider.OpenableColumns
import java.io.File
import java.io.FileNotFoundException
import java.io.FileOutputStream
import java.io.IOException
import java.io.InputStream
import java.util.UUID

class ShareImageProvider : ContentProvider() {
    private lateinit var shareDirectory: File

    override fun onCreate(): Boolean {
        val context = context ?: return false
        shareDirectory = File(context.cacheDir, "shared-images")
        return shareDirectory.exists() || shareDirectory.mkdirs()
    }

    override fun call(method: String, arg: String?, extras: Bundle?): Bundle? {
        if (ModuleActivation.METHOD_REPORT_INJECTED == method) {
            enforcePortalCaller()
            ModuleActivation.recordInjected(contextOrThrow(), extras)
            return Bundle.EMPTY
        }
        if (DragShareSettings.METHOD_GET_SETTINGS == method) {
            enforcePortalCaller()
            return DragShareSettings.readLocal(contextOrThrow()).toBundle()
        }
        if (ImageStagingClient.METHOD_GRANT == method) {
            enforcePortalCaller()
            return grantImage(extras)
        }
        if (ImageStagingClient.METHOD_REVOKE == method) {
            enforcePortalCaller()
            return revokeImage(extras)
        }
        if (ImageStagingClient.METHOD_PUBLISH == method) {
            enforcePortalCaller()
            return publishImage(extras)
        }
        if (ImageStagingClient.METHOD_STAGE != method) {
            return super.call(method, arg, extras)
        }
        enforcePortalCaller()
        return stageImage(extras)
    }

    private fun stageImage(extras: Bundle?): Bundle {
        if (extras == null) {
            throw IllegalArgumentException("Missing image data")
        }
        val stream = extras.getParcelable(
            ImageStagingClient.EXTRA_STREAM,
            ParcelFileDescriptor::class.java,
        )
        val bytes = extras.getByteArray(ImageStagingClient.EXTRA_BYTES)
        val png = stream != null
        if (!png && (bytes == null || bytes.isEmpty() || bytes.size > MAX_FILE_BYTES)) {
            throw IllegalArgumentException("Invalid staged image size")
        }

        cleanupExpiredFiles()
        val token = UUID.randomUUID().toString()
        val suffix = if (png) ShareUriToken.PNG_SUFFIX else ShareUriToken.JPEG_SUFFIX
        val temporary = File(shareDirectory, "$token.tmp")
        val destination = fileForToken(token, suffix)
        val written: Long
        try {
            written = FileOutputStream(temporary).use { output ->
                val count = if (stream != null) {
                    ParcelFileDescriptor.AutoCloseInputStream(stream).use { input ->
                        copyImage(input, output)
                    }
                } else {
                    val payload = bytes
                        ?: throw IllegalArgumentException("Invalid staged image size")
                    output.write(payload)
                    payload.size.toLong()
                }
                output.fd.sync()
                count
            }
        } catch (error: IOException) {
            temporary.delete()
            throw IllegalStateException("Unable to stage shared image", error)
        } finally {
            if (stream != null) {
                try {
                    stream.close()
                } catch (_: IOException) {
                    // AutoCloseInputStream normally owns this descriptor.
                }
            }
        }
        if (!temporary.renameTo(destination)) {
            temporary.delete()
            throw IllegalStateException("Unable to publish staged image")
        }

        val uri = uriForToken(token, suffix)
        grantReadAccessAsOwner(
            PORTAL_PACKAGE,
            uri,
        )
        DragShareLog.i(
            TAG,
            "staged format=" + (if (png) "png" else "jpeg") + " bytes=" + written,
        )
        val result = Bundle()
        result.putString(ImageStagingClient.RESULT_URI, uri.toString())
        return result
    }

    /**
     * Mirrors an already staged image into the shared media collection. Some devices report the
     * image as missing in the recipient — the cause is not understood, and it does not reproduce on
     * the devices this module is developed on — and a shared file is the one form every recipient
     * accepts. It is also a real file in the user's `Pictures` tree, so it is off by default and,
     * when enabled, created only once the caller knows the image is being handed to another app.
     *
     * The user's location preference is enforced here rather than in the caller: the setting lives
     * in this UID's preferences, so a portal process holding a stale settings bundle can neither
     * publish a copy the user has switched off nor suppress one they asked for.
     */
    private fun publishImage(extras: Bundle?): Bundle {
        val uri = requireShareUri(extras)
        val file = resolveFile(uri)
        if (!file.isFile) {
            throw IllegalArgumentException("Staged image no longer exists")
        }
        val context = contextOrThrow()
        val settings = DragShareSettings.readLocal(context)
        if (settings.sharedCopyLocation == DragShareSettings.SHARED_COPY_LOCATION_MODULE) {
            DragShareLog.i(TAG, "shared copy skipped location=module")
            return Bundle.EMPTY
        }
        val suffix = resolveSuffix(uri) ?: ShareUriToken.PNG_SUFFIX
        val published = SharedImagePublisher.publish(
            context,
            file,
            mimeTypeForSuffix(suffix),
            suffix,
        )
        DragShareLog.i(TAG, "shared copy created=" + (published != null))
        val result = Bundle()
        if (published != null) {
            result.putString(ImageStagingClient.RESULT_URI, published.toString())
        }
        return result
    }

    @Throws(FileNotFoundException::class)
    override fun openFile(uri: Uri, mode: String): ParcelFileDescriptor? {
        if ("r" != mode) {
            throw FileNotFoundException("Read-only provider")
        }
        try {
            val file = resolveFile(uri)
            if (!file.isFile) {
                throw FileNotFoundException("Staged image is missing")
            }
            DragShareLog.i(
                TAG,
                "open uid=" + Binder.getCallingUid() + " bytes=" + file.length(),
            )
            return ParcelFileDescriptor.open(file, ParcelFileDescriptor.MODE_READ_ONLY)
        } catch (error: FileNotFoundException) {
            DragShareLog.w(
                TAG,
                "open failed uid=" + Binder.getCallingUid() + " uri=" + describeUri(uri),
                error,
            )
            throw error
        } catch (error: RuntimeException) {
            DragShareLog.w(
                TAG,
                "open failed uid=" + Binder.getCallingUid() + " uri=" + describeUri(uri),
                error,
            )
            throw error
        }
    }

    override fun getType(uri: Uri): String? {
        val suffix = resolveSuffix(uri)
        DragShareLog.d(
            TAG,
            "getType uid=" + Binder.getCallingUid() + " uri=" + describeUri(uri) +
                " result=" + (if (suffix == null) null else mimeTypeForSuffix(suffix)),
        )
        return if (suffix == null) null else mimeTypeForSuffix(suffix)
    }

    override fun query(
        uri: Uri,
        projection: Array<String>?,
        selection: String?,
        selectionArgs: Array<String>?,
        sortOrder: String?,
    ): Cursor {
        val file: File
        try {
            file = resolveFile(uri)
        } catch (error: RuntimeException) {
            DragShareLog.w(
                TAG,
                "query failed uid=" + Binder.getCallingUid() + " uri=" + describeUri(uri),
                error,
            )
            throw error
        }
        DragShareLog.d(
            TAG,
            "query uid=" + Binder.getCallingUid() + " uri=" + describeUri(uri) +
                " bytes=" + file.length(),
        )
        val columns = projection ?: arrayOf(
            OpenableColumns.DISPLAY_NAME,
            OpenableColumns.SIZE,
            MediaStore.MediaColumns.MIME_TYPE,
        )
        val cursor = MatrixCursor(columns, 1)
        val row = cursor.newRow()
        for (column in columns) {
            if (OpenableColumns.DISPLAY_NAME == column) {
                val token = resolveToken(uri)
                val suffix = resolveSuffix(uri)
                row.add(
                    if (token == null || suffix == null) {
                        "drag-share.png"
                    } else {
                        ShareUriToken.fileName(token, suffix)
                    },
                )
            } else if (OpenableColumns.SIZE == column) {
                row.add(file.length())
            } else if (MediaStore.MediaColumns.MIME_TYPE == column) {
                row.add(mimeTypeForSuffix(resolveSuffix(uri)))
            } else if (MediaStore.MediaColumns.DATE_MODIFIED == column) {
                row.add(file.lastModified() / 1000L)
            } else if (MediaStore.MediaColumns.TITLE == column) {
                row.add("DragShare image")
            } else {
                row.add(null)
            }
        }
        return cursor
    }

    override fun insert(uri: Uri, values: ContentValues?): Uri? {
        throw UnsupportedOperationException("Use call(stage_image)")
    }

    override fun delete(uri: Uri, selection: String?, selectionArgs: Array<String>?): Int = 0

    override fun update(
        uri: Uri,
        values: ContentValues?,
        selection: String?,
        selectionArgs: Array<String>?,
    ): Int = 0

    private fun grantImage(extras: Bundle?): Bundle {
        val uri = requireShareUri(extras)
        val packageName = requirePackageName(extras)
        val file = resolveFile(uri)
        if (!file.isFile) {
            throw IllegalArgumentException("Staged image no longer exists")
        }
        grantReadAccessAsOwner(
            packageName,
            uri,
        )
        DragShareLog.i(TAG, "granted package=" + packageName)
        val result = Bundle()
        result.putBoolean(ImageStagingClient.RESULT_GRANTED, true)
        return result
    }

    private fun revokeImage(extras: Bundle?): Bundle {
        val uri = requireShareUri(extras)
        val packageName = requirePackageName(extras)
        revokeReadAccessAsOwner(
            packageName,
            uri,
        )
        return Bundle.EMPTY
    }

    private fun requireShareUri(extras: Bundle?): Uri {
        val value = extras?.getString(ImageStagingClient.RESULT_URI)
        val uri = if (value == null) null else Uri.parse(value)
        if (uri == null || resolveToken(uri) == null) {
            throw IllegalArgumentException("Invalid share URI")
        }
        return uri
    }

    private fun requirePackageName(extras: Bundle?): String {
        val packageName = extras?.getString(ImageStagingClient.EXTRA_PACKAGE)
        if (packageName == null || packageName.trim().isEmpty()) {
            throw IllegalArgumentException("Missing target package")
        }
        return packageName
    }

    private fun enforcePortalCaller() {
        val callingUid = Binder.getCallingUid()
        if (callingUid == Process.myUid()) {
            return
        }
        val packageManager = contextOrThrow().packageManager
        val packages = packageManager.getPackagesForUid(callingUid)
        if (packages != null) {
            for (packageName in packages) {
                if (PORTAL_PACKAGE == packageName) {
                    return
                }
            }
        }
        throw SecurityException("Only Taplus can stage share images")
    }

    private fun resolveFile(uri: Uri): File {
        val token = resolveToken(uri)
        val suffix = resolveSuffix(uri)
        if (token == null || suffix == null) {
            throw IllegalArgumentException("Invalid share URI")
        }
        return fileForToken(token, suffix)
    }

    private fun resolveToken(uri: Uri?): String? {
        val pathSegment = resolvePathSegment(uri)
        return if (pathSegment == null) null else ShareUriToken.parse(pathSegment)
    }

    private fun resolveSuffix(uri: Uri?): String? {
        val pathSegment = resolvePathSegment(uri)
        return if (pathSegment == null) null else ShareUriToken.suffix(pathSegment)
    }

    private fun resolvePathSegment(uri: Uri?): String? {
        if (uri == null) {
            return null
        }
        if (ImageStagingClient.AUTHORITY != uri.authority) {
            return null
        }
        if (uri.pathSegments.size != 2 || "shared" != uri.pathSegments[0]) {
            return null
        }
        return uri.pathSegments[1]
    }

    private fun fileForToken(token: String, suffix: String): File =
        File(shareDirectory, ShareUriToken.fileName(token, suffix))

    private fun cleanupExpiredFiles() {
        SharedImagePublisher.sweep(contextOrThrow())
        val files = shareDirectory.listFiles() ?: return
        val cutoff = System.currentTimeMillis() - MAX_CACHE_AGE_MS
        for (file in files) {
            if (file.lastModified() < cutoff) {
                val token = ShareUriToken.parse(file.name)
                if (token != null) {
                    val suffix = ShareUriToken.suffix(file.name)
                    revokeReadAccessAsOwner(null, uriForToken(token, suffix))
                }
                file.delete()
            }
        }
    }

    private fun uriForToken(token: String, suffix: String?): Uri =
        ImageStagingClient.BASE_URI.buildUpon()
            .appendPath("shared")
            .appendPath(ShareUriToken.fileName(token, suffix))
            .build()

    private fun grantReadAccessAsOwner(packageName: String?, uri: Uri) {
        val identity = Binder.clearCallingIdentity()
        try {
            contextOrThrow().grantUriPermission(
                packageName,
                uri,
                Intent.FLAG_GRANT_READ_URI_PERMISSION,
            )
        } finally {
            Binder.restoreCallingIdentity(identity)
        }
    }

    private fun revokeReadAccessAsOwner(packageName: String?, uri: Uri) {
        val identity = Binder.clearCallingIdentity()
        try {
            if (packageName == null) {
                contextOrThrow().revokeUriPermission(
                    uri,
                    Intent.FLAG_GRANT_READ_URI_PERMISSION,
                )
            } else {
                contextOrThrow().revokeUriPermission(
                    packageName,
                    uri,
                    Intent.FLAG_GRANT_READ_URI_PERMISSION,
                )
            }
        } finally {
            Binder.restoreCallingIdentity(identity)
        }
    }

    private fun contextOrThrow(): android.content.Context =
        context ?: throw IllegalStateException("Provider is not attached")

    companion object {
        private const val TAG = "DragShareProvider"
        private const val MIME_PNG = "image/png"
        private const val MIME_JPEG = "image/jpeg"
        private const val MAX_CACHE_AGE_MS = 24L * 60L * 60L * 1000L
        private const val MAX_FILE_BYTES = 32L * 1024L * 1024L

        @Throws(IOException::class)
        private fun copyImage(input: InputStream, output: FileOutputStream): Long {
            val buffer = ByteArray(64 * 1024)
            var total = 0L
            while (true) {
                val read = input.read(buffer)
                if (read < 0) {
                    break
                }
                if (read == 0) {
                    continue
                }
                total += read.toLong()
                if (total > MAX_FILE_BYTES) {
                    throw IOException("Staged PNG exceeds size limit")
                }
                output.write(buffer, 0, read)
            }
            if (total == 0L) {
                throw IOException("Staged PNG is empty")
            }
            return total
        }

        private fun describeUri(uri: Uri?): String {
            if (uri == null) {
                return "null"
            }
            return uri.scheme + "://" + uri.authority +
                "/" + (if (uri.pathSegments.isEmpty()) "" else uri.pathSegments[0]) +
                "/<redacted>"
        }

        private fun mimeTypeForSuffix(suffix: String?): String =
            if (ShareUriToken.JPEG_SUFFIX == suffix) MIME_JPEG else MIME_PNG
    }
}
