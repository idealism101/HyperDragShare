package com.leaf.hyperdragshare.codex

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Color
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.io.ByteArrayOutputStream
import java.io.IOException

@RunWith(RobolectricTestRunner::class)
@Config(manifest = Config.NONE)
class LocalImageSaverTest {
    @Test
    fun timestampNameUsesSecondPrecisionAndPngExtension() {
        val name = LocalImageSaver.timestampName(0L)

        assertTrue(name.endsWith(".png"))
        assertEquals(19, name.length)
        assertTrue(name.matches("\\d{8}_\\d{6}\\.png".toRegex()))
    }

    @Test
    @Throws(IOException::class)
    fun pngEncodingPreservesTransparentPadding() {
        val source = Bitmap.createBitmap(3, 3, Bitmap.Config.ARGB_8888)
        source.eraseColor(Color.TRANSPARENT)
        source.setPixel(0, 0, Color.RED)
        val output = ByteArrayOutputStream()

        LocalImageSaver.writePng(source, output)

        val encoded = output.toByteArray()
        val decoded = BitmapFactory.decodeByteArray(encoded, 0, encoded.size)
        assertNotNull(decoded)
        assertEquals(0, Color.alpha(decoded.getPixel(2, 2)))
        assertEquals(Color.RED, decoded.getPixel(0, 0))
    }

    @Test
    @Throws(IOException::class)
    fun pngEncodingKeepsOpaqueRightAndBottomEdgesAtDifferentDensity() {
        val source = Bitmap.createBitmap(4, 3, Bitmap.Config.ARGB_8888)
        source.eraseColor(Color.rgb(17, 83, 149))
        source.density = 640
        val output = ByteArrayOutputStream()

        LocalImageSaver.writePng(source, output)

        val encoded = output.toByteArray()
        val decoded = BitmapFactory.decodeByteArray(encoded, 0, encoded.size)
        assertNotNull(decoded)
        assertEquals(4, decoded.width)
        assertEquals(3, decoded.height)
        assertEquals(Color.rgb(17, 83, 149), decoded.getPixel(3, 2))
    }
}
