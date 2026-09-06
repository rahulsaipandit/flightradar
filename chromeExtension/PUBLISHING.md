# Building & publishing the FlightPulse Chrome extension

This documents the general steps for taking `chromeExtension/` from source to a
published Chrome Web Store listing. It assumes the extension itself already
works locally (see `README.md` for the "load unpacked" dev flow) — this file
is about packaging and the store submission process specifically.

## 1. Anatomy recap (what makes this a valid extension)

Every Chrome extension needs, at minimum:

- **`manifest.json`** — declares the extension's name, version, permissions,
  and entry points (Manifest V3 is required for new Web Store submissions;
  MV2 is being phased out). This is the one file Chrome reads first.
- **An entry-point UI** — a popup, a side panel, an options page, or content
  scripts. FlightPulse uses a side panel (`sidepanel.html`) plus a full tab
  (`radar.html`) instead of a popup, since the radar view needs geolocation's
  native permission prompt (which service workers can't trigger) and more
  room than a popup gives.
- **A background service worker** (optional, but almost every non-trivial
  extension has one) — `background.js` here only seeds default settings and
  configures side-panel-on-click behavior; it deliberately does no polling,
  since MV3 workers are killed after ~30s idle.
- **Icons** — 16/48/128px PNGs, referenced from both `action.default_icon`
  and the top-level `icons` field in the manifest. Required for Web Store
  listings (a missing 128px icon is a common rejection reason).

## 2. Local development loop

1. `chrome://extensions` → enable **Developer mode**.
2. **Load unpacked** → select `chromeExtension/`.
3. After editing any file, click the refresh icon on the extension's card in
   `chrome://extensions` (service worker and manifest changes need this;
   HTML/CSS/JS changes in an already-open tab just need a tab reload).
4. Use **Inspect views** on the extension's card to open DevTools against the
   background service worker, side panel, or options page individually.

## 3. Pre-submission checklist

- [ ] Bump `"version"` in `manifest.json` (Web Store rejects re-uploading an
      unchanged version number, including for the very first submission if
      you've uploaded a draft before).
- [ ] Confirm `manifest_version: 3` — MV2 items are no longer accepted for
      new listings.
- [ ] Double-check `host_permissions` only lists what's actually called
      (`opensky-network.org`, `auth.opensky-network.org`) — Web Store review
      flags broad/unused host permissions.
- [ ] Remove any leftover dev artifacts (the `icons/generate_icons.py` script
      is fine to ship — it's inert, not loaded by the manifest — but don't
      ship `.git`, node_modules, or editor files if any crept in).
- [ ] Test the full flow once from a **clean profile** (`chrome://extensions`
      → load unpacked in a fresh Chrome user, no prior storage state): first
      run should default to the anonymous OpenSky tier with no errors before
      any settings are saved.
- [ ] Test the geolocation prompt itself — deny it once to confirm
      `radar.js`'s error path (`radar.js`'s `catch` branch) shows a sane
      message instead of a blank canvas.

## 4. Package it

Chrome Web Store uploads want a `.zip` of the extension's contents (not the
folder itself — the manifest must be at the zip's root):

```
cd chromeExtension
npm run package
```

This runs `scripts/package-extension.js`, a small dependency-free ZIP writer,
and writes `flightpulse-extension.zip` next to the repo root (pass a path as
an argument to write somewhere else instead). **Don't use PowerShell's
`Compress-Archive` for this** — on Windows/.NET Framework it stores entry
paths with backslashes (`aircraft-icons\aircraft_airliner.png`) instead of
the ZIP spec's required forward slashes, which the Web Store (and unzip on
non-Windows) can misread as flattened files instead of nested folders. The
script always emits forward-slash paths regardless of platform.

`PUBLISHING.md`/`README.md`/`PRIVACY.md`/`privacy.html` are included in the
package — harmless (not referenced by the manifest), not worth excluding.

## 5. Chrome Web Store Developer Dashboard

1. Register as a Chrome Web Store developer at
   `chrome.google.com/webstore/devconsole` — **one-time $5 registration fee**
   per developer account, paid via Google's payment system.
2. **New item** → upload `flightpulse-extension.zip`.
3. Fill in the store listing:
   - **Description** — short summary + longer description. Draft: *"See
     nearby aircraft on a live radar display, powered by the OpenSky Network.
     Works out of the box on OpenSky's free anonymous tier; optionally add
     your own OpenSky account for a higher polling rate."*
   - **Category** — Tools, or Productivity.
   - **Screenshots** — 1280×800 or 640×400 PNG/JPEG, at least one required.
     Capture the side panel (settings) and the radar tab (with a location
     granted and aircraft rendering) from a real loaded session.
   - **Icon** — already have `icons/icon128.png`; Web Store pulls it from the
     manifest but double-check it renders correctly in the dashboard preview.
4. **Privacy practices tab** (required for any listing requesting
   geolocation, a "sensitive" permission category):
   - **Single purpose description** — explain the extension does one thing:
     show nearby aircraft on a radar display.
   - **Permission justifications** — one sentence each for `storage`
     (persisting settings and OAuth token locally), `sidePanel` (the settings
     UI surface), and the two `host_permissions` (fetching aircraft data /
     refreshing OAuth tokens from OpenSky).
   - **Privacy policy URL** — required once geolocation is requested from a
     page context. Use:
     `https://claude.ai/code/artifact/3107f07b-95f4-4b4d-b698-fcf3daa11e22`
     (contact: `extension@guideaxon.com`). It's private by default — open it
     and use its share menu if the Web Store's reviewer needs to view it as a
     public link, though pasting the URL into the dashboard field itself
     works either way.
5. Submit for review. Typical review turnaround is hours to a few days;
   first-time submissions and anything touching sensitive permissions
   (geolocation counts) tend toward the longer end and sometimes get a manual
   follow-up questionnaire about data use.

## 6. After publishing

- **Updates**: bump `version` in `manifest.json`, re-zip, upload a new
  package version from the same dashboard item — no need to re-register.
- **Rejections**: the dashboard gives a specific reason (permission
  justification, privacy policy mismatch, etc.) — fix and resubmit rather
  than appealing blind.
- Consider gating the listing as **Unlisted** first (installable only via
  direct link, not searchable) if you want to soak-test with a few real users
  before a public launch.

## What's still needed before this can actually be submitted

- [ ] A Google developer account for the Web Store (see step 5.1) — this
      needs your own Google account and payment, can't be done for you.
- [x] A hosted privacy policy page — done, see step 5.4's URL.
- [ ] Real screenshots taken from a loaded session (side panel + radar tab).
- [x] Unlisted vs. public — confirmed public: this is being listed on the
      Web Store for general use, not soak-tested privately first.
