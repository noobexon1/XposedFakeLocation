package com.noobexon.xposedfakelocation.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class RoutingServerUrlTest {
    @Test
    fun placeholderIsKeptAndValidated() {
        assertEquals(DEFAULT_ROUTING_SERVER_URL, normalizeRoutingServerUrl(" https://routing.openstreetmap.de/routed-{profile}/ "))
        assertEquals("https://{profile}.example.org", normalizeRoutingServerUrl("https://{profile}.example.org"))
    }

    @Test
    fun urlWithoutPlaceholderIsAccepted() {
        assertEquals("http://10.0.2.2:5000", normalizeRoutingServerUrl("http://10.0.2.2:5000/"))
    }

    @Test
    fun badTemplatesAreRejected() {
        assertNull(normalizeRoutingServerUrl(null))
        assertNull(normalizeRoutingServerUrl("https://a.org/{profile}/{profile}"))
        assertNull(normalizeRoutingServerUrl("https://a.org/{mode}"))
        assertNull(normalizeRoutingServerUrl("https://a.org/{profile"))
        assertNull(normalizeRoutingServerUrl("routing.openstreetmap.de/routed-{profile}"))
    }
}
