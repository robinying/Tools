# Tools

A unified Android utility app combining media tools, camera editing, image/QR/OCR utilities, ebook conversion, light metering, face analysis, and storage benchmarking into a single application. Built with a clean multi-module architecture and Jetpack Compose.

## Features

### 🎬 Media Editor

Compression, conversion, and FFmpeg-powered video utilities. Long work runs in a **foreground service** with notifications; results are saved via MediaStore (videos under `Movies/VideoEditor/`).

| Tool | Description |
|------|-------------|
| **Video Compression** | Reduce video size with Low / Medium / High quality (FFmpeg) |
| **Custom Compression** | Choose output resolution (480p/720p/1080p) and bitrate manually |
| **Image Compression** | Compress images with JPEG quality control |
| **GIF Conversion** | Video → animated GIF with quality options |
| **Extract Audio** | Pull AAC/M4A audio track from video |
| **Remove Audio** | Export video without sound (`-an`, copy when possible) |
| **Transcode to MP4** | Convert video to MP4 for wider compatibility |
| **Speed Change** | 0.5× / 1.5× / 2× with synced audio (`setpts` + `atempo`) |
| **Reverse Video** | Reverse playback; mute or keep reversed audio |
| **Merge Videos** | Join multiple clips in order (concat; re-encode fallback) |
| **Crop Aspect** | Center crop to **1:1**, **9:16**, or **16:9** |
| **Volume and Fade** | Volume 50% / 100% / 150% plus fade in/out |
| **Audio Trim** | Cut an audio file to a precise range; save to Music or Ringtones |
| **Audio Merge** | Join multiple audio files in order |
| **Audio Recorder** | Record with a live waveform and save to Music/Tools |
| **Background Music** | Replace or mix the video soundtrack with a music file |
| **Picture in Picture** | Overlay a second video in a corner |
| **Custom Speed** | 0.25x / 0.5x / 2x / 4x / 8x slow motion and time-lapse |
| **Extract Frames** | Save evenly spaced still frames from a video |
| **Image Filter** | Grayscale, Blur, Edge Detection, Cartoon, Sharpen, Sketch (OpenCV) |

### 📸 Camera

Camera2 + OpenGL preview pipeline, multi-segment recording, and offline editors.

| Tool | Description |
|------|-------------|
| **Take Photo** | Still capture with live GPU filters, grid/level guides, and 3s/10s timer; save to gallery |
| **Record Video** | Multi-segment recording, countdown, beauty/color filters, flip camera, rule-of-thirds grid and horizon level |
| **Burst Photo** | Single, 3-shot, or 5-shot capture with grid, level, and timer |
| **Edit Video** | Filters, text watermark, **stickers**, timed **subtitles** with style templates (classic/yellow/minimal/top) and **SRT import**, export with effects burn-in |
| **Trim Video** | Trim range, rotate / orientation helpers |
| **Select Cover** | Pick a cover frame from the video |
| **Text to Video** | Solid-color text card → short H.264 MP4 (no audio) |
| **Photo Slideshow** | Multi-photo → silent H.264 slideshow (seconds per image) |

### 🖼️ Image Studio

- Convert images between JPEG / PNG / WebP
- Resize, rotate, and strip EXIF metadata
- Export a photo as a single-page PDF
- QR code generation and decoding from images
- On-device text recognition (Chinese + Latin)
- Offline document scanner: page contour detection + perspective flattening (OpenCV)

### 🎵 Audio Trim

- Pick an audio file and trim it to a precise start/end range
- Re-encodes the result as AAC/M4A

### 📖 Ebook Converter

- **EPUB → PDF** conversion
- Chapter-by-chapter processing with progress tracking
- PDF merge for multi-chapter books
- Save to Downloads with MediaStore integration

### 💡 Light Meter

- Real-time ambient light measurement using the device sensor
- Live chart with a rolling time window
- Snapshot save with Room database persistence
- History view with swipe-to-delete

### 🧹 Storage Cleanup

- Find media files above a configurable size threshold
- Group suspected duplicates by size + sampled content hash
- Delete a large file or the extra copies of a duplicate group via the system confirmation dialog
- Export benchmark history as CSV
- Read-only review; deletion stays with the system file manager

### 💾 Storage Benchmark

- Sequential write / read, random 4 KB read / write, and small-file throughput
- Optional cache-eviction cold read and SQLite insert phase
- Per-phase latency percentiles, throughput timeline chart, and rating
- Room-backed history of the most recent runs

### 👤 Face Tools

- **Face Compare** — select two photos, detect faces with ML Kit, and score cosine similarity (MobileFaceNet when the model is present)
- **Face Blur** — detect every face and apply mosaic or soft blur for privacy
- **ID Photo** — 1-inch / 2-inch crops with white, blue, or red background replacement (ML Kit selfie segmentation)
- **Group Similar Faces** — cluster selected photos by the largest detected face

### 🔎 Home, History, and Sharing

- Searchable tool registry with favorites and recent tools
- Task history (Room) for media, camera, ebook, and toolkit results
- `ACTION_SEND` / `ACTION_VIEW` share targets route images, videos, audio, and text into compatible tools

## Architecture

```
:app  →  :feature:media    →  :core
      →  :feature:camera   →  :core
      →  :feature:ebook    →  :core
      →  :feature:lightlux →  :core
      →  :feature:face     →  :core
      →  :feature:storage  →  :core
      →  :feature:toolkit  →  :core
```

Features depend only on `:core`. Features do **not** depend on each other.

### Module Structure

| Module | Package | Description |
|--------|---------|-------------|
| `:app` | `com.robin.tools` | Entry point, type-safe navigation, theme |
| `:core` | `com.robin.tools.core` | Shared UI (`FeatureCard`, tokens), extensions, utilities |
| `:feature:media` | `com.robin.tools.feature.media` | Compression / audio / speed / reverse / merge / crop / fade / filters (FFmpeg + OpenCV) |
| `:feature:camera` | `com.robin.tools.feature.camera` | Photo, record, edit (watermark/sticker/subtitle), trim, cover, text-to-video, slideshow |
| `:feature:ebook` | `com.robin.tools.feature.ebook` | EPUB → PDF |
| `:feature:lightlux` | `com.robin.tools.feature.lightlux` | Light sensor + Room |
| `:feature:face` | `com.robin.tools.feature.face` | Face similarity (ML Kit + TFLite) |
| `:feature:storage` | `com.robin.tools.feature.storage` | Storage speed benchmark + cleanup scanner |
| `:feature:toolkit` | `com.robin.tools.feature.toolkit` | Image studio, QR codes, on-device OCR |

### Design System

Shared tokens in `:core`:

| Token | Values |
|-------|--------|
| **Dimension** | `xs=4dp, sm=8dp, md=12dp, lg=16dp, xl=24dp, xxl=32dp, xxxl=48dp` |
| **Shapes** | `small=8dp, medium=12dp, large=16dp, extraLarge=20dp` |
| **Typography** | `headlineLarge` … `labelSmall` (Material 3 style scale) |

### Key Design Decisions

- **Kotlin DSL** for all Gradle build files
- **Jetpack Compose** + **Material 3** (dark/light, dynamic color on Android 12+)
- **Strategy pattern** — `CompressionDelegate` + `CompressionDelegateFactory` for every media tool
- **Camera2 + OpenGL** for live preview / filters (not CameraX for the main capture path)
- **Shared `FeatureCard`** for home and feature menus (rounded card + elevation; no icon plate)
- **Type-safe Compose Navigation** (`AppRoute` sealed interface + serialization)
- **Foreground Service** for long-running media jobs (`CompressionService`)
- **OpenCV** / **FFmpeg Kit** AARs bundled under `feature/media/libs/`

## Tech Stack

- **Min SDK**: 28 | **Target / Compile SDK**: 36
- **Kotlin** + **AGP** (see `gradle/libs.versions.toml`)
- **Compose BOM**, **Room** (KSP), **Coil**
- **FFmpeg Kit** (local AAR) — video/audio tools
- **OpenCV 4.12.0** (local AAR) — image filters
- **Camera2 + GLES** — camera preview / export
- **ML Kit + TensorFlow Lite** — face detection / embedding, barcode scanning, text recognition
- **epublib + PDFBox Android** — ebook conversion

## Building

### Debug

```bash
./gradlew :app:assembleDebug
# or install on a connected device
./gradlew :app:installDebug
```

### Release (signed)

1. Create a keystore:
```bash
keytool -genkey -v -keystore ~/Tools-release.jks \
  -keyalg RSA -keysize 2048 -validity 10000 -alias tools
```

2. Add to `local.properties` (gitignored):
```properties
RELEASE_STORE_FILE=/path/to/Tools-release.jks
RELEASE_STORE_PASSWORD=your_password
RELEASE_KEY_ALIAS=tools
RELEASE_KEY_PASSWORD=your_password
```

3. Build:
```bash
./gradlew :app:assembleRelease
```

Output: `app/build/outputs/apk/release/app-release.apk`

### Requirements

- Android Studio Ladybug or later
- JDK 17
- Android SDK 36
- NDK (for FFmpeg / OpenCV native libraries)

## Testing

```bash
# All unit tests
./gradlew test

# Module unit tests
./gradlew :core:test
./gradlew :feature:media:test
./gradlew :feature:camera:test
./gradlew :feature:lightlux:test
./gradlew :feature:ebook:test
./gradlew :feature:face:test

# Single class examples
./gradlew :core:test --tests "com.robin.tools.core.test.ResultStateTest"
./gradlew :feature:media:test --tests "com.robin.tools.feature.media.data.CompressionDelegateFactoryTest"
./gradlew :feature:camera:test --tests "com.robin.tools.feature.camera.opengl.CameraOrientationTest"

# Instrumented tests (device/emulator required)
./gradlew connectedAndroidTest

# Bypass system multi-select UI: concat + slideshow on a real device
# (uses file paths / synthetic bitmaps instead of the photo picker)
./scripts/device_bypass_multiselect_test.sh

# Lint
./gradlew lint
```

### Device test notes

- **Single-file** media tools can be exercised end-to-end through the UI picker.
- **Merge Videos** / multi-image pickers use `GetMultipleContents`; system Photo Picker multi-select is hard to automate. Prefer `./scripts/device_bypass_multiselect_test.sh` or:
  - `ConcatBypassUiTest` / `ConcatServiceBypassUiTest` (paths → concat)
  - `SlideshowBypassUiTest` (in-memory bitmaps → slideshow MP4)
- Seed video for concat tests (optional):  
  `adb push your_short.mp4 /sdcard/Download/tools_test_av.mp4`

## Project Structure

```
Tools/
├── app/                              # Main application module
│   └── src/main/
│       ├── java/com/robin/tools/
│       │   ├── App.kt
│       │   ├── MainActivity.kt       # Navigation host
│       │   ├── navigation/AppRoute.kt
│       │   └── ui/theme/
│       └── AndroidManifest.xml
├── core/                             # Shared infrastructure
│   └── src/main/java/com/robin/tools/core/
│       ├── ui/                       # FeatureCard, TextOptionChip, Dimension
│       ├── widget/                   # SwipeBackContainer, …
│       ├── ext/
│       ├── network/
│       └── state/                    # ResultState
├── feature/
│   ├── media/
│   │   └── src/main/java/.../media/
│   │       ├── data/                 # CompressionType, CompressionManager
│   │       ├── delegate/             # Video/Image/GIF/Audio/Speed/Reverse/Concat/Crop/Volume…
│   │       ├── ui/screens/
│   │       ├── service/              # CompressionService (+ startWithFilePaths for tests)
│   │       └── libs/                 # ffmpeg-kit.aar, opencv.aar
│   ├── camera/
│   │   └── src/main/java/.../camera/
│   │       ├── camera2/              # Camera2Controller
│   │       ├── opengl/               # GlRenderer, orientation helpers
│   │       ├── editor/               # Effects export, TextCard, Slideshow
│   │       ├── filter/               # Beauty + color filters
│   │       └── ui/                   # photo, record, edit, trim, cover, text, slideshow
│   ├── ebook/
│   ├── lightlux/
│   └── face/
├── scripts/
│   └── device_bypass_multiselect_test.sh
├── gradle/
│   └── libs.versions.toml
├── build.gradle.kts
└── settings.gradle.kts
```

## License

Private project — All rights reserved.
