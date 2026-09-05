package com.deskradar.wear

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.deskradar.common.FusedLocationProvider
import com.deskradar.common.GeoPoint
import com.deskradar.common.OpenSkyApiClient
import com.deskradar.common.RadarRepository
import com.deskradar.common.RadarSnapshot
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn

class RadarViewModel(application: Application) : AndroidViewModel(application) {

    companion object {
        /** Fixed range for this pass — no settings UI yet (see docs/design.md). */
        const val RANGE_KM = 50.0
    }

    private val locationProvider = FusedLocationProvider(application)
    private val openSkyClient = OpenSkyApiClient()
    private val repository = RadarRepository(openSkyClient, RANGE_KM)

    // WhileSubscribed (not Eagerly): GPS updates and OpenSky polling should stop when the
    // screen isn't actually visible, not run forever in the background draining battery.
    private val location: StateFlow<GeoPoint?> =
        locationProvider.observeLocation().stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    val snapshot: StateFlow<RadarSnapshot?> =
        repository.observeSnapshots(location).stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    override fun onCleared() {
        openSkyClient.close()
    }
}
