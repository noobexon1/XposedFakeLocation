package com.noobexon.xposedfakelocation.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class ServerUrlTest {
    @Test
    fun trailingSlashesAndWhitespaceAreStripped() {
        assertEquals("https://photon.komoot.io", normalizeServerUrl("  https://photon.komoot.io// "))
    }

    @Test
    fun portAndPathAreKept() {
        assertEquals("http://10.0.2.2:2322", normalizeServerUrl("http://10.0.2.2:2322"))
        assertEquals("https://example.org/photon", normalizeServerUrl("https://example.org/photon/"))
    }

    @Test
    fun schemeIsCaseInsensitive() {
        assertEquals("HTTPS://example.org", normalizeServerUrl("HTTPS://example.org"))
    }

    @Test
    fun invalidInputIsRejected() {
        assertNull(normalizeServerUrl(null))
        assertNull(normalizeServerUrl(""))
        assertNull(normalizeServerUrl("   "))
        assertNull(normalizeServerUrl("photon.komoot.io"))
        assertNull(normalizeServerUrl("ftp://example.org"))
        assertNull(normalizeServerUrl("https://"))
        assertNull(normalizeServerUrl("https://example.org?x=1"))
        assertNull(normalizeServerUrl("https://example.org#top"))
        assertNull(normalizeServerUrl("https://exa mple.org"))
        assertNull(normalizeServerUrl("https://example.org:99999"))
        assertNull(normalizeServerUrl("https://example.org:0"))
    }
}
