# FlightPulse Privacy Policy

**Effective:** 2026-09-06
**Applies to:** the FlightPulse Chrome extension (all versions)
**Contact:** extension@guideaxon.com

Hosted, formatted version: https://claude.ai/code/artifact/3107f07b-95f4-4b4d-b698-fcf3daa11e22
(this file is the plain-text source of record, kept in the repo so the policy
isn't only reachable through an external link).

## Plain-language summary

- Your location is used only to ask OpenSky "what's flying near here" — it's
  never stored by us, never sent anywhere but OpenSky, and never leaves your
  device otherwise.
- There is no FlightPulse server. Every request goes straight from your
  browser to OpenSky Network or adsbdb.com.
- No accounts, no ads, no analytics, no trackers, nothing sold to anyone.
- If you add your own OpenSky credentials, they stay on your machine in the
  browser's local extension storage.

## What FlightPulse does

FlightPulse is a Chrome extension that draws a radar-style display of
aircraft near a location you provide, using live position data from the
[OpenSky Network](https://opensky-network.org). It has no companion server
or backend — the extension talks directly to OpenSky's and adsbdb.com's
public APIs from your browser.

## Location data

When you open the radar view, your browser's native location prompt asks
for permission, the same way any website's would. If you allow it:

- Your coordinates are used locally to build a bounding-box query, which is
  sent to OpenSky to ask which aircraft are nearby.
- Your coordinates are never stored, logged, or sent to any destination
  other than OpenSky's `states/all` endpoint.
- Location updates stop the moment the radar tab is closed — nothing runs
  or tracks you in the background.

If you deny the prompt, the radar simply can't show anything — there's no
fallback tracking method.

## Aircraft data from OpenSky

FlightPulse fetches publicly broadcast aircraft positions (callsign,
altitude, speed, heading) within roughly 200km of the location above, on
OpenSky's free anonymous tier by default. This is public flight-tracking
data about aircraft, not about you, and OpenSky's own terms govern how that
data may be used.

## Optional OpenSky account credentials

You can optionally enter your own OpenSky client ID and secret in Settings
for a higher rate limit. If you do:

- They're stored only in `chrome.storage.local` — local to your browser
  profile, on your own device.
- They're sent only to OpenSky's own authentication server, to obtain an
  access token — never to us, never anywhere else.
- We have no visibility into these credentials at any point; there's
  nowhere for them to go but your device and OpenSky.

## Registration/model lookups

Tapping an aircraft on the radar looks up its registration and model via
[adsbdb.com](https://api.adsbdb.com), a free public aviation database. Only
the aircraft's ICAO24 identifier is sent — no location or personal data
accompanies that request. Results are cached in memory for the current
session so the same aircraft isn't looked up twice.

## What we don't do

- No user accounts, sign-in, or profiles.
- No analytics, telemetry, or crash reporting.
- No advertising or ad networks.
- No cookies.
- No selling, renting, or sharing data with third parties beyond the two
  APIs named on this page, which receive only what's described above.

Questions about this policy or how FlightPulse handles data can be sent to
extension@guideaxon.com.

This policy may be updated as FlightPulse changes. Material changes to what
data is collected or how it's used will be reflected here with an updated
effective date.
