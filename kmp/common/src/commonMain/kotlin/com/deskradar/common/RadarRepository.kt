package com.deskradar.common

import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.isActive

data class RadarSnapshot(
    val nearest: RadarTarget?,
    val count: Int,
    val targets: List<RadarTarget>
)

/** Guest-mode OpenSky poll intervals, matching fetchAndMapFlights()'s pollInterval backoff. */
private const val POLL_INTERVAL_GUEST_MS = 108_000L
private const val POLL_INTERVAL_RATE_LIMITED_MS = 60_000L
private const val POLL_INTERVAL_ERROR_MS = 15_000L
private const val NO_LOCATION_RECHECK_MS = 1_000L

class RadarRepository(
    private val client: OpenSkyApiClient,
    private val rangeKm: Double
) {
    /**
     * Emits null while there's no location fix yet, then a [RadarSnapshot] after every poll.
     * Reads [center]'s latest value on each tick rather than reacting to every change —
     * this is a fixed-cadence poll, not a live location-following radar.
     */
    fun observeSnapshots(center: StateFlow<GeoPoint?>): Flow<RadarSnapshot?> = flow {
        var pollIntervalMs = POLL_INTERVAL_GUEST_MS

        while (currentCoroutineContext().isActive) {
            val currentCenter = center.value
            if (currentCenter == null) {
                emit(null)
                delay(NO_LOCATION_RECHECK_MS)
                continue
            }

            val box = boundingBox(currentCenter, rangeKm)
            when (val result = client.fetchStates(box)) {
                is OpenSkyResult.Success -> {
                    pollIntervalMs = POLL_INTERVAL_GUEST_MS
                    val targets = result.aircraft.mapNotNull { projectOrNull(currentCenter, it, rangeKm) }
                    emit(RadarSnapshot(targets.minByOrNull { it.distanceKm }, targets.size, targets))
                }
                OpenSkyResult.RateLimited -> pollIntervalMs = POLL_INTERVAL_RATE_LIMITED_MS
                is OpenSkyResult.Failure -> pollIntervalMs = POLL_INTERVAL_ERROR_MS
            }

            delay(pollIntervalMs)
        }
    }
}
