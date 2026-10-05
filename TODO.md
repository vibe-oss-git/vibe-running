# TODO

## Replace Google Play Services location with Android `LocationManager`

Location updates currently come from `FusedLocationProviderClient` (`com.google.android.gms:play-services-location`), used in:

- `app/src/main/java/com/viberunning/service/LocationTrackingService.kt` — GPS sampling during a run
- `app/src/main/java/com/viberunning/util/GpsStatusMonitor.kt` — GPS status indicator on the Run tab

Switch both to the platform `LocationManager` with `GPS_PROVIDER` and drop the dependency from `app/build.gradle.kts`.

**Why**
- Works on devices without Google Play Services (LineageOS without GApps, GrapheneOS, Huawei, etc.)
- Location requests no longer pass through a Google component — consistent with the fully-offline design
- Removes the only proprietary dependency: eligible for F-Droid, and the Play Services linking exception in `NOTICE` can be dropped

**Notes**
- Keep the existing 3s sampling interval and noise filters (accuracy >30m, speed >50m/s, jumps >100m)
- `GpsStatusMonitor` can use `GnssStatus.Callback` (API 24+) for satellite count/fix state
- Expect a slower first fix than the fused provider (no Wi-Fi/cell assist)
- Test on a real device during an actual run: fix acquisition time, distance accuracy, background tracking, auto-pause

Make the length of the inactivity timer customizable

Currently the inactivity timer automatically exits the run after 2 minutes of inactivity. This works fine for where I run, it may not for you.
