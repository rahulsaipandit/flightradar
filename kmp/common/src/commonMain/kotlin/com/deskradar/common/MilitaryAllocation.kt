package com.deskradar.common

/**
 * ICAO24 hex-address ranges reserved for military use. This is intentionally a small,
 * high-confidence starting set (currently just the well-documented US DoD block) rather than
 * a broad guess at ranges for other countries — expand from a maintained source (e.g. a
 * community-published military-range table) before relying on this for more than a rough
 * first-pass filter.
 */
private val MILITARY_ICAO24_RANGES: List<LongRange> = listOf(
    0xADF7C8L..0xAFFFFFL // US DoD block
)

fun isMilitaryIcao24(icao24: String): Boolean {
    val value = icao24.toLongOrNull(16) ?: return false
    return MILITARY_ICAO24_RANGES.any { value in it }
}
