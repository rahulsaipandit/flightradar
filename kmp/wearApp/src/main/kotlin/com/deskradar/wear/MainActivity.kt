package com.deskradar.wear

import android.Manifest
import android.content.pm.PackageManager
import android.os.Bundle
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.core.content.ContextCompat
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.wear.compose.material.MaterialTheme
import com.deskradar.common.RadarSnapshot

sealed class RadarUiState {
    object PermissionRequired : RadarUiState()
    object Loading : RadarUiState()
    data class Data(val snapshot: RadarSnapshot) : RadarUiState()
}

class MainActivity : ComponentActivity() {

    private fun hasLocationPermission() = ContextCompat.checkSelfPermission(
        this, Manifest.permission.ACCESS_FINE_LOCATION
    ) == PackageManager.PERMISSION_GRANTED

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // This is a glanceable-but-actively-watched radar, not a background service — without
        // this the watch's short default screen timeout kicks it back to the watch face while
        // waiting on a GPS fix or just sitting on the live view, which looks like a crash.
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)

        setContent {
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
                        onGrantPermission = { requestPermission.launch(Manifest.permission.ACCESS_FINE_LOCATION) },
                        onZoom = {},
                        onPan = { _, _ -> },
                        onRecenter = {}
                    )
                } else {
                    val viewModel: RadarViewModel = viewModel()
                    val snapshot by viewModel.snapshot.collectAsState()
                    val displayRangeKm by viewModel.displayRangeKm.collectAsState()
                    val isPanned by viewModel.isPanned.collectAsState()

                    RadarScreen(
                        uiState = snapshot?.let { RadarUiState.Data(it) } ?: RadarUiState.Loading,
                        displayRangeKm = displayRangeKm,
                        isPanned = isPanned,
                        onGrantPermission = {},
                        onZoom = viewModel::zoomBy,
                        onPan = viewModel::panBy,
                        onRecenter = viewModel::recenterOnMe
                    )
                }
            }
        }
    }
}
