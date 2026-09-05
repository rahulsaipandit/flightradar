# deskradar
Flightradar display

Register at: https://opensky-network.org/
get the api from there.

pinout: <img width="372" height="629" alt="image" src="https://github.com/user-attachments/assets/3b508ce9-fae3-42f8-ad22-682656f8b529" />

## Layout

- `firmware/ESP32Radar/` — the ESP32 Arduino sketch (TFT radar display + OpenSky polling + WiFi captive-portal setup). Board-specific differences (screen orientation, default PIN, backlight GPIO, TLS mode) are selected via a single `#define BOARD_*` at the top of `board_config.h`.
- `kmp/` — Kotlin Multiplatform port of the same radar app, modeled on [PeopleInSpace](https://github.com/joreilly/PeopleInSpace): a shared `common` logic module plus native Compose UI modules per Android form factor (`app` for phone, `wearApp` for Wear OS, `compose-desktop` for Desktop). See `kmp/README.md`.
- `iosApp/` / `watchosApp/` — native SwiftUI apps for iOS and watchOS, consuming `kmp/common`'s business logic via its iOS framework output (Compose Multiplatform UI isn't used on either Apple platform).
- `docs/assembly/` — hardware assembly instructions.
- `docs/kmp-watch/` — design notes for the watch/Apple targets.
