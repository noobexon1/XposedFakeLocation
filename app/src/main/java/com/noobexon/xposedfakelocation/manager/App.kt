package com.noobexon.xposedfakelocation.manager

import android.app.Application
import com.noobexon.xposedfakelocation.data.repository.PreferencesRepository
import com.noobexon.xposedfakelocation.manager.gnss.GnssOrbitRepository
import io.github.libxposed.service.XposedService
import io.github.libxposed.service.XposedServiceHelper
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.launch

class App : Application(), XposedServiceHelper.OnServiceListener {
    companion object {
        private val _serviceState = MutableStateFlow<XposedService?>(null)
        val serviceState: StateFlow<XposedService?> = _serviceState.asStateFlow()
        val service: XposedService? get() = _serviceState.value   // keep existing callers working
    }

    /** Lives as long as the process; only runs the orbit refresh below. */
    private val appScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private var orbitRefreshJob: Job? = null

    override fun onCreate() {
        super.onCreate()
        XposedServiceHelper.registerListener(this)   // exactly once
    }

    override fun onServiceBind(service: XposedService) {
        _serviceState.value = service
        startOrbitRefresh()
    }

    override fun onServiceDied(service: XposedService) {
        _serviceState.value = null
        orbitRefreshJob?.cancel()
    }

    /** Refreshes satellite orbits for the GNSS hooks now and each time spoofing starts. */
    private fun startOrbitRefresh() {
        orbitRefreshJob?.cancel()
        orbitRefreshJob = appScope.launch {
            val orbits = GnssOrbitRepository(this@App)
            orbits.refreshIfStale()
            PreferencesRepository(this@App).getIsPlayingFlow()
                .distinctUntilChanged()
                .collect { playing -> if (playing) orbits.refreshIfStale() }
        }
    }
}
