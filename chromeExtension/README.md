# FlightPulse — Chrome extension

A browser port of `kmp/wearApp`'s radar screen. This is a deliberate visual/behavioral **port**
of the Wear OS Compose UI — not an independent reinterpretation — so someone without a watch can
see what the watch app actually looks and works like. Every color, dimension, and interaction in
`radar.js`/`radar.css` is taken directly from `kmp/wearApp/src/main/kotlin/com/flightpulse/wear/RadarScreen.kt`
and `RadarViewModel.kt` (and `kmp/common`'s `AircraftClassification.kt`/`AircraftMetadataProvider.kt`/
`RadarMath.kt`), not approximated by eye. It renders full-width in a normal browser tab (not a
fixed circular watch-face mockup) — the individual controls keep the watch's compact native sizing
rather than being scaled up to fill a desktop screen.

**This is a hand-maintained clone, not shared code.** Kotlin Compose and JS/canvas share nothing at
build time — if `RadarScreen.kt` changes, this needs a matching manual update or the two will drift
apart, same risk called out below for the ESP32/KMP split in `docs/design.md`.

## Feature parity with the watch app

- **Zoom stepper**: pill-shaped `‹ 20 km ›`, 5–200km range, 5km steps, bottom-center.
- **Category filter menu**: hamburger button (top-center) opens a Military/Helicopters/Airliners/
  Private checklist plus a km/mi units toggle, all in one menu (matches the watch — there's no
  separate units control).
- **Aircraft classification**: ported 1:1 in `lib/aircraftClassification.js` — ADS-B category 7 =
  helicopter, US DoD ICAO24 hex block = military, `^[A-Z]{3}\d{1,4}[A-Z]?$` callsign regex =
  airliner, everything else = private. Same caveats apply (US-only military range, heuristic
  airliner detection — see `docs/design.md`).
- **Aircraft icons**: the actual bundled silhouette PNGs from
  `kmp/wearApp/src/main/res/drawable-nodpi/` (`aircraft-icons/` here), tinted per category with a
  canvas `source-in` composite — the same effect as Compose's `ColorFilter.tint(_, BlendMode.SrcIn)`.
- **Pinning**: tap an aircraft → PIN/PINNED button in the detail overlay; pinned callsign renders
  in purple, same as the watch. Only one pinned aircraft at a time.
- **Registration/model lookup**: `lib/aircraftMetadata.js` hits `api.adsbdb.com` lazily on tap,
  cached per icao24, identical to `AircraftMetadataProvider.kt`.
- **Pan**: pointer-drag on the canvas, converted to a km offset via the same
  `kmPerPixel = displayRangeKm / maxRadius` formula, using the exact equirectangular
  distance/bearing math from `RadarMath.kt` (ported in `lib/radarMath.js`) — not haversine.
- **Tap vs. drag**: a touch-slop threshold distinguishes a tap (select target) from a drag (pan),
  matching `RadarScreen.kt`'s custom pointer-input gesture logic.
- **Colors**: black background, grid `#00A000`/outer ring `#00FF00`, label text `#00E000`,
  military `#4B5320`, airliner `#FFD700`, private `#E53935`, callsign white (purple when pinned),
  altitude/speed `#4FC3F7`, climb arrow/UI chrome orange `#FFA000`, GPS marker `#2196F3`.
- **Sweep animation**: a 48-segment fading "comet tail" over a 90° span, 4-second linear rotation
  — same technique as `drawSweep()`, not a simple rotating line.
- **Polling cadence**: fixed 108s guest-mode interval (`POLL_INTERVAL_GUEST_MS`), same as
  `RadarRepository.kt` — not user-configurable, because it isn't on the watch either.

## Structure

- `manifest.json` — Manifest V3, side panel + options page, no popup.
- `background.js` — service worker; only sets defaults on install and enables
  "click the toolbar icon to open the side panel" behavior. No polling happens
  here — MV3 workers get killed after ~30s idle, so polling lives in the radar
  tab instead, which stays alive as long as the tab is open.
- `sidepanel.html`/`.js` — status + OpenSky credentials, opened via the toolbar icon.
  Has an "Open Radar" button that opens the radar in a new tab.
- `options.html`/`.js` — same settings form, reachable from
  `chrome://extensions` → Details → Extension options, or the small ⚙ button
  in the radar tab's top-left corner (not part of the watch UI — the
  credentials form has no watch-side equivalent).
- `radar.html`/`.js`/`.css` — the radar view itself, opened as a normal tab.
  Requests geolocation the same way any webpage would (a native Chrome
  permission prompt) — this only works from a tab/side panel document
  context, not from the service worker.
- `lib/opensky.js` — OpenSky client: anonymous tier by default; only calls the
  Keycloak OAuth2 client-credentials endpoint if the user has entered their
  own client id/secret in settings. Fetches a fixed 200km radius (matching
  `RadarViewModel.MAX_DISPLAY_RANGE_KM`) so zoom is a client-side re-filter,
  not a re-fetch — same design as the watch app.
- `lib/radarMath.js` — equirectangular distance/bearing/pan math, ported from
  `RadarMath.kt` (not haversine — matching the watch app exactly matters more
  here than using a "more correct" formula it doesn't use).
- `lib/aircraftClassification.js` — category classification, ported from
  `AircraftClassification.kt`/`MilitaryAllocation.kt`.
- `lib/aircraftMetadata.js` — adsbdb.com registration/model lookup, ported
  from `AircraftMetadataProvider.kt`.
- `lib/settings.js` / `lib/settingsForm.js` — `chrome.storage.local`-backed
  OpenSky credentials and the shared form UI used by both the side panel and
  options page. Deliberately does NOT hold zoom/pan/filter/units/pin state —
  that lives in `radar.js`'s in-memory state only, same as `RadarViewModel`
  not persisting across process death.
- `aircraft-icons/` — the exact PNG silhouettes from `kmp/wearApp`'s
  `res/drawable-nodpi/`, copied as-is.

## Why anonymous-by-default

Extension code is fully inspectable by users (`chrome://extensions` →
"Inspect views" / unpacked source), so a bundled OpenSky client secret
wouldn't actually be secret the way it is in the ESP32 firmware's NVS storage.
Instead, this follows the firmware's captive-portal pattern: ship working on
OpenSky's unauthenticated anonymous tier out of the box, and let each user
optionally supply their own OpenSky account credentials via the settings
screen for a higher rate limit.

## Privacy

`privacy.html` is the styled policy page, viewable by opening the file
directly or at `chrome-extension://<id>/privacy.html` once loaded — it's the
same content as the version hosted at
https://claude.ai/code/artifact/3107f07b-95f4-4b4d-b698-fcf3daa11e22, which
is what to paste into the Web Store's Privacy Practices tab. `PRIVACY.md` is
a plain-text copy of the same policy for quick reading in the repo/GitHub.

## Loading it unpacked (dev)

1. `chrome://extensions` → enable **Developer mode**.
2. **Load unpacked** → select this `chromeExtension/` folder.
3. Click the toolbar icon to open the side panel, or right-click it →
   **Options** for the settings page directly.

## Known gaps

- No background alerts or location history — same reasoning as
  `docs/design.md`'s "Later" column: MV3 service workers don't run
  continuously enough for reliable proximity alerts, and persistent location
  history has unresolved privacy/retention questions either way.
- No rotary-crown-equivalent zoom gesture (mouse wheel isn't wired up) —
  the stepper is the only zoom control, matching what's actually reachable
  without a physical crown.
- Not yet published to the Web Store (see `PUBLISHING.md`).

## Packaging for the Web Store

`npm run package` (see `package.json`/`scripts/package-extension.js`) zips
this folder into a Web Store-ready `flightpulse-extension.zip`. See
`PUBLISHING.md` for the full submission flow.

## Icon

`icons/generate_icons.py` draws the toolbar/tab icon with Pillow (concentric
green/blue/red radar rings, a white N-S/E-W crosshair, a black sweep line at
the 2 o'clock mark, and a yellow airplane silhouette with a thin black
outline) and rasterizes it at 16/48/128px. Re-run it after editing the
script; the checked-in PNGs are the committed output, not generated at build
time. This icon is a separate, simplified graphic — it does not need to match
the watch app's actual aircraft icons pixel-for-pixel the way the in-app
rendering does.
