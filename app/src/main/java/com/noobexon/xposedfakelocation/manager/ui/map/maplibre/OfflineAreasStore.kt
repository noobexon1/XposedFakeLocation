package com.noobexon.xposedfakelocation.manager.ui.map.maplibre

import android.content.Context
import kotlinx.coroutines.suspendCancellableCoroutine
import org.maplibre.android.offline.OfflineManager
import org.maplibre.android.offline.OfflineRegion
import org.maplibre.android.offline.OfflineRegionStatus
import org.maplibre.android.offline.OfflineTilePyramidRegionDefinition
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

/** An offline storage error reported by MapLibre. */
class OfflineAreaException(message: String) : Exception(message)

/** Suspend wrappers around MapLibre's callback-based [OfflineManager]. Main thread only. */
internal class OfflineAreasStore(private val context: Context) {
    private val manager: OfflineManager get() = OfflineManager.getInstance(context)

    suspend fun regions(): List<OfflineRegion> = suspendCancellableCoroutine { continuation ->
        manager.listOfflineRegions(object : OfflineManager.ListOfflineRegionsCallback {
            override fun onList(offlineRegions: Array<OfflineRegion>?) {
                continuation.resume(offlineRegions?.toList().orEmpty())
            }

            override fun onError(error: String) {
                continuation.resumeWithException(OfflineAreaException(error))
            }
        })
    }

    suspend fun status(region: OfflineRegion): OfflineRegionStatus = suspendCancellableCoroutine { continuation ->
        region.getStatus(object : OfflineRegion.OfflineRegionStatusCallback {
            override fun onStatus(status: OfflineRegionStatus?) {
                if (status != null) {
                    continuation.resume(status)
                } else {
                    continuation.resumeWithException(OfflineAreaException("No status for region ${region.id}"))
                }
            }

            override fun onError(error: String?) {
                continuation.resumeWithException(OfflineAreaException(error.orEmpty()))
            }
        })
    }

    suspend fun create(definition: OfflineTilePyramidRegionDefinition, metadata: ByteArray): OfflineRegion =
        suspendCancellableCoroutine { continuation ->
            manager.createOfflineRegion(definition, metadata, object : OfflineManager.CreateOfflineRegionCallback {
                override fun onCreate(offlineRegion: OfflineRegion) {
                    continuation.resume(offlineRegion)
                }

                override fun onError(error: String) {
                    continuation.resumeWithException(OfflineAreaException(error))
                }
            })
        }

    suspend fun updateMetadata(region: OfflineRegion, metadata: ByteArray): Unit = suspendCancellableCoroutine { continuation ->
        region.updateMetadata(metadata, object : OfflineRegion.OfflineRegionUpdateMetadataCallback {
            override fun onUpdate(metadata: ByteArray) {
                continuation.resume(Unit)
            }

            override fun onError(error: String) {
                continuation.resumeWithException(OfflineAreaException(error))
            }
        })
    }

    suspend fun delete(region: OfflineRegion): Unit = suspendCancellableCoroutine { continuation ->
        region.delete(object : OfflineRegion.OfflineRegionDeleteCallback {
            override fun onDelete() {
                continuation.resume(Unit)
            }

            override fun onError(error: String) {
                continuation.resumeWithException(OfflineAreaException(error))
            }
        })
    }
}
