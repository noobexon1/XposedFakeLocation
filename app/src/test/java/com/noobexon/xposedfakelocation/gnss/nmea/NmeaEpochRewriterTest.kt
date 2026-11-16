package com.noobexon.xposedfakelocation.gnss.nmea

import org.junit.Assert.assertEquals
import org.junit.Test

class NmeaEpochRewriterTest {
    private val style = NmeaStyle.DEFAULT
    private val t = 1_794_844_022_979L

    /** One captured update before the first fix, in delivery order, with its timestamps. */
    private val update = listOf(
        t to "\$GPGSV,3,1,12,18,09,203,11,24,28,175,17,05,12,133,,06,14,037,,1*6D\r\n",
        t to "\$GPGSV,3,2,12,11,43,054,,12,56,073,,13,66,061,,21,13,099,,1*60\r\n",
        t to "\$GPGSV,3,3,12,25,58,334,,28,18,316,,29,44,268,,32,07,274,,1*6E\r\n",
        t to "\$GLGSV,1,1,04,76,53,347,,75,54,102,,87,30,210,,85,28,028,,1*79\r\n",
        t to "\$GAGSV,3,1,12,04,01,320,,13,50,154,,15,45,064,,19,19,272,,7*71\r\n",
        t to "\$GBGSV,2,1,08,02,27,118,,03,02,098,,06,09,087,,08,04,052,,1*79\r\n",
        t + 3 to "\$GPGSA,A,1,,,,,,,,,,,,,,,,*32\r\n",
        t + 3 to "\$PQXM,1,2*17\r\n",
        t + 3 to "\$GPDTM,,,,,,,,*4A\r\n",
        t + 3 to "\$GPRMC,,V,,,,,,,,,,N,V*29\r\n",
        t + 3 to "\$GPGNS,,,,,,N,,,,,,,V*79\r\n",
        t + 3 to "\$GPGGA,,,,,,0,,,,,,,,*66\r\n",
        t + 3 to "\$GPVTG,,T,,M,,N,,K,N*2C\r\n",
    )

    private fun rewriteAll(rewriter: NmeaEpochRewriter, lines: List<Pair<Long, String>>, fix: NmeaFix): List<String> =
        lines.flatMap { (timestamp, line) -> rewriter.rewrite(line, timestamp, style) { fix } }

    @Test
    fun updateBeforeTheFix() {
        val out = rewriteAll(NmeaEpochRewriter(), update, NmeaFixtures.NO_FIX)
        val expected = NmeaFixtures.GSV + listOf(
            NmeaFixtures.NO_FIX_GSA,
            NmeaFixtures.NO_FIX_DTM,
            NmeaFixtures.NO_FIX_RMC,
            NmeaFixtures.NO_FIX_GNS,
            NmeaFixtures.NO_FIX_GGA,
            NmeaFixtures.NO_FIX_VTG,
        )
        assertEquals(expected, out)
    }

    @Test
    fun updateAfterTheFix() {
        val out = rewriteAll(NmeaEpochRewriter(), update, NmeaFixtures.FIX)
        val expected = NmeaFixtures.GSV + NmeaFixtures.GSA + listOf(
            NmeaFixtures.DTM,
            NmeaFixtures.RMC,
            NmeaFixtures.GNS,
            NmeaFixtures.GGA,
            NmeaFixtures.VTG,
        )
        assertEquals(expected, out)
    }

    @Test
    fun oneSnapshotPerUpdate() {
        val rewriter = NmeaEpochRewriter()
        var snapshots = 0
        val supplier = { snapshots++; NmeaFixtures.FIX }
        update.forEach { (timestamp, line) -> rewriter.rewrite(line, timestamp, style, supplier) }
        assertEquals(1, snapshots)
        val next = rewriter.rewrite(update.first().second, t + 1000, style, supplier)
        assertEquals(2, snapshots)
        assertEquals(NmeaFixtures.GSV, next)
    }

    @Test
    fun updateEndsAfterAGapSinceThePreviousSentence() {
        val rewriter = NmeaEpochRewriter()
        val gsv = update.first().second
        assertEquals(4, rewriter.rewrite(gsv, t, style) { NmeaFixtures.FIX }.size)
        assertEquals(0, rewriter.rewrite(gsv, t + 400, style) { NmeaFixtures.FIX }.size)
        assertEquals(0, rewriter.rewrite(gsv, t + 800, style) { NmeaFixtures.FIX }.size)
        assertEquals(4, rewriter.rewrite(gsv, t + 1301, style) { NmeaFixtures.FIX }.size)
        assertEquals(4, rewriter.rewrite(gsv, t, style) { NmeaFixtures.FIX }.size)
    }

    @Test
    fun malformedAndUnknownSentencesAreDropped() {
        val rewriter = NmeaEpochRewriter()
        var snapshots = 0
        val supplier = { snapshots++; NmeaFixtures.FIX }
        assertEquals(emptyList<String>(), rewriter.rewrite("\$GPDTM,,,,,,,,*4B\r\n", t, style, supplier))
        assertEquals(emptyList<String>(), rewriter.rewrite("\$PQXM,1,2*17\r\n", t, style, supplier))
        assertEquals(emptyList<String>(), rewriter.rewrite(NmeaSentence("GN", "TXT", listOf("1")).format(), t, style, supplier))
        assertEquals(0, snapshots)
    }
}
