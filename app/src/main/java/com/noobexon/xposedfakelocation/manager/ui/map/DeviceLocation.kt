package com.noobexon.xposedfakelocation.manager.ui.map

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.location.Location
import android.location.LocationManager
import androidx.core.content.ContextCompat
import com.noobexon.xposedfakelocation.manager.geo.LatLon
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/** Whether fine or coarse location permission is granted. */
internal fun hasLocationPermission(context: Context): Boolean =
    ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED ||
        ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_COARSE_LOCATION) == PackageManager.PERMISSION_GRANTED

/**
 * The freshest last-known fix from any enabled provider, or `null` without permission or a fix.
 * Runs on [Dispatchers.IO] because [LocationManager.getLastKnownLocation] can hit the disk.
 */
internal suspend fun getLastKnownDeviceLocation(context: Context): LatLon? = withContext(Dispatchers.IO) {
    if (!hasLocationPermission(context)) return@withContext null
    val lm = context.getSystemService(Context.LOCATION_SERVICE) as? LocationManager
        ?: return@withContext null
    val providers = try {
        lm.getProviders(true)
    } catch (e: SecurityException) {
        return@withContext null
    }
    var best: Location? = null
    for (provider in providers) {
        val loc = try {
            lm.getLastKnownLocation(provider)
        } catch (e: SecurityException) {
            null
        } ?: continue
        if (best == null || loc.time > best.time) best = loc
    }
    best?.let { LatLon(it.latitude, it.longitude) }
}
