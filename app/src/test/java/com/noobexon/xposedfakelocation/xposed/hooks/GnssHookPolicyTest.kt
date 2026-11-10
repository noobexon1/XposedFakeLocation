package com.noobexon.xposedfakelocation.xposed.hooks

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class GnssHookPolicyTest {
    private val statusLocationDisabled = 2
    private val statusNotAllowed = 3

    @Test
    fun rawDataIsDroppedOnlyWhileSpoofing() {
        assertFalse(GnssHookPolicy.deliversRawData(spoofing = true))
        assertTrue(GnssHookPolicy.deliversRawData(spoofing = false))
    }

    @Test
    fun readyBecomesNotSupportedBeforeAndroid12WhileSpoofing() {
        for (sdk in listOf(29, 30)) {
            assertEquals(
                GnssHookPolicy.STATUS_NOT_SUPPORTED,
                GnssHookPolicy.rawDataStatus(sdk, spoofing = true, status = GnssHookPolicy.STATUS_READY),
            )
        }
    }

    @Test
    fun otherStatusesPassBeforeAndroid12() {
        assertEquals(statusLocationDisabled, GnssHookPolicy.rawDataStatus(30, spoofing = true, status = statusLocationDisabled))
        assertEquals(statusNotAllowed, GnssHookPolicy.rawDataStatus(29, spoofing = true, status = statusNotAllowed))
    }

    @Test
    fun statusesAreUnchangedFromAndroid12() {
        for (sdk in listOf(31, 36)) {
            assertEquals(
                GnssHookPolicy.STATUS_READY,
                GnssHookPolicy.rawDataStatus(sdk, spoofing = true, status = GnssHookPolicy.STATUS_READY),
            )
        }
    }

    @Test
    fun statusesAreUnchangedWhenNotSpoofing() {
        assertEquals(
            GnssHookPolicy.STATUS_READY,
            GnssHookPolicy.rawDataStatus(29, spoofing = false, status = GnssHookPolicy.STATUS_READY),
        )
    }

    @Test
    fun rawDataCapabilitiesAreHiddenWhileSpoofing() {
        for (name in GnssHookPolicy.UNSUPPORTED_CAPABILITIES) {
            assertFalse(name, GnssHookPolicy.capability(name, spoofing = true, real = true))
        }
    }

    @Test
    fun capabilitiesKeepRealValueWhenNotSpoofing() {
        for (name in GnssHookPolicy.UNSUPPORTED_CAPABILITIES) {
            assertTrue(name, GnssHookPolicy.capability(name, spoofing = false, real = true))
        }
    }

    @Test
    fun otherCapabilitiesKeepRealValue() {
        assertTrue(GnssHookPolicy.capability("hasAntennaInfo", spoofing = true, real = true))
        assertFalse(GnssHookPolicy.capability("hasLowPowerMode", spoofing = true, real = false))
    }

    @Test
    fun capabilityListMatchesSpec() {
        assertEquals(
            setOf(
                "hasMeasurements",
                "hasNavMessages",
                "hasNavigationMessages",
                "hasMeasurementCorrections",
                "hasMeasurementCorrectionsLosSats",
                "hasMeasurementCorrectionsExcessPathLength",
                "hasMeasurementCorrectionsReflectingPane",
                "hasMeasurementCorrectionsReflectingPlane",
                "hasMeasurementCorrectionsForDriving",
                "hasMeasurementCorrelationVectors",
                "hasSatellitePvt",
            ),
            GnssHookPolicy.UNSUPPORTED_CAPABILITIES,
        )
    }
}
