package com.noobexon.xposedfakelocation.manager.joystick

import com.noobexon.xposedfakelocation.data.MPS_TO_KMH
import kotlin.math.roundToInt

/** Speed shown on the overlay's speedometer: whole km/h, like a car's digital display. */
fun displayKmh(speedMps: Float): Int = (speedMps * MPS_TO_KMH).roundToInt()
