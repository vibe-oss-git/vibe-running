# Vibe Running

A private, fully offline GPS run tracker for Android. No accounts, no cloud, no internet permission — your runs, routes and profile never leave your phone.

## Features

- **Live tracking** — duration, distance, pace, speed and max speed, with a persistent notification so tracking continues with the screen off
- **Reliable in the background** — progress is saved every 30 seconds and the tracking service recovers if Android kills it
- **Auto-pause** — pauses after 1 minute without movement, resumes when you move, and finishes the run after 2 minutes so a forgotten session doesn't drain your battery (both times are configurable)
- **Automatic lap detection** — laps are counted when you pass back near your starting point, with a lap splits table after the run
- **Route map** — colored by speed or elevation, with pinch-to-zoom and a speed range filter
- **Stats** — personal records (top speed, longest distance, best pace, longest run), lifetime totals, and per-activity trend charts
- **Calorie estimates** — based on the Mifflin-St Jeor BMR equation and MET values from the Compendium of Physical Activities
- **KML export** — share any run as a KML 2.2 file with a timed track for playback in Google Earth
- **Imperial or metric** units

See [Usage.md](Usage.md) for a full guide to every screen.

## Privacy

- The app has no `INTERNET` permission and makes no network calls.
- All data is stored in a local database on the device. Android backup is disabled, so it isn't copied to cloud backups.
- Exported KML files are only shared when and where you choose to share them.

Location updates currently come from Google Play Services' fused location provider, so the app needs Play Services on the device. Replacing it with Android's built-in `LocationManager` is planned. See [TODO.md](TODO.md).

## Requirements

- Android 8.0 (API 26) or newer
- A device with GPS hardware
- Google Play Services

## Building

The Gradle wrapper is included. You need the Android SDK (platform 35) and JDK 21.

```bash
./gradlew assembleDebug    # build a debug APK
./gradlew installDebug     # install it on a connected device
```

The APK is written to `app/build/outputs/apk/debug/app-debug.apk`. See [Building from the Command Line](Usage.md#building-from-the-command-line) for SDK and JDK setup details.

## Tech stack

Kotlin, Jetpack Compose (Material 3), Room, Navigation Compose, and a foreground location service, structured as MVVM. Built with AGP 9.1.1, Kotlin 2.2.10 and Gradle 9.3.1, targeting SDK 35.

## License

Copyright (C) 2026 vibe-oss-git

Licensed under the [GNU General Public License v3.0](LICENSE) only. See [NOTICE](NOTICE) for an additional permission covering linking with Google Play Services.
