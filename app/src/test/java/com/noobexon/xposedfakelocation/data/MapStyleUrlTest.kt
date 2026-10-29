package com.noobexon.xposedfakelocation.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class MapStyleUrlTest {
    @Test
    fun blankMeansTheDefaultStyles() {
        assertEquals("", normalizeMapStyleUrl(""))
        assertEquals("", normalizeMapStyleUrl("   "))
    }

    @Test
    fun missingStaysMissing() {
        assertNull(normalizeMapStyleUrl(null))
    }

    @Test
    fun validUrlsAreTrimmedAndKeepTheirQuery() {
        assertEquals("https://tiles.example.org/style.json", normalizeMapStyleUrl("  https://tiles.example.org/style.json "))
        assertEquals("https://tiles.example.org/style.json?key=abc", normalizeMapStyleUrl("https://tiles.example.org/style.json?key=abc"))
        assertEquals("http://10.0.0.2:8080/styles/basic", normalizeMapStyleUrl("http://10.0.0.2:8080/styles/basic"))
    }

    @Test
    fun invalidUrlsAreRejected() {
        assertNull(normalizeMapStyleUrl("ftp://tiles.example.org/style.json"))
        assertNull(normalizeMapStyleUrl("https://"))
        assertNull(normalizeMapStyleUrl("not a url"))
        assertNull(normalizeMapStyleUrl("tiles.example.org/style.json"))
        assertNull(normalizeMapStyleUrl("https://tiles.example.org/style.json#top"))
    }
}
