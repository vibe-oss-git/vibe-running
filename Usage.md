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

### Starting
1. Open the app — you land on the **Run** tab.
2. Tap the large green **START** button in the center of the screen.
3. A notification appears confirming tracking is active. You can now lock your screen or switch apps — tracking continues in the background.

### During a Run
The screen displays real-time stats:
- **Duration** — large timer at the top (excludes paused time)
- **Distance** — in miles or kilometers
- **Pace** — minutes per mile or per kilometer
- **Speed** — current speed
- **Max Speed** — fastest speed recorded so far

### Pausing and Resuming
Tap the large **Pause** button (left circle) to pause tracking. The timer stops and "PAUSED" appears on screen. GPS data is not recorded while paused. Tap the **Play** button to resume.

### Stopping
Tap the large red **STOP** button (right side). A confirmation dialog appears showing your distance and duration. Choose:
- **Stop & Save** — ends the run and saves it to your history. You are taken to the activity detail screen.
- **Keep Going** — dismisses the dialog and continues tracking.
- **Stop Without Saving** — stops tracking and permanently discards the activity. The run will not appear in your history or stats.

---

## Viewing History

Tap the **History** tab in the bottom navigation bar. All completed runs are listed newest-first, showing:
- Date and start time
- Distance, duration, and average pace

Tap any run to open its detail screen.

---

## Activity Details

The detail screen shows full stats for a completed run:
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
