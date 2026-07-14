# Tools

A unified Android utility app combining media tools, camera editing, ebook conversion, light metering, and face comparison into a single application. Built with a clean multi-module architecture and Jetpack Compose.

## Features

### 🎬 Media Editor

Compression, conversion, and FFmpeg-powered video utilities. Long work runs in a **foreground service** with notifications; results are saved via MediaStore (videos under `Movies/VideoEditor/`).

| Tool | Description |
|------|-------------|
| **Video Compression** | Reduce video size with Low / Medium / High quality (FFmpeg) |
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
| **Image Filter** | Grayscale, Blur, Edge Detection, Cartoon, Sharpen, Sketch (OpenCV) |

### 📸 Camera

Camera2 + OpenGL preview pipeline, multi-segment recording, and offline editors.

| Tool | Description |
|------|-------------|
| **Take Photo** | Still capture with live GPU filters; save to gallery |
| **Record Video** | Multi-segment recording, countdown, beauty/color filters, flip camera |
| **Edit Video** | Filters, text watermark, **stickers**, timed **subtitles** / **SRT import**, export with effects burn-in |
| **Trim Video** | Trim range, rotate / orientation helpers |
| **Select Cover** | Pick a cover frame from the video |
| **Text to Video** | Solid-color text card → short H.264 MP4 (no audio) |
| **Photo Slideshow** | Multi-photo → silent H.264 slideshow (seconds per image) |

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

### 👤 Face Compare

- Select two photos and compare face similarity
- On-device ML Kit face detection with landmark alignment
- Cosine similarity scoring with visual result card
- TFLite MobileFaceNet embedding when the model is present

## Architecture

```
:app  →  :feature:media    →  :core
      →  :feature:camera   →  :core
      →  :feature:ebook    →  :core
      →  :feature:lightlux →  :core
      →  :feature:face     →  :core
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
- **ML Kit + TensorFlow Lite** — face detection / embedding
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
