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
- Keep the existing 1s sampling interval and noise filters (accuracy >30m, speed >50m/s, jumps >100m)
- `GpsStatusMonitor` can use `GnssStatus.Callback` (API 24+) for satellite count/fix state
- Expect a slower first fix than the fused provider (no Wi-Fi/cell assist)
- Test on a real device during an actual run: fix acquisition time, distance accuracy, background tracking, auto-pause

## Export and import all activity history

Add a way to export every activity (the `activities` and `location_points` tables) to a single file, and to import that file on another device, so moving to a new phone doesn't lose history or stats.

**Why**
- `android:allowBackup="false"` is set, so Android's backup and device-to-device transfer don't carry app data over. Today a new phone starts empty.
- The existing KML export is per-activity and meant for Google Earth. It doesn't hold everything needed to rebuild an activity.

**Notes**
- Use the Storage Access Framework (`ACTION_CREATE_DOCUMENT` / `ACTION_OPEN_DOCUMENT`) so the user chooses where the file goes. No storage permission needed, and no network use.
- Include a format version and the Room schema version (currently 2) in the file so older exports can still be imported after schema changes
- Stats are computed from the tables by Room queries, so restoring the two tables restores stats
- Decide whether to include profile data and settings (`PreferencesManager`: height, weight, sex, date of birth, units, inactivity timers)
- Decide how import handles existing data: replace everything, or merge and skip duplicates (e.g. same start time)
- Don't allow import while a run is in progress
- Large histories can mean a lot of location points; stream the read/write rather than loading everything into memory
