package com.flightpulse.common

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.doubleOrNull
import kotlinx.serialization.json.jsonPrimitive

/** OpenSky's `/states/all` response: a flat array per aircraft, indexed positionally (see below). */
@Serializable
data class OpenSkyStatesResponse(
    val time: Long,
    val states: List<List<JsonElement>>? = null
)

data class Aircraft(
    val icao24: String,
    val callsign: String,
    val latitude: Double,
    val longitude: Double,
    val altitudeMeters: Double?,
    val velocityMs: Double?,
    val trueTrackDeg: Double?,
    /** Climb/descent rate, m/s — positive climbing, negative descending. Null if unknown. */
    val verticalRateMs: Double?,
    /** ADS-B emitter category enum (OpenSky index 17) — e.g. 7 = rotorcraft/helicopter. */
    val category: Int?,
    val onGround: Boolean
)

/**
 * Maps one raw OpenSky state vector to [Aircraft], or null if it has no position.
 * Indices match the OpenSky state vector spec (and firmware/ESP32Radar/ESP32Radar.ino's
 * fetchAndMapFlights()): 0=icao24, 1=callsign, 5=longitude, 6=latitude, 7=baro_altitude,
 * 8=on_ground, 9=velocity, 10=true_track, 11=vertical_rate, 17=category.
 */
fun List<JsonElement>.toAircraftOrNull(): Aircraft? {
    val lon = getOrNull(5)?.jsonPrimitive?.doubleOrNull ?: return null
    val lat = getOrNull(6)?.jsonPrimitive?.doubleOrNull ?: return null

    val icao24 = (getOrNull(0) as? JsonPrimitive)?.contentOrNull?.trim().orEmpty()
    val rawCallsign = (getOrNull(1) as? JsonPrimitive)?.contentOrNull
    val callsign = rawCallsign?.trim().takeUnless { it.isNullOrEmpty() || it == "null" } ?: "UNK"

    return Aircraft(
        icao24 = icao24,
        callsign = callsign,
        latitude = lat,
        longitude = lon,
        altitudeMeters = getOrNull(7)?.jsonPrimitive?.doubleOrNull,
        velocityMs = getOrNull(9)?.jsonPrimitive?.doubleOrNull,
        trueTrackDeg = getOrNull(10)?.jsonPrimitive?.doubleOrNull,
        verticalRateMs = getOrNull(11)?.jsonPrimitive?.doubleOrNull,
        category = getOrNull(17)?.jsonPrimitive?.doubleOrNull?.toInt(),
        onGround = (getOrNull(8) as? JsonPrimitive)?.content == "true"
    )
}
