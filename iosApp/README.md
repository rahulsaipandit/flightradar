# iosApp

Native SwiftUI iOS app — sibling of `../kmp/` and `../watchosApp/`, not nested inside the Gradle project, since it's a separate Xcode project that only consumes a built artifact from the Gradle side (mirrors PeopleInSpace's `PeopleInSpaceSwiftUI` module: https://github.com/joreilly/PeopleInSpace).

It depends on `../kmp/common`'s iOS-target XCFramework (data models, OpenSky client, radar projection math) and renders its own SwiftUI views — no Compose Multiplatform UI is shared with iOS, matching the same approach used for `../watchosApp/`.

## Status

Placeholder only. No Xcode project checked in yet — create one once `common` has real logic to export as an XCFramework.
