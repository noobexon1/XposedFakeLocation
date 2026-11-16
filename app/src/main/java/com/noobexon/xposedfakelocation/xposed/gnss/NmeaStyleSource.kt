package com.noobexon.xposedfakelocation.xposed.gnss

import com.noobexon.xposedfakelocation.gnss.nmea.NmeaSentence
import com.noobexon.xposedfakelocation.gnss.nmea.NmeaSignals
import com.noobexon.xposedfakelocation.gnss.nmea.NmeaStyle

/**
 * The chip's NMEA style in this process, learned from the real sentences passing through the wrappers. GSV
 * sentences also teach [GnssSkySource] the chip's systems and bands.
 */
internal object NmeaStyleSource {
    @Volatile var style: NmeaStyle = NmeaStyle.DEFAULT
        private set

    fun observe(line: String) {
        runCatching {
            val sentence = NmeaSentence.parse(line) ?: return
            synchronized(this) { style = style.observe(sentence) }
            NmeaSignals.observation(sentence)?.let(GnssSkySource::observeNmea)
        }
    }
}
