// Shared settings store, backed by chrome.storage.local.
// Mirrors the firmware's captive-portal config: users supply their own
// OpenSky credentials, anonymous tier is the default until they do.
//
// Zoom/pan/filters/units/pin state live only in radar.js's in-memory state (matching
// RadarViewModel, which also doesn't persist them across process death) — this store only
// holds the OpenSky credentials, which have no equivalent on the watch (that's baked in via
// the firmware's captive portal / the KMP app's own OAuth config, not a runtime user setting).

export const DEFAULT_SETTINGS = {
  openskyAuthMode: 'anonymous', // 'anonymous' | 'authenticated'
  openskyClientId: '',
  openskyClientSecret: '',
  aeroDataBoxApiKey: '', // RapidAPI key for AeroDataBox, powers the "Find Flight" status lookup
};

const SETTINGS_KEYS = Object.keys(DEFAULT_SETTINGS);

export async function getSettings() {
  const stored = await chrome.storage.local.get(SETTINGS_KEYS);
  return { ...DEFAULT_SETTINGS, ...stored };
}

export async function saveSettings(partial) {
  await chrome.storage.local.set(partial);
}
