// Ported 1:1 from kmp/common/.../RadarMath.kt — same equirectangular approximation
// (not great-circle/haversine) so pan/zoom/positions match the Wear OS app exactly.

const KM_PER_DEGREE = 111.1;

export function boundingBox(center, rangeKm) {
  const dLat = rangeKm / KM_PER_DEGREE;
  const dLon = rangeKm / (KM_PER_DEGREE * Math.cos((center.latitude * Math.PI) / 180));
  return {
    latMin: center.latitude - dLat,
    lonMin: center.longitude - dLon,
    latMax: center.latitude + dLat,
    lonMax: center.longitude + dLon,
  };
}

// Returns { distanceKm, bearingRad } — bearingRad normalized to [0, 2*PI), 0 = north.
export function distanceAndBearing(center, point) {
  const dY = (point.latitude - center.latitude) * KM_PER_DEGREE;
  const dX =
    (point.longitude - center.longitude) * KM_PER_DEGREE * Math.cos((center.latitude * Math.PI) / 180);
  const distanceKm = Math.sqrt(dX * dX + dY * dY);
  let bearingRad = Math.atan2(dX, dY);
  if (bearingRad < 0) bearingRad += 2 * Math.PI;
  return { distanceKm, bearingRad };
}

export function projectOrNull(center, aircraft, maxRangeKm) {
  const { distanceKm, bearingRad } = distanceAndBearing(center, {
    latitude: aircraft.lat,
    longitude: aircraft.lon,
  });
  if (distanceKm > maxRangeKm) return null;
  return { aircraft, distanceKm, bearingRad };
}

export function projectPoint(center, point) {
  return distanceAndBearing(center, point);
}

// Inverse of the projection above — moves `center` by a km offset (east/north), for pan.
export function offsetGeoPoint(center, eastwardKm, northwardKm) {
  const newLat = center.latitude + northwardKm / KM_PER_DEGREE;
  const newLon =
    center.longitude + eastwardKm / (KM_PER_DEGREE * Math.cos((center.latitude * Math.PI) / 180));
  return { latitude: newLat, longitude: newLon };
}

// Screen position for a target at (distanceKm, bearingRad) relative to the radar center —
// matches RadarScreen.kt's targetPosition(): bearingRad 0 = up (north), clockwise.
export function targetPosition(cx, cy, maxRadiusPx, distanceKm, bearingRad, rangeKm) {
  const pixelRadius = Math.min(Math.max(distanceKm / rangeKm, 0), 1) * maxRadiusPx;
  const x = cx + pixelRadius * Math.sin(bearingRad);
  const y = cy - pixelRadius * Math.cos(bearingRad);
  return { x, y };
}
