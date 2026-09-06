// Flight status lookup by flight number, via AeroDataBox (RapidAPI). Separate from opensky.js —
// OpenSky only exposes live ADS-B position state, not a flight number's schedule/status/gate
// times, so this hits a different provider entirely.

const API_HOST = 'aerodatabox.p.rapidapi.com';
const API_BASE = `https://${API_HOST}/flights/number`;

function pickTime(node, ...keys) {
  if (!node) return null;
  for (const key of keys) {
    const value = node[key];
    if (value?.local) return value.local;
    if (value?.utc) return value.utc;
  }
  return null;
}

function formatAirport(airport) {
  if (!airport) return '?';
  return airport.iata || airport.icao || airport.name || '?';
}

function normalizeFlight(raw) {
  return {
    number: raw.number || raw.flight?.iata || '?',
    airline: raw.airline?.name ?? null,
    status: raw.status ?? 'Unknown',
    departure: {
      airport: formatAirport(raw.departure?.airport),
      scheduled: pickTime(raw.departure, 'scheduledTime'),
      actual: pickTime(raw.departure, 'runwayTime', 'revisedTime', 'actualTime'),
    },
    arrival: {
      airport: formatAirport(raw.arrival?.airport),
      scheduled: pickTime(raw.arrival, 'scheduledTime'),
      estimated: pickTime(raw.arrival, 'predictedTime', 'revisedTime', 'runwayTime', 'actualTime'),
    },
  };
}

// Picks the most relevant flight when a number resolves to several (codeshares, or several
// days of a recurring number): prefer one that's actually in progress, else the first result.
function pickMostRelevant(flights) {
  const inProgress = flights.find((f) =>
    ['EnRoute', 'Departed', 'Boarding', 'CheckIn'].includes(f.status),
  );
  return inProgress ?? flights[0];
}

// settings: { aeroDataBoxApiKey }
export async function fetchFlightStatus(flightNumber, settings) {
  const apiKey = settings.aeroDataBoxApiKey;
  if (!apiKey) {
    throw new Error('Add an AeroDataBox API key in Settings to use Find Flight.');
  }

  const code = flightNumber.trim().toUpperCase().replace(/\s+/g, '');
  if (!code) {
    throw new Error('Enter a flight number, e.g. DL838.');
  }

  const res = await fetch(`${API_BASE}/${encodeURIComponent(code)}`, {
    headers: {
      'X-RapidAPI-Key': apiKey,
      'X-RapidAPI-Host': API_HOST,
    },
  });
  if (res.status === 404) {
    throw new Error(`No flight found for ${code}.`);
  }
  if (res.status === 403) {
    const body = await res.json().catch(() => null);
    const reason = body?.message || 'not subscribed / plan does not include this endpoint';
    throw new Error(
      `RapidAPI rejected the request (403: ${reason}). Check that your key is subscribed to a ` +
        'plan for AeroDataBox on RapidAPI (flight status is a paid Tier 2 endpoint).',
    );
  }
  if (!res.ok) {
    throw new Error(`Flight status request failed (${res.status})`);
  }
  const data = await res.json();
  if (!Array.isArray(data) || data.length === 0) {
    throw new Error(`No flight found for ${code}.`);
  }
  return normalizeFlight(pickMostRelevant(data));
}
