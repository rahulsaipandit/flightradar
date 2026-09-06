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

/** Distance/bearing from a radar center to some other point — e.g. a "my GPS location" marker. */
data class RadarMarkerPosition(val distanceKm: Double, val bearingRad: Double)

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
private fun distanceAndBearing(center: GeoPoint, point: GeoPoint): RadarMarkerPosition {
    val dY = (point.latitude - center.latitude) * KM_PER_DEGREE
    val dX = (point.longitude - center.longitude) * KM_PER_DEGREE * cos(center.latitude * PI / 180.0)
    val distanceKm = sqrt(dX * dX + dY * dY)

    var bearingRad = atan2(dX, dY)
    if (bearingRad < 0) bearingRad += 2 * PI

    return RadarMarkerPosition(distanceKm, bearingRad)
}

fun projectOrNull(center: GeoPoint, aircraft: Aircraft, maxRangeKm: Double): RadarTarget? {
    val (distanceKm, bearingRad) = distanceAndBearing(center, GeoPoint(aircraft.latitude, aircraft.longitude))
    if (distanceKm > maxRangeKm) return null
    return RadarTarget(aircraft, distanceKm, bearingRad)
}

/** Distance/bearing from [center] to some other point, e.g. the device's actual GPS location. */
fun projectPoint(center: GeoPoint, point: GeoPoint): RadarMarkerPosition = distanceAndBearing(center, point)

/** Inverse of the projection above — moves [center] by a km offset (east/north), for pan. */
fun offsetGeoPoint(center: GeoPoint, eastwardKm: Double, northwardKm: Double): GeoPoint {
    val newLat = center.latitude + northwardKm / KM_PER_DEGREE
    val newLon = center.longitude + eastwardKm / (KM_PER_DEGREE * cos(center.latitude * PI / 180.0))
    return GeoPoint(newLat, newLon)
}
