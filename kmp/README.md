# FlightPulse (KMP)

Kotlin Multiplatform port of the ESP32 FlightPulse firmware (`../firmware/ESP32Radar/`), modeled on [PeopleInSpace](https://github.com/joreilly/PeopleInSpace)'s module layout: one shared logic module, a native Compose UI per Android form factor, and native SwiftUI for Apple platforms.

## Modules

- **`common`** — business logic only: data models, OpenSky API client (Keycloak OAuth2 + `/states/all` polling), radar projection math. No UI. This is what `../iosApp/` and `../watchosApp/` consume via its iOS framework output.
- **`app`** — Android phone app, Jetpack Compose UI over `common`.
- **`wearApp`** — Wear OS app, Compose for Wear Material UI over `common`.
- **`compose-desktop`** — Desktop app, Compose for Desktop UI over `common`.

iOS and watchOS are native SwiftUI apps living outside this Gradle project — see `../iosApp/` and `../watchosApp/`.

## Status

Structural scaffold only — module wiring and Gradle targets are in place, no feature code has been ported yet. See `../firmware/ESP32Radar/ESP32Radar.ino` for the logic to port into `common/src/commonMain`.

## Requirements

JDK 17+, Android Studio (Koala+) with the Kotlin Multiplatform plugin, Xcode for iOS/watchOS builds. Run `gradle wrapper` once inside `kmp/` to generate the Gradle wrapper before building.
