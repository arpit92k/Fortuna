# Fortuna

An offline, on-device wealth tracker.

- [Architecture](docs/architecture.md)
- [User flows](docs/user-flows.md)
- [Roadmap](docs/roadmap.md)
- [Technology choices](docs/technology.md)
- [Screen map](docs/screen-map.md)
- [Implementation plan](docs/implementation-plan.md)

## Project layout

- `core`: the shared Kotlin Multiplatform module. It holds everything behind the user interface and is built for Android, the JVM and Linux.
- `androidApp`: the Android app, built with Jetpack Compose on top of `core`.

## Checks

Every pull request runs three checks on GitHub Actions:

- **Core tests:** `./gradlew :core:jvmTest`
- **Linux target:** `./gradlew :core:compileKotlinLinuxX64`
- **Debug app:** `./gradlew :androidApp:assembleDebug :androidApp:verifyDebugNoInternetPermission`

The debug app is attached to the run as `androidApp-debug.apk` and can be installed on a phone. It is signed with the key in the `DEBUG_KEYSTORE_BASE64` secret, so each build installs over the last.
