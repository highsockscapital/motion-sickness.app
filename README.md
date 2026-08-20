# Motion Sickness Visual Cues App (`motion-sickness.app`)

[![Download APK](https://img.shields.io/github/v/release/highsockscapital/motion-sickness.app?label=Download%20APK&style=for-the-badge&color=00BCD4)](https://github.com/highsockscapital/motion-sickness.app/releases/latest)

An open-source Android application designed to alleviate motion sickness (kinetosis) while reading or viewing content in moving vehicles. The app draws a customizable, ambient grid of subtle animated visual cues over any application without interfering with screen touches.

The overlay provides a stable visual horizon anchor that helps reconcile vestibular and visual mismatch — the root cause of motion sickness — while remaining fully non-intrusive.

## Download & Installation

**Direct APK Download:** [app-debug.apk](https://github.com/highsockscapital/motion-sickness.app/releases/latest/download/app-debug.apk)

Or get the latest release from the badge above: `https://github.com/highsockscapital/motion-sickness.app/releases/latest` → `app-debug.apk`

### Step-by-Step Setup

1. **Download & Install:**
   - Tap the direct link above on your Android device to download `app-debug.apk`.
   - Open the file — if prompted, enable **Install unknown apps** for your browser/file manager.

2. **Grant Overlay Permission:**
   - Open the **Motion Overlay** app.
   - Toggle **Enable Overlay** → system will prompt **Display over other apps**.
   - Tap **Allow display over other apps** for `motion-sickness.app` (this uses `Settings.ACTION_MANAGE_OVERLAY_PERMISSION`).
   - Return to the app — toggle again to start the overlay. You can verify with `Settings.canDrawOverlays(context)`.

3. **Enable Quick Settings Tile:**
   - Swipe down to open **Quick Settings**.
   - Tap the **pencil/edit** icon → find **Motion Cue** / **Motion Overlay** tile (icon `@drawable/ic_motion_cue`).
   - **Drag** it into your active tiles area.
   - Tap the tile to toggle the overlay instantly — tile shows `STATE_ACTIVE` when on, `STATE_INACTIVE` when off. If overlay permission is missing, tapping the tile will collapse the shade via `unlockAndRun` and open `MainActivity` to grant it.

4. **Optional — Battery Optimization:**
   - If prompted, tap **Disable battery optimization** to allow `PowerManager.isIgnoringBatteryOptimizations` exemption, keeping the overlay smooth on long drives.

---

## Overview & Features

### Ambient Visual Cue Grid
- **Full-screen staggered 2D dot matrix** rendered with Jetpack Compose `Canvas` across the entire display.
- **Vibrant cyan/teal palette** (`#00BCD4` / `#00E5FF`, with Neon Green, Soft Amber, White themes) and **depth & horizon effect** — center/horizon dots are larger and fully opaque, edges fade to smaller, semi-transparent for focal depth.
- **Buttery-smooth ambient motion** via `withFrameNanos` + `derivedStateOf` + `LinearEasing` with `BoxWithConstraints` dynamic recalculation for portrait/landscape, pre-allocated `Paint`/`Color` for 60/120 FPS with minimal battery overhead.
- **Safe-inset aware**: respects `WindowInsets.displayCutout` (camera hole-punches) and `WindowInsets.navigationBars` (gesture handles) so dots never overlap unsafe areas; optional `hideInLandscape` auto-hides in horizontal video.

### Zero-Touch Pass-Through Overlay
- Runs via `WindowManager` with `TYPE_APPLICATION_OVERLAY` and strict `FLAG_NOT_FOCUSABLE | FLAG_NOT_TOUCHABLE` — taps, swipes, and gestures pass through completely to underlying apps.
- No sensor tracking required — purely ambient; toggle on/off instantly.
- **Battery-aware**: optional `PowerManager.isIgnoringBatteryOptimizations` prompt with `ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS` to keep overlay smooth during long drives, plus configurable auto-dismiss sleep timer (`Never` / `15 Mins` / `30 Mins` / `1 Hour`) that auto-stops the service via lightweight `Coroutine` `delay`.

### Quick Settings Tile Integration
- **One-tap toggle** from notification shade via `MotionTileService` (`TileService`).
- Tile reflects live state `STATE_ACTIVE`/`STATE_INACTIVE` via `onStartListening()` checking `MotionOverlayService.isRunning` (static flag + `ActivityManager` fallback).
- Handles `Settings.canDrawOverlays` — if granted, toggles service and updates tile immediately; if not, collapses shade with `unlockAndRun { ... }` / `startActivityAndCollapse` and launches `MainActivity` for permission grant.
- Uses monochrome `ic_motion_cue.xml` (`viewport 24x24`, `#FFFFFF`) for dynamic system tinting.

### Customization & Preview
- **Live preview card** inside `MainActivity` renders `MotionCuesOverlay` directly with current settings.
- **Controls**: Speed slider (Slow `0.5` / Medium `1.0` / Fast `2.0`), Opacity slider (`10%–100%`), Color selector row (Cyan, Neon Green, Soft Amber, White), Hide-in-landscape switch, and Sleep Timer selector.
- All settings persisted via **Jetpack DataStore** (`dotSpeed`, `dotOpacity`, `themeColor`, `hideInLandscape`, `timerDuration`) as `Flow<OverlaySettings>` — `MotionOverlayService` collects the `Flow` to update rendering dynamically without restart.

---

## 🛡️ **Privacy & Security First**

System overlay permissions can rightfully trigger safety concerns. `motion-sickness.app` is engineered from the ground up with a **Zero-Trust Privacy Model**:

* **No Internet Permission:** The app does not request or possess `android.permission.INTERNET`. It is physically impossible for the app to send data off your device.
* **Strict Pass-Through Overlay:** The visual grid uses `FLAG_NOT_TOUCHABLE` and `FLAG_NOT_FOCUSABLE`. It acts like a piece of colored glass on top of your screen—it cannot intercept taps, detect keystrokes, or record screen content.
* **Zero Analytics or Tracking:** No telemetry, crash reporting libraries, or third-party SDKs are included.
* **Fully Local:** All configuration preferences (speed, color, opacity) remain strictly on your device.

---

## Tech Stack

| Layer | Technology |
|-------|------------|
| **Language** | **Kotlin** (JVM target 17) |
| **UI Framework** | **Jetpack Compose** — `Canvas`, `BoxWithConstraints`, `Material 3`, `rememberInfiniteTransition` + `withFrameNanos` |
| **System Overlay** | **WindowManager** — `TYPE_APPLICATION_OVERLAY`, `FLAG_NOT_FOCUSABLE | FLAG_NOT_TOUCHABLE`, `PixelFormat.TRANSLUCENT` |
| **Architecture** | **Android Foreground Service** + `LifecycleService` (`MotionOverlayService`) with `ComposeView` + `ViewTreeLifecycleOwner` |
| **Preferences** | **Jetpack DataStore (Preferences)** — `preferencesDataStore`, `floatPreferencesKey` / `intPreferencesKey` / `booleanPreferencesKey`, `Flow` |
| **Quick Settings** | **TileService** — `BIND_QUICK_SETTINGS_TILE`, `QS_TILE` intent, `Tile.STATE_ACTIVE/INACTIVE` |
| **Icons** | **VectorDrawable** `ic_motion_cue.xml` — central solid dot + 4 satellite dots + dashed orbit, `24x24` `viewport`, `#FFFFFF` tint |
| **Services** | **Android 14+ Foreground Services** — `foregroundServiceType="specialUse"` + `PROPERTY_SPECIAL_USE_FGS_SUBTYPE="Visual aid overlay for motion comfort"`, `FOREGROUND_SERVICE_TYPE_SPECIAL_USE` with SDK 34 fallback, `NotificationChannel` (`IMPORTANCE_LOW`) |
| **Target / Min SDK** | **Target SDK 34 (Android 14, UPSIDE_DOWN_CAKE)** / **Min SDK 26 (Android 8.0)** — `compileSdk 34` |
| **Concurrency** | **Kotlin Coroutines** — `lifecycleScope`, `delay` for sleep timer, `withFrameNanos` frame loop |
| **Build** | **Gradle Kotlin DSL** (`build.gradle.kts`), AGP `8.5.2`, Kotlin `2.0.21`, Compose Compiler Plugin |

---

## Build & Setup Instructions

### Prerequisites
- **OpenJDK 17** (`java --version` should report 17)
- **Android SDK & Command Line Tools** (platform-tools, build-tools 34, platforms `android-34`)
- **Termux** (Android) or **PRoot Ubuntu** inside Termux for a full Linux environment

> Project builds entirely via **Gradle CLI** — no Android Studio required.

### 1. Prepare Termux / PRoot Ubuntu Environment

```bash
# Inside Termux
pkg update && pkg upgrade -y
pkg install -y openjdk-17 git wget unzip proot proot-distro

# Install PRoot Ubuntu (recommended for full Gradle toolchain)
proot-distro install ubuntu
proot-distro login ubuntu

# Inside PRoot Ubuntu
apt update && apt upgrade -y
apt install -y openjdk-17-jdk git wget unzip
export JAVA_HOME=/usr/lib/jvm/java-17-openjdk-amd64
java -version  # verify 17
```

### 2. Install Android SDK Command Line Tools

```bash
mkdir -p ~/android-sdk/cmdline-tools
cd ~/android-sdk/cmdline-tools
wget https://dl.google.com/android/repository/commandlinetools-linux-11076708_latest.zip
unzip commandlinetools-linux-11076708_latest.zip
mv cmdline-tools latest
export ANDROID_HOME=~/android-sdk
export ANDROID_SDK_ROOT=$ANDROID_HOME
export PATH=$PATH:$ANDROID_HOME/cmdline-tools/latest/bin:$ANDROID_HOME/platform-tools

# Accept licenses and install required components
yes | sdkmanager --licenses
sdkmanager --install "platform-tools" "platforms;android-34" "build-tools;34.0.0"
sdkmanager --list_installed
```

### 3. Clone the Project

```bash
git clone https://github.com/highsockscapital/motion-sickness.app.git
cd motion-sickness.app
# Or if working from this workspace:
# cd /root/motion
ls -R  # should show app/build.gradle.kts, app/src/main/...
```

### 4. Configure Local Properties (if needed)

```bash
# Tell Gradle where the SDK is (Termux path may differ)
echo "sdk.dir=$ANDROID_HOME" > local.properties
cat local.properties

# Verify Gradle wrapper
chmod +x gradlew
./gradlew --version  # uses JVM 17
```

### 5. Compile via Gradle CLI

```bash
# Clean
./gradlew clean

# Debug build (fast, for device/emulator)
./gradlew assembleDebug --info

# Release build (requires signing config for Play Store)
./gradlew assembleRelease

# Run unit tests (if present)
./gradlew test

# Install directly to connected device/emulator (adb must be authorized)
adb devices
./gradlew installDebug
adb shell am start -n com.example.motionoverlay/.MainActivity
```

### 6. Permissions & First Run

1. Launch `Motion Overlay` from launcher.
2. Toggle **Enable Overlay** — grant **Display over other apps** when prompted (`Settings.ACTION_MANAGE_OVERLAY_PERMISSION`).
3. If shown, accept **Disable battery optimization** (`PowerManager.isIgnoringBatteryOptimizations` → `ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS`) to keep overlay smooth during long drives.
4. Adjust **Speed**, **Opacity**, **Theme Color**, **Hide in landscape**, and **Sleep Timer** (Never/15/30/60 min) — preview updates instantly and `MotionOverlayService` re-renders via DataStore `Flow`.
5. Add **Motion Cue** tile to Quick Settings (edit tiles) for one-tap shade toggling.

### Troubleshooting (Termux/PRoot)

```bash
# JAVA_HOME mismatch
export JAVA_HOME=/usr/lib/jvm/java-17-openjdk-amd64
./gradlew --stop

# SDK not found
echo $ANDROID_HOME && ls $ANDROID_HOME/platforms/android-34

# Permission denied for overlay
adb shell appops set com.example.motionoverlay SYSTEM_ALERT_WINDOW allow
```

---

## Architecture Summary

### `MotionOverlayService.kt` — Core Foreground Service
- **Lifecycle**: `LifecycleService` with `isRunning` static flag, `SavedStateRegistryController`, `ViewModelStore` for `ComposeView` lifecycle.
- **Foreground**: `startForeground(NOTIFICATION_ID, notification, FOREGROUND_SERVICE_TYPE_SPECIAL_USE)` on API 34+ with fallback to legacy `startForeground()`; `NotificationChannel` (`IMPORTANCE_LOW`) + persistent notification with **Stop** action (`PendingIntent.getService`).
- **Overlay**: `WindowManager.addView(ComposeView, LayoutParams(MATCH_PARENT, MATCH_PARENT, TYPE_APPLICATION_OVERLAY, FLAG_NOT_FOCUSABLE|FLAG_NOT_TOUCHABLE, TRANSLUCENT))` + `setViewTreeLifecycleOwner/SavedStateRegistryOwner/ViewModelStoreOwner`.
- **Data-Driven**: `OverlayPreferences.overlaySettingsFlow().collectAsState()` inside `setContent { MotionCuesOverlay(dotSpeed, dotOpacity, themeColor, hideInLandscape) }` — updates dynamically.
- **Sleep Timer**: `timerJob: Job?` in `lifecycleScope`; `scheduleTimerFromPreferences()` reads `timerDuration` via `first()`, `scheduleTimer()` does `delay(millis)` then `stopSelf()` + `TileService.requestListeningState(..., MotionTileService)` to set QS tile OFF; cancelled on `ACTION_STOP`/`onDestroy`.

### `MotionTileService.kt` — Quick Settings Tile
- Extends `TileService`. `onStartListening()` checks `isOverlayServiceRunning()` (static `MotionOverlayService.isRunning` + `ActivityManager.getRunningServices` fallback) and sets `qsTile.state = STATE_ACTIVE/INACTIVE`.
- `onClick()` checks `Settings.canDrawOverlays`: if true, toggles `MotionOverlayService` (`startForegroundService`/`stopService`) and updates tile immediately; if false, `unlockAndRun { startActivity(MainActivity) }` or `startActivityAndCollapse(PendingIntent)` to request overlay permission.

### `MotionCuesOverlay.kt` — Performance-Optimized Canvas
- **Staggered grid** across full screen with `Canvas`, `BoxWithConstraints` for portrait/landscape recalculation, `spacing 42f`, `radius 2.2–5.8`, depth-based `alpha 0.18–0.95` modulated by `dotOpacity`.
- **Palette**: `baseColor #00BCD4` / `accent #00E5FF` derived from `themeColor` (Cyan/Neon Green/Soft Amber/White).
- **Animation**: `withFrameNanos` frame loop + `derivedStateOf` `driftFraction = (elapsed / (6500/dotSpeed)) % 1` with `LinearEasing`; secondary `rememberInfiniteTransition` pulse; `driftX = driftFraction*spacing` with slant `0.18` and `verticalDriftFactor 0.12`.
- **Safe insets**: `WindowInsets.displayCutout.getLeft/...` + `WindowInsets.navigationBars`/`statusBars` → `left/right/top/bottomInsetPx`; grid inset (`leftInset+col*spacing`) and culling `if (x < safeLeft|| x > safeRight) continue` avoids camera/gesture areas; `hideInLandscape` early return when `maxWidth > maxHeight`.
- **Optimization**: Pre-allocated `Paint`, `Color`, `Math` outside `DrawScope`; no allocations in inner `for (row) for (col) drawCircle` loop.

### Supporting Layers
- **`MainActivity.kt`**: Compose `Scaffold` with overlay toggle (handling `canDrawOverlays` → `ACTION_MANAGE_OVERLAY_PERMISSION`), battery optimization card (`isIgnoringBatteryOptimizations` → `ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS`), live preview `Card` (180dp `MotionCuesOverlay`), sliders, color selector, hide-in-landscape `Switch`, sleep timer chips — all backed by `OverlayPreferences` `DataStore`.
- **`OverlayPreferences.kt`**: `preferencesDataStore("overlay_prefs")`, `OverlaySettings` + `TIMER_OPTIONS`, `Flow` and `update*` suspend functions.
- **`ic_motion_cue.xml`**: `vector` `24x24` `viewport`, `#FFFFFF`, central solid circle `r=4` + 4 satellite dots `r=1.25` `fillAlpha 0.48–0.62` + dashed orbit `strokeWidth 0.9` `strokeAlpha 0.35`.

---

## Permissions

```xml
<uses-permission android:name="android.permission.SYSTEM_ALERT_WINDOW" />
<uses-permission android:name="android.permission.FOREGROUND_SERVICE" />
<uses-permission android:name="android.permission.FOREGROUND_SERVICE_SPECIAL_USE" />
<uses-permission android:name="android.permission.REQUEST_IGNORE_BATTERY_OPTIMIZATIONS" />
<uses-permission android:name="android.permission.BIND_QUICK_SETTINGS_TILE" />
```

Foreground service declaration requires for Android 14+:
```xml
<service android:name=".MotionOverlayService"
    android:foregroundServiceType="specialUse">
    <property android:name="android.app.PROPERTY_SPECIAL_USE_FGS_SUBTYPE"
        android:value="Visual aid overlay for motion comfort" />
</service>
```

---

## License

MIT — see `LICENSE` for details.

*Built for comfort on the move. Contributions welcome via pull requests.*

