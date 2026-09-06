package com.deskradar.wear

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.deskradar.common.AircraftCategory
import com.deskradar.common.FusedLocationProvider
import com.deskradar.common.GeoPoint
import com.deskradar.common.OpenSkyApiClient
import com.deskradar.common.RadarMarkerPosition
import com.deskradar.common.RadarRepository
import com.deskradar.common.RadarTarget
import com.deskradar.common.offsetGeoPoint
import com.deskradar.common.projectOrNull
import com.deskradar.common.projectPoint
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
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

    // Set from MainActivity's onStart/onStop — an AndroidViewModel isn't cleared just because
    // the Activity stops, so without this GPS would keep polling forever in the background.
    // Defaults true so a fresh launch behaves normally before onStart's explicit call lands.
    private val isForeground = MutableStateFlow(true)

    fun setForeground(foreground: Boolean) {
        isForeground.value = foreground
    }

    // Eagerly, not WhileSubscribed: RadarRepository only ever *reads* this StateFlow's .value
    // on each poll tick, it never .collect()s it — so WhileSubscribed's "start when someone
    // subscribes" never triggers here, and the GPS flow would never actually launch, leaving
    // .value stuck at its initial null forever. (snapshot below is genuinely collected by the
    // UI via collectAsState, so WhileSubscribed correctly pauses OpenSky polling there.)
    //
    // The actual foreground-gating happens one level down, via flatMapLatest: when isForeground
    // flips to false, flatMapLatest cancels the collector on locationProvider.observeLocation(),
    // which genuinely stops the underlying FusedLocationProviderClient updates (not just a
    // no-op within our own code) — while the outer StateFlow stays Eagerly so RadarRepository's
    // .value reads keep working.
    private val location: StateFlow<GeoPoint?> =
        isForeground
            .flatMapLatest { foreground -> if (foreground) locationProvider.observeLocation() else flowOf(null) }
            .stateIn(viewModelScope, SharingStarted.Eagerly, null)

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

    private val snapshot = repository.observeSnapshots(effectiveCenter)

    // Re-projects the last poll's raw aircraft against the *current* center on every emission —
    // reactive to panning, not just to new poll data. Without this, panning would only visibly
    // move aircraft at the next ~108s poll (the bug this fixes): RadarRepository bakes in
    // distance/bearing once, at fetch time, against whatever center was active *then*; it has
    // no way to know the center changed a second later. Re-deriving here, against effectiveCenter
    // (which updates instantly on every pan), is what makes pan feel instant — the same principle
    // zoom already uses (a client-side re-filter of already-fetched data).
    val targets: StateFlow<List<RadarTarget>?> =
        combine(snapshot, effectiveCenter) { snap, center ->
            if (snap == null || center == null) null
            else snap.aircraft.mapNotNull { projectOrNull(center, it, MAX_DISPLAY_RANGE_KM) }
        }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    // Only meaningful once panned away (panOverride non-null) — deriving this from
    // effectiveCenter instead would make center == location whenever NOT panned, so
    // distanceKm would always be ~0 and the marker would render at the radar center on every
    // frame, permanently, instead of only when actually panned away.
    val myLocationMarker: StateFlow<RadarMarkerPosition?> =
        combine(panOverride, location) { override, loc ->
            if (override != null && loc != null) projectPoint(override, loc) else null
        }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    private val _displayRangeKm = MutableStateFlow(DEFAULT_DISPLAY_RANGE_KM)
    val displayRangeKm: StateFlow<Double> = _displayRangeKm.asStateFlow()

    fun zoomBy(deltaKm: Double) {
        _displayRangeKm.value = (_displayRangeKm.value + deltaKm).coerceIn(MIN_DISPLAY_RANGE_KM, MAX_DISPLAY_RANGE_KM)
    }

    /** Shifts the manual center by a km offset (east/north) — starts from GPS on the first pan. */
    fun panBy(eastwardKm: Double, northwardKm: Double) {
        val base = panOverride.value ?: location.value ?: return
        // android.util.Log.d("DeskRadarPan", "panBy($eastwardKm, $northwardKm) base=$base")
        panOverride.value = offsetGeoPoint(base, eastwardKm, northwardKm)
        // android.util.Log.d("DeskRadarPan", "panOverride now = ${panOverride.value}")
    }

    /** Resets both pan and zoom back to defaults — the center-of-radar reset icon does both at once. */
    fun resetView() {
        panOverride.value = null
        _displayRangeKm.value = DEFAULT_DISPLAY_RANGE_KM
    }

    // Empty set = show everything (no filter applied).
    private val _activeFilters = MutableStateFlow<Set<AircraftCategory>>(emptySet())
    val activeFilters: StateFlow<Set<AircraftCategory>> = _activeFilters.asStateFlow()

    fun toggleFilter(category: AircraftCategory) {
        _activeFilters.value = _activeFilters.value.let { current ->
            if (category in current) current - category else current + category
        }
    }

    override fun onCleared() {
        openSkyClient.close()
    }
}
