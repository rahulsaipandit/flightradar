package com.flightpulse.common

import kotlinx.coroutines.flow.Flow

/**
 * GPS access is inherently platform-specific. This is a plain interface (not expect/actual)
 * since the concrete implementation needs a platform type (Android's Context) in its
 * constructor that commonMain can't express — see FusedLocationProvider in androidMain.
 */
interface LocationProvider {
    fun observeLocation(): Flow<GeoPoint?>
}
