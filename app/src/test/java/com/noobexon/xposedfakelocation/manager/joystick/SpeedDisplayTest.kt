package com.noobexon.xposedfakelocation.manager.joystick

import org.junit.Assert.assertEquals
import org.junit.Test

class SpeedDisplayTest {
    @Test
    fun roundsToWholeKilometersPerHour() {
        assertEquals(0, displayKmh(0f))
        assertEquals(5, displayKmh(1.4f))
        assertEquals(11, displayKmh(3.0f))
        assertEquals(200, displayKmh(55.6f))
    }
}
