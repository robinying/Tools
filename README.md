# Tools

A unified Android utility app combining three powerful tools into a single application, built with clean multi-module architecture.

## Features

### 🎬 Media Editor
- **Video Compression** — Reduce video file size with configurable quality levels
- **Image Compression** — Compress images with JPEG quality control
- **GIF Conversion** — Convert video to animated GIF with FPS/size settings
- Foreground service for background processing with notifications
- Progress tracking and gallery save

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

## Architecture

```
:app  →  :feature:media  →  :core
       →  :feature:ebook   →  :core
       →  :feature:lightlux →  :core
```

### Module Structure

| Module | Package | Description |
|--------|---------|-------------|
| `:app` | `com.robin.tools` | Application entry point, unified navigation |
| `:core` | `com.robin.tools.core` | Base classes, extensions, utilities from BaseApp |
| `:feature:media` | `com.robin.tools.feature.media` | Video/image/GIF compression (from VideoEditor) |
| `:feature:ebook` | `com.robin.tools.feature.ebook` | EPUB to PDF conversion (from EbookConverter) |
| `:feature:lightlux` | `com.robin.tools.feature.lightlux` | Light sensor meter (from LightLux2, converted to Compose) |

### Key Design Decisions

- **Kotlin DSL** for all Gradle build files
- **Jetpack Compose** for all feature UI (LightLux converted from XML/ViewBinding)
- **Room database** for LightLux data persistence
- **Foreground Service** for media compression
- **Compose Canvas** chart for real-time lux display (replacing MPAndroidChart)
- **Sealed class navigation** — no Navigation Compose dependency, lean approach

## Tech Stack

- **Min SDK**: 28 | **Target SDK**: 36
- **Kotlin**: 2.0.21 | **AGP**: 8.13.0
- **Compose BOM**: 2024.12.01
- **Room**: 2.6.1 with KSP
- **FFmpeg Kit**: Custom AAR for media processing
- **epublib** + **PDFBox Android**: For ebook conversion
- **Retrofit + OkHttp**: Network layer (core module)
- **ViewBinding**: Core module supports both Compose and ViewBinding

## Building

```bash
./gradlew :app:assembleDebug
```

### Requirements
- Android Studio Hedgehog or later
- JDK 17
- Android SDK 35+
- NDK (for FFmpeg native libraries)

## Testing

```bash
# All unit tests (179+ tests across all modules)
./gradlew test

# Run tests for specific modules
./gradlew :core:test
./gradlew :feature:media:test
./gradlew :feature:lightlux:test
./gradlew :feature:ebook:test

# Run a single test class
./gradlew :core:test --tests "com.robin.tools.core.state.ResultStateTest"
./gradlew :feature:lightlux:test --tests "com.robin.tools.feature.lightlux.data.MainViewModelTest"

# Instrumented tests
./gradlew connectedAndroidTest

# Lint
./gradlew lint
```

### Test Coverage by Module

| Module | Tests | Scope |
|--------|-------|-------|
| `:core` | 14 test classes | ResultState, ExceptionHandle, AppException, Error, NumberExtension, BooleanExtension, NumberUtils, UrlEncoderUtils, RegexTool, StringExt, ListDataUiState, UpdateUiState, LogUtilsExt, BaseViewModel |
| `:feature:media` | 3 test classes | CompressionManager, CompressionState, CompressionDelegateFactory |
| `:feature:lightlux` | 3 test classes | LightEntry, MainViewModel (MockK), SnapshotListViewModel (MockK) |
| `:feature:ebook` | 2 test classes | EpubToPdfConverter, ConversionState |

**Testing patterns**: Core module uses JUnit 4 only. Feature modules with MockK + kotlinx-coroutines-test use `UnconfinedTestDispatcher` for ViewModel coroutine testing and `coEvery`/`coVerify` for suspend function mocking.

## Project Structure

```
Tools/
├── app/                          # Main application module
│   └── src/main/
│       ├── java/com/robin/tools/
│       │   ├── App.kt            # Application class
│       │   └── MainActivity.kt   # Unified navigation hub
│       └── AndroidManifest.xml
├── core/                         # Shared infrastructure
│   └── src/main/java/com/robin/tools/core/
│       ├── base/                 # BaseActivity, BaseViewModel, BaseApp...
│       ├── callback/             # LiveData + ObservableField wrappers
│       ├── coroutine/            # Coroutine helpers
│       ├── event/                # SharedFlowBus, AppViewModel
│       ├── ext/                  # Kotlin extensions
│       ├── network/              # Retrofit, interceptors, error handling
│       ├── state/                # ResultState
│       ├── util/                 # ViewBinding, LogUtils, PageStack...
│       └── widget/               # Custom views, dialogs, popups
├── feature/
│   ├── media/                    # Video/Image/GIF compression
│   │   └── src/main/java/.../media/
│   │       ├── data/             # CompressionManager, State, FileUtils
│   │       ├── delegate/         # Strategy pattern: Video/Image/GIF delegates
│   │       ├── ui/screens/       # Compose UI screens
│   │       └── service/         # ForegroundService
│   ├── ebook/                    # EPUB to PDF
│   │   └── src/main/java/.../ebook/
│   │       ├── converter/       # EpubToPdfConverter
│   │       ├── ui/              # Compose UI + ViewModel
│   │       └── util/            # StorageUtils
│   └── lightlux/                 # Light sensor meter
│       └── src/main/java/.../lightlux/
│           ├── data/             # Room DB, DAO, Repository, ViewModels
│           └── presentation/     # Compose screens (meter, snapshot list)
├── gradle/
│   └── libs.versions.toml       # Version catalog
├── build.gradle.kts
└── settings.gradle.kts
```

## License

Private project — All rights reserved.