package com.noobexon.xposedfakelocation.manager.ui.map.maplibre

import android.app.Application
import android.util.Log
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.noobexon.xposedfakelocation.R
import com.noobexon.xposedfakelocation.data.model.OfflineAreaMetadata
import com.noobexon.xposedfakelocation.data.model.OfflineAreaMetadataCodec
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import org.maplibre.android.geometry.LatLngBounds
import org.maplibre.android.offline.OfflineRegion
import org.maplibre.android.offline.OfflineRegionError
import org.maplibre.android.offline.OfflineRegionStatus
import org.maplibre.android.offline.OfflineTilePyramidRegionDefinition
import java.util.UUID

/** The download started from the map, shown on its progress card. */
data class OfflineDownload(
    val areaId: String,
    val name: String,
    val percent: Int = 0,
    val waitingForNetwork: Boolean = false,
    /** The last download error other than a lost connection; cleared once progress resumes. */
    val problem: String? = null,
)

/** Stored offline areas and the running download. [available] is `false` when MapLibre can't run. */
data class OfflineAreasState(
    val available: Boolean = true,
    val loaded: Boolean = false,
    val areas: List<OfflineArea> = emptyList(),
    val download: OfflineDownload? = null,
)

/** One-shot results for a toast. */
sealed interface OfflineAreasEvent {
    data class Finished(val name: String) : OfflineAreasEvent
    data class Failed(val message: String) : OfflineAreasEvent
}

/**
 * Lists, downloads, resumes and deletes MapLibre offline areas. MapLibre is loaded on the first
 * call, so osmdroid users never load it. Downloads keep running while the app process lives.
 */
class OfflineAreasViewModel(application: Application) : AndroidViewModel(application) {
    private val store: OfflineAreasStore? by lazy {
        if (MapLibreSupport.ensure(application)) OfflineAreasStore(application) else null
    }

    private val _state = MutableStateFlow(OfflineAreasState())
    val state: StateFlow<OfflineAreasState> = _state.asStateFlow()

    private val _events = Channel<OfflineAreasEvent>(Channel.BUFFERED)
    val events: Flow<OfflineAreasEvent> = _events.receiveAsFlow()

    /** Known regions by id. Regions this view model downloads keep the object it observes. */
    private val regions = mutableMapOf<Long, OfflineRegion>()
    private val downloading = mutableMapOf<Long, OfflineRegion>()
    private val stored = mutableMapOf<Long, StoredRegion>()
    private var creatingAreaId: String? = null
    private val reloadLock = Mutex()

    /** Reloads the stored areas. Skipped while a reload is already running. */
    fun refresh() {
        val store = store
        if (store == null) {
            _state.update { it.copy(available = false, loaded = true) }
            return
        }
        if (reloadLock.isLocked) return
        viewModelScope.launch { reporting { reload(store) } }
    }

    /**
     * Downloads [area] for each of [styles] as regions sharing one area id, from the current zoom
     * up to the offline maximum. Ignored while another download from here is running.
     */
    fun startDownload(name: String, area: VisibleArea, styles: List<OfflineStyle>, pixelRatio: Float) {
        val store = store ?: return
        if (_state.value.download != null) return
        val areaId = UUID.randomUUID().toString()
        val createdAt = System.currentTimeMillis()
        val zoom = offlineZoomRange(area.zoom)
        _state.update { it.copy(download = OfflineDownload(areaId, name)) }
        creatingAreaId = areaId
        viewModelScope.launch {
            try {
                val b = area.bounds
                val bounds = LatLngBounds.from(b.north, b.east, b.south, b.west)
                for (style in styles) {
                    val definition = OfflineTilePyramidRegionDefinition(style.url, bounds, zoom.first.toDouble(), zoom.last.toDouble(), pixelRatio)
                    val metadata = OfflineAreaMetadataCodec.encode(OfflineAreaMetadata(areaId, name, createdAt, style.kind, style.url))
                    val region = store.create(definition, metadata)
                    regions[region.id] = region
                    if (_state.value.download?.areaId != areaId) {
                        // Cancelled while the regions were being created.
                        regions.remove(region.id)
                        store.delete(region)
                        return@launch
                    }
                    track(region)
                }
            } catch (e: OfflineAreaException) {
                fail(areaId, e.message)
                return@launch
            } catch (e: IllegalArgumentException) {
                fail(areaId, e.message)
                return@launch
            } finally {
                if (creatingAreaId == areaId) creatingAreaId = null
            }
            publish()
        }
    }

    /** Stops the map's download and deletes its area. */
    fun cancelDownload() {
        val download = _state.value.download ?: return
        _state.update { it.copy(download = null) }
        delete(download.areaId)
    }

    /** Continues downloading the unfinished regions of [areaKey]. */
    fun resume(areaKey: String) {
        regions.values
            .filter { areaKeyOf(it) == areaKey && stored[it.id]?.complete != true }
            .forEach(::track)
    }

    /** Renames [areaKey] by rewriting the metadata of each of its regions. Areas without readable metadata are skipped. */
    fun rename(areaKey: String, name: String) {
        val store = store ?: return
        val targets = regions.values.filter { areaKeyOf(it) == areaKey }
        viewModelScope.launch {
            reporting {
                for (region in targets) {
                    val metadata = OfflineAreaMetadataCodec.decode(region.metadata) ?: continue
                    store.updateMetadata(region, OfflineAreaMetadataCodec.encode(metadata.copy(name = name)))
                    stored[region.id]?.let { stored[region.id] = it.copy(metadata = OfflineAreaMetadataCodec.decode(region.metadata)) }
                }
            }
            _state.value.download?.takeIf { it.areaId == areaKey }?.let { current ->
                _state.update { it.copy(download = current.copy(name = name)) }
            }
            reporting { reload(store) }
        }
    }

    /** Stops and deletes every region of [areaKey]. */
    fun delete(areaKey: String) {
        val store = store ?: return
        val targets = regions.values.filter { areaKeyOf(it) == areaKey }
        targets.forEach { region ->
            region.setDownloadState(OfflineRegion.STATE_INACTIVE)
            region.setObserver(null)
            regions.remove(region.id)
            downloading.remove(region.id)
            stored.remove(region.id)
        }
        publish()
        viewModelScope.launch {
            reporting { targets.forEach { store.delete(it) } }
            reporting { reload(store) }
        }
    }

    private suspend fun reload(store: OfflineAreasStore) = reloadLock.withLock {
        val listed = store.regions()
        regions.clear()
        listed.forEach { regions[it.id] = downloading[it.id] ?: it }
        stored.keys.retainAll(regions.keys)
        for (region in regions.values.toList()) {
            if (region.id !in downloading || region.id !in stored) {
                val status = store.status(region)
                if (region.id in regions) stored[region.id] = status.toStored(region)
            }
        }
        publish()
    }

    private fun track(region: OfflineRegion) {
        downloading[region.id] = region
        region.setObserver(object : OfflineRegion.OfflineRegionObserver {
            override fun onStatusChanged(status: OfflineRegionStatus) {
                if (region.id !in regions) return
                var update = status.toStored(region)
                if (status.isComplete) {
                    region.setDownloadState(OfflineRegion.STATE_INACTIVE)
                    region.setObserver(null)
                    downloading.remove(region.id)
                    update = update.copy(active = false)
                }
                stored[region.id] = update
                publish(waitingForNetwork = false)
            }

            override fun onError(error: OfflineRegionError) {
                if (region.id !in regions) return
                if (error.reason == OfflineRegionError.REASON_CONNECTION) {
                    publish(waitingForNetwork = true)
                } else {
                    Log.w(TAG, "Offline download error ${error.reason}: ${error.message}")
                    publish(problem = error.message?.takeIf { it.isNotBlank() } ?: error.reason)
                }
            }

            override fun mapboxTileCountLimitExceeded(limit: Long) {
                if (region.id !in regions) return
                region.setDownloadState(OfflineRegion.STATE_INACTIVE)
                region.setObserver(null)
                downloading.remove(region.id)
                stored[region.id]?.let { stored[region.id] = it.copy(active = false) }
                failed(getApplication<Application>().getString(R.string.offline_error_tile_limit, limit))
                publish()
            }
        })
        region.setDownloadState(OfflineRegion.STATE_ACTIVE)
        val previous = stored[region.id]
        stored[region.id] = previous?.copy(active = true)
            ?: StoredRegion(region.id, OfflineAreaMetadataCodec.decode(region.metadata), 0, 0, 0, complete = false, active = true)
        publish()
    }

    /** Recomputes the areas and the map's download card. */
    private fun publish(waitingForNetwork: Boolean? = null, problem: String? = null) {
        val areas = groupOfflineAreas(stored.values.toList())
        val download = _state.value.download?.let { current ->
            val status = areas.firstOrNull { it.key == current.areaId }?.status
            val waiting = waitingForNetwork ?: current.waitingForNetwork
            val percent = (status as? OfflineAreaStatus.Downloading)?.percent ?: current.percent
            val shownProblem = problem ?: current.problem.takeIf { percent <= current.percent }
            when {
                current.areaId == creatingAreaId ->
                    current.copy(percent = percent, waitingForNetwork = waiting, problem = shownProblem)
                status is OfflineAreaStatus.Downloading ->
                    current.copy(percent = percent, waitingForNetwork = waiting, problem = shownProblem)
                status is OfflineAreaStatus.Complete -> {
                    _events.trySend(OfflineAreasEvent.Finished(current.name))
                    null
                }
                else -> null
            }
        }
        _state.value = _state.value.copy(available = true, loaded = true, areas = areas, download = download)
    }

    private fun fail(areaId: String, message: String?) {
        failed(message)
        if (_state.value.download?.areaId == areaId) _state.update { it.copy(download = null) }
        delete(areaId)
    }

    private fun failed(message: String?) {
        val text = message?.takeIf { it.isNotBlank() } ?: getApplication<Application>().getString(R.string.offline_error_unknown)
        _events.trySend(OfflineAreasEvent.Failed(text))
    }

    private suspend fun reporting(block: suspend () -> Unit) {
        try {
            block()
        } catch (e: OfflineAreaException) {
            failed(e.message)
        }
    }

    override fun onCleared() {
        downloading.values.forEach { it.setObserver(null) }
    }

    private fun areaKeyOf(region: OfflineRegion): String =
        offlineAreaKey(region.id, OfflineAreaMetadataCodec.decode(region.metadata))

    private fun OfflineRegionStatus.toStored(region: OfflineRegion) = StoredRegion(
        regionId = region.id,
        metadata = OfflineAreaMetadataCodec.decode(region.metadata),
        completedBytes = completedResourceSize,
        completedCount = completedResourceCount,
        requiredCount = requiredResourceCount,
        complete = isComplete,
        active = downloadState == OfflineRegion.STATE_ACTIVE,
    )

    private companion object {
        const val TAG = "OfflineAreas"
    }
}
