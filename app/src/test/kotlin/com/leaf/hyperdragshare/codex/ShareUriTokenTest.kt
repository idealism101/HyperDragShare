package com.leaf.hyperdragshare.codex

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class ShareUriTokenTest {
    @Test
    fun parsesPngJpegAndLegacyTokenPaths() {
        assertEquals(TOKEN, ShareUriToken.parse("$TOKEN.png"))
        assertEquals(TOKEN, ShareUriToken.parse("$TOKEN.jpg"))
        assertEquals(TOKEN, ShareUriToken.parse(TOKEN))
    }

    @Test
    fun createsPngFileNameAndRetainsLegacySuffixMetadata() {
        assertEquals("$TOKEN.png", ShareUriToken.fileName(TOKEN))
        assertEquals(".png", ShareUriToken.suffix("$TOKEN.png"))
        assertEquals(".jpg", ShareUriToken.suffix("$TOKEN.jpg"))
        assertEquals(".jpg", ShareUriToken.suffix(TOKEN))
    }

    @Test
    fun rejectsInvalidPathSegments() {
        assertNull(ShareUriToken.parse("not-a-token.jpg"))
        assertNull(ShareUriToken.suffix("not-a-token.png"))
        assertNull(ShareUriToken.parse(null))
    }

    private companion object {
        private const val TOKEN = "42da71e4-df3c-4de0-a6e5-526dedc34dc7"
    }
}
