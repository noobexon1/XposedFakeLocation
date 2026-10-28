package com.noobexon.xposedfakelocation.manager.route

import java.util.Locale
import kotlin.math.ceil
import kotlin.math.max

/** Number formatting for route UI; units and wording come from string resources. */
object RouteFormat {
    fun clock(seconds: Int): String = String.format(Locale.ROOT, "%d:%02d", seconds / 60, seconds % 60)

    fun km(meters: Double, locale: Locale = Locale.getDefault()): String =
        String.format(locale, "%.1f", meters / 1000.0)

    fun minutes(seconds: Double): Int = max(1, ceil(seconds / 60.0).toInt())

    fun multiplier(value: Float): String =
        if (value % 1f == 0f) value.toInt().toString() else value.toString()
}
