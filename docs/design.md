# DeskRadar Project Structure

This document describes how the repo is organized after splitting the ESP32 firmware from the new Kotlin Multiplatform (KMP) port.

## Overview

DeskRadar started as a single ESP32 sketch that polls the OpenSky Network API and renders nearby aircraft on a small TFT radar display. The repo now holds two independent implementations of that idea:

1. **`firmware/`** — the original ESP32/Arduino firmware, consolidated into one configurable sketch.
2. **`kmp/`, `iosApp/`, `watchosApp/`** — a from-scratch Kotlin Multiplatform port of the same functionality, targeting Desktop, Android, iOS, Wear OS, and watchOS. Its module layout mirrors [PeopleInSpace](https://github.com/joreilly/PeopleInSpace), a proven open-source KMP app with the same shape (poll an API, plot positions, run across phone/desktop/watch).

## Target audience & product rationale

This section refines an earlier informal audience analysis (`docs/reddit-post.md`) into something grounded in what this project can actually ship, on the actual surfaces it targets (ESP32 desk display, Wear OS/watchOS, phone/desktop apps) — not a hypothetical phone-camera AR app.

### Personas, ranked against feasibility (not just interest)

| Persona | Why they care | Best-served surface | Feasible with current architecture? |
|---|---|---|---|
| Flight-path residents / "what keeps flying over my house" | Ambient curiosity about a recurring nuisance/interest | ESP32 desk display, watch complication | **Yes** — this is the desk display's core use case already |
| Aviation enthusiasts / plane spotters | Identify type, airline, route | Phone/desktop app (detail view), desk display (ambient) | **Yes**, within OpenSky's data limits (see below) |
| Curious/general "what's that plane?" | Occasional, prompted by hearing/seeing an aircraft | Phone app | **Partially** — manual lookup yes; camera-pointing AR identification is a much larger, separate effort (needs camera + AR framework + attitude/heading sensor fusion) and is **out of scope** for this port |
| Photographers | Want advance notice of interesting aircraft | Phone/desktop app | **Partially** — needs an aircraft-type/registration filter, which needs a richer data source than OpenSky provides (see below) |
| Pilots / student pilots | Situational/traffic awareness outside the cockpit | Not a target surface | **No** — explicitly out of scope; this is not, and must never be marketed as, a traffic-separation or ATC tool |
| Frequent travelers / family tracking a flight | Track one specific flight | Not currently designed for | **No** — would need per-flight lookup (callsign search), not built |

Ranking criterion here is **"why + can we build it with `common`'s data source and these five surfaces"**, not raw popularity — a persona ranked lower isn't less interesting, it's less reachable without new capabilities (camera/AR, a backend, a richer aircraft database) this project hasn't scoped.

### Feature scope: what each surface can actually deliver

| Feature (from the original analysis) | v1 (buildable on `common` + OpenSky today) | Later (needs new capability) |
|---|---|---|
| **Nearby** — aircraft within range, radar-style | ✅ this is the existing ESP32 radar view, ported as-is | |
| **Overhead** — aircraft close to directly overhead | ✅ derivable from existing distance/bearing math | |
| **Identify by tap** — tap a target for callsign/altitude/speed | ✅ data already available from `/states/all` | |
| **Interesting** — filter by aircraft type/operator | | ❌ needs a registration/aircraft-type database; OpenSky's `states/all` doesn't include type, registration, or livery |
| **Point-your-camera identify (AR)** | | ❌ needs camera + AR framework + compass/attitude fusion; a materially different, larger project |
| **Alerts** ("a 747 is 10mi away") | | ❌ needs background location + push infra; expensive on watch battery budgets, deferred until there's a concrete design for it |
| **History** ("what flew over my house today") | | ❌ needs persistent per-user location-tagged storage; privacy/retention questions (see below) need answering before this is built, and it should start as local-only device storage, not a backend service |

### Known constraints this analysis has to work within

- **OpenSky data is coarser than the original analysis assumed.** `/api/states/all` returns position, altitude, heading, and velocity — not aircraft type, registration, or livery. Several example UI mockups in the original analysis ("747-8 — Lufthansa Cargo", "C-17 Globemaster III") need a second, enriched data source that isn't part of this project yet.
- **OpenSky's usage terms constrain any engagement loop.** Free-tier polling is rate-limited and the ToS restricts commercial use — any notification/alert feature has to be designed around that ceiling, not around ideal UX first.
- **Military and sensitive aircraft aren't guaranteed to appear.** Feeds like OpenSky commonly lack aircraft that are blocked from public ADS-B distribution (military, VIP, law enforcement); a UI shouldn't imply full traffic visibility.
- **The "hear it, look up, open the app" flow has a real latency gap.** Sound arrives after the aircraft has moved on, and polling itself lags by several seconds to tens of seconds — the app can corroborate what someone just heard, but shouldn't be presented as instantaneous confirmation.
- **This isn't a novel feature set.** FlightRadar24 and similar apps already ship proximity views and AR "point to identify." The actual differentiator here is the **ambient hardware form factor** (a physical desk radar, a watch face) that those apps don't offer — that's where product effort should concentrate, not on re-building phone-app features that already exist elsewhere.
- **Background alerts and history both have cost implications** beyond engineering effort: continuous location polling drains watch battery fast, and persisting a user's location history (for "what flew over my house") is a privacy-sensitive feature that should default to on-device storage with a clear retention limit, not silent cloud aggregation.

## Top-level layout

```
flightradar/
├── README.md
├── docs/
│   ├── design.md                 (this file)
│   ├── assembly/
│   │   └── Desk Radar Instructions assembly.pdf
│   └── kmp-watch/
│       └── README.md             (watch/Apple-platform design notes)
├── firmware/
│   └── ESP32Radar/
│       ├── ESP32Radar.ino        (consolidated sketch)
│       └── board_config.h        (per-board #define config)
├── kmp/
│   ├── settings.gradle.kts       (includes :common, :app, :wearApp, :compose-desktop)
│   ├── build.gradle.kts
│   ├── gradle.properties
│   ├── common/                   (shared business logic, no UI)
│   ├── app/                      (Android phone, Jetpack Compose)
│   ├── wearApp/                  (Wear OS, Compose for Wear)
│   └── compose-desktop/          (Desktop, Compose for Desktop)
├── iosApp/                       (native SwiftUI, top-level — not a Gradle module)
└── watchosApp/                   (native SwiftUI, top-level — not a Gradle module)
```

## ESP32 firmware (`firmware/ESP32Radar/`)

The project previously had 4 divergent Arduino sketches that had been copy-pasted and hand-tweaked per physical device. They've been consolidated into a single `ESP32Radar.ino`, with the per-device differences captured as compile-time config in `board_config.h`:

| Config field | What it controls |
|---|---|
| `DEFAULT_DEVICE_PIN` | Default captive-portal setup PIN |
| `SCREEN_WIDTH` / `SCREEN_HEIGHT` | Physical TFT resolution |
| `GRID_CENTER_X` / `GRID_CENTER_Y` | Center point used for full-screen status text |
| `BACKLIGHT_PIN` | GPIO driving the display backlight |
| `SOFTAP_SSID` / `PORTAL_TITLE` | WiFi setup portal branding |
| `FORCE_INSECURE_TLS` | Whether to bypass TLS cert validation (`WiFiClientSecure::setInsecure()`) for boards that hit HTTP 308 errors |

Exactly one `BOARD_*` block is active via `#define` at the top of the file. The 240x240 radar sprite itself (grid circles, sweep line, plane triangles) is fixed regardless of board — only full-screen status text and portal branding vary.

The firmware handles its own runtime configuration (WiFi credentials, OpenSky client ID/secret, PIN) through a WiFi captive portal backed by ESP32 `Preferences` (NVS) — there are no secrets hardcoded in source to extract.

## KMP port

The goal is to reimplement the same radar functionality — OpenSky polling, OAuth2 token refresh, lat/lon-to-radar projection math — once in Kotlin, then present it with a UI native to each platform.

### `kmp/common` — shared business logic

Holds everything that's directly portable from the ESP32 C++ logic:

- Data models (aircraft/target: icao24, callsign, lat, lon, altitude, heading)
- `OpenSkyApiClient` (Keycloak OAuth2 client-credentials flow + `/api/states/all` polling)
- Radar projection math (lat/lon → x/y, distance/bearing)

No UI code lives here. `expect`/`actual` is used only for what's genuinely platform-specific: the HTTP client engine and persistent settings storage. This module builds for JVM (desktop), Android, and iOS (as a framework/XCFramework) — the same shared artifact is consumed by every other module and by the native Apple apps.

### `kmp/app`, `kmp/wearApp`, `kmp/compose-desktop` — Compose UI modules

Each is a separate Gradle module with its own Compose UI, rather than one module targeting multiple platforms:

- **`app`** — Android phone, Jetpack Compose.
- **`wearApp`** — Wear OS, Compose for Wear Material (separate app, since a watch UI needs its own layout, not a shrunk phone screen).
- **`compose-desktop`** — Desktop, Compose for Desktop.

All three depend on `common` as a plain Gradle project dependency.

### `iosApp/` and `watchosApp/` — native SwiftUI

Compose Multiplatform can render on iOS, but this project deliberately does **not** share Compose UI there — both Apple targets get native SwiftUI apps instead, consuming `common`'s iOS XCFramework directly. This matches PeopleInSpace's approach and keeps both Apple apps feeling native rather than like a ported Android UI.

Both live at the **top level**, as siblings of `kmp/`, not nested inside the Gradle project — they're separate Xcode projects that only consume a built artifact (the XCFramework) from the Gradle side; nesting them inside `kmp/` wouldn't buy anything and would mix Gradle/Xcode tooling awkwardly.

### Why not one shared Compose UI for everything?

The original plan called for one Compose Multiplatform UI shared across Desktop/Android/iOS/Wear OS. After reviewing PeopleInSpace, we switched to per-platform native UI for both Apple targets (SwiftUI) while keeping Compose shared across the three Android/JVM-family targets (phone, Wear OS, Desktop). Native UI per platform is more proven, especially on iOS/watchOS where full Compose Multiplatform support is newer; the shared `common` module still means the actual app logic (networking, math, state) is written once.

## On-device voice assistant (Wear OS)

A planned `wearApp` feature: press a mic button, ask a question about nearby traffic ("what's the nearest plane?"), and get a spoken/text answer generated on-device — no cloud dependency. Design notes below; not yet implemented.

### Pipeline

1. **Speech-to-text**: try Wear OS's built-in `SpeechRecognizer` / `RecognizerIntent.ACTION_RECOGNIZE_SPEECH` with `EXTRA_PREFER_OFFLINE=true` first — if it runs as a separate system process, it costs the app's memory budget nothing. **This is not guaranteed, though** — see below, the source author explicitly hit "no voice recognizer" on their device and had to build ASR from scratch. Treat this as the first thing to validate on our actual target hardware, not an assumed free win, and keep a bundled-ASR fallback (e.g. whisper.cpp tiny) in the design, sized against the same RAM budget as the LLM.
2. **Context, not knowledge**: the LLM never answers from its own weights. It's handed the already-fetched, already-computed aircraft list from `kmp/common` (callsign, distance, altitude, heading — the same data the radar view renders) plus the recognized question, and its only job is to phrase a concise natural-language answer over that structured data. This keeps the prompt tiny (a handful of aircraft, not a document) and avoids hallucinated flight data.
3. **On-device LLM inference**: [LFM2.5-350M-GGUF](https://huggingface.co/LiquidAI/LFM2.5-350M-GGUF) via llama.cpp, chosen over SmolLM2-360M (the model used in the source reference, `docs/reddit-post.md`) because Liquid built the LFM2 family specifically for edge/low-spec hardware (a hybrid conv+attention architecture rather than a standard transformer) rather than adapting a general-purpose model after the fact.
4. **Output**: short text answer, optionally read aloud via Android `TextToSpeech`.

### Memory: verified against a real on-device benchmark, not assumption

`docs/reddit-post.md`'s `host_ptr`/mmap fix solves a specific problem: llama.cpp allocates a *second* full copy of the model when tensors need staging for GPU (Vulkan/OpenCL/Metal) offload, on top of the mmap'd CPU copy — doubling peak RAM (524MB for a 270MB model). Before assuming this project needs the same patch, we checked [`shige0501/wear-os-local-llm`](https://github.com/shige0501/wear-os-local-llm), a real Wear OS build of this *exact* model (LFM2.5-350M, Q4_K_M/Q4_0, llama.cpp, tested on a Pixel Watch 4):

- It runs **CPU-only** (NEON) — its GPU/OpenCL path "loads & generates, then hangs" (immature driver). Since GPU offload is what triggers the duplicate-copy bug, and Wear OS GPU backends are unreliable in general, **we're not carrying the `host_ptr` patch** — we're staying CPU-only, which shouldn't hit that bug in the first place.
- That said, peak RAM was still **~375MB CPU-only** for a ~175–200MB model file — not free. Their own notes say the setup is "invariant to mmap" (enabling/disabling it barely moved the number), which matches the source post's other finding: Android counts file-backed mmap pages fully against the app's tracked memory (unlike desktop OSes, which hide them as reclaimable cache). So there's real overhead here beyond the GPU-duplication bug specifically — KV cache, compute buffers, JNI/Android overhead — worth profiling on our actual target device/quant rather than assuming it's small.

### Constraints from the source author's own account

The reddit post (`docs/reddit-post.md`) is a shortened writeup; the author's original LinkedIn post lists the actual device constraints behind it, on a Samsung Galaxy Watch 4 Classic — worth carrying forward as things to validate rather than assume away:

- **1.5GB total RAM, 380MB free** — the number already used above.
- **`armeabi-v7a` (32-bit) despite 64-bit-capable hardware.** This is the same thing we found independently in the `wear-os-local-llm` benchmark — not a one-off build mistake, but an apparent pattern on Wear OS devices where the shipped system image/ABI doesn't match what the SoC could support. Building our own APK for `arm64-v8a` doesn't guarantee the *device* will run it as 64-bit; this needs verifying per target device, with a 32-bit fallback path budgeted in rather than assumed unnecessary.
- **Mali G68 GPU via Vulkan, no shader cache.** Beyond the memory-duplication problem already covered, a missing shader cache means every cold start on the GPU path pays full shader compilation again — another independent reason to stay CPU-only, not just the duplicate-copy and driver-stability issues already noted.
- **"No voice recognizer; need to build from scratch."** The author had to abandon the built-in speech-recognizer assumption entirely on this hardware (tried SenseVoice, ended up hand-rolling a CMUdict-based phoneme decoder). This directly weakens the plan's step 1 above — confirms it needs validating early, with a real fallback budgeted, not assumed available.
- **Android SEpolicy blocked "clever" tricks.** SELinux restrictions prevented several lower-level optimization attempts. Any memory or IPC trick beyond standard public APIs (shared memory segments, non-standard mmap flags, etc.) needs to be checked against the target device's SELinux policy early — what works on a rooted dev machine or desktop Linux may simply be denied on a locked-down Wear OS build.

### Speed is the bigger open risk, not memory

The same benchmark measured **~0.28–0.31 tokens/sec** CPU decode — a 30-token answer would take 100+ seconds. Part of that is a fixable build issue (they shipped `armeabi-v7a` 32-bit, missing llama.cpp's faster arm64-only quantized kernels), but even a 3–5x improvement from building `arm64-v8a` properly still lands in single-digit tokens/sec territory on watch-class CPUs. This means:

- Build llama.cpp for **`arm64-v8a` only** (all modern Wear OS chips support it; don't ship 32-bit).
- Design the UX around **several-seconds-to-tens-of-seconds latency**, not instant response — an explicit "thinking" state, not a chat-style expectation.
- **Cap answers tightly** (15–25 tokens — "nearest aircraft is a Boeing 737, 4 miles" is plenty; no paragraphs).
- **Benchmark on our actual target hardware/quant before committing further** — both the RAM and tok/s numbers above are quant/device-specific; don't design further against assumed numbers.
- Gate the feature behind a device capability/RAM check, with a non-LLM rule-based fallback (regex over "nearest/how many/altitude"-style phrases against the same structured aircraft list) so older/lower-RAM watches still get an answer, just not an LLM-phrased one.
- **If the system speech recognizer isn't available and a bundled ASR model is needed**, it and the LLM can't both stay resident in ~375MB of headroom — load sequentially (ASR model in, transcribe, unload, then load the LLM) rather than assuming a combined memory budget.

### Where this lives in the repo

Android/Wear-only: JNI llama.cpp bindings and `SpeechRecognizer` are Android APIs, so the native inference glue, model loading, and voice input live in `kmp/wearApp`'s Android source set — not `kmp/common`. `common` owns the reusable, pure-Kotlin piece: serializing the current aircraft list into the LLM's prompt context. An equivalent iOS/watchOS voice feature (whisper.cpp/CoreML + llama.cpp-Metal) would be a separate, later effort, not in scope now.

### Connecting a physical Wear OS watch for development

One-time setup to deploy `kmp/wearApp` to a real watch (no USB port on Wear OS, so this is wireless):

1. **Enable Developer Options on the watch**: Settings → About watch → tap **Software version** (or **Build number**) 7 times.
2. **Enable debugging** in Settings → Developer options — two paths depending on watch/OS version:
   - **Wireless debugging** (Wear OS 3+, most current watches): enable it, then **Pair new device** — shows an IP:port and a 6-digit code. Watch and computer must be on the same Wi-Fi network.
   - **Debug over Bluetooth** (older/some Samsung watches): enable it on the watch, then in the phone's paired **Wear OS companion app** → Advanced settings → Developer options, enable the matching Bluetooth debugging toggle.
3. **Connect from a terminal**:
   - Wireless debugging: `adb pair <ip>:<pairing-port>` (enter the 6-digit code), then `adb connect <ip>:<connect-port>` (a second port shown on the watch's main Wireless debugging screen).
   - Bluetooth debugging: `adb forward tcp:4444 localabstract:/adb-hub` then `adb connect localhost:4444`.
4. **Verify**: `adb devices` should list the watch as `device` (if it shows `unauthorized`, approve the prompt on the watch face).
5. **Deploy**: open `kmp/` in Android Studio, select a `:wearApp` run configuration, pick the connected watch from the device dropdown, and Run. `wearApp/MainActivity` (below) gives this something real to show.

### Command-line build/install (no Android Studio)

`:wearApp:installDebug` builds the debug APK and installs it over the adb connection from step 5 above in one command:

```
cd kmp
.\gradlew.bat :wearApp:installDebug   # gradlew on macOS/Linux
```

Two one-time environment issues came up getting this working on a fresh Windows machine, worth knowing before hitting them again:

- **`adb` not on PATH**: it ships inside the Android SDK's `platform-tools/` (e.g. `%ANDROID_HOME%\platform-tools`), not as a standalone install — add that folder to PATH rather than expecting `adb` to already resolve just because Android Studio or an emulator is installed.
- **JVM toolchain mismatch**: Android Studio's bundled JDK (JBR) is often newer (e.g. 21) than AGP's default javac target (1.8), which fails the build with `Inconsistent JVM-target compatibility detected`. Fixed by pinning `kotlin { jvmToolchain(17) }` plus `android.compileOptions` to Java 17 in every module's `build.gradle.kts` (`common`, `app`, `wearApp`, `compose-desktop`) — do this once up front rather than per-module when it breaks.
- The Gradle wrapper (`gradlew`, `gradlew.bat`, `gradle/wrapper/`) wasn't committed until this point — bootstrapped via `gradle wrapper --gradle-version 8.13` using a Gradle install Android Studio had already cached locally. It's checked in now, so this is a one-time cost.
- Committing the wrapper on Windows needs a `.gitattributes` (`gradlew text eol=lf`) — otherwise `core.autocrlf=true` silently corrupts `gradlew`'s line endings on checkout, breaking it as a Unix shell script even though `gradlew.bat` still works fine.

## Development vs. production settings

Getting the live radar working on real hardware required a few dev-convenience settings that are genuinely bad for battery life in normal use — worth tracking explicitly rather than quietly shipping them.

**How the split is implemented**: not a `.env` file — that's a runtime-file-read convention from Node/web apps and doesn't really map onto a compiled Android app. The idiomatic equivalent here is Gradle **build types** (`debug`/`release`) with `buildConfigField`, which bakes a constant into a generated `BuildConfig` class at compile time (see `kmp/wearApp/build.gradle.kts`) — no runtime file parsing, and R8/ProGuard can strip debug-only branches entirely out of a release build. `MainActivity.kt` reads `BuildConfig.KEEP_SCREEN_ON` rather than hardcoding the behavior.

| Setting | Current (dev/debug) | Desired (production/release) | Status |
|---|---|---|---|
| Screen timeout | `FLAG_KEEP_SCREEN_ON` forces the screen on continuously — added because the watch's default timeout was kicking the app back to the watch face mid-GPS-fix, which looked like a crash during testing | Off — let the system manage screen timeout normally; a real app shouldn't force the display on | ✅ Done — gated behind `BuildConfig.KEEP_SCREEN_ON` (`true` in debug, `false` in release) |
| Sweep animation | Paused in ambient mode — `MainActivity` implements `AmbientModeSupport.AmbientCallbackProvider` (from `androidx.wear:wear`, which requires the Activity to be a `FragmentActivity`, not just `ComponentActivity` — that's why `MainActivity` extends `FragmentActivity` now), bridging `onEnterAmbient`/`onExitAmbient` into a Compose `isAmbient` state. `RadarScreen` skips creating the `rememberInfiniteTransition`/`animateFloat` entirely while ambient (not just ignoring its output — a merely-unused animation still ticks a Choreographer callback every frame) and hides the interactive chrome (zoom stepper, recenter). | Same as implemented | ✅ Implemented, compiles for both build types — not yet verified on-device (watch battery too low to stay connected) |
| GPS updates | `RadarViewModel.setForeground(Boolean)` is called from `MainActivity.onStart()`/`onStop()`. `location` is now built via `isForeground.flatMapLatest { fg -> if (fg) locationProvider.observeLocation() else flowOf(null) }` — when backgrounded, `flatMapLatest` cancels the collector on the GPS flow, which genuinely stops the underlying `FusedLocationProviderClient` updates (not just a no-op in our own code), while the outer `stateIn` stays `Eagerly` so `RadarRepository`'s `.value` reads still work correctly (same reasoning as the earlier "stuck on Getting location" fix — `Eagerly` there is non-negotiable, the gating happens one level down instead). | Same as implemented | ✅ Implemented, compiles for both build types — not yet verified on-device |
| OpenSky polling | ~108s guest-mode cadence, correctly paused when backgrounded via `SharingStarted.WhileSubscribed(5_000)` on `snapshot` (this one **is** genuinely collected by the UI, so `WhileSubscribed` works as intended here) | Same as current — this one's already right | ✅ Already correct, no change needed |

**Net effect**: all four battery-relevant knobs now have a real production-shaped answer, not just a documented gap. What's unverified is whether they *behave* correctly on real hardware — the watch's battery is too depleted to hold a Wi-Fi debugging connection long enough to test, so this needs re-confirming once it's charged: does ambient actually stop the CPU/GPU work, does backgrounding actually drop the GPS radio, does re-foregrounding correctly resume both.

## Current status

This is a structural scaffold, not a working app: `firmware/ESP32Radar/` has real, consolidated logic, but most KMP modules and both native Apple app folders still contain only Gradle wiring / placeholder READMEs, no ported feature code yet. The one exception is `kmp/wearApp`, which has a minimal "Hello DeskRadar" Compose screen (`MainActivity.kt`) — a connectivity smoke test, not the real radar UI, kept in place until there's real data from `common` to render. This has been built and installed on a real Galaxy Watch (SM-R935U) via the command-line flow above, confirming the Gradle wrapper, toolchain config, and adb pairing all actually work end-to-end. The on-device voice assistant above is design notes only — nothing has been benchmarked or built. See each module's README for what's next.
