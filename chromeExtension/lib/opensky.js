// OpenSky Network client: anonymous tier by default, OAuth2 client-credentials flow only when
// the user has supplied their own client id/secret via the settings screen (same shape as
// kmp/common's OpenSkyApiClient). Field mapping and fetch radius match RadarRepository.kt /
// Aircraft.kt exactly, so this is a faithful client-side port, not just "similar".

import { boundingBox } from './radarMath.js';

const STATES_URL = 'https://opensky-network.org/api/states/all';
const TOKEN_URL =
  'https://auth.opensky-network.org/auth/realms/opensky-network/protocol/openid-connect/token';

// Matches RadarViewModel.MAX_DISPLAY_RANGE_KM — the fixed radius actually fetched; zoom (5-200km)
// is a client-side re-filter of this same fetched superset, not a re-fetch.
export const FETCH_RADIUS_KM = 200.0;

async function getAccessToken(clientId, clientSecret) {
  const cached = await chrome.storage.local.get([
    'openskyAccessToken',
    'openskyTokenExpiry',
  ]);
  const now = Date.now();
  if (
    cached.openskyAccessToken &&
    cached.openskyTokenExpiry &&
    now < cached.openskyTokenExpiry - 5000
  ) {
    return cached.openskyAccessToken;
  }

  const body = new URLSearchParams({
    grant_type: 'client_credentials',
    client_id: clientId,
    client_secret: clientSecret,
  });
  const res = await fetch(TOKEN_URL, {
    method: 'POST',
    headers: { 'Content-Type': 'application/x-www-form-urlencoded' },
    body,
  });
  if (!res.ok) {
    throw new Error(`OpenSky auth failed (${res.status}) — check your client id/secret`);
  }
  const data = await res.json();
  const expiry = now + data.expires_in * 1000;
  await chrome.storage.local.set({
    openskyAccessToken: data.access_token,
    openskyTokenExpiry: expiry,
  });
  return data.access_token;
}

// settings: { openskyAuthMode, openskyClientId, openskyClientSecret }
export async function fetchNearbyAircraft(center, settings) {
  const box = boundingBox(center, FETCH_RADIUS_KM);
  const params = new URLSearchParams({
    lamin: box.latMin.toFixed(4),
    lamax: box.latMax.toFixed(4),
    lomin: box.lonMin.toFixed(4),
    lomax: box.lonMax.toFixed(4),
  });

  const headers = {};
  const useAuth =
    settings.openskyAuthMode === 'authenticated' &&
    settings.openskyClientId &&
    settings.openskyClientSecret;
  if (useAuth) {
    const token = await getAccessToken(settings.openskyClientId, settings.openskyClientSecret);
    headers.Authorization = `Bearer ${token}`;
  }

  const res = await fetch(`${STATES_URL}?${params.toString()}`, { headers });
  if (!res.ok) {
    throw new Error(`OpenSky request failed (${res.status})`);
  }
  const data = await res.json();
  // Index mapping matches Aircraft.kt's toAircraftOrNull() exactly: 0=icao24, 1=callsign,
  // 5=longitude, 6=latitude, 7=baro_altitude, 8=on_ground, 9=velocity, 10=true_track,
  // 11=vertical_rate, 17=category.
  return (data.states || [])
    .filter((s) => s[5] != null && s[6] != null)
    .map((s) => ({
      icao24: (s[0] || '').trim(),
      callsign: (s[1] || '').trim() || 'UNK',
      lon: s[5],
      lat: s[6],
      altitudeMeters: s[7] ?? null,
      onGround: s[8] === true,
      velocityMs: s[9] ?? null,
      trueTrackDeg: s[10] ?? null,
      verticalRateMs: s[11] ?? null,
      category: s[17] != null ? Math.trunc(s[17]) : null,
    }));
}
