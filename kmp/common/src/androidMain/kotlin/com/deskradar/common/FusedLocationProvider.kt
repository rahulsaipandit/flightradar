package com.deskradar.common

import android.annotation.SuppressLint
import android.content.Context
import com.google.android.gms.location.LocationCallback
import com.google.android.gms.location.LocationRequest
import com.google.android.gms.location.LocationResult
import com.google.android.gms.location.LocationServices
import com.google.android.gms.location.Priority
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow

/**
 * Assumes the caller has already checked ACCESS_FINE_LOCATION/ACCESS_COARSE_LOCATION —
 * this class doesn't request permissions itself, that's a UI concern (see wearApp/MainActivity).
 */
class FusedLocationProvider(private val context: Context) : LocationProvider {

    @SuppressLint("MissingPermission")
    override fun observeLocation(): Flow<GeoPoint?> = callbackFlow {
        val client = LocationServices.getFusedLocationProviderClient(context)

        // Emit the last known fix immediately so the UI isn't blocked waiting on a fresh GPS lock.
        client.lastLocation.addOnSuccessListener { location ->
            trySend(location?.let { GeoPoint(it.latitude, it.longitude) })
        }

        val request = LocationRequest.Builder(Priority.PRIORITY_BALANCED_POWER_ACCURACY, 30_000L).build()
        val callback = object : LocationCallback() {
            override fun onLocationResult(result: LocationResult) {
                result.lastLocation?.let { trySend(GeoPoint(it.latitude, it.longitude)) }
            }
        }
        client.requestLocationUpdates(request, callback, context.mainLooper)

        awaitClose { client.removeLocationUpdates(callback) }
    }
}
