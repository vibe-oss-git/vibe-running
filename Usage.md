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
4. A persistent notification appears showing the running timer, distance, pace, and speed. You can now lock your screen or switch apps — tracking continues in the background. The notification updates every second.

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

The detail screen opens with an interactive **speed map** at the top — a plotted route of your run colored by speed:
- **Blue** segments = slowest
- **Green/Yellow** segments = moderate
- **Orange/Red** segments = fastest
- A **green dot** marks the start and a **red dot** marks the finish
- A gradient legend below the map shows the speed range in **mph to 2 decimal places**

### Zooming and Panning
- **Pinch** to zoom into the map and inspect specific sections of your route
- **Drag** to pan around while zoomed in
- **Double-tap** to reset the view to the original zoom level

### Adjusting the Speed Range
Below the map is a **range slider** that controls which speeds are displayed:
- Drag the **left handle** to raise the minimum speed — segments slower than this become hidden (gaps in the route)
- Drag the **right handle** to lower the maximum speed — segments faster than this become hidden
- The full color gradient always maps to the selected range, giving you finer detail in the range you care about
- Tap **Reset** to restore the original range

For example, if your run shows speeds from 3.00 to 8.00 mph and you set the range to 5.00–8.00, only the portions where you ran at least 5 mph will be visible, with the full blue-to-red gradient spread across that 5–8 mph range.

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
- Total calories burned
- Average calories per run

Stats update automatically as you complete more runs. Deleting an activity updates all stats and records accordingly.

---

## Profile

Tap the **Profile** tab in the bottom navigation bar to manage your height, weight, and BMI.

### First Launch
On first launch, the app takes you directly to the Profile screen. You must enter your height and weight before you can start tracking — this data is needed to estimate calories burned.

### Height & Weight
- Enter your height in feet/inches (imperial) or centimeters (metric)
- Enter your weight in pounds (imperial) or kilograms (metric)
- All values are stored locally on your device

### BMI
Once your profile is saved, the screen displays your current **BMI** (Body Mass Index) with a category label (Underweight, Normal, Overweight, Obese). BMI is calculated as (weight × 703) / height² using imperial units. Note that BMI is a general indicator and does not account for muscle mass or body composition.

### Weight Updates
Every **14 days**, the app prompts you to update your weight. Keeping this current improves the accuracy of calorie estimates. You can also update your weight at any time from the Profile tab.

---

## Calorie Tracking

Each completed activity includes an estimated calorie count based on:
- Your current weight
- The average speed of the activity
- The duration of the activity

The estimation uses the **MET method** (Metabolic Equivalent of Task) with values from the Compendium of Physical Activities. MET values are interpolated based on your running speed — faster running burns more calories per minute.

Calories appear in:
- **History** cards — shown next to the time range
- **Activity Detail** — displayed in the stats grid
- **Stats** tab — total and average calories in lifetime totals

For the most accurate estimates, keep your weight up to date in the Profile tab.

---

## Settings

### Switching Units
The app defaults to **imperial** units (miles, mph, min/mi). To switch to **metric** (kilometers, km/h, min/km):
- The unit preference is stored locally and persists across app restarts.

All internal measurements use meters and seconds — unit conversion is applied only at display time, so switching units does not affect your saved data.
