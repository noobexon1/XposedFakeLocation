package com.noobexon.xposedfakelocation.xposed

import android.app.Application
import android.util.Log
import android.widget.Toast
import com.noobexon.xposedfakelocation.data.REMOTE_PREFS_GROUP
import com.noobexon.xposedfakelocation.xposed.hooks.LocationApiHooks
import com.noobexon.xposedfakelocation.xposed.hooks.LocationManagerApiHooks
import com.noobexon.xposedfakelocation.xposed.hooks.PhoneServicesHooks
import com.noobexon.xposedfakelocation.xposed.hooks.SystemServicesHooks
import com.noobexon.xposedfakelocation.xposed.utils.LocationUtil
import com.noobexon.xposedfakelocation.xposed.utils.PreferencesUtil
import io.github.libxposed.api.XposedInterface
import io.github.libxposed.api.XposedModule
import io.github.libxposed.api.XposedModuleInterface.ModuleLoadedParam
import io.github.libxposed.api.XposedModuleInterface.PackageLoadedParam
import io.github.libxposed.api.XposedModuleInterface.PackageReadyParam
import io.github.libxposed.api.XposedModuleInterface.SystemServerStartingParam

private const val TAG = "[ModuleEntry]"

/**
 * Entry point for the Xposed module, loaded by LSPosed into every process in scope.
 *
 * Responsibilities:
 * - Wire loggers into [LocationUtil] and [PreferencesUtil] on module load.
 * - Initialize remote preferences and install the appropriate hook set for each process:
 *   - Regular target apps → [LocationApiHooks] + [LocationManagerApiHooks].
 *   - `com.android.phone` → [PhoneServicesHooks] only (telephony cell/Wi-Fi spoofing).
 *   - `android` (system_server, when opted in) → [SystemServicesHooks].
 */
class ModuleEntry() : XposedModule() {
    private var framework: XposedInterface = this

    constructor(module: XposedInterface, param: ModuleLoadedParam) : this() {
        framework = module
        onModuleLoaded(param)
    }

    
    /**
     * Called once when the module is first loaded into a process.
     * Wires the module logger into [LocationUtil] and [PreferencesUtil] so hook-side
     * log calls are routed through the libxposed logging channel.
     */
    override fun onModuleLoaded(param: ModuleLoadedParam) {
        framework.log(Log.INFO, TAG, "onModuleLoaded: ${param.processName}")
        initLoggers()
    }

    /**
     * Called when a package's dex is loaded but before [Application.onCreate].
     * Used to initialize remote preferences early so they are available by the time
     * hooks fire.
     */
    override fun onPackageLoaded(param: PackageLoadedParam) {
        framework.log(Log.INFO, TAG, "onPackageLoaded: ${param.packageName}")
        initRemotePreferences()
    }

    /**
     * Called when a package's [Application] is ready.
     * Installs the correct hook set for the process:
     * - `com.android.phone` gets [PhoneServicesHooks] only.
     * - All other scoped apps get [LocationApiHooks] + [LocationManagerApiHooks],
     *   plus an optional toast indicating that spoofing is active.
     *
     * Only the first package in the process is processed ([PackageReadyParam.isFirstPackage]).
     */
    override fun onPackageReady(param: PackageReadyParam) {
        framework.log(Log.INFO, TAG, "onPackageReady: ${param.packageName}")

        if (!param.isFirstPackage) return // Run per-package setup only once.

        if (param.packageName == "com.android.phone") {
            initPhoneServiceHooks(param.classLoader) 
        } else {
            initHooks(param.classLoader)

            val result = PreferencesUtil.getHideFakeLocationToast()
            if (result == null || !result) {
                showActiveToast(param)
            }
        }
    }

    /**
     * Called when system_server is starting.
     * Only fires when the user has added `android` to the module scope to enable
     * deep system-level location interception. Installs [SystemServicesHooks].
     */
    override fun onSystemServerStarting(param: SystemServerStartingParam) {
        framework.log(Log.INFO, TAG, "onSystemServerStarting: ${param.classLoader}")
        initRemotePreferences()
        initSystemHooks(param.classLoader)
    }

    /** Routes [LocationUtil] and [PreferencesUtil] log calls through the libxposed logger. */
    private fun initLoggers() {
        val logger: (Int, String, String) -> Unit = { priority, tag, message -> framework.log(priority, tag, message) }
        LocationUtil.logger = logger
        PreferencesUtil.logger = logger
    }

    /** Fetches and initializes the LSPosed remote [SharedPreferences] used by hook-side utils. */
    private fun initRemotePreferences() {
        PreferencesUtil.init(framework.getRemotePreferences(REMOTE_PREFS_GROUP))
    }

    /** Installs [LocationApiHooks] and [LocationManagerApiHooks] into the target app process. */
    private fun initHooks(classLoader: ClassLoader) {
        LocationApiHooks(framework, classLoader).init()
        LocationManagerApiHooks(framework, classLoader).init()
    }

    /** Installs [PhoneServicesHooks] into the `com.android.phone` process. */
    private fun initPhoneServiceHooks(classLoader: ClassLoader) {
        PhoneServicesHooks(framework, classLoader).init()
    }

    /** Installs [SystemServicesHooks] into the system_server process. */
    private fun initSystemHooks(classLoader: ClassLoader) {
        SystemServicesHooks(framework, classLoader).init()
    }

    /**
     * Hooks [android.app.Instrumentation.callApplicationOnCreate] to show a short toast
     * in the target app once its [Application] context is available.
     */
    private fun showActiveToast(param: PackageReadyParam) {
        val clazz = Class.forName("android.app.Instrumentation", false, param.classLoader)
        val method = clazz.getDeclaredMethod("callApplicationOnCreate", Application::class.java)
        framework.hook(method).intercept { chain ->
            val result = chain.proceed()
            runCatching {
                val context = (chain.getArg(0) as Application).applicationContext
                framework.log(Log.INFO, TAG, "Target App's context has been acquired (${param.packageName}).")
                Toast.makeText(context, "Fake Location Is Active!", Toast.LENGTH_SHORT).show()
            }.onFailure { framework.log(Log.ERROR, TAG, "Toast/context failed - ${it.message}") }
            result
        }
    }
}
