package com.deskradar.wear

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.deskradar.common.FusedLocationProvider
import com.deskradar.common.GeoPoint
import com.deskradar.common.OpenSkyApiClient
import com.deskradar.common.RadarRepository
import com.deskradar.common.RadarSnapshot
import com.deskradar.common.offsetGeoPoint
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn

class RadarViewModel(application: Application) : AndroidViewModel(application) {

    companion object {
        const val DEFAULT_DISPLAY_RANGE_KM = 20.0
        const val MIN_DISPLAY_RANGE_KM = 5.0

        /**
         * Both the zoom-out ceiling AND the fixed range RadarRepository actually fetches/projects
         * at. Fetching a superset up front means zoom is a purely client-side re-filter/rescale
         * of already-fetched data — instant, no waiting on the next ~108s poll to "see more".
         */
        const val MAX_DISPLAY_RANGE_KM = 200.0
    }

    private val locationProvider = FusedLocationProvider(application)
    private val openSkyClient = OpenSkyApiClient()
    private val repository = RadarRepository(openSkyClient, MAX_DISPLAY_RANGE_KM)

    // Eagerly, not WhileSubscribed: RadarRepository only ever *reads* this StateFlow's .value
    // on each poll tick, it never .collect()s it — so WhileSubscribed's "start when someone
    // subscribes" never triggers here, and the GPS flow would never actually launch, leaving
    // .value stuck at its initial null forever. (snapshot below is genuinely collected by the
    // UI via collectAsState, so WhileSubscribed correctly pauses OpenSky polling there.)
    private val location: StateFlow<GeoPoint?> =
        locationProvider.observeLocation().stateIn(viewModelScope, SharingStarted.Eagerly, null)

    /** null = follow GPS; non-null = user panned away, holds the manual center. */
    private val panOverride = MutableStateFlow<GeoPoint?>(null)

    // WhileSubscribed is fine here (unlike `location`/`effectiveCenter` above) — the UI genuinely
    // .collect()s this one (via collectAsState, to show/hide the recenter button).
    val isPanned: StateFlow<Boolean> =
        panOverride.map { it != null }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), false)

    // Same Eagerly rule as `location`: this is only ever read via .value by RadarRepository.
    private val effectiveCenter: StateFlow<GeoPoint?> =
        combine(location, panOverride) { loc, override -> override ?: loc }
            .stateIn(viewModelScope, SharingStarted.Eagerly, null)

    val snapshot: StateFlow<RadarSnapshot?> =
        repository.observeSnapshots(effectiveCenter).stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    private val _displayRangeKm = MutableStateFlow(DEFAULT_DISPLAY_RANGE_KM)
    val displayRangeKm: StateFlow<Double> = _displayRangeKm.asStateFlow()

    fun zoomBy(deltaKm: Double) {
        _displayRangeKm.value = (_displayRangeKm.value + deltaKm).coerceIn(MIN_DISPLAY_RANGE_KM, MAX_DISPLAY_RANGE_KM)
    }

    /** Shifts the manual center by a km offset (east/north) — starts from GPS on the first pan. */
    fun panBy(eastwardKm: Double, northwardKm: Double) {
        val base = panOverride.value ?: location.value ?: return
        panOverride.value = offsetGeoPoint(base, eastwardKm, northwardKm)
    }

    fun recenterOnMe() {
        panOverride.value = null
    }

    override fun onCleared() {
        openSkyClient.close()
    }
}
