// Ported 1:1 from kmp/common/.../AircraftClassification.kt + MilitaryAllocation.kt.

const CATEGORY_ROTORCRAFT = 7;

// Airline ICAO callsigns are a 3-letter operator code plus a flight number (e.g. "DLH456",
// "BAW123A") — a tail-number-style callsign ("N12345", "G-ABCD") won't match. A heuristic, not
// authoritative: some private/business jets file airline-style callsigns and vice versa.
const AIRLINE_CALLSIGN_REGEX = /^[A-Z]{3}\d{1,4}[A-Z]?$/;

// ICAO24 hex-address ranges reserved for military use — intentionally a small, high-confidence
// starting set (currently just the well-documented US DoD block), matching MilitaryAllocation.kt.
const MILITARY_ICAO24_RANGES = [[0xadf7c8, 0xafffff]]; // US DoD block

export function isMilitaryIcao24(icao24) {
  const value = parseInt(icao24, 16);
  if (Number.isNaN(value)) return false;
  return MILITARY_ICAO24_RANGES.some(([lo, hi]) => value >= lo && value <= hi);
}

// An aircraft can belong to more than one category at once (e.g. a military helicopter is both
// MILITARY and HELICOPTER) — drives multi-select filter chips with OR semantics.
export function categoriesFor(aircraft) {
  const categories = new Set();
  const isHelicopter = aircraft.category === CATEGORY_ROTORCRAFT;
  const isAirliner = AIRLINE_CALLSIGN_REGEX.test((aircraft.callsign || '').trim());

  if (isHelicopter) categories.add('HELICOPTER');
  if (isMilitaryIcao24(aircraft.icao24)) categories.add('MILITARY');
  if (isAirliner) categories.add('AIRLINER');
  if (!isAirliner && !isHelicopter) categories.add('PRIVATE');
  return categories;
}
