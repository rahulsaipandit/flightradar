package com.deskradar.common

import kotlin.math.PI
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.sqrt

data class GeoPoint(val latitude: Double, val longitude: Double)

data class BoundingBox(
    val latMin: Double,
    val lonMin: Double,
    val latMax: Double,
    val lonMax: Double
)

data class RadarTarget(
    val aircraft: Aircraft,
    val distanceKm: Double,
    /** Bearing from the radar center to the aircraft, radians, normalized to [0, 2*PI). */
    val bearingRad: Double
)

/** km per degree of latitude (and, scaled by cos(lat), of longitude) — same approximation the firmware uses. */
private const val KM_PER_DEGREE = 111.1

/** Ports calculateBoundingBox() from firmware/ESP32Radar/ESP32Radar.ino. */
fun boundingBox(center: GeoPoint, rangeKm: Double): BoundingBox {
    val dLat = rangeKm / KM_PER_DEGREE
    val dLon = rangeKm / (KM_PER_DEGREE * cos(center.latitude * PI / 180.0))
    return BoundingBox(
        latMin = center.latitude - dLat,
        lonMin = center.longitude - dLon,
        latMax = center.latitude + dLat,
        lonMax = center.longitude + dLon
    )
}

/**
 * Ports the distance/bearing math from fetchAndMapFlights(): an equirectangular approximation
 * (accurate enough at radar-display ranges), not great-circle distance.
 */
fun projectOrNull(center: GeoPoint, aircraft: Aircraft, maxRangeKm: Double): RadarTarget? {
    val dY = (aircraft.latitude - center.latitude) * KM_PER_DEGREE
    val dX = (aircraft.longitude - center.longitude) * KM_PER_DEGREE * cos(center.latitude * PI / 180.0)
    val distanceKm = sqrt(dX * dX + dY * dY)
    if (distanceKm > maxRangeKm) return null

    var bearingRad = atan2(dX, dY)
    if (bearingRad < 0) bearingRad += 2 * PI

    return RadarTarget(aircraft, distanceKm, bearingRad)
}
