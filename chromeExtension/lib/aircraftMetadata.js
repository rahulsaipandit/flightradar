// Ported from kmp/common/.../AircraftMetadataProvider.kt — registration/model lookup via
// adsbdb.com, a free public API with no key required. In-memory cache per icao24, fetched
// lazily only for aircraft the user has actually tapped (not the whole visible set).

const cache = new Map(); // icao24 (lowercase) -> AircraftMetadata | null

export async function lookupAircraftMetadata(icao24) {
  const key = icao24.toLowerCase();
  if (cache.has(key)) return cache.get(key);

  let result = null;
  try {
    const res = await fetch(`https://api.adsbdb.com/v0/aircraft/${key}`);
    if (res.ok) {
      const body = await res.json();
      const aircraft = body?.response?.aircraft;
      if (aircraft) {
        result = {
          registration: aircraft.registration ?? null,
          model: aircraft.type ?? null,
          operator: aircraft.registered_owner ?? null,
        };
      }
    }
  } catch {
    result = null;
  }

  cache.set(key, result);
  return result;
}
