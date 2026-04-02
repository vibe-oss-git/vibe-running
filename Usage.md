# Usage Guide — Vibe Running

## Getting Started

### Installation
Build and install the APK from Android Studio or via `./gradlew installDebug` with a device connected. The app requires a device with GPS hardware — it will not appear in the Play Store for devices without GPS.

### Permissions
On first launch, the app requests:
- **Location (Fine & Coarse)** — required for GPS tracking. Tracking will not work without this.
- **Notifications** (Android 13+) — required to show the persistent tracking notification while a run is in progress.

Grant both permissions when prompted. If denied, you can re-enable them in your device's Settings > Apps > Vibe Running > Permissions.

### No Internet Required
Vibe Running is completely offline. It never connects to the internet. All your data stays on your device.

---

## Tracking a Run

### GPS Signal
Before you can start, the app must acquire a GPS signal. A status indicator at the top of the Run tab shows the current state:
- **GPS Unavailable** — GPS is disabled on your device. Enable it in system settings.
- **Searching for GPS...** — the app is acquiring satellites. Wait for a fix.
- **Weak GPS Signal** — a fix is acquired but accuracy is poor. You can wait for it to improve.
- **GPS Ready** — signal is strong. The START button becomes active.

The START button is disabled until GPS reaches the "Ready" state.

### Starting
1. Open the app — you land on the **Run** tab.
2. Wait for the GPS status to show **GPS Ready**.
3. Tap the large green **START** button.
4. A notification appears confirming tracking is active. You can now lock your screen or switch apps — tracking continues in the background.

### During a Run
The screen displays real-time stats:
- **Duration** — large timer at the top (excludes paused time)
- **Distance** — in miles or kilometers
- **Pace** — minutes per mile or per kilometer
- **Speed** — current speed
- **Max Speed** — fastest speed recorded so far

### Pausing and Resuming
Tap the large **PAUSE** button (left side) to pause tracking. The timer stops and "PAUSED" appears on screen. GPS data is not recorded while paused. Tap **RESUME** to continue.

### Stopping
Tap the large red **STOP** button (right side). A full-screen confirmation appears with three options, spread vertically:
- **Stop Without Saving** (top) — stops tracking and permanently discards the activity.
- **Keep Going** (middle) — dismisses and continues tracking.
- **Stop & Save** (bottom) — ends the run, saves it, and opens the activity detail screen with the speed map.

---

## Viewing History

Tap the **History** tab in the bottom navigation bar. All completed runs are listed newest-first, showing:
- Date in **MM/DD/YYYY** format and the day of the week
- Start and end times
- Distance, duration, and average pace

Tap any run to open its speed map and detail screen.

---

## Activity Details & Speed Map

The detail screen opens with a **speed map** at the top — a plotted route of your run colored by speed:
- **Blue** segments = slowest
- **Green/Yellow** segments = moderate
- **Orange/Red** segments = fastest
- A **green dot** marks the start and a **red dot** marks the finish
- A gradient legend below the map shows the speed range in **mph to 2 decimal places**

Below the map, full stats are shown:
- Distance, duration
- Average pace, average speed
- Max speed
- Number of GPS points recorded

### Exporting to Google Earth (KML)
1. Open an activity from the History tab.
2. Tap **Export to KML (Google Earth)**.
3. A share sheet appears — choose where to save or send the file (e.g., Files, Google Drive, email).
4. Open the `.kml` file in Google Earth to see your route with:
   - A green pin at the start and red pin at the finish
   - A blue line tracing your path
   - An animated timed track for playback

The export button is disabled if no GPS data was recorded for the activity.

### Deleting an Activity
Tap the trash icon in the top-right corner of the detail screen. Confirm the deletion — this permanently removes the activity and all its GPS data.

---

## Stats

Tap the **Stats** tab to see your all-time records and totals.

### Personal Records
- **Top Speed** — fastest instantaneous speed across all runs
- **Longest Distance** — single run with the most distance
- **Best Pace** — run with the fastest average pace
- **Longest Run** — run with the greatest duration

### Lifetime Totals
- Total number of completed activities
- Total distance across all runs
- Total time spent running
- Average distance per run

Stats update automatically as you complete more runs.

---

## Settings

### Switching Units
The app defaults to **imperial** units (miles, mph, min/mi). To switch to **metric** (kilometers, km/h, min/km):
- The unit preference is stored locally and persists across app restarts.

All internal measurements use meters and seconds — unit conversion is applied only at display time, so switching units does not affect your saved data.
