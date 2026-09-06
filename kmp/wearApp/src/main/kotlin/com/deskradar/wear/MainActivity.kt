package com.deskradar.wear

import android.Manifest
import android.content.pm.PackageManager
import android.os.Bundle
import android.util.Log
import android.view.WindowManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalView
import androidx.core.content.ContextCompat
import androidx.fragment.app.FragmentActivity
import androidx.wear.ambient.AmbientModeSupport
import androidx.wear.compose.material.MaterialTheme
import com.deskradar.common.AircraftCategory
import com.deskradar.common.RadarMarkerPosition
import com.deskradar.common.RadarTarget

sealed class RadarUiState {
    object PermissionRequired : RadarUiState()
    object Loading : RadarUiState()
    data class Data(val targets: List<RadarTarget>) : RadarUiState()
}

class MainActivity : FragmentActivity(), AmbientModeSupport.AmbientCallbackProvider {

    private val viewModel: RadarViewModel by viewModels<RadarViewModel>()

    // Bridges the non-Compose ambient callback into Compose state — `by mutableStateOf` works
    // fine on a plain class property, not just inside a composable.
    private var isAmbient by mutableStateOf(false)

    private fun hasLocationPermission() = ContextCompat.checkSelfPermission(
        this, Manifest.permission.ACCESS_FINE_LOCATION
    ) == PackageManager.PERMISSION_GRANTED

    override fun getAmbientCallback(): AmbientModeSupport.AmbientCallback =
        object : AmbientModeSupport.AmbientCallback() {
            override fun onEnterAmbient(ambientDetails: Bundle?) {
                Log.d(TAG, "onEnterAmbient")
                isAmbient = true
            }

            override fun onExitAmbient() {
                Log.d(TAG, "onExitAmbient")
                isAmbient = false
            }
        }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        AmbientModeSupport.attach(this)

        // Dev-only: without this, the watch's short default screen timeout kicks the app back to
        // the watch face mid-testing (looks like a crash). Real battery cost — BuildConfig makes
        // this false in release builds. See "Development vs. production settings" in
        // docs/design.md.
        if (BuildConfig.KEEP_SCREEN_ON) {
            window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        }

        setContent {
            // Wear OS's system-level "swipe to dismiss" can swallow our own pan-drag gesture
            // before Compose ever sees it, closing the app on what was meant to be a pan. This
            // tells the system this whole screen handles its own gestures, don't intercept.
            val view = LocalView.current
            LaunchedEffect(Unit) {
                view.post {
                    view.systemGestureExclusionRects = listOf(android.graphics.Rect(0, 0, view.width, view.height))
                }
            }

            var permissionGranted by remember { mutableStateOf(hasLocationPermission()) }

            val requestPermission = rememberLauncherForActivityResult(
                ActivityResultContracts.RequestPermission()
            ) { granted -> permissionGranted = granted }

            MaterialTheme {
                if (!permissionGranted) {
                    RadarScreen(
                        uiState = RadarUiState.PermissionRequired,
                        displayRangeKm = RadarViewModel.DEFAULT_DISPLAY_RANGE_KM,
                        isPanned = false,
                        isAmbient = isAmbient,
                        activeFilters = emptySet(),
                        myLocationMarker = null,
                        useMiles = false,
                        pinnedIcao24 = null,
                        metadataByIcao24 = emptyMap(),
                        onGrantPermission = { requestPermission.launch(Manifest.permission.ACCESS_FINE_LOCATION) },
                        onZoom = {},
                        onPan = { _, _ -> },
                        onRecenter = {},
                        onToggleFilter = {},
                        onToggleUnits = {},
                        onTogglePin = {},
                        onRequestMetadata = {}
                    )
                } else {
                    val targets by viewModel.targets.collectAsState()
                    val displayRangeKm by viewModel.displayRangeKm.collectAsState()
                    val isPanned by viewModel.isPanned.collectAsState()
                    val activeFilters by viewModel.activeFilters.collectAsState()
                    val myLocationMarker by viewModel.myLocationMarker.collectAsState()
                    val useMiles by viewModel.useMiles.collectAsState()
                    val pinnedIcao24 by viewModel.pinnedIcao24.collectAsState()
                    val metadataByIcao24 by viewModel.metadataByIcao24.collectAsState()

                    RadarScreen(
                        uiState = targets?.let { RadarUiState.Data(it) } ?: RadarUiState.Loading,
                        displayRangeKm = displayRangeKm,
                        isPanned = isPanned,
                        isAmbient = isAmbient,
                        activeFilters = activeFilters,
                        myLocationMarker = myLocationMarker,
                        useMiles = useMiles,
                        pinnedIcao24 = pinnedIcao24,
                        metadataByIcao24 = metadataByIcao24,
                        onGrantPermission = {},
                        onZoom = viewModel::zoomBy,
                        onPan = viewModel::panBy,
                        onRecenter = viewModel::resetView,
                        onToggleFilter = viewModel::toggleFilter,
                        onToggleUnits = viewModel::toggleUnits,
                        onTogglePin = viewModel::togglePin,
                        onRequestMetadata = viewModel::requestMetadata
                    )
                }
            }
        }
    }

    override fun onStart() {
        super.onStart()
        Log.d(TAG, "onStart -> setForeground(true)")
        viewModel.setForeground(true)
    }

    override fun onStop() {
        Log.d(TAG, "onStop -> setForeground(false)")
        viewModel.setForeground(false)
        super.onStop()
    }

    companion object {
        private const val TAG = "DeskRadarMainActivity"
    }
}
