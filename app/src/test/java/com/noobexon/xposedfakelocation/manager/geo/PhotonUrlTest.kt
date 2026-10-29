package com.noobexon.xposedfakelocation.manager.geo

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.util.Locale

class PhotonUrlTest {
    @Test
    fun urlEncodesQueryAndAddsBiasAndLanguage() {
        assertEquals(
            "https://photon.komoot.io/api?q=Herzl+5%2C+Tel+Aviv&limit=6&lat=32.08&lon=34.78&lang=en",
            buildSearchUrl("https://photon.komoot.io", "Herzl 5, Tel Aviv", LatLon(32.08, 34.78), "en"),
        )
    }

    @Test
    fun biasAndLanguageAreOptional() {
        assertEquals(
            "http://10.0.2.2:2322/api?q=%E5%8C%97%E4%BA%AC&limit=3",
            buildSearchUrl("http://10.0.2.2:2322/", "北京", near = null, language = null, limit = 3),
        )
    }

    @Test
    fun reverseUrlHasTheCoordinateAndOptionalLanguage() {
        assertEquals(
            "https://photon.komoot.io/reverse?lat=32.08&lon=34.78&limit=1&lang=de",
            buildReverseUrl("https://photon.komoot.io/", LatLon(32.08, 34.78), "de"),
        )
        assertEquals(
            "http://10.0.2.2:2322/reverse?lat=-1.5&lon=2.25&limit=1",
            buildReverseUrl("http://10.0.2.2:2322", LatLon(-1.5, 2.25), language = null),
        )
    }

    @Test
    fun onlyPhotonLanguagesArePassed() {
        assertEquals("en", photonLanguage(Locale.ENGLISH))
        assertEquals("de", photonLanguage(Locale.GERMANY))
        assertEquals("fr", photonLanguage(Locale.FRENCH))
        assertNull(photonLanguage(Locale.CHINESE))
        assertNull(photonLanguage(Locale("he")))
    }
}
