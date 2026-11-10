package com.noobexon.xposedfakelocation.xposed.hooks

/**
 * Pure decisions for presenting target apps with a device that has no raw GNSS measurements or
 * navigation messages while spoofing, so the real position can't be computed from them.
 */
internal object GnssHookPolicy {
    /** `GnssMeasurementsEvent.Callback` / `GnssNavigationMessage.Callback` status values. */
    const val STATUS_NOT_SUPPORTED = 0
    const val STATUS_READY = 1

    /** From Android 12 the platform always reports `STATUS_READY` once, whatever the hardware supports. */
    private const val FIRST_ALWAYS_READY_SDK = 31

    /** `GnssCapabilities` getters that report raw-data support. */
    val UNSUPPORTED_CAPABILITIES: Set<String> = setOf(
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
    )

    /** Whether a measurement or navigation-message event reaches the app. */
    fun deliversRawData(spoofing: Boolean): Boolean = !spoofing

    /** The status an app receives for [status], matching what an unsupported device reports on [sdkInt]. */
    fun rawDataStatus(sdkInt: Int, spoofing: Boolean, status: Int): Int =
        if (spoofing && sdkInt < FIRST_ALWAYS_READY_SDK && status == STATUS_READY) STATUS_NOT_SUPPORTED else status

    /** The value the `GnssCapabilities` getter [name] returns, given its [real] value. */
    fun capability(name: String, spoofing: Boolean, real: Boolean): Boolean =
        if (spoofing && name in UNSUPPORTED_CAPABILITIES) false else real
}
