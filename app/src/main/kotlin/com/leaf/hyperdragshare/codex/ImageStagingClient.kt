package com.leaf.hyperdragshare.codex

import android.content.Context
import android.graphics.Bitmap
import android.net.Uri
import android.os.Bundle
import android.os.ParcelFileDescriptor
import java.io.IOException
import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicReference

internal object ImageStagingClient {
    interface Callback {
        /** Receives the private provider URI; the shared copy is published later, on demand. */
        fun onStaged(staged: Uri?)

        fun onFailure(error: Throwable?)
    }

    private const val TAG = "DragShare/Stage"

    const val AUTHORITY = "com.leaf.hyperdragshare.codex.share"

    val BASE_URI: Uri = Uri.parse("content://" + AUTHORITY)
    const val METHOD_STAGE = "stage_image"
    const val METHOD_GRANT = "grant_image"
    const val METHOD_REVOKE = "revoke_image"
    const val METHOD_PUBLISH = "publish_image"

    /** Legacy byte-array input accepted by the Provider during process upgrades. */
    const val EXTRA_BYTES = "bytes"
    const val EXTRA_STREAM = "stream"
    const val EXTRA_PACKAGE = "package"
    const val RESULT_URI = "uri"
    const val RESULT_GRANTED = "granted"

    private const val ENCODER_JOIN_TIMEOUT_MS = 60_000L
    private val EXECUTOR = Executors.newSingleThreadExecutor { runnable ->
        val thread = Thread(runnable, "drag-share-image-stage")
        thread.isDaemon = true
        thread
    }

    fun stage(context: Context?, bitmap: Bitmap?, callback: Callback) {
        EXECUTOR.execute {
            try {
                callback.onStaged(stagePng(context, bitmap))
            } catch (error: Throwable) {
                callback.onFailure(error)
            }
        }
    }

    /**
     * Mirrors the staged image into the shared media collection and returns that URI, or null
     * when the collection is unavailable.
     *
     * A shared copy is the one form every recipient accepts, but it is a real file in the user's
     * `Pictures` tree and therefore visible to gallery apps for as long as it exists. So the RPC is
     * sent at the moment the user commits to handing the image to another app, and never for a
     * preview, a cancelled drag, or a local-only action; whether it publishes anything is the
     * Provider's decision, since only it can read the current location preference.
     */
    fun publishShared(context: Context, staged: Uri?): Uri? {
        if (staged == null || !isOwnAuthority(staged)) {
            return null
        }
        return try {
            val extras = Bundle()
            extras.putString(RESULT_URI, staged.toString())
            val result = context.contentResolver.call(BASE_URI, METHOD_PUBLISH, null, extras)
            val value = result?.getString(RESULT_URI)
            if (value == null) null else Uri.parse(value)
        } catch (error: Throwable) {
            DragShareLog.w(TAG, "shared copy is unavailable", error)
            null
        }
    }

    /** True for the module's own authority, the only URI kind this client can grant. */
    fun isOwnAuthority(uri: Uri?): Boolean = uri != null && AUTHORITY == uri.authority

    private fun stagePng(context: Context?, bitmap: Bitmap?): Uri {
        if (context == null || bitmap == null || bitmap.isRecycled) {
            throw IllegalArgumentException("Bitmap is unavailable")
        }
        val pipe = ParcelFileDescriptor.createPipe()
        val readSide = pipe[0]
        val writeSide = pipe[1]
        val encoderFailure = AtomicReference<Throwable>()
        val encoder = Thread({
            try {
                ParcelFileDescriptor.AutoCloseOutputStream(writeSide).use { output ->
                    BitmapEncoder.writePng(bitmap, output)
                }
            } catch (error: Throwable) {
                encoderFailure.set(error)
            }
        }, "drag-share-png-encoder")
        encoder.isDaemon = true
        encoder.start()

        var result: Bundle? = null
        var providerFailure: Throwable? = null
        try {
            val extras = Bundle()
            extras.putParcelable(EXTRA_STREAM, readSide)
            result = context.contentResolver.call(BASE_URI, METHOD_STAGE, null, extras)
        } catch (error: Throwable) {
            providerFailure = error
        } finally {
            closeQuietly(readSide)
        }

        val writeFailure = waitForEncoder(encoder, writeSide, encoderFailure)
        if (providerFailure != null) {
            if (writeFailure != null && writeFailure !== providerFailure) {
                providerFailure.addSuppressed(writeFailure)
            }
            throw providerFailure
        }
        if (writeFailure != null) {
            throw writeFailure
        }
        val uriValue = result?.getString(RESULT_URI)
            ?: throw IllegalStateException("Image provider returned no URI")
        return Uri.parse(uriValue)
    }

    private fun waitForEncoder(
        encoder: Thread,
        writeSide: ParcelFileDescriptor,
        encoderFailure: AtomicReference<Throwable>,
    ): Throwable? {
        try {
            encoder.join(ENCODER_JOIN_TIMEOUT_MS)
        } catch (interrupted: InterruptedException) {
            Thread.currentThread().interrupt()
            closeQuietly(writeSide)
            encoder.interrupt()
            return IOException("PNG encoding interrupted", interrupted)
        }
        if (encoder.isAlive) {
            closeQuietly(writeSide)
            encoder.interrupt()
            return IOException("PNG encoding timed out")
        }
        return encoderFailure.get()
    }

    private fun closeQuietly(descriptor: ParcelFileDescriptor?) {
        if (descriptor == null) {
            return
        }
        try {
            descriptor.close()
        } catch (_: IOException) {
            // The paired stream can already have closed the descriptor.
        }
    }

    fun grantReadAccess(context: Context, uri: Uri, packageName: String?) {
        val extras = Bundle()
        extras.putString(RESULT_URI, uri.toString())
        extras.putString(EXTRA_PACKAGE, packageName)
        val result = context.contentResolver.call(BASE_URI, METHOD_GRANT, null, extras)
        if (result == null || !result.getBoolean(RESULT_GRANTED)) {
            throw SecurityException("Image provider did not grant URI access")
        }
    }

    fun revokeReadAccess(context: Context, uri: Uri, packageName: String?) {
        val extras = Bundle()
        extras.putString(RESULT_URI, uri.toString())
        extras.putString(EXTRA_PACKAGE, packageName)
        context.contentResolver.call(BASE_URI, METHOD_REVOKE, null, extras)
    }
}
