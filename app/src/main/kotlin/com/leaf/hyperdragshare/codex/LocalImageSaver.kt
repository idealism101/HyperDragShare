package com.leaf.hyperdragshare.codex

import android.content.ContentValues
import android.content.Context
import android.graphics.Bitmap
import android.media.MediaScannerConnection
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import java.io.OutputStream
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/** Copies a captured image into the user's Pictures collection. */
object LocalImageSaver {
    private const val DIRECTORY_NAME = "HyperDragShare"
    private const val MIME_PNG = "image/png"

    @JvmStatic
    @Throws(IOException::class)
    fun save(context: Context?, bitmap: Bitmap?): Uri {
        if (context == null || bitmap == null || bitmap.isRecycled) {
            throw IOException("Image is unavailable")
        }
        return save(context) { output -> writePng(bitmap, output) }
    }

    @JvmStatic
    fun timestampName(timestampMillis: Long): String =
        SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US).format(Date(timestampMillis)) + ".png"

    @JvmStatic
    @Throws(IOException::class)
    fun writePng(bitmap: Bitmap?, output: OutputStream?) {
        BitmapEncoder.writePng(bitmap, output)
    }

    @Throws(IOException::class)
    private fun save(context: Context, writer: OutputWriter): Uri {
        val displayName = timestampName(System.currentTimeMillis())
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            return saveWithMediaStore(context, displayName, writer)
        }
        return saveLegacy(context, displayName, writer)
    }

    @Throws(IOException::class)
    private fun saveWithMediaStore(
        context: Context,
        displayName: String,
        writer: OutputWriter,
    ): Uri {
        val resolver = context.contentResolver
        val values = ContentValues()
        values.put(MediaStore.Images.Media.DISPLAY_NAME, displayName)
        values.put(MediaStore.Images.Media.MIME_TYPE, MIME_PNG)
        values.put(
            MediaStore.Images.Media.RELATIVE_PATH,
            Environment.DIRECTORY_PICTURES + File.separator + DIRECTORY_NAME,
        )
        values.put(MediaStore.Images.Media.IS_PENDING, 1)

        val uri = resolver.insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, values)
            ?: throw IOException("MediaStore insert returned null")
        try {
            resolver.openOutputStream(uri).use { output ->
                if (output == null) {
                    throw IOException("Unable to open destination image")
                }
                writer.write(output)
            }
            val ready = ContentValues()
            ready.put(MediaStore.Images.Media.IS_PENDING, 0)
            resolver.update(uri, ready, null, null)
            return uri
        } catch (error: Throwable) {
            try {
                resolver.delete(uri, null, null)
            } catch (_: Throwable) {
                // Preserve the original encoding or storage error.
            }
            if (error is IOException) {
                throw error
            }
            throw IOException("Unable to save image", error)
        }
    }

    @Suppress("DEPRECATION")
    @Throws(IOException::class)
    private fun saveLegacy(context: Context, displayName: String, writer: OutputWriter): Uri {
        val directory = File(
            Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_PICTURES),
            DIRECTORY_NAME,
        )
        if (!directory.exists() && !directory.mkdirs()) {
            throw IOException("Unable to create Pictures directory")
        }
        val destination = uniqueFile(directory, displayName)
        try {
            FileOutputStream(destination).use { output ->
                writer.write(output)
                output.fd.sync()
            }
        } catch (error: Throwable) {
            destination.delete()
            if (error is IOException) {
                throw error
            }
            throw IOException("Unable to save image", error)
        }
        MediaScannerConnection.scanFile(
            context,
            arrayOf(destination.absolutePath),
            arrayOf(MIME_PNG),
            null,
        )
        return Uri.fromFile(destination)
    }

    private fun uniqueFile(directory: File, displayName: String): File {
        var destination = File(directory, displayName)
        if (!destination.exists()) {
            return destination
        }
        val extension = displayName.lastIndexOf('.')
        val stem = if (extension < 0) displayName else displayName.substring(0, extension)
        val suffix = if (extension < 0) "" else displayName.substring(extension)
        var index = 1
        do {
            destination = File(directory, stem + "_" + index + suffix)
            index++
        } while (destination.exists())
        return destination
    }

    private fun interface OutputWriter {
        @Throws(IOException::class)
        fun write(output: OutputStream)
    }
}
