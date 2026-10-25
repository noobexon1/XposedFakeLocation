package com.noobexon.xposedfakelocation.manager.route

import org.junit.Assert.assertEquals
import org.junit.Test
import java.util.Locale

class RouteFormatTest {
    @Test
    fun clockPadsSeconds() {
        assertEquals("0:24", RouteFormat.clock(24))
        assertEquals("2:05", RouteFormat.clock(125))
    }

    @Test
    fun kmHasOneDecimal() {
        assertEquals("1.1", RouteFormat.km(1_100.0, Locale.US))
        assertEquals("0.0", RouteFormat.km(0.0, Locale.US))
        assertEquals("2,4", RouteFormat.km(2_400.0, Locale.GERMANY))
    }

    @Test
    fun minutesRoundUpWithAMinimumOfOne() {
        assertEquals(1, RouteFormat.minutes(0.0))
        assertEquals(1, RouteFormat.minutes(59.0))
        assertEquals(2, RouteFormat.minutes(61.0))
    }

    @Test
    fun multiplierDropsTrailingZero() {
        assertEquals("0.5", RouteFormat.multiplier(0.5f))
        assertEquals("1", RouteFormat.multiplier(1f))
        assertEquals("2", RouteFormat.multiplier(2f))
    }
}
