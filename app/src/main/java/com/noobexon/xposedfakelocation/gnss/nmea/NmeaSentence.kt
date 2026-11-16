package com.noobexon.xposedfakelocation.gnss.nmea

/**
 * One NMEA 0183 sentence: the talker (`GP`, `GN`, ..., or `P` for proprietary sentences), the sentence type
 * and the fields after the address.
 */
data class NmeaSentence(
    val talker: String,
    val type: String,
    val fields: List<String>,
) {
    /** The characters between `$` and `*`. */
    val body: String get() = (listOf(talker + type) + fields).joinToString(",")

    /** `$body*HH\r\n`, as the platform delivers sentences. */
    fun format(): String = "\$$body*${checksum(body)}\r\n"

    companion object {
        private const val CHECKSUM_LENGTH = 2
        private const val ADDRESS_LENGTH = 5

        /** XOR of the characters of [body], as two uppercase hex digits. */
        fun checksum(body: String): String = "%02X".format(body.fold(0) { acc, char -> acc xor char.code })

        /** The sentence in [line], or `null` unless it is a well-formed sentence with a valid checksum. */
        fun parse(line: String): NmeaSentence? {
            val trimmed = line.trimEnd('\r', '\n')
            if (!trimmed.startsWith('$')) return null
            val star = trimmed.lastIndexOf('*')
            if (star < 0 || star != trimmed.length - 1 - CHECKSUM_LENGTH) return null
            val body = trimmed.substring(1, star)
            if (!checksum(body).equals(trimmed.substring(star + 1), ignoreCase = true)) return null
            val parts = body.split(',')
            val address = parts.first()
            val (talker, type) = when {
                address.length > 1 && address[0] == 'P' -> "P" to address.substring(1)
                address.length == ADDRESS_LENGTH -> address.substring(0, 2) to address.substring(2)
                else -> return null
            }
            return NmeaSentence(talker, type, parts.drop(1))
        }
    }
}
