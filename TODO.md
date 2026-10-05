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
- Keep the existing 1s sampling interval and noise filters (accuracy >50m, speed >50m/s, jumps >100m)
- `GpsStatusMonitor` can use `GnssStatus.Callback` (API 24+) for satellite count/fix state
- Expect a slower first fix than the fused provider (no Wi-Fi/cell assist)
- Test on a real device during an actual run: fix acquisition time, distance accuracy, background tracking, auto-pause

## Export and import activity history, profile, and settings

Add a way to export activity history, profile data, and settings to a single file, and to import that file on another device, so moving to a new phone doesn't lose history or stats.

**Why**
- `android:allowBackup="false"` turns off cloud backup. According to Android's Auto Backup documentation, on Android 12+ it does not turn off device-to-device transfer, but whether that runs depends on the phone's migration tool, so it can't be relied on to move run history.
- The existing KML export is per-activity and meant for Google Earth. It doesn't hold everything needed to rebuild an activity.

**Notes**
- Use the Storage Access Framework (`ACTION_CREATE_DOCUMENT` / `ACTION_OPEN_DOCUMENT`) so the user chooses where the file goes. No storage permission needed, and no network use.
- Include a format version and the Room schema version (currently 2) in the file so older exports can still be imported after schema changes
- Stats are computed from the tables by Room queries, so restoring the two tables restores stats

**Export**
- Prompt the user to choose which areas to back up, all selected by default:
  - **Profile data** — height, weight, sex, date of birth (from `PreferencesManager`)
  - **Settings** — units, inactivity pause and end times (from `PreferencesManager`)
  - **Activity** — the `activities` and `location_points` tables
- Record in the file which areas it contains

**Import**
- After the user picks a file, read which areas it contains and offer only those as options, all selected by default. For example, a file exported without Profile data offers only Settings and Activity.
- Activity: merge with existing history and skip duplicates (e.g. an activity with the same start time). Imported activities get new IDs, and their location points must be re-linked to them.
- Profile data and Settings are single values, so importing them replaces the current values

**Other**
- Don't allow import while a run is in progress
- Large histories can mean a lot of location points; stream the read/write rather than loading everything into memory
