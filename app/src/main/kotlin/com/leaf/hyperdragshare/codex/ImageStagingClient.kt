package com.leaf.hyperdragshare.codex

import android.content.Context
import android.graphics.Bitmap
import android.net.Uri
import android.os.Bundle
import android.os.ParcelFileDescriptor
import java.io.IOException
import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicReference

object ImageStagingClient {
    interface Callback {
        fun onStaged(uri: Uri?)

        fun onFailure(error: Throwable?)
    }

    const val AUTHORITY = "com.leaf.hyperdragshare.codex.share"

    @JvmField
    val BASE_URI: Uri = Uri.parse("content://" + AUTHORITY)
    const val METHOD_STAGE = "stage_image"
    const val METHOD_GRANT = "grant_image"
    const val METHOD_REVOKE = "revoke_image"

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

    @JvmStatic
    fun stage(context: Context?, bitmap: Bitmap?, callback: Callback) {
        EXECUTOR.execute {
            try {
                callback.onStaged(stagePng(context, bitmap))
            } catch (error: Throwable) {
                callback.onFailure(error)
            }
        }
    }

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

    @JvmStatic
    fun grantReadAccess(context: Context, uri: Uri, packageName: String?) {
        val extras = Bundle()
        extras.putString(RESULT_URI, uri.toString())
        extras.putString(EXTRA_PACKAGE, packageName)
        val result = context.contentResolver.call(BASE_URI, METHOD_GRANT, null, extras)
        if (result == null || !result.getBoolean(RESULT_GRANTED)) {
            throw SecurityException("Image provider did not grant URI access")
        }
    }

    @JvmStatic
    fun revokeReadAccess(context: Context, uri: Uri, packageName: String?) {
        val extras = Bundle()
        extras.putString(RESULT_URI, uri.toString())
        extras.putString(EXTRA_PACKAGE, packageName)
        context.contentResolver.call(BASE_URI, METHOD_REVOKE, null, extras)
    }
}
