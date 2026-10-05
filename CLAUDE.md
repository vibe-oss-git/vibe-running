# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## Build & Run

This is an Android project using Gradle with Kotlin DSL. Open in Android Studio or use command line:

```bash
# Build debug APK
./gradlew assembleDebug

# Build release APK
./gradlew assembleRelease

# Install on connected device
./gradlew installDebug

# Run all checks
./gradlew check

# Clean build
./gradlew clean
```

No test suite exists yet. The project uses KSP for Room annotation processing — run a full build after modifying Room entities or DAOs to regenerate code.

## Architecture

MVVM with these layers:

**Service → ViewModel → UI (Compose)** for live tracking
**Room DB → Repository → ViewModel → UI** for persisted data

### Tracking flow
`LocationTrackingService` is a bound foreground service using `FusedLocationProviderClient`. It samples GPS every 1s, filters noise (accuracy >30m, speed >50m/s, distance jumps >100m), calculates running totals, and persists `LocationPoint` rows via the repository. It exposes `StateFlow<TrackingState>` that `TrackingViewModel` collects by binding to the service via `ServiceConnection`.

### Data persistence
Room database (`vibe_running.db`) with two tables: `activities` (run sessions) and `location_points` (GPS breadcrumbs, foreign key to activity with CASCADE delete). `ActivityRepository` is the single access point — it provides both suspend functions and Flow-based reactive queries. Stats (top speed, longest distance, best pace) are computed via Room aggregation queries, not in-memory.

### DI approach
Manual construction in `VibeRunningApp` (Application subclass). Database singleton, repository, and `PreferencesManager` are created in `onCreate()` and accessed via casting `application as VibeRunningApp`. ViewModels use `AndroidViewModel` to access these.

### KML export
`KmlExporter` generates valid KML 2.2 with `gx:Track` extensions for Google Earth. Files go to cache dir, shared via `FileProvider` with scoped URI grants. The export is triggered from `ActivityDetailScreen` through `HistoryViewModel`.

### Navigation
Jetpack Compose Navigation with `NavHost`. Four bottom nav tabs (Run/History/Stats/Profile) plus `ActivityDetail` as a pushed route. Routes defined as sealed class in `NavGraph.kt`.

## Key Constraints

- **Fully offline** — no INTERNET permission, no network calls. All data stays on-device; `android:allowBackup="false"`.
- **License** — GPL-3.0 only, with a Play Services linking exception in `NOTICE`. Planned work is tracked in `TODO.md`.
- **GPS hardware required** — `uses-feature android:required="true"` in manifest.
- **Min SDK 26** (Android 8.0) — no need for pre-Oreo compat.
- **Units toggle** — imperial (default) or metric, stored in SharedPreferences. All internal calculations use meters/seconds; conversion happens at display time in `FormatUtils`.

## Dependencies

- Compose BOM 2024.12.01 with Material 3
- Room 2.7.1 with KSP compiler
- Google Play Services Location 21.3.0
- Lifecycle 2.8.7 (includes lifecycle-service for foreground service)
- Navigation Compose 2.8.5
- Kotlin 2.2.10, KSP 2.2.10-2.0.2, Gradle 9.3.1, AGP 9.1.1, compileSdk 35
- JDK 21 toolchain (see `Usage.md` for CLI build setup)
