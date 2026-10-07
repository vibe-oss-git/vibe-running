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
`LocationTrackingService` is a bound foreground service using `FusedLocationProviderClient`. It samples GPS every 1s, filters noise (accuracy >50m, speed >50m/s, distance jumps >100m), calculates running totals, measures max speed with `SustainedSpeedTracker` (fastest straight-line speed over 5 s, confirmed by two consecutive windows, so single bad fixes can't set it), detects inactivity by distance from the last point where movement was seen (10m), and persists `LocationPoint` rows via the repository. It exposes `StateFlow<TrackingState>` that `TrackingViewModel` collects by binding to the service via `ServiceConnection`.

### Data persistence
Room database (`vibe_running.db`) with two tables: `activities` (run sessions) and `location_points` (GPS breadcrumbs, foreign key to activity with CASCADE delete). `ActivityRepository` is the single access point — it provides both suspend functions and Flow-based reactive queries. Stats (top speed, longest distance, best pace) are computed via Room aggregation queries, not in-memory. `activities.excludedRecords` is a bit field of `PersonalRecord` flags: a run the user disregarded for a record (long-press on the Stats screen) is skipped by that record's query only. Schema version 3; migrations are in `AppDatabase`. Saved runs' `maxSpeedMps` is recalculated from their points at app start whenever `SustainedSpeed.ALGORITHM_VERSION` is higher than the version stored in preferences; bump it when the max speed rules change.

### DI approach
Manual construction in `VibeRunningApp` (Application subclass). Database singleton, repository, and `PreferencesManager` are created in `onCreate()` and accessed via casting `application as VibeRunningApp`. ViewModels use `AndroidViewModel` to access these.

### KML export
`KmlExporter` generates valid KML 2.2 with `gx:Track` extensions for Google Earth. Files go to cache dir, shared via `FileProvider` with scoped URI grants. The export is triggered from `ActivityDetailScreen` through `HistoryViewModel`.

### Backup
`BackupFile` (`data/backup/`) writes and reads a streamed JSON backup (`android.util.JsonWriter`/`JsonReader`) with three optional sections: profile, settings, activities. The header (format, `formatVersion`, sections, activity count) comes first so import can list what a file contains without reading the data. Import merges activities (same `startTime` = duplicate, skipped; each activity inserted in its own transaction) and replaces profile/settings values after validating them. `BackupViewModel` runs it off the main thread; the UI is `DataBackupSection` on the Settings screen, using the Storage Access Framework (no storage permission). Bump `formatVersion` on incompatible format changes.

Device-to-device transfer is allowed and cloud backup is not: `allowBackup` is a resource (`false` on API 26–27, `true` on 28+), `res/xml/backup_rules.xml` (API 28–30) includes data only with `requireFlags="deviceToDeviceTransfer"`, and `res/xml/data_extraction_rules.xml` (API 31+) excludes everything from cloud backup and includes the database and preferences for device transfer.

### Navigation
Jetpack Compose Navigation with `NavHost`. Four bottom nav tabs (Run/History/Stats/Profile) plus `ActivityDetail` and `Settings` as pushed routes. `Settings` is opened from a gear icon on the Profile tab. Routes defined as sealed class in `NavGraph.kt`.

## Key Constraints

- **Fully offline** — no INTERNET permission, no network calls. Data leaves the device only through a file the user exports or a device-to-device transfer; never cloud backup.
- **Versioning** — every code change bumps `versionCode` by 1 in `app/build.gradle.kts`, and `versionName` too: minor (2.4 → 2.5) for new features, patch (2.4 → 2.4.1) for fixes only.
- **License** — GPL-3.0 only, with a Play Services linking exception in `NOTICE`. Planned work is tracked in `TODO.md`.
- **GPS hardware required** — `uses-feature android:required="true"` in manifest.
- **Min SDK 26** (Android 8.0) — no need for pre-Oreo compat.
- **Units toggle** — imperial (default) or metric, stored in SharedPreferences and set on the Settings screen. All internal calculations use meters/seconds; conversion happens at display time in `FormatUtils`.

## Dependencies

- Compose BOM 2024.12.01 with Material 3
- Room 2.7.1 with KSP compiler
- Google Play Services Location 21.3.0
- Lifecycle 2.8.7 (includes lifecycle-service for foreground service)
- Navigation Compose 2.8.5
- Kotlin 2.2.10, KSP 2.2.10-2.0.2, Gradle 9.3.1, AGP 9.1.1, compileSdk 35
- JDK 21 toolchain (see `Usage.md` for CLI build setup)
