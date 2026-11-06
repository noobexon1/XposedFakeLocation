// SystemServicesHooks.kt
package com.noobexon.xposedfakelocation.xposed.hooks

import android.location.Location
import android.location.LocationManager
import android.net.wifi.WifiInfo
import android.os.Build
import android.telephony.CellInfo
import android.util.Log
import com.noobexon.xposedfakelocation.xposed.utils.LocationUtil
import com.noobexon.xposedfakelocation.xposed.utils.PreferencesUtil
import dalvik.system.PathClassLoader
import io.github.libxposed.api.XposedInterface
import io.github.libxposed.api.XposedInterface.Chain
import io.github.libxposed.api.XposedInterface.Hooker
import java.lang.reflect.Field
import java.lang.reflect.Method
import java.lang.reflect.Modifier

class SystemServicesHooks(
    private val module: XposedInterface,
    private val classLoader: ClassLoader
) {
    private val tag = "[SystemServicesHooks]"

    fun init() {
        hookLastLocation(classLoader)
        hookCurrentLocation(classLoader)
        hookLocationDispatch(classLoader)
        hookMiuiLocationServices(classLoader)
        hookWifiServices(classLoader)
        hookGnssRegistration(classLoader)
        hookGeofence(classLoader)
        module.log(Log.INFO, tag, "Instantiated hooks successfully")
    }

    private fun hookLastLocation(classLoader: ClassLoader) {
        val serviceClass = findClass(
            classLoader,
            "com.android.server.location.LocationManagerService",
            "com.android.server.LocationManagerService"
        ) ?: return

        hookAll(serviceClass, "getLastLocation") { chain ->
            val result = chain.proceed()
            if (shouldSpoofArgs(chain.args)) {
                val original = result as? Location
                module.log(Log.INFO, tag, "Replaced getLastLocation result.")
                LocationUtil.createFakeLocation(original)
            } else {
                result
            }
        }
    }

    private fun hookCurrentLocation(classLoader: ClassLoader) {
        val serviceClass = findClass(
            classLoader,
            "com.android.server.location.LocationManagerService",
            "com.android.server.LocationManagerService"
        ) ?: return

        hookAll(serviceClass, "getCurrentLocation") { chain ->
            if (shouldSpoofArgs(chain.args)) {
                module.log(Log.INFO, tag, "Blocked getCurrentLocation request for spoofed target.")
                defaultReturnValue(chain.executable as? Method)
            } else {
                chain.proceed()
            }
        }
    }

    private fun hookLocationDispatch(classLoader: ClassLoader) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            hookRegistrationDelivery(classLoader)
        }
        hookReceiverCallbacks(classLoader)
    }

    private fun hookRegistrationDelivery(classLoader: ClassLoader) {
        val providerClass = findClass(
            classLoader,
            "com.android.server.location.provider.LocationProviderManager"
        ) ?: return

        val registrations = providerClass.declaredClasses.filter { clazz ->
            clazz.declaredMethods.any {
                it.name == "acceptLocationChange" && !Modifier.isAbstract(it.modifiers)
            }
        }
        if (registrations.isEmpty()) {
            module.log(Log.WARN, tag, "No acceptLocationChange override found on ${providerClass.name}")
            return
        }

        registrations.forEach { clazz -> hookAll(clazz, "acceptLocationChange") { chain ->
            interceptAcceptLocationChange(chain)
        } }
    }

    private fun interceptAcceptLocationChange(chain: Chain): Any? {
        if (PreferencesUtil.getIsPlaying() != true) return chain.proceed()
        // This runs while the framework holds mMultiplexerLock, so attribute with a single identity
        // lookup instead of walking the registration's object graph.
        val spoofedPackage = registrationPackageName(chain.thisObject)
            ?.takeIf { shouldSpoofPackage(it) }
            ?: return chain.proceed()

        val spoofedResult = createFakeLocationResult(chain.args.firstOrNull())
            ?: return chain.proceed()
        module.log(Log.INFO, tag, "Replaced provider location for $spoofedPackage.")
        return chain.proceed(arrayOf<Any?>(spoofedResult))
    }

    private fun registrationPackageName(registration: Any): String? = runCatching {
        val identity = findMethod(registration.javaClass, "getIdentity")?.invoke(registration)
        identity?.let { findMethod(it.javaClass, "getPackageName")?.invoke(it) } as? String
    }.getOrNull()

    // The reported LocationResult instance is shared by every registration of the provider, so the
    // spoofed copy is built from scratch instead of being written into place.
    private fun createFakeLocationResult(locationResult: Any?): Any? {
        val originalLocations = locationResult?.let { result ->
            findField(result.javaClass, "mLocations")?.get(result)
        } as? List<*> ?: return null
        val original = originalLocations.firstOrNull() as? Location ?: return null

        return runCatching {
            val builder = findMethod(locationResult.javaClass, "builder")?.invoke(null) ?: return null
            val populated = findMethod(builder.javaClass, "addLocation", Location::class.java)
                ?.invoke(builder, LocationUtil.createFakeLocation(original)) ?: return null
            findMethod(populated.javaClass, "build")?.invoke(populated)
        }.onFailure {
            module.log(Log.ERROR, tag, "Could not build spoofed location result: ${it.message}")
        }.getOrNull()
    }

    private fun hookMiuiLocationServices(classLoader: ClassLoader) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.S) return
        if (!isXiaomiFamilyDevice()) {
            module.log(Log.INFO, tag, "Skipping MIUI location hooks on non-Xiaomi device.")
            return
        }

        val miuiClass = findClass(
            classLoader,
            "com.android.server.location.MiuiBlurLocationManagerImpl",
            "com.android.server.location.MiuiBlurLocationManager"
        ) ?: return

        hookAll(miuiClass, "getBlurryLocation") { chain ->
            val result = chain.proceed()
            if (shouldSpoofArgs(chain.args)) {
                module.log(Log.INFO, tag, "Replaced MIUI blurry location result.")
                replaceLocationLikeResult(result, chain.executable as? Method)
            } else {
                result
            }
        }

        hookAll(miuiClass, "getBlurryCellLocation") { chain ->
            val result = chain.proceed()
            if (shouldSpoofArgs(chain.args)) {
                module.log(Log.INFO, tag, "Cleared MIUI blurry cell location result.")
                null
            } else {
                result
            }
        }

        hookAll(miuiClass, "getBlurryCellInfos") { chain ->
            val result = chain.proceed()
            if (shouldSpoofArgs(chain.args)) {
                module.log(Log.INFO, tag, "Cleared MIUI blurry cell info result.")
                emptyList<CellInfo>()
            } else {
                result
            }
        }

        hookAll(miuiClass, "handleGpsLocationChangedLocked") { chain ->
            if (shouldSpoofArgs(chain.args)) {
                module.log(Log.INFO, tag, "Blocked MIUI GPS location refresh while spoofing.")
                defaultReturnValue(chain.executable as? Method)
            } else {
                chain.proceed()
            }
        }
    }

    private fun hookReceiverCallbacks(classLoader: ClassLoader) {
        val receiverClass = findClass(
            classLoader,
            "com.android.server.location.LocationManagerService\$Receiver",
            "com.android.server.LocationManagerService\$Receiver"
        ) ?: return

        hookAll(receiverClass, "callLocationChangedLocked") { chain ->
            interceptCallLocationChanged(chain)
        }
    }

    private fun interceptCallLocationChanged(chain: Chain): Any? {
        if (PreferencesUtil.getIsPlaying() != true) return chain.proceed()
        // The Receiver itself carries the caller package, so attribute by inspecting `thisObject`.
        if (collectPackageNames(chain.thisObject).none { shouldSpoofPackage(it) }) return chain.proceed()

        val args = chain.args
        val locationArgIndex = args.indexOfFirst { it is Location }
        if (locationArgIndex == -1) return chain.proceed()

        val original = args[locationArgIndex] as? Location
        val newArgs = args.toTypedArray()
        newArgs[locationArgIndex] = LocationUtil.createFakeLocation(original)
        module.log(Log.INFO, tag, "Replaced Receiver.callLocationChangedLocked argument.")
        return chain.proceed(newArgs)
    }

    // Name-based scope attribution for the system-level hooks: a package is spoofed only when it is
    // one of the manager-selected target apps (mirrored into the remote `target_apps` preference).
    private fun shouldSpoofPackage(packageName: String?): Boolean {
        if (packageName.isNullOrBlank()) return false
        return PreferencesUtil.getTargetApps().contains(packageName)
    }

    /** Attributes the Binder call to a selected target package after the shared gate passes. */
    private fun shouldSpoofWifiArgs(
        args: List<Any?>?,
        targetApps: Set<String>
    ): Boolean = WifiIdentityHookPolicy.targetsSystemWifiCaller(args, targetApps)

    private fun hookGnssRegistration(classLoader: ClassLoader) {
        val serviceClasses = listOfNotNull(
            findClass(classLoader, "com.android.server.location.gnss.GnssManagerService"),
            findClass(
                classLoader,
                "com.android.server.location.LocationManagerService",
                "com.android.server.LocationManagerService"
            )
        ).distinct()

        val methodsToBlock = listOf(
            "addGnssBatchingCallback",
            "addGnssMeasurementsListener",
            "addGnssNavigationMessageListener",
            "addGnssAntennaInfoListener",
            "registerGnssStatusCallback",
            "registerGnssNmeaCallback"
        )

        serviceClasses.forEach { serviceClass ->
            methodsToBlock.forEach { methodName ->
                hookAll(serviceClass, methodName) { chain ->
                    if (shouldSpoofArgs(chain.args)) {
                        module.log(Log.INFO, tag, "Blocked $methodName while spoofing is enabled.")
                        defaultReturnValue(chain.executable as? Method)
                    } else {
                        chain.proceed()
                    }
                }
            }
        }
    }

    private fun hookWifiServices(classLoader: ClassLoader) {
        val systemServiceManagerClass = findClass(
            classLoader,
            "com.android.server.SystemServiceManager"
        ) ?: return

        hookAll(systemServiceManagerClass, "loadClassFromLoader") { chain ->
            val result = chain.proceed()
            val serviceName = chain.args.getOrNull(0) as? String
            if (serviceName == "com.android.server.wifi.WifiService") {
                val serviceClassLoader = chain.args.getOrNull(1) as? PathClassLoader
                if (serviceClassLoader != null) {
                    val wifiServiceClass = findClass(
                        serviceClassLoader,
                        "com.android.server.wifi.WifiServiceImpl"
                    )
                    if (wifiServiceClass != null) {
                        hookWifiServiceImpl(wifiServiceClass)
                    }
                }
            }
            result
        }
    }

    private fun hookWifiServiceImpl(wifiServiceClass: Class<*>) {
        hookAll(wifiServiceClass, "getScanResults") { chain ->
            val result = chain.proceed()
            val identity = WifiIdentityHookPolicy.readActiveIdentity(module)
            if (identity != null && shouldSpoofWifiArgs(chain.args, identity.targetApps)) {
                module.log(Log.INFO, tag, "Cleared Wi-Fi scan results while spoofing.")
                emptyList<Any>()
            } else {
                result
            }
        }

        hookAll(wifiServiceClass, "getConnectionInfo") { chain ->
            val result = chain.proceed()
            val identity = WifiIdentityHookPolicy.readActiveIdentity(module)
            if (identity != null && shouldSpoofWifiArgs(chain.args, identity.targetApps)) {
                module.log(Log.INFO, tag, "Replaced Wi-Fi connection info while spoofing.")
                createFakeWifiInfo(identity)
            } else {
                result
            }
        }
    }

    private fun createFakeWifiInfo(identity: WifiIdentity): WifiInfo =
        WifiInfo.Builder()
            .setBssid(identity.bssid)
            .setSsid(identity.ssid.toByteArray())
            .setRssi(identity.rssi)
            .setNetworkId(0)
            .build()

    private fun hookGeofence(classLoader: ClassLoader) {
        val serviceClass = findClass(
            classLoader,
            "com.android.server.location.LocationManagerService",
            "com.android.server.LocationManagerService"
        ) ?: return

        hookAll(serviceClass, "requestGeofence") { chain ->
            if (shouldSpoofArgs(chain.args)) {
                module.log(Log.INFO, tag, "Blocked geofence registration while spoofing is enabled.")
                defaultReturnValue(chain.executable as? Method)
            } else {
                chain.proceed()
            }
        }
    }

    private fun isXiaomiFamilyDevice(): Boolean {
        val markers = listOf("xiaomi", "redmi", "poco")
        val buildInfo = listOf(
            Build.MANUFACTURER.orEmpty(),
            Build.BRAND.orEmpty(),
            Build.PRODUCT.orEmpty(),
            Build.DEVICE.orEmpty()
        )
        return buildInfo.any { info ->
            val lower = info.lowercase()
            markers.any(lower::contains)
        }
    }

    private fun hookAll(clazz: Class<*>, methodName: String, hooker: Hooker) {
        val methods = clazz.declaredMethods.filter { it.name == methodName }
        if (methods.isEmpty()) {
            module.log(Log.WARN, tag, "No method named $methodName on ${clazz.name}")
            return
        }

        var hooked = 0
        methods.forEach { method ->
            try {
                module.hook(method).intercept(hooker)
                hooked++
            } catch (e: Throwable) {
                module.log(Log.ERROR, tag, "Failed hooking ${clazz.name}#$methodName: ${e.message}")
            }
        }

        if (hooked > 0) {
            module.log(Log.INFO, tag, "Hooked ${clazz.name}#$methodName ($hooked overloads).")
        }
    }

    private fun findClass(classLoader: ClassLoader, vararg names: String): Class<*>? {
        names.forEach { name ->
            try {
                return Class.forName(name, false, classLoader)
            } catch (_: Throwable) {
                // Try the next framework class name. AOSP moved these across releases.
            }
        }
        module.log(Log.WARN, tag, "None of these classes were found: ${names.joinToString()}")
        return null
    }

    private fun findField(clazz: Class<*>, fieldName: String): Field? {
        var currentClass: Class<*>? = clazz
        while (currentClass != null) {
            try {
                return currentClass.getDeclaredField(fieldName).apply { isAccessible = true }
            } catch (_: NoSuchFieldException) {
                currentClass = currentClass.superclass
            }
        }
        return null
    }

    // Name-based attribution for pull/query style calls: only spoof while playing and when a target
    // package can be recovered from the call arguments (caller identity, work source, request, etc.).
    private fun shouldSpoofArgs(args: List<Any?>?): Boolean {
        if (PreferencesUtil.getIsPlaying() != true) return false
        return args?.asSequence()
            ?.flatMap { collectPackageNames(it).asSequence() }
            ?.distinct()
            ?.any { shouldSpoofPackage(it) } == true
    }

    private fun collectPackageNames(value: Any?): Set<String> {
        return collectPackageNames(value, mutableSetOf(), 0)
    }

    private fun collectPackageNames(value: Any?, visited: MutableSet<Int>, depth: Int): Set<String> {
        if (value == null || depth > 5) return emptySet()
        if (value is String) return setOfNotNull(value.takeIf(::looksLikePackageName))

        val identity = System.identityHashCode(value)
        if (!visited.add(identity)) return emptySet()

        val packageNames = linkedSetOf<String>()

        if (value is Iterable<*>) {
            value.forEach { packageNames += collectPackageNames(it, visited, depth + 1) }
            return packageNames
        }

        if (value is Map<*, *>) {
            value.forEach { (key, mapValue) ->
                packageNames += collectPackageNames(key, visited, depth + 1)
                packageNames += collectPackageNames(mapValue, visited, depth + 1)
            }
            return packageNames
        }

        packageNames += collectWorkSourcePackageNames(value)

        listOf(
            "mPackageName",
            "packageName",
            "callingPackage",
            "mCallingPackage",
            "mCallerPackageName",
            "callerPackageName",
            "mOpPackageName",
            "opPackageName"
        ).forEach { fieldName ->
            val packageName = findField(value.javaClass, fieldName)?.get(value) as? String
            packageName?.takeIf(::looksLikePackageName)?.let(packageNames::add)
        }

        listOf(
            "getPackageName",
            "getCallingPackage",
            "getCallerPackageName",
            "getOpPackageName"
        ).forEach { methodName ->
            val packageName = runCatching {
                findMethod(value.javaClass, methodName)?.invoke(value) as? String
            }.getOrNull()
            packageName?.takeIf(::looksLikePackageName)?.let(packageNames::add)
        }

        listOf(
            "mIdentity",
            "mCallerIdentity",
            "callerIdentity",
            "identity",
            "mCallingIdentity",
            "callingIdentity",
            "mAttributionSource",
            "attributionSource",
            "mNext",
            "next",
            "mWorkSource",
            "workSource",
            "mRequest",
            "request",
            "mLocationRequest",
            "locationRequest"
        ).forEach { fieldName ->
            packageNames += collectPackageNames(findField(value.javaClass, fieldName)?.get(value), visited, depth + 1)
        }

        listOf("getAttributionSource", "getNext", "getWorkSource", "getLocationRequest").forEach { methodName ->
            val nestedValue = runCatching {
                findMethod(value.javaClass, methodName)?.invoke(value)
            }.getOrNull()
            packageNames += collectPackageNames(nestedValue, visited, depth + 1)
        }

        return packageNames
    }

    private fun collectWorkSourcePackageNames(value: Any): Set<String> {
        if (value.javaClass.name != "android.os.WorkSource") return emptySet()

        val packageNames = linkedSetOf<String>()
        val size = runCatching {
            findMethod(value.javaClass, "size")?.invoke(value) as? Int
        }.getOrNull() ?: return emptySet()

        repeat(size) { index ->
            val name = runCatching {
                findMethod(value.javaClass, "getName", Integer.TYPE)?.invoke(value, index) as? String
            }.getOrNull()
            name?.takeIf(::looksLikePackageName)?.let(packageNames::add)
        }

        return packageNames
    }

    private fun findMethod(clazz: Class<*>, methodName: String, vararg parameterTypes: Class<*>): Method? {
        var currentClass: Class<*>? = clazz
        while (currentClass != null) {
            try {
                return currentClass.getDeclaredMethod(methodName, *parameterTypes).apply { isAccessible = true }
            } catch (_: NoSuchMethodException) {
                currentClass = currentClass.superclass
            }
        }

        return clazz.methods.firstOrNull {
            it.name == methodName && it.parameterTypes.contentEquals(parameterTypes)
        }?.apply { isAccessible = true }
    }

    private fun looksLikePackageName(value: String?): Boolean {
        return value != null && "." in value && !value.startsWith("android.location.")
    }

    private fun replaceLocationLikeResult(result: Any?, method: Method?): Any? {
        if (result is Location) {
            return LocationUtil.createFakeLocation(result)
        }

        if (result != null) {
            val locationsField = findField(result.javaClass, "mLocations")
            val originalLocations = locationsField?.get(result) as? List<*>
            val original = originalLocations?.firstOrNull() as? Location
            if (locationsField != null) {
                locationsField.set(result, arrayListOf(LocationUtil.createFakeLocation(original)))
                return result
            }

            if (result is List<*>) {
                val original = result.firstOrNull() as? Location
                return listOf(LocationUtil.createFakeLocation(original))
            }

            runCatching {
                val sizeMethod = result.javaClass.methods.firstOrNull { it.name == "size" && it.parameterTypes.isEmpty() }
                val getMethod = result.javaClass.methods.firstOrNull { it.name == "get" && it.parameterTypes.size == 1 }
                val size = sizeMethod?.invoke(result) as? Int ?: return@runCatching
                if (size > 0) {
                    val originalLocation = getMethod?.invoke(result, 0) as? Location ?: return@runCatching
                    val fakeLocation = LocationUtil.createFakeLocation(originalLocation)
                    originalLocation.latitude = fakeLocation.latitude
                    originalLocation.longitude = fakeLocation.longitude
                    originalLocation.altitude = fakeLocation.altitude
                    originalLocation.accuracy = fakeLocation.accuracy
                    originalLocation.speed = fakeLocation.speed
                }
            }.onFailure {
                module.log(Log.ERROR, tag, "Could not inspect MIUI location container: ${it.message}")
            }

            return result
        }

        return if (method?.returnType?.let { Location::class.java.isAssignableFrom(it) } == true) {
            LocationUtil.createFakeLocation(provider = LocationManager.FUSED_PROVIDER)
        } else {
            null
        }
    }

    private fun defaultReturnValue(method: Method?): Any? {
        return when (method?.returnType) {
            java.lang.Boolean.TYPE -> false
            java.lang.Integer.TYPE -> 0
            java.lang.Long.TYPE -> 0L
            java.lang.Float.TYPE -> 0F
            java.lang.Double.TYPE -> 0.0
            else -> null
        }
    }

}
