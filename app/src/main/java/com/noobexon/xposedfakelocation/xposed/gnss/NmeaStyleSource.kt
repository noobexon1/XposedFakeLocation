package com.noobexon.xposedfakelocation.xposed.gnss

import com.noobexon.xposedfakelocation.gnss.nmea.NmeaSentence
import com.noobexon.xposedfakelocation.gnss.nmea.NmeaStyle

/** The chip's NMEA style in this process, learned from the real sentences passing through the wrappers. */
internal object NmeaStyleSource {
    @Volatile var style: NmeaStyle = NmeaStyle.DEFAULT
        private set

    @Synchronized
    fun observe(line: String) {
        runCatching {
            val sentence = NmeaSentence.parse(line) ?: return
            style = style.observe(sentence)
        }
    }
}
