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
4. A persistent notification appears showing the running timer, distance, pace, and speed. You can now lock your screen or switch apps — tracking continues in the background. The notification updates every second. Your progress is saved to the database every 30 seconds, so even if the system kills the app under memory pressure, at most 30 seconds of data is lost. If the app is swiped from recents, the tracking service continues running independently. If the system kills the service, it automatically restarts and resumes tracking. When you reopen the app, it reconnects to the running service and displays your live stats.

### During a Run
The screen displays real-time stats:
- **Duration** — large timer at the top (excludes paused time)
- **Distance** — in miles or kilometers
- **Pace** — minutes per mile or per kilometer
- **Speed** — current speed
- **Max Speed** — fastest speed recorded so far
- **Laps / Current Lap** — appears automatically when you complete a lap (pass within 30m of your starting point after covering at least 200m)

### Pausing and Resuming
Tap the large **PAUSE** button (left side) to pause tracking. The timer stops and "PAUSED" appears on screen. GPS data is not recorded while paused. Tap **RESUME** to continue.

### Automatic Inactivity Pause
If you stop moving for **1 minute** (e.g., you stop to wait at a crosswalk, or you forget to stop the app), tracking automatically pauses and your progress is saved. If you start moving again, tracking resumes automatically. If you remain stationary for another minute (**2 minutes total** of no movement), the app finalizes the activity, stops the tracking service, and exits, so you don't burn battery if you forgot to stop.

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

The detail screen opens with an interactive **route map** at the top — a plotted route of your run colored by a selected metric. Two color modes are available via toggle chips above the map:

#### Speed Mode (default)
- **Blue** segments = slowest
- **Green/Yellow** segments = moderate
- **Orange/Red** segments = fastest

#### Elevation Mode
- **Dark green** segments = lowest altitude
- **Green/Lime** segments = moderate altitude
- **Orange/Brown** segments = highest altitude
- **Gray** segments = no altitude data available for that portion

If the activity has no valid elevation data, the elevation toggle is hidden.

In both modes:
- A **green dot** marks the start and a **red dot** marks the finish
- A gradient legend below the map shows the value range (speed in mph, or elevation in ft/m)

### Zooming and Panning
- **Tap** the map once to enlarge it — this unlocks zoom/pan gestures
- **Pinch** (while enlarged) to zoom into the map and inspect specific sections of your route
- **Drag** (while enlarged and zoomed) to pan around
- **Double-tap** (while enlarged) to reset the view to the original zoom level
- **Tap** the map again (while enlarged but not zoomed) to shrink it back to its default size

The route is rendered in a square drawing area that preserves true aspect ratio — it is never stretched or squashed to fit the container.

### Adjusting the Speed Range
Below the map is a **range slider** that controls which speeds are displayed:
- Drag the **left handle** to raise the minimum speed — segments slower than this become hidden (gaps in the route)
- Drag the **right handle** to lower the maximum speed — segments faster than this become hidden
- The color scale is **absolute** — colors always map to the full speed range of the activity, so narrowing the filter hides segments but does not recompress the remaining colors
- Tap **Reset** to restore the original range

For example, if your run shows speeds from 3.00 to 8.00 mph and you set the range to 5.00–8.00, only the portions where you ran at least 5 mph will be visible — and each visible segment keeps the same color it had before you narrowed the range.

Below the map, full stats are shown:
- Distance, duration
- Average pace, average speed
- Max speed, calories
- Number of GPS points recorded
- **Elevation Gain** and **Elevation Loss** — cumulative ascent and descent across the run (only shown if valid altitude data exists). Values with missing altitude readings (0.0) are excluded from the calculation unless all readings are near sea level.

### Lap Splits
If you ran a loop route (passing within 30m of your starting point), a **Lap Splits** table appears below the stats. Each lap shows distance, time, and pace. The fastest lap is highlighted in bold with the primary color. Laps require at least 200m and 60 seconds to register, which filters out false detections from passing near the start point mid-lap.

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

Tap any personal record card to view the activity where that record was set.

### Lifetime Totals
- Total number of completed activities
- Total distance across all runs
- Total time spent running
- Average distance per run
- Total calories burned
- Average calories per run

### Trends

Below the totals, a **Trends** chart plots one metric per completed activity in chronological order (oldest to newest). Tap a chip to switch metrics:
- **Distance**, **Duration**, **Calories** — shown as bar charts (cumulative quantities)
- **Avg Speed**, **Top Speed**, **Pace** — shown as line charts (rates)

The chart shows the max value and activity count above the plot. With no completed activities yet, the chart area shows "Not enough data yet."

Stats update automatically as you complete more runs. Deleting an activity updates all stats and records accordingly.

---

## Profile

Tap the **Profile** tab in the bottom navigation bar to manage your personal data.

### First Launch
On first launch, the app takes you directly to the Profile screen. You must complete all fields (sex, date of birth, height, and weight) before you can start tracking — this data is needed to estimate calories burned accurately.

### Sex
Select **Male** or **Female**. This is used in the Mifflin-St Jeor equation to calculate your Basal Metabolic Rate (BMR), which affects calorie estimation. Males and females of the same weight burn calories at different rates.

### Date of Birth
Tap the date picker to select your date of birth. Age is used in the BMR calculation — metabolic rate decreases with age. The picker does not allow future dates.

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
- Your sex, age, height, and weight (via the **Mifflin-St Jeor BMR** equation)
- The average speed of the activity (via **MET** values from the Compendium of Physical Activities)
- The duration of the activity

The formula is: **Calories = MET × (BMR / 24) × duration in hours**

The Mifflin-St Jeor equation calculates your Basal Metabolic Rate differently for males and females:
- **Male:** BMR = 10 × weight(kg) + 6.25 × height(cm) - 5 × age + 5
- **Female:** BMR = 10 × weight(kg) + 6.25 × height(cm) - 5 × age - 161

MET values are interpolated based on your running speed — faster running burns more calories per minute.

Calories appear in:
- **History** cards — shown next to the time range
- **Activity Detail** — displayed in the stats grid
- **Stats** tab — total and average calories in lifetime totals

For the most accurate estimates, keep your weight and date of birth up to date in the Profile tab.

---

## Settings

### Switching Units
The app defaults to **imperial** units (miles, mph, min/mi). To switch to **metric** (kilometers, km/h, min/km):
- The unit preference is stored locally and persists across app restarts.

All internal measurements use meters and seconds — unit conversion is applied only at display time, so switching units does not affect your saved data.

---

## Building from the Command Line

You can build the app without Android Studio using the Gradle wrapper included in the repo.

### Prerequisites

1. **Android SDK** — must be installed with `platforms;android-35` and `build-tools;35.0.0` (or newer). The SDK path is set in `local.properties` (e.g., `sdk.dir=/home/you/Android/Sdk`).
2. **JDK 21** — install via your system package manager (e.g., `java-21-openjdk-devel` on openSUSE, `openjdk-21-jdk` on Ubuntu).
3. **JetBrains Runtime (JBR)** — Kotlin 2.2.10 requires a JetBrains JDK for compilation. If you have Android Studio installed, it bundles one (look for a `jbr/` directory inside the Android Studio installation). You can find it with:
   ```bash
   find ~ -path "*/jbr/bin/java" -type f 2>/dev/null
   ```

### Global Gradle Configuration

Create or edit `~/.gradle/gradle.properties` with paths to your JDKs:

```properties
org.gradle.java.home=/usr/lib64/jvm/java-21-openjdk-21
org.gradle.java.installations.paths=/path/to/android-studio/jbr
```

- `org.gradle.java.home` — the JDK that runs Gradle itself (any JDK 21+ works).
- `org.gradle.java.installations.paths` — path to the JetBrains Runtime so the Kotlin compiler can find it.

**Note:** If Android Studio is installed via Flatpak, the JBR path includes a hash that changes on every update. After updating Android Studio, re-run the `find` command above and update this path.

### Build Commands

```bash
# Build debug APK
./gradlew assembleDebug

# Build release APK
./gradlew assembleRelease

# Install debug APK on a connected device
./gradlew installDebug

# Clean build artifacts
./gradlew clean
```

The debug APK is output to `app/build/outputs/apk/debug/app-debug.apk`.
