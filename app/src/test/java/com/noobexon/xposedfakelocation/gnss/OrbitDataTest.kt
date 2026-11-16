package com.noobexon.xposedfakelocation.gnss

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class OrbitDataTest {
    private val header =
        "OBJECT_NAME,OBJECT_ID,EPOCH,MEAN_MOTION,ECCENTRICITY,INCLINATION,RA_OF_ASC_NODE,ARG_OF_PERICENTER," +
            "MEAN_ANOMALY,EPHEMERIS_TYPE,CLASSIFICATION_TYPE,NORAD_CAT_ID,ELEMENT_SET_NO,REV_AT_EPOCH,BSTAR," +
            "MEAN_MOTION_DOT,MEAN_MOTION_DDOT"

    private val csv = listOf(
        header,
        "GPS BIIR-5  (PRN 22),2000-040A,2026-11-16T02:29:41.009568,2.00559057,.01147756,54.8248,209.1347,305.1712,227.7324,0,U,26407,999,19299,0,.9E-7,0",
        "COSMOS 2434 (721),2007-065A,2026-11-16T08:03:04.975200,2.13101412,.00040394,64.3171,186.7891,68.7485,112.1139,0,U,32393,999,14701,0,.49E-6,0",
        "COSMOS 2433 (720),2007-052A,2026-11-15T22:55:14.744928,2.13104292,.00056654,65.4856,310.8134,244.4805,115.4749,0,U,32275,999,14829,0,-.15E-6,0",
        "garbage,row",
        header,
        "GSAT0101 (GALILEO-PFM),2011-060A,2026-11-14T14:25:01.421760,1.70475808,.00022602,56.9332,337.6751,311.3317,48.6888,0,U,37846,999,9368,0,-.67E-6,0",
        "BEIDOU-2 IGSO-1 (C06),2010-036A,2026-11-12T00:36:39.548736,1.00272326,.0055753,54.3082,161.6241,219.2975,147.5878,0,U,36828,999,5958,0,-.56E-6,0",
    ).joinToString("\n")

    private val svidMap = CelestrakCsvParser.parseSvidMap(
        "NORAD_CAT_ID,constellation,svid,glonass_channel\n32393,GLONASS,13,-2\n37846,GALILEO,11,\nbad,line\n",
    )

    @Test
    fun svidMapParsesGlonassChannelAndGalileo() {
        assertEquals(CelestrakCsvParser.SvidEntry(Constellation.GLONASS, 13, -2), svidMap[32393])
        assertEquals(CelestrakCsvParser.SvidEntry(Constellation.GALILEO, 11, null), svidMap[37846])
        assertEquals(2, svidMap.size)
    }

    @Test
    fun parsesAllConstellationsAndSkipsUnmappedAndBadRows() {
        val parsed = CelestrakCsvParser.parse(csv, svidMap)
        assertEquals(
            listOf(
                Triple(Constellation.GPS, 22, null),
                Triple(Constellation.GLONASS, 13, -2),
                Triple(Constellation.GALILEO, 11, null),
                Triple(Constellation.BEIDOU, 6, null),
            ),
            parsed.map { Triple(it.constellation, it.svid, it.glonassChannel) },
        )
    }

    @Test
    fun parsesElementValues() {
        val gps = CelestrakCsvParser.parse(csv, svidMap).first()
        assertEquals(1794796181009L, gps.epochMillis)
        assertEquals(2.00559057, gps.meanMotionRevPerDay, 0.0)
        assertEquals(0.01147756, gps.eccentricity, 0.0)
        assertEquals(54.8248, gps.inclinationDeg, 0.0)
        assertEquals(209.1347, gps.raanDeg, 0.0)
        assertEquals(305.1712, gps.argPerigeeDeg, 0.0)
        assertEquals(227.7324, gps.meanAnomalyDeg, 0.0)
    }

    @Test
    fun codecRoundTripsAndSkipsBadLines() {
        val parsed = CelestrakCsvParser.parse(csv, svidMap)
        val encoded = OrbitCodec.encode(parsed)
        assertEquals(parsed, OrbitCodec.decode(encoded))
        assertEquals(parsed, OrbitCodec.decode(encoded + "\nnot,a,satellite"))
        assertTrue(OrbitCodec.decode("").isEmpty())
    }

    @Test
    fun constellationFromAndroidType() {
        assertEquals(Constellation.GALILEO, Constellation.fromAndroidType(6))
        assertNull(Constellation.fromAndroidType(4))
    }

    @Test
    fun staleAfterOneDay() {
        assertFalse(OrbitUpdate.isStale(versionMillis = 1_000L, nowMillis = 1_000L + OrbitUpdate.MAX_AGE_MILLIS))
        assertTrue(OrbitUpdate.isStale(versionMillis = 1_000L, nowMillis = 1_001L + OrbitUpdate.MAX_AGE_MILLIS))
        assertTrue(OrbitUpdate.isStale(versionMillis = 0L, nowMillis = 1_800_000_000_000L))
    }

    @Test
    fun mergeReplacesDownloadedGroupsAndKeepsFailedOnes() {
        val parsed = CelestrakCsvParser.parse(csv, svidMap)
        val oldGps = parsed.first { it.constellation == Constellation.GPS }
        val newGps = oldGps.copy(meanAnomalyDeg = 1.0)
        val merged = OrbitUpdate.merge(
            previous = parsed,
            downloaded = mapOf(
                Constellation.GPS to listOf(newGps),
                Constellation.GLONASS to null,
                Constellation.GALILEO to emptyList(),
            ),
        )
        assertEquals(listOf(newGps), merged.filter { it.constellation == Constellation.GPS })
        assertEquals(parsed.filter { it.constellation != Constellation.GPS }.toSet(), merged.filter { it.constellation != Constellation.GPS }.toSet())
    }
}
