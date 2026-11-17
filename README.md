# GestoShare

**Offline, Gesture-Controlled Peer-to-Peer File & Content Sharing for Android**

GestoShare lets you stage files, photos, videos, documents, text notes, and web links and beam them directly between nearby Android devices using real-time hand gestures—completely offline with **zero cloud accounts, zero servers, and cryptographic SHA-256 verification**.

---

## Core Features

### 1. On-Device AI Hand Gesture Control
- **CameraX + MediaPipe HandLandmarker (`LIVE_STREAM`)**: Extracts 21 3D hand landmarks at low latency completely offline using `hand_landmarker.task`.
- **HaGRIDv2 ONNX Classifier (`airshare_gesture.onnx`)**: Converts hand landmarks into wrist-centered, palm-scale-invariant `[1, 63]` feature vectors (`preprocessing.json`) and classifies 6 classes via ONNX Runtime (`fist`, `like`, `point`, `stop`, `three_gun`, `OTHER`).
- **Temporal Gesture Stabilizer**: Applies configurable confidence thresholds (`50%–95%`), consecutive-frame smoothing, hold duration, and command cooldowns to prevent accidental triggers.
- **Safety Operation Modes**:
  - **Debug / Dry Run Mode** *(default)*: Visualizes landmarks, live ONNX probabilities, latency, and state transitions without triggering real P2P sends.
  - **Gesture Transfer Mode**: Arms `THREE_GUN` to dispatch real Nearby transfer offers (still protected by receiver Accept/Decline consent).

#### Gesture Command State Machine
| Gesture | Class | Command | Action |
| :--- | :--- | :--- | :--- |
| 👍 | `like` | **Activate** | Arms an active gesture session from `IDLE` |
| ✊ | `fist` | **Grab** | Locks staged content (`CONTENT_HELD`) |
| ☝️ | `point` | **Select Peer** | Cycles discovered or connected nearby peers |
| 🤟 / 🔫 | `three_gun` | **Send / Release** | Sends `TRANSFER_OFFER` to the selected connected peer or multi-recipient group |
| ✋ | `stop` | **Cancel** | Cancels active gesture workflow or in-flight transfer |

---

### 2. Offline Multi-Peer Networking (`P2P_CLUSTER`)
- Built on **Google Play Services Nearby Connections** (`Strategy.P2P_CLUSTER`) over Bluetooth / Wi-Fi Direct / LAN without internet access.
- **6-Digit Authentication Handshake**: Both sender and receiver verify pairing codes before establishing links.
- **Explicit Receiver Consent**: Every incoming `TRANSFER_OFFER` displays a confirmation dialog listing sender name, item names, and sizes before transferring payloads.
- **Single & Multi-Recipient Group Transfers**: Send to one selected peer or toggle multi-select mode to dispatch sequentially managed streams to multiple connected peers.
- **Built-In Peer Messaging**: Send quick notes or ping tests over the connected Nearby channel.

---

### 3. Scoped-Storage & Integrity Verification Pipeline
GestoShare enforces modern Android 10–16 (`API 29–36`) Scoped Storage best practices:
1. **Lazy Payload Resolution**: Resolves incoming `Payload.Type.FILE` streams via `content://` URIs (`ContentResolver.openInputStream`) first, falling back to `ParcelFileDescriptor` when needed and blocking unsafe raw `.nearby` filesystem paths on Android 10+.
2. **Pre-Commit Verification**: Streams incoming bytes into app-private staging while computing streaming **SHA-256 digests** and validating expected byte sizes.
3. **Atomic No-Overwrite Reservation**: Reserves unique filenames in app-private storage (`File.createNewFile()`) so concurrent transfers with identical filenames never overwrite each other.
4. **Public `Downloads/GestoShare` Export**: Exports verified files to public Downloads via `MediaStore` (`IS_PENDING` atomic commit on API 29+) with `FileProvider` fallback (`content://` URIs only).

---

### 4. Rich Content & Android Sharesheet Support
- **Stage From App**: Pick single or multiple images, videos, PDFs, or generic files, or type quick text notes and URLs.
- **System Sharesheet Integration**: Accepts `ACTION_SEND` and `ACTION_SEND_MULTIPLE` directly from Gallery, Chrome, Files, or third-party apps.

---

### 5. Local History & Diagnostics Center
- **Room SQLite (`AirShareDatabase` v2)**: Persists transfer records (SENT / RECEIVED / FAILED), recent paired devices, structured error events (`GS-NET-*`, `GS-FILE-*`, `GS-GESTURE-*`), and operational telemetry.
- **Privacy-Aware Exports**: Export sanitized TXT or JSON diagnostic bundles via Android's Sharesheet with automatic redaction of raw filesystem paths and message contents.
- **Material 3 Glassmorphism UI**: Switch seamlessly between **Midnight Glass (Dark)** and **Frosted Ice (Light)** themes with customizable haptic/audio cues and front/rear camera switching.

---

## Tech Stack

- **Language**: Kotlin 2.2
- **UI**: Jetpack Compose (Material 3 BOM `2024.09.00`), custom glassmorphism surfaces & Canvas hand-skeleton overlay
- **AI / Vision**: CameraX `1.5.0`, MediaPipe Tasks Vision `0.10.14`, ONNX Runtime `1.20.0`
- **Networking**: Google Play Services Nearby `19.3.0`
- **Persistence**: Room `2.7.0` with KSP, SharedPreferences-backed settings repository
- **Media & Sharing**: Coil `2.7.0`, AndroidX `FileProvider`, `MediaStore.Downloads`
- **Testing**: JUnit 4, Robolectric `4.16.1`, Kotlinx Coroutines Test, Compose UI Test

---

## Project Structure

```text
app/src/main/
├── assets/
│   ├── airshare_gesture.onnx     # Trained HaGRIDv2 6-class ONNX gesture classifier
│   ├── hand_landmarker.task      # MediaPipe 21-point 3D hand landmarker bundle
│   ├── labels.json               # Gesture class order and command descriptions
│   └── preprocessing.json        # Feature normalization specification ([1, 63])
├── java/com/example/
│   ├── MainActivity.kt           # Edge-to-edge host, permissions, Sharesheet intents, Consent dialog
│   ├── data/                     # Room database, DAOs, TransferHistory, Diagnostics, Settings repositories
│   ├── manager/                  # CameraX/MediaPipe tracker, ONNX classifier, Stabilizer, Nearby P2P, Scoped Storage
│   ├── model/                    # Transfer, Gesture, Device, Session, and Diagnostic domain models
│   ├── ui/                       # AirShareViewModel, Theme, Glass components, and Compose screens
│   └── util/                     # HashUtil (SHA-256 & filename sanitization), PayloadManifestTracker
└── res/                          # Adaptive icons, strings, themes, FileProvider paths
```

---

## Build, Test & Install via USB Debugging

### Prerequisites
- Android Studio (or JDK 11+ & Android SDK 36)
- Two physical Android phones (Android 7.0 / API 24+) with Bluetooth, Wi-Fi, and Camera for live Nearby + gesture testing

### 1. Run Unit & Robolectric Tests
```bash
gradle :app:testDebugUnitTest
```
*(Or `./gradlew :app:testDebugUnitTest` if running in a standard local Android Studio checkout with Gradle wrapper.)*

### 2. Build the Debug APK
```bash
gradle :app:assembleDebug
```
The compiled APK is generated at:
```text
app/build/outputs/apk/debug/app-debug.apk
```

### 3. Install & Test on Physical Devices (USB Debugging)
1. On both Android phones, enable **Developer options → USB debugging**.
2. Connect each phone via USB and verify connection:
   ```bash
   adb devices
   ```
3. Install the debug APK on connected device(s):
   ```bash
   adb install -r app/build/outputs/apk/debug/app-debug.apk
   ```
4. Launch **GestoShare** on both phones:
   - Grant **Camera** and **Nearby Devices / Bluetooth / Wi-Fi** permissions during onboarding.
   - Put Phone B in **Receive Mode** (Advertising) and tap **Scan Nearby** on Phone A.
   - Tap to pair and confirm the matching **6-digit code** on both screens.
   - Stage files/text on Phone A, open **Gesture Control**, switch from **Debug / Dry Run** to **Gesture Transfer Mode**, and perform: 👍 **Like** → ✊ **Fist** → ☝️ **Point** → 🤟 **Three Gun** (or use **Manual Send**).
   - Tap **Accept & Save** on Phone B — verified files are saved to `Downloads/GestoShare`.

---

## Permissions Explained

- `CAMERA`: Real-time offline MediaPipe hand tracking (no images or video ever leave the device).
- `BLUETOOTH_SCAN`, `BLUETOOTH_ADVERTISE`, `BLUETOOTH_CONNECT`, `NEARBY_WIFI_DEVICES` (plus legacy location below Android 13): Required solely for Google Nearby Connections local peer discovery and direct P2P transfer (`neverForLocation` flag declared on Android 12+).
- `VIBRATE`: Optional tactile confirmation when a stabilized gesture command fires.
