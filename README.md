# Tools

A unified Android utility app combining six powerful tools into a single application, built with clean multi-module architecture.

## Features

### 🎬 Media Editor
- **Video Compression** — Reduce video file size with configurable quality levels (FFmpeg)
- **Image Compression** — Compress images with JPEG quality control
- **GIF Conversion** — Convert video to animated GIF with FPS/size settings
- **Image Filter** — 6 stylization filters: Grayscale, Blur, Edge Detection, Cartoon, Sharpen, Sketch (OpenCV)
- Foreground service for background processing with notifications
- Progress tracking and gallery save

### 📸 Camera
- **Video Recording** — CameraX-based recording with filter selection
- **Video Editing** — Preview, filter application, watermark/sticker overlay
- **Video Trimming** — Draggable range selector with thumbnail strip
- **Cover Selection** — Extract and select video cover frames

### 📖 Ebook Converter
- **EPUB → PDF** conversion
- Chapter-by-chapter processing with progress tracking
- PDF merge for multi-chapter books
- Save to Downloads with MediaStore integration

### 💡 Light Meter
- Real-time ambient light measurement using device sensor
- Live chart with 60-second rolling window
- Snapshot save with Room database persistence
- History view with swipe-to-delete

### 👤 Face Compare
- Select two photos and compare face similarity
- On-device ML Kit face detection with landmark alignment
- Cosine similarity scoring with visual result card
- Landmark-geometry fallback when TFLite model is not available

## Architecture

```
:app  →  :feature:media   →  :core
       →  :feature:camera  →  :core
       →  :feature:ebook   →  :core
       →  :feature:lightlux →  :core
       →  :feature:face    →  :core
```

### Module Structure

| Module | Package | Description |
|--------|---------|-------------|
| `:app` | `com.robin.tools` | Application entry point, unified navigation, theme |
| `:core` | `com.robin.tools.core` | Shared UI components, dimension tokens, extensions, utilities |
| `:feature:media` | `com.robin.tools.feature.media` | Video/image/GIF compression, image filters (FFmpeg + OpenCV) |
| `:feature:camera` | `com.robin.tools.feature.camera` | Camera recording, video editing, trimming, cover selection |
| `:feature:ebook` | `com.robin.tools.feature.ebook` | EPUB to PDF conversion |
| `:feature:lightlux` | `com.robin.tools.feature.lightlux` | Light sensor meter with Room persistence |
| `:feature:face` | `com.robin.tools.feature.face` | Face similarity comparison using ML Kit + TFLite |

### Design System

Shared tokens in `:core` ensure visual consistency across all modules:

| Token | Values |
|-------|--------|
| **Dimension** | `xs=4dp, sm=8dp, md=12dp, lg=16dp, xl=24dp, xxl=32dp, xxxl=48dp` |
| **Shapes** | `small=8dp, medium=12dp, large=16dp, extraLarge=20dp` |
| **Typography** | `headlineLarge(32sp), titleLarge(24sp), titleMedium(18sp), bodyLarge(16sp), bodyMedium(14sp), labelLarge(14sp), labelMedium(12sp), labelSmall(10sp)` |

### Key Design Decisions

- **Kotlin DSL** for all Gradle build files
- **Jetpack Compose** for all feature UI
- **Material 3** with dark/light theme and dynamic color support (Android 12+)
- **Strategy pattern** for compression delegates (Video/Image/GIF)
- **Shared FeatureCard** component in `:core` for consistent card UI
- **OpenCV** for image filtering (no network required, fully on-device)
- **Compose Navigation** with type-safe routes
- **Foreground Service** for long-running media processing

## Tech Stack

- **Min SDK**: 28 | **Target SDK**: 36
- **Kotlin**: 2.0.21 | **AGP**: 8.13.0
- **Compose BOM**: 2024.12.01
- **Room**: 2.6.1 with KSP
- **FFmpeg Kit**: Custom AAR for video processing
- **OpenCV 4.12.0**: Image filtering and stylization
- **CameraX**: Camera recording
- **ML Kit + TensorFlow Lite**: Face detection and recognition
- **epublib + PDFBox Android**: Ebook conversion
- **Retrofit + OkHttp**: Network layer (core module)

## Building

### Debug

```bash
./gradlew :app:assembleDebug
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
- NDK (for FFmpeg/OpenCV native libraries)

## Testing

```bash
# All unit tests across all modules
./gradlew test

# Run tests for specific modules
./gradlew :core:test
./gradlew :feature:media:test
./gradlew :feature:lightlux:test
./gradlew :feature:ebook:test
./gradlew :feature:face:test

# Run a single test class
./gradlew :core:test --tests "com.robin.tools.core.state.ResultStateTest"
./gradlew :feature:media:test --tests "com.robin.tools.feature.media.data.FilterFeatureTest"
./gradlew :feature:lightlux:test --tests "com.robin.tools.feature.lightlux.data.MainViewModelTest"

# Instrumented tests
./gradlew connectedAndroidTest

# Lint
./gradlew lint
```

## Project Structure

```
Tools/
├── app/                              # Main application module
│   └── src/main/
│       ├── java/com/robin/tools/
│       │   ├── App.kt                # Application class
│       │   ├── MainActivity.kt       # Unified navigation hub
│       │   ├── navigation/AppRoute.kt # Type-safe route definitions
│       │   └── ui/theme/             # Color, Typography, Shape, Theme
│       └── AndroidManifest.xml
├── core/                             # Shared infrastructure
│   └── src/main/java/com/robin/tools/core/
│       ├── base/                     # BaseActivity, BaseViewModel, BaseApp
│       ├── ui/                       # FeatureCard, Dimension tokens
│       ├── widget/                   # SwipeBackContainer, custom views
│       ├── ext/                      # Kotlin extensions
│       ├── network/                  # Retrofit, OkHttp, error handling
│       └── state/                    # ResultState sealed class
├── feature/
│   ├── media/                        # Video/Image/GIF compression + filters
│   │   └── src/main/java/.../media/
│   │       ├── data/                 # CompressionManager, FilterManager
│   │       ├── delegate/             # Strategy pattern delegates
│   │       ├── ui/screens/           # Compose screens
│   │       ├── service/              # ForegroundService
│   │       └── libs/                 # ffmpeg-kit.aar, opencv.aar
│   ├── camera/                       # Camera recording/editing
│   │   └── src/main/java/.../camera/
│   │       └── ui/                   # RecordScreen, VideoEdit, Trim, Cover
│   ├── ebook/                        # EPUB to PDF
│   │   └── src/main/java/.../ebook/
│   │       ├── converter/            # EpubToPdfConverter
│   │       └── ui/                   # Compose UI + ViewModel
│   ├── lightlux/                     # Light sensor meter
│   │   └── src/main/java/.../lightlux/
│   │       ├── data/                 # Room DB, DAO, Repository
│   │       └── presentation/         # Compose screens
│   └── face/                         # Face similarity comparison
│       └── src/main/java/.../face/
│           ├── data/                 # FaceDetector, Calculator
│           └── ui/                   # Compose screen + ViewModel
├── gradle/
│   └── libs.versions.toml           # Version catalog
├── build.gradle.kts
└── settings.gradle.kts
```

## License

Private project — All rights reserved.
