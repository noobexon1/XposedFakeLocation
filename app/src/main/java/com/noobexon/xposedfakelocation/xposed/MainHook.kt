package com.noobexon.xposedfakelocation.xposed

import android.app.Application
import android.content.Context
import android.widget.Toast
import com.noobexon.xposedfakelocation.data.MANAGER_APP_PACKAGE_NAME
import com.noobexon.xposedfakelocation.xposed.location.LocationApiHooks
import de.robv.android.xposed.IXposedHookLoadPackage
import de.robv.android.xposed.XC_MethodHook
import de.robv.android.xposed.XposedBridge
import de.robv.android.xposed.XposedHelpers
import de.robv.android.xposed.callbacks.XC_LoadPackage.LoadPackageParam

class MainHook : IXposedHookLoadPackage {
    val tag = "[MainHook]"

    lateinit var context: Context
    var locationApiHooks: LocationApiHooks? = null

    override fun handleLoadPackage(lpparam: LoadPackageParam) {
        // Avoid hooking own app to prevent recursion
        if (lpparam.packageName == MANAGER_APP_PACKAGE_NAME) return

        // If not playing or null, do not proceed with hooking
        val isPlayingPref = UserPreferences.getIsPlaying()
        if (isPlayingPref?.isPlaying != true) return

        initHookingLogic(lpparam)
    }

    private fun initHookingLogic(lpparam: LoadPackageParam) {
        XposedHelpers.findAndHookMethod(
            "android.app.Instrumentation",
            lpparam.classLoader,
            "callApplicationOnCreate",
            Application::class.java,
            object : XC_MethodHook() {
                override fun afterHookedMethod(param: MethodHookParam) {
                    context = (param.args[0] as Application).applicationContext.also {
                        XposedBridge.log("$tag Target App's context has been acquired successfully.")
                        Toast.makeText(it, "Fake Location Is Active!", Toast.LENGTH_LONG).show()
                    }
                    locationApiHooks = LocationApiHooks(context, lpparam).also { it.initHooks() }
                }
            }
        )
    }
}