package com.noobexon.xposedfakelocation.xposed

import android.app.Application
import android.util.Log
import android.widget.Toast
import com.noobexon.xposedfakelocation.data.REMOTE_PREFS_GROUP
import com.noobexon.xposedfakelocation.xposed.hooks.LocationApiHooks
import com.noobexon.xposedfakelocation.xposed.hooks.PhoneServicesHooks
import com.noobexon.xposedfakelocation.xposed.hooks.SystemServicesHooks
import com.noobexon.xposedfakelocation.xposed.utils.LocationUtil
import com.noobexon.xposedfakelocation.xposed.utils.PreferencesUtil
import com.noobexon.xposedfakelocation.xposed.utils.RoutePlayer
import io.github.libxposed.api.XposedModule
import io.github.libxposed.api.XposedModuleInterface.ModuleLoadedParam
import io.github.libxposed.api.XposedModuleInterface.PackageLoadedParam
import io.github.libxposed.api.XposedModuleInterface.PackageReadyParam
import io.github.libxposed.api.XposedModuleInterface.SystemServerStartingParam

class ModuleEntry : XposedModule() {
    companion object {
        const val TAG = "[ModuleEntry]"
        private const val PHONE_PACKAGE = "com.android.phone"
    }

    private var locationApiHooks: LocationApiHooks? = null
    private var systemServicesHooks: SystemServicesHooks? = null
    private var phoneServicesHooks: PhoneServicesHooks? = null

    override fun onModuleLoaded(param: ModuleLoadedParam) {
        log(Log.INFO, TAG, "onModuleLoaded: ${param.processName}")
        LocationUtil.logger = { priority, tag, message -> log(priority, tag, message) }
        PreferencesUtil.logger = { priority, tag, message -> log(priority, tag, message) }
        RoutePlayer.logger = { priority, tag, message -> log(priority, tag, message) }
    }

    override fun onPackageLoaded(param: PackageLoadedParam) {
        log(Log.INFO, TAG, "onPackageLoaded: ${param.packageName}")
        log(Log.INFO, TAG, "\tdefault classloder: ${param.defaultClassLoader}")
    }

    override fun onPackageReady(param: PackageReadyParam) {
        log(Log.INFO, TAG, "onPackageReady: ${param.packageName}")
        log(Log.INFO, TAG, "\tapp classloder: ${param.classLoader}")
        log(Log.INFO, TAG, "\tmodule apk path: ${moduleApplicationInfo.sourceDir}")

        // Run per-package setup only once.
        if (!param.isFirstPackage) return

        PreferencesUtil.init(getRemotePreferences(REMOTE_PREFS_GROUP))

        if (param.packageName == PHONE_PACKAGE) {
            // Telephony process: only the cell/Wi-Fi telephony spoofing belongs here. We deliberately
            // skip LocationApiHooks so we don't fake com.android.phone's own location requests.
            phoneServicesHooks = PhoneServicesHooks(this, param.classLoader).also { it.initHooks() }
        } else {
            initHookingLogic(param)
        }
    }

    override fun onSystemServerStarting(param: SystemServerStartingParam) {
        log(Log.INFO, TAG, "onSystemServerStarting:\n\t${param.classLoader}")

        // system_server is a hooked process only when the user enabled system-level hooks (which adds
        // "android" to the module scope). Per-intercept isPlaying + target_apps gating keeps these
        // inert until the user is actively spoofing a selected target.
        PreferencesUtil.init(getRemotePreferences(REMOTE_PREFS_GROUP))
        systemServicesHooks = SystemServicesHooks(this, param.classLoader).also { it.initHooks() }
    }

    private fun initHookingLogic(param: PackageReadyParam) {
        val clazz = Class.forName("android.app.Instrumentation", false, param.classLoader)
        val method = clazz.getDeclaredMethod("callApplicationOnCreate", Application::class.java)

        hook(method).intercept { chain ->
            val result = chain.proceed()

            try {
                val context = (chain.getArg(0) as Application).applicationContext
                log(Log.INFO, TAG, "Target App's context has been acquired (${param.packageName}).")
                if (PreferencesUtil.getHideFakeLocationToast() != true) {
                    Toast.makeText(context, "Fake Location Is Active!", Toast.LENGTH_SHORT).show()
                }
            } catch (e: Exception) {
                log(Log.ERROR, TAG, "Toast/context failed - ${e.message}")
            }

            locationApiHooks = LocationApiHooks(this, param.classLoader).also { it.initHooks() }
            result
        }
    }
}