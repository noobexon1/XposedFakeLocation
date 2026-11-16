package com.noobexon.xposedfakelocation.gnss.nmea

import com.noobexon.xposedfakelocation.gnss.Constellation
import com.noobexon.xposedfakelocation.gnss.SkySatellite
import java.time.Instant
import java.time.OffsetDateTime
import java.time.ZoneOffset
import java.util.Locale
import kotlin.math.abs
import kotlin.math.roundToInt
import kotlin.math.roundToLong

/**
 * Writes the fake NMEA sentences of an [NmeaFix] in a chip's [NmeaStyle]. Without a fix, the position
 * sentences take the chip's empty forms with the `GP` talker; GSV lists the sky either way.
 */
object NmeaWriter {
    private const val NO_FIX_TALKER = "GP"
    private const val MULTI_SYSTEM_TALKER = "GN"
    private const val SATELLITES_PER_GSV = 4
    private const val GSA_SLOTS = 12
    private const val MAX_GGA_SATELLITES = 12
    private const val MIN_COURSE_SPEED_MPS = 0.5f
    private const val KNOTS_PER_MPS = 3600.0 / 1852.0
    private const val KMH_PER_MPS = 3.6
    private const val GLONASS_NMEA_OFFSET = 64
    private const val MAX_DOP = 99.9

    private val SYSTEMS = listOf(Constellation.GPS, Constellation.GLONASS, Constellation.GALILEO, Constellation.BEIDOU)
    private val SYSTEM_IDS = mapOf(
        Constellation.GPS to "1",
        Constellation.GLONASS to "2",
        Constellation.GALILEO to "3",
        Constellation.BEIDOU to "4",
    )

    /** The fake sentences replacing a real sentence of [type]; empty for types that aren't replaced. */
    fun sentences(type: String, fix: NmeaFix, style: NmeaStyle): List<NmeaSentence> = when (type) {
        "GSV" -> gsv(fix, style)
        "GSA" -> gsa(fix, style)
        "GGA" -> listOf(gga(fix, style))
        "RMC" -> listOf(rmc(fix, style))
        "GNS" -> listOf(gns(fix, style))
        "VTG" -> listOf(vtg(fix, style))
        "GLL" -> listOf(gll(fix, style))
        "ZDA" -> listOf(zda(fix, style))
        "DTM" -> listOf(dtm(fix, style))
        else -> emptyList()
    }

    /** Satellites in view: one group per system and band, [SATELLITES_PER_GSV] satellites per page. */
    fun gsv(fix: NmeaFix, style: NmeaStyle): List<NmeaSentence> = SYSTEMS.flatMap { constellation ->
        fix.sky.filter { it.constellation == constellation }
            .groupBy { NmeaSignals.signalId(constellation, it.carrierFrequencyHz) }
            .entries
            .sortedBy { NmeaSignals.order(constellation, it.key) }
            .flatMap { (signalId, satellites) -> gsvPages(style.talker(constellation), signalId, satellites) }
    }

    private fun gsvPages(talker: String, signalId: String, satellites: List<SkySatellite>): List<NmeaSentence> {
        val sorted = satellites.sortedBy(::nmeaSvid)
        val pages = sorted.chunked(SATELLITES_PER_GSV)
        return pages.mapIndexed { index, page ->
            val satelliteFields = page.flatMap { satellite ->
                listOf(
                    two(nmeaSvid(satellite)),
                    two(satellite.elevationDeg.roundToInt().coerceIn(0, 90)),
                    three(satellite.azimuthDeg.roundToInt().mod(360)),
                    satellite.cn0DbHz.roundToInt().takeIf { it > 0 }?.let(::two) ?: "",
                )
            }
            NmeaSentence(talker, "GSV", listOf(pages.size.toString(), (index + 1).toString(), two(sorted.size)) + satelliteFields + signalId)
        }
    }

    /** DOP and active satellites: one sentence per system with satellites used, by C/N0. */
    fun gsa(fix: NmeaFix, style: NmeaStyle): List<NmeaSentence> {
        if (!fix.fixed) return listOf(NmeaSentence(NO_FIX_TALKER, "GSA", listOf("A", "1") + empty(GSA_SLOTS + 4)))
        val used = usedSatellites(fix)
        val dop = Dop.of(used)
        val dopFields = dop?.let { listOf(dopValue(it.pdop), dopValue(it.hdop), dopValue(it.vdop)) } ?: empty(3)
        val talker = fixTalker(fix, style)
        val systems = SYSTEMS.filter { system -> used.any { it.constellation == system } }
        if (systems.isEmpty()) {
            return listOf(NmeaSentence(talker, "GSA", listOf("A", "3") + empty(GSA_SLOTS) + dopFields + SYSTEM_IDS.getValue(Constellation.GPS)))
        }
        return systems.map { system ->
            val ids = used.filter { it.constellation == system }
                .sortedByDescending { it.cn0DbHz }
                .take(GSA_SLOTS)
                .map { two(nmeaSvid(it)) }
            NmeaSentence(talker, "GSA", listOf("A", "3") + ids + empty(GSA_SLOTS - ids.size) + dopFields + SYSTEM_IDS.getValue(system))
        }
    }

    fun gga(fix: NmeaFix, style: NmeaStyle): NmeaSentence {
        if (!fix.fixed) return NmeaSentence(NO_FIX_TALKER, "GGA", empty(5) + "0" + empty(8))
        val used = usedSatellites(fix)
        return NmeaSentence(
            fixTalker(fix, style),
            "GGA",
            listOf(time(fix)) + position(fix, style) + listOf(
                "1",
                two(used.size.coerceAtMost(MAX_GGA_SATELLITES)),
                hdop(used),
                one(fix.mslAltitudeMeters),
                "M",
                one(fix.geoidSeparationMeters),
                "M",
                "",
                "",
            ),
        )
    }

    fun rmc(fix: NmeaFix, style: NmeaStyle): NmeaSentence {
        if (!fix.fixed) return NmeaSentence(NO_FIX_TALKER, "RMC", listOf("", "V") + empty(9) + listOf("N", "V"))
        return NmeaSentence(
            fixTalker(fix, style),
            "RMC",
            listOf(time(fix), "A") + position(fix, style) + listOf(
                one(fix.speedMetersPerSecond * KNOTS_PER_MPS),
                course(fix),
                date(fix),
                one(abs(fix.magneticVariationDegrees)),
                if (fix.magneticVariationDegrees >= 0) "E" else "W",
                "A",
                "V",
            ),
        )
    }

    fun gns(fix: NmeaFix, style: NmeaStyle): NmeaSentence {
        if (!fix.fixed) return NmeaSentence(NO_FIX_TALKER, "GNS", empty(5) + "N" + empty(6) + "V")
        val used = usedSatellites(fix)
        val mode = (0 until style.gnsModeLength).map { index ->
            val system = SYSTEMS.getOrNull(index)
            if (system != null && used.any { it.constellation == system }) 'A' else 'N'
        }.joinToString("")
        return NmeaSentence(
            fixTalker(fix, style),
            "GNS",
            listOf(time(fix)) + position(fix, style) + listOf(
                mode,
                two(used.size),
                hdop(used),
                one(fix.mslAltitudeMeters),
                one(fix.geoidSeparationMeters),
                "",
                "",
                "V",
            ),
        )
    }

    fun vtg(fix: NmeaFix, style: NmeaStyle): NmeaSentence {
        if (!fix.fixed) return NmeaSentence(NO_FIX_TALKER, "VTG", listOf("", "T", "", "M", "", "N", "", "K", "N"))
        return NmeaSentence(
            fixTalker(fix, style),
            "VTG",
            listOf(
                course(fix), "T", "", "M",
                one(fix.speedMetersPerSecond * KNOTS_PER_MPS), "N",
                one(fix.speedMetersPerSecond * KMH_PER_MPS), "K",
                "A",
            ),
        )
    }

    fun gll(fix: NmeaFix, style: NmeaStyle): NmeaSentence {
        if (!fix.fixed) return NmeaSentence(NO_FIX_TALKER, "GLL", empty(5) + listOf("V", "N"))
        return NmeaSentence(fixTalker(fix, style), "GLL", position(fix, style) + listOf(time(fix), "A", "A"))
    }

    fun zda(fix: NmeaFix, style: NmeaStyle): NmeaSentence {
        if (!fix.fixed) return NmeaSentence(NO_FIX_TALKER, "ZDA", empty(6))
        val utc = utc(fix)
        return NmeaSentence(
            fixTalker(fix, style),
            "ZDA",
            listOf(time(fix), two(utc.dayOfMonth), two(utc.monthValue), utc.year.toString(), "00", "00"),
        )
    }

    fun dtm(fix: NmeaFix, style: NmeaStyle): NmeaSentence {
        if (!fix.fixed) return NmeaSentence(NO_FIX_TALKER, "DTM", empty(8))
        return NmeaSentence(fixTalker(fix, style), "DTM", style.dtmFields)
    }

    /** One entry per satellite used in the fix (secondary bands dropped). */
    private fun usedSatellites(fix: NmeaFix): List<SkySatellite> =
        if (!fix.fixed) emptyList() else fix.sky.filter { it.usedInFix }.distinctBy { it.constellation to it.svid }

    /** `GN` when the used satellites span several systems, else that system's talker (`GP` when none). */
    private fun fixTalker(fix: NmeaFix, style: NmeaStyle): String {
        val systems = usedSatellites(fix).map { it.constellation }.distinct()
        return when {
            !fix.fixed -> NO_FIX_TALKER
            systems.size > 1 -> MULTI_SYSTEM_TALKER
            systems.size == 1 -> style.talker(systems.single())
            else -> NO_FIX_TALKER
        }
    }

    private fun nmeaSvid(satellite: SkySatellite): Int =
        if (satellite.constellation == Constellation.GLONASS) satellite.svid + GLONASS_NMEA_OFFSET else satellite.svid

    private fun hdop(used: List<SkySatellite>): String = Dop.of(used)?.let { dopValue(it.hdop) } ?: ""

    /** NMEA DOP fields hold at most [MAX_DOP]; nearly degenerate geometries are capped. */
    private fun dopValue(value: Double): String = one(value.coerceAtMost(MAX_DOP))

    private fun course(fix: NmeaFix): String {
        val course = fix.courseDegrees ?: return ""
        if (fix.speedMetersPerSecond < MIN_COURSE_SPEED_MPS) return ""
        return one(course.mod(360f).toDouble())
    }

    private fun position(fix: NmeaFix, style: NmeaStyle): List<String> = listOf(
        coordinate(fix.latitude, 2, style.minuteDecimals),
        if (fix.latitude >= 0) "N" else "S",
        coordinate(fix.longitude, 3, style.minuteDecimals),
        if (fix.longitude >= 0) "E" else "W",
    )

    /** `d...dmm.mmm`: whole degrees padded to [degreeDigits], minutes with [decimals] decimals, rounded once. */
    private fun coordinate(degrees: Double, degreeDigits: Int, decimals: Int): String {
        var scale = 1L
        repeat(decimals) { scale *= 10 }
        val total = (abs(degrees) * 60.0 * scale).roundToLong()
        val whole = total / (60 * scale)
        val minutes = total % (60 * scale)
        return String.format(Locale.US, "%0${degreeDigits}d%02d.%0${decimals}d", whole, minutes / scale, minutes % scale)
    }

    private fun utc(fix: NmeaFix): OffsetDateTime =
        Instant.ofEpochMilli(Math.floorDiv(fix.utcMillis + 500, 1000L) * 1000L).atOffset(ZoneOffset.UTC)

    private fun time(fix: NmeaFix): String = utc(fix).let {
        String.format(Locale.US, "%02d%02d%02d.00", it.hour, it.minute, it.second)
    }

    private fun date(fix: NmeaFix): String = utc(fix).let {
        String.format(Locale.US, "%02d%02d%02d", it.dayOfMonth, it.monthValue, it.year % 100)
    }

    private fun one(value: Double): String =
        String.format(Locale.US, "%.1f", value).let { if (it == "-0.0") "0.0" else it }

    private fun two(value: Int): String = String.format(Locale.US, "%02d", value)

    private fun three(value: Int): String = String.format(Locale.US, "%03d", value)

    private fun empty(count: Int): List<String> = List(count) { "" }
}
