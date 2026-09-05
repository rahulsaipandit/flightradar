# watchosApp

Native SwiftUI watchOS app — sibling of `../kmp/` and `../iosApp/`, not nested inside the Gradle project, since it's a separate Xcode project that only consumes a built artifact from the Gradle side.

It depends on `../kmp/common`'s iOS-target XCFramework (data models, OpenSky client, radar projection math) and renders its own SwiftUI views. Compose Multiplatform is not used on either Apple platform in this project — both `iosApp` and `watchosApp` are native SwiftUI, matching the pattern used by [PeopleInSpace](https://github.com/joreilly/PeopleInSpace).

## Status

Placeholder only. No Xcode project checked in yet — create one once `common` has real logic to export as an XCFramework. See `../docs/kmp-watch/README.md` for design notes.
