package com.deskradar.common

enum class AircraftCategory { HELICOPTER, MILITARY, AIRLINER, PRIVATE }

/** OpenSky ADS-B emitter category enum value for rotorcraft/helicopter. */
private const val CATEGORY_ROTORCRAFT = 7

/**
 * Airline ICAO callsigns are a 3-letter operator code plus a flight number (e.g. "DLH456",
 * "BAW123A") — a tail-number-style callsign ("N12345", "G-ABCD") won't match. A heuristic, not
 * authoritative: some private/business jets file airline-style callsigns and vice versa.
 */
private val AIRLINE_CALLSIGN_REGEX = Regex("^[A-Z]{3}\\d{1,4}[A-Z]?$")

/**
 * An aircraft can belong to more than one category at once (e.g. a military helicopter is both
 * MILITARY and HELICOPTER) — this drives multi-select filter chips with OR semantics, not a
 * single mutually-exclusive classification.
 */
fun Aircraft.categories(): Set<AircraftCategory> = buildSet {
    val isHelicopter = category == CATEGORY_ROTORCRAFT
    val isAirliner = AIRLINE_CALLSIGN_REGEX.matches(callsign.trim())

    if (isHelicopter) add(AircraftCategory.HELICOPTER)
    if (isMilitaryIcao24(icao24)) add(AircraftCategory.MILITARY)
    if (isAirliner) add(AircraftCategory.AIRLINER)
    if (!isAirliner && !isHelicopter) add(AircraftCategory.PRIVATE)
}
