package com.noobexon.xposedfakelocation.xposed.hooks

import android.location.Location
import android.os.Build
import android.util.Log
import com.noobexon.xposedfakelocation.xposed.utils.LocationUtil
import com.noobexon.xposedfakelocation.xposed.utils.PreferencesUtil
import io.github.libxposed.api.XposedInterface

// TODO: in all hooks, we should check every 3 seconds if we are still in scope. isPlaying is not enough.

class LocationApiHooks(private val module: XposedInterface, private val classLoader: ClassLoader) {
    private val tag = "[LocationApiHooks]"

    fun initHooks() {
        hookLocation()
        hookLocationManager()
        module.log(Log.INFO, tag, "Instantiated hooks successfully")
    }

    private fun hookLocation() {
        try {
            val locationClass = Class.forName("android.location.Location", false, classLoader)

            module.hook(locationClass.getDeclaredMethod("getLatitude")).intercept { chain ->
                val original = chain.proceed()
                LocationUtil.updateLocation()
                module.log(Log.INFO, tag, "Leaving method getLatitude()")
                module.log(Log.INFO, tag, "\t Original latitude: $original")
                if (PreferencesUtil.getIsPlaying() == true) {
                    module.log(Log.INFO, tag, "\t Modified to: ${LocationUtil.latitude}")
                    LocationUtil.latitude
                } else {
                    original
                }
            }

            module.hook(locationClass.getDeclaredMethod("getLongitude")).intercept { chain ->
                val original = chain.proceed()
                LocationUtil.updateLocation()
                module.log(Log.INFO, tag, "Leaving method getLongitude()")
                module.log(Log.INFO, tag, "\t Original longitude: $original")
                if (PreferencesUtil.getIsPlaying() == true) {
                    module.log(Log.INFO, tag, "\t Modified to: ${LocationUtil.longitude}")
                    LocationUtil.longitude
                } else {
                    original
                }
            }

            module.hook(locationClass.getDeclaredMethod("getAccuracy")).intercept { chain ->
                val original = chain.proceed()
                LocationUtil.updateLocation()
                module.log(Log.INFO, tag, "Leaving method getAccuracy()")
                module.log(Log.INFO, tag, "\t Original accuracy: $original")
                if (PreferencesUtil.getIsPlaying() == true && PreferencesUtil.getUseAccuracy() == true) {
                    module.log(Log.INFO, tag, "\t Modified to: ${LocationUtil.accuracy}")
                    LocationUtil.accuracy
                } else {
                    original
                }
            }

            module.hook(locationClass.getDeclaredMethod("getAltitude")).intercept { chain ->
                val original = chain.proceed()
                LocationUtil.updateLocation()
                module.log(Log.INFO, tag, "Leaving method getAltitude()")
                module.log(Log.INFO, tag, "\t Original altitude: $original")
                if (PreferencesUtil.getIsPlaying() == true && PreferencesUtil.getUseAltitude() == true) {
                    module.log(Log.INFO, tag, "\t Modified to: ${LocationUtil.altitude}")
                    LocationUtil.altitude
                } else {
                    original
                }
            }

            module.hook(locationClass.getDeclaredMethod("getVerticalAccuracyMeters")).intercept { chain ->
                val original = chain.proceed()
                LocationUtil.updateLocation()
                module.log(Log.INFO, tag, "Leaving method getVerticalAccuracyMeters()")
                module.log(Log.INFO, tag, "\tOriginal vertical accuracy: $original")
                if (PreferencesUtil.getIsPlaying() == true && PreferencesUtil.getUseVerticalAccuracy() == true) {
                    module.log(Log.INFO, tag, "\tModified to: ${LocationUtil.verticalAccuracy}")
                    LocationUtil.verticalAccuracy
                } else {
                    original
                }
            }

            module.hook(locationClass.getDeclaredMethod("getSpeed")).intercept { chain ->
                val original = chain.proceed()
                LocationUtil.updateLocation()
                module.log(Log.INFO, tag, "Leaving method getSpeed()")
                module.log(Log.INFO, tag, "\tOriginal speed: $original")
                if (PreferencesUtil.getIsPlaying() == true && PreferencesUtil.getUseSpeed() == true) {
                    module.log(Log.INFO, tag, "\tModified to: ${LocationUtil.speed}")
                    LocationUtil.speed
                } else {
                    original
                }
            }

            module.hook(locationClass.getDeclaredMethod("getSpeedAccuracyMetersPerSecond")).intercept { chain ->
                val original = chain.proceed()
                LocationUtil.updateLocation()
                module.log(Log.INFO, tag, "Leaving method getSpeedAccuracyMetersPerSecond()")
                module.log(Log.INFO, tag, "\tOriginal speed accuracy: $original")
                if (PreferencesUtil.getIsPlaying() == true && PreferencesUtil.getUseSpeedAccuracy() == true) {
                    module.log(Log.INFO, tag, "\tModified to: ${LocationUtil.speedAccuracy}")
                    LocationUtil.speedAccuracy
                } else {
                    original
                }
            }

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
                module.hook(locationClass.getDeclaredMethod("getMslAltitudeMeters")).intercept { chain ->
                    val original = chain.proceed()
                    LocationUtil.updateLocation()
                    module.log(Log.INFO, tag, "Leaving method getMslAltitudeMeters()")
                    module.log(Log.INFO, tag, "\tOriginal MSL altitude: $original")
                    if (PreferencesUtil.getIsPlaying() == true && PreferencesUtil.getUseMeanSeaLevel() == true) {
                        module.log(Log.INFO, tag, "\tModified to: ${LocationUtil.meanSeaLevel}")
                        LocationUtil.meanSeaLevel
                    } else {
                        original
                    }
                }

                module.hook(locationClass.getDeclaredMethod("getMslAltitudeAccuracyMeters")).intercept { chain ->
                    val original = chain.proceed()
                    LocationUtil.updateLocation()
                    module.log(Log.INFO, tag, "Leaving method getMslAltitudeAccuracyMeters()")
                    module.log(Log.INFO, tag, "\tOriginal MSL altitude accuracy: $original")
                    if (PreferencesUtil.getIsPlaying() == true && PreferencesUtil.getUseMeanSeaLevelAccuracy() == true) {
                        module.log(Log.INFO, tag, "\tModified to: ${LocationUtil.meanSeaLevelAccuracy}")
                        LocationUtil.meanSeaLevelAccuracy
                    } else {
                        original
                    }
                }
            } else {
                module.log(Log.INFO, tag, "getMslAltitudeMeters() and getMslAltitudeAccuracyMeters() not available on this API level")
            }

        } catch (e: Exception) {
            module.log(Log.ERROR, tag, "Error hooking Location class - ${e.message}")
        }
    }

    private fun hookLocationManager() {
        try {
            val locationManagerClass = Class.forName("android.location.LocationManager", false, classLoader)
            val method = locationManagerClass.getDeclaredMethod("getLastKnownLocation", String::class.java)

            module.hook(method).intercept { chain ->
                val original = chain.proceed() as? Location
                module.log(Log.INFO, tag, "Leaving method getLastKnownLocation(provider)")
                module.log(Log.INFO, tag, "\t Original location: $original")
                val provider = chain.getArg(0) as String
                module.log(Log.INFO, tag, "\t Requested data from: $provider")
                if (PreferencesUtil.getIsPlaying() == true) {
                    val fakeLocation = LocationUtil.createFakeLocation(provider = provider)
                    module.log(Log.INFO, tag, "\t Modified location: $fakeLocation")
                    fakeLocation
                } else {
                    original
                }
            }

            hookRequestLocationUpdates(locationManagerClass)

        } catch (e: Exception) {
            module.log(Log.ERROR, tag, "Error hooking LocationManager - ${e.message}")
        }
    }

    private fun hookRequestLocationUpdates(locationManagerClass: Class<*>) {
        try {
            val listenerClass = Class.forName("android.location.LocationListener", false, classLoader)
            val method1 = locationManagerClass.getDeclaredMethod(
                "requestLocationUpdates", String::class.java, Long::class.java,
                Float::class.java, listenerClass
            )
            module.log(Log.INFO, tag, "Hooking requestLocationUpdates(String, long, float, LocationListener)")
            module.hook(method1).intercept { chain ->
                val minTime = chain.getArg(1) as Long
                val provider = chain.getArg(0) as String
                module.log(Log.INFO, tag, "requestLocationUpdates called: provider=$provider minTime=${minTime}ms")
                if (minTime > 1000) {
                    module.log(Log.INFO, tag, "Reducing minTime: ${minTime}ms -> 1000ms")
                    chain.proceed(arrayOf(
                        chain.getArg(0), 1000L, chain.getArg(2), chain.getArg(3)
                    ))
                } else {
                    chain.proceed()
                }
            }

            val methodWithLooper = try {
                locationManagerClass.getDeclaredMethod(
                    "requestLocationUpdates", String::class.java, Long::class.java,
                    Float::class.java, listenerClass, android.os.Looper::class.java
                )
            } catch (e: NoSuchMethodException) { null }
            if (methodWithLooper != null) {
                module.log(Log.INFO, tag, "Hooking requestLocationUpdates(String, long, float, LocationListener, Looper)")
                module.hook(methodWithLooper).intercept { chain ->
                    val minTime = chain.getArg(1) as Long
                    val provider = chain.getArg(0) as String
                    module.log(Log.INFO, tag, "requestLocationUpdates(looper): provider=$provider minTime=${minTime}ms")
                    if (minTime > 1000) {
                        module.log(Log.INFO, tag, "Reducing minTime(looper): ${minTime}ms -> 1000ms")
                        chain.proceed(arrayOf(
                            chain.getArg(0), 1000L, chain.getArg(2), chain.getArg(3), chain.getArg(4)
                        ))
                    } else {
                        chain.proceed()
                    }
                }
            }
        } catch (e: Exception) {
            module.log(Log.WARN, tag, "requestLocationUpdates hooks not available: ${e.message}")
        }
    }
}
