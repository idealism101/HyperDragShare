package com.leaf.hyperdragshare.codex

import android.content.Context
import android.content.res.AssetManager
import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import java.io.InputStream
import java.util.ArrayList

/** Lazy, process-local wrapper around cppjieba for the text-segmentation screen. */
internal class TextSegmenter private constructor(context: Context) {
    private val appContext: Context = context.applicationContext
    private var initialized = false
    private var preloading = false

    @Synchronized
    fun segment(text: String?): IntArray? {
        if (text == null || text.isEmpty()) {
            return null
        }
        ensureNativeLibraryLoaded()
        ensureInitialized()
        return buildSegments(text, nativeCut(text))
    }

    private fun preloadAsync() {
        synchronized(this) {
            if (initialized || preloading) {
                return
            }
            preloading = true
        }
        val worker = Thread({
            try {
                synchronized(this@TextSegmenter) {
                    ensureNativeLibraryLoaded()
                    ensureInitialized()
                }
                DragShareLog.d(TAG, "tokenizer preloaded")
            } catch (error: Throwable) {
                // A later foreground segmentation retries initialization and surfaces its own error.
                DragShareLog.w(TAG, "tokenizer preload failed", error)
            } finally {
                synchronized(this@TextSegmenter) {
                    preloading = false
                }
            }
        }, "drag-share-jieba-preload")
        worker.isDaemon = true
        try {
            worker.start()
        } catch (error: Throwable) {
            synchronized(this) {
                preloading = false
            }
            DragShareLog.w(TAG, "unable to start tokenizer preload", error)
        }
    }

    private fun ensureInitialized() {
        if (initialized) {
            return
        }
        val dictionaryDirectory = ensureDictionaryDirectory()
        if (!nativeInit(dictionaryDirectory.absolutePath)) {
            throw IllegalStateException("cppjieba init failed")
        }
        initialized = true
    }

    private fun ensureDictionaryDirectory(): File {
        val directory = File(appContext.filesDir, DICTIONARY_DIRECTORY)
        if (hasRequiredFiles(directory)) {
            return directory
        }
        if (!directory.exists() && !directory.mkdirs()) {
            throw IllegalStateException("Unable to create tokenizer dictionary directory")
        }
        val assets = appContext.assets
        for (fileName in REQUIRED_FILES) {
            val destination = File(directory, fileName)
            if (destination.isFile && destination.length() > 0L) {
                continue
            }
            copyAsset(assets, ASSET_DIRECTORY + "/" + fileName, destination)
        }
        if (!hasRequiredFiles(directory)) {
            throw IllegalStateException("Tokenizer dictionary files are missing")
        }
        return directory
    }

    companion object {
        private const val TAG = "DragShare/Jieba"
        private const val DICTIONARY_DIRECTORY = "dragshare_jieba"
        private const val ASSET_DIRECTORY = "dict"
        private val REQUIRED_FILES = arrayOf(
            "jieba.dict.utf8",
            "hmm_model.utf8",
            "user.dict.utf8",
        )

        private val STATIC_LOCK = Any()

        @Volatile
        private var instance: TextSegmenter? = null

        @Volatile
        private var nativeLibraryLoaded = false

        fun get(context: Context): TextSegmenter {
            val existing = instance
            if (existing != null) {
                return existing
            }
            return synchronized(STATIC_LOCK) {
                val current = instance
                if (current != null) {
                    current
                } else {
                    val created = TextSegmenter(context)
                    instance = created
                    created
                }
            }
        }

        fun preloadIfEnabled(context: Context?) {
            if (context == null || !DragShareSettings.readLocal(context).preloadTextSegmenter) {
                return
            }
            preload(context)
        }

        fun preload(context: Context?) {
            if (context != null) {
                get(context).preloadAsync()
            }
        }

        /**
         * Converts cppjieba's UTF-16 token spans to the original BigBang word/punctuation format.
         */
        fun buildSegments(text: String?, tokenSpans: IntArray?): IntArray? {
            if (text == null || text.isEmpty() || tokenSpans == null || tokenSpans.isEmpty()) {
                return null
            }
            val words = ArrayList<Int>()
            val punctuations = ArrayList<Int>()
            var cursor = 0
            var index = 0
            while (index + 1 < tokenSpans.size) {
                val start = tokenSpans[index]
                val endExclusive = tokenSpans[index + 1]
                if (start < cursor || start < 0 || endExclusive <= start ||
                    endExclusive > text.length
                ) {
                    index += 2
                    continue
                }
                appendPunctuation(text, cursor, start, punctuations)
                if (hasWordCodePoint(text, start, endExclusive)) {
                    words.add(start)
                    words.add(endExclusive - 1)
                } else {
                    appendPunctuation(text, start, endExclusive, punctuations)
                }
                cursor = endExclusive
                index += 2
            }
            appendPunctuation(text, cursor, text.length, punctuations)
            if (words.isEmpty() && punctuations.isEmpty()) {
                return null
            }
            val result = IntArray(words.size + punctuations.size + 1)
            var outputIndex = 0
            for (value in words) {
                result[outputIndex++] = value
            }
            result[outputIndex++] = -1
            for (value in punctuations) {
                result[outputIndex++] = value
            }
            return result
        }

        private fun ensureNativeLibraryLoaded() {
            if (nativeLibraryLoaded) {
                return
            }
            synchronized(STATIC_LOCK) {
                if (!nativeLibraryLoaded) {
                    System.loadLibrary("dragshare_jieba")
                    nativeLibraryLoaded = true
                }
            }
        }

        private fun hasRequiredFiles(directory: File): Boolean {
            if (!directory.isDirectory) {
                return false
            }
            for (fileName in REQUIRED_FILES) {
                val dictionaryFile = File(directory, fileName)
                if (!dictionaryFile.isFile || dictionaryFile.length() <= 0L) {
                    return false
                }
            }
            return true
        }

        private fun copyAsset(assets: AssetManager, assetPath: String, destination: File) {
            try {
                assets.open(assetPath).use { input ->
                    FileOutputStream(destination, false).use { output ->
                        copyStream(input, output)
                        output.fd.sync()
                    }
                }
            } catch (error: IOException) {
                throw IllegalStateException("Unable to copy tokenizer dictionary", error)
            }
        }

        @Throws(IOException::class)
        private fun copyStream(input: InputStream, output: FileOutputStream) {
            val buffer = ByteArray(32 * 1024)
            while (true) {
                val read = input.read(buffer)
                if (read == -1) {
                    break
                }
                output.write(buffer, 0, read)
            }
        }

        private fun appendPunctuation(
            text: String,
            start: Int,
            endExclusive: Int,
            out: ArrayList<Int>,
        ) {
            var index = start
            while (index < endExclusive) {
                val codePoint = text.codePointAt(index)
                val next = index + Character.charCount(codePoint)
                if (!Character.isWhitespace(codePoint) && !Character.isSpaceChar(codePoint)) {
                    out.add(index)
                    out.add(next - 1)
                }
                index = next
            }
        }

        private fun hasWordCodePoint(text: String, start: Int, endExclusive: Int): Boolean {
            var index = start
            while (index < endExclusive) {
                val codePoint = text.codePointAt(index)
                if (Character.isLetterOrDigit(codePoint) || codePoint == '_'.code) {
                    return true
                }
                index += Character.charCount(codePoint)
            }
            return false
        }

        @JvmStatic
        private external fun nativeInit(dictionaryDirectory: String): Boolean

        @JvmStatic
        private external fun nativeCut(text: String): IntArray?
    }
}
