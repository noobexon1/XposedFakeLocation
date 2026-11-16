package com.noobexon.xposedfakelocation.xposed.hooks

import android.location.GnssMeasurementsEvent
import android.location.GnssNavigationMessage
import android.os.Build
import com.noobexon.xposedfakelocation.xposed.utils.PreferencesUtil

internal fun gnssSpoofingActive(): Boolean = PreferencesUtil.getIsPlaying() == true

/** Forwards to the app's measurements callback, hiding measurements while spoofing. */
internal class MeasurementsCallbackWrapper(
    private val app: GnssMeasurementsEvent.Callback,
) : GnssMeasurementsEvent.Callback() {
    override fun onGnssMeasurementsReceived(eventArgs: GnssMeasurementsEvent) {
        if (GnssHookPolicy.deliversRawData(gnssSpoofingActive())) app.onGnssMeasurementsReceived(eventArgs)
    }

    @Suppress("OVERRIDE_DEPRECATION", "DEPRECATION")
    override fun onStatusChanged(status: Int) {
        app.onStatusChanged(GnssHookPolicy.rawDataStatus(Build.VERSION.SDK_INT, gnssSpoofingActive(), status))
    }
}

/** Forwards to the app's navigation-message callback, hiding messages while spoofing. */
internal class NavigationMessageCallbackWrapper(
    private val app: GnssNavigationMessage.Callback,
) : GnssNavigationMessage.Callback() {
    override fun onGnssNavigationMessageReceived(event: GnssNavigationMessage) {
        if (GnssHookPolicy.deliversRawData(gnssSpoofingActive())) app.onGnssNavigationMessageReceived(event)
    }

    @Suppress("OVERRIDE_DEPRECATION", "DEPRECATION")
    override fun onStatusChanged(status: Int) {
        app.onStatusChanged(GnssHookPolicy.rawDataStatus(Build.VERSION.SDK_INT, gnssSpoofingActive(), status))
    }
}
