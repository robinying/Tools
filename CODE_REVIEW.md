# Code Review: Tools Project

## Architecture Review

### Overall Assessment
The project merges three independently-developed Android apps (VideoEditor, EbookConverter, LightLux2) into a single multi-module application with clean architecture. The core module provides shared infrastructure from BaseApp, and each feature module is self-contained with its own data/domain/presentation layers.

---

## Risk Points

### 🔴 High Priority

#### 1. FFmpeg Binary Distribution
- **File**: `feature/media/libs/ffmpeg-kit.aar`
- **Issue**: The AAR is committed directly to the repository. It's a large native binary (~50MB+) that may not support all ABIs.
- **Recommendation**: Use Maven dependency `com.arthenica:ffmpeg-kit-full` instead of local AAR, or configure ABI splits in Gradle.

#### 2. WebView PDF Rendering on Main Thread
- **File**: `feature/ebook/.../EpubToPdfConverter.kt:76-86`
- **Issue**: `renderHtmlToPdf()` uses `suspendCancellableCoroutine` with WebView on the main thread. The `CompletableDeferred.await()` inside `withContext(Dispatchers.Main)` can block if WebView fails to load.
- **Recommendation**: Add timeout handling and error recovery. Consider a max retry count or fallback error handling for WebView rendering failures.

#### 3. Temp File Cleanup
- **Files**: `feature/media/.../FileUtils.kt`, `feature/ebook/.../EpubToPdfConverter.kt`
- **Issue**: Temp files created during compression/conversion are not always cleaned up on failure. `EpubToPdfConverter` has a commented-out `tempDir.deleteRecursively()` call.
- **Recommendation**: Use `try-finally` blocks to ensure cleanup, or register a shutdown hook for temp directory cleanup.

#### 4. CompressionManager Singleton with Mutable State
- **File**: `feature/media/.../CompressionManager.kt`
- **Issue**: `object` singleton with `@Volatile` and `MutableStateFlow` is not truly thread-safe for compound operations. `startTask()` + `updateState()` should be atomic.
- **Recommendation**: Use a `Mutex` or `StateFlow` with atomic updates to prevent race conditions between service and UI.

### 🟡 Medium Priority

#### 5. Permission Handling Across Android Versions
- **Manifest**: Multiple `maxSdkVersion` attributes for storage permissions
- **Issue**: The app needs `READ_MEDIA_IMAGES`, `READ_MEDIA_VIDEO` (Android 13+) and `READ_EXTERNAL_STORAGE` (below 13). Runtime permission requests are only in CompressionScreen, not in the unified MainActivity.
- **Recommendation**: Create a centralized permission helper in `:core` and request all needed permissions at app startup or before feature navigation.

#### 6. Foreground Service Type Declaration
- **File**: `app/src/main/AndroidManifest.xml`
- **Issue**: `foregroundServiceType="mediaProcessing"` requires Android 14+ declaration. On Android 14+, the service must declare the type both in manifest AND at runtime via `startForeground()`.
- **Recommendation**: Already handled in `CompressionService.kt` with `ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PROCESSING`, but verify the `<uses-permission>` for `FOREGROUND_SERVICE_MEDIA_PROCESSING` is present (it is).

#### 7. Room Database Singleton Pattern
- **File**: `feature/lightlux/.../AppDatabase.kt`
- **Issue**: Double-checked locking pattern with `@Volatile` is correct, but no migration strategy is defined (`exportSchema = false`).
- **Recommendation**: Enable schema export for production. Add migration strategies or use `fallbackToDestructiveMigration()` during development.

#### 8. EbookConverter Uses Hidden API
- **File**: `feature/ebook/.../PrintDocumentAdapterHelper.java`
- **Issue**: This class accesses hidden Android APIs (`PrintDocumentAdapter.LayoutCallback`, `PrintDocumentAdapter.WriteCallback`) which may break on different Android versions or OEM skins.
- **Recommendation**: Wrap in try-catch and provide a fallback (e.g., use PdfDocument directly if the hidden API fails).

### 🟢 Low Priority

#### 9. Different minSdk Across Original Projects
- VideoEditor: minSdk 28, EbookConverter: minSdk 30, LightLux2: minSdk 28
- **Resolution**: Unified to minSdk 28 across all modules. EbookConverter's WebView PDF rendering works on API 28+.

#### 10. Network Module Unused
- The `:core` network module (Retrofit, OkHttp, interceptors) is included but not used by any feature module.
- **Recommendation**: Keep for future use or remove to reduce APK size (~200KB savings).

#### 11. No Dependency Injection Framework
- ViewModels are created manually with `ViewModelProvider.Factory`.
- **Recommendation**: For a project this size, manual injection is acceptable. If the project grows, consider Hilt or Koin.

#### 12. LightLux Sensor Lifecycle
- **Original**: Sensor registration in Activity `onResume`/`onPause`
- **Current**: LightLux doesn't register the sensor in Compose — the sensor listener needs to be wired up in the Activity or a `remember` block.
- **Recommendation**: Create a `SensorEventListener` composable that registers in `LaunchedEffect` and unregisters in `onDispose`.

---

## Improvement Suggestions

1. **Add ProGuard rules** for each feature module — especially for FFmpeg and PDFBox reflection usage
2. **Add CI/CD** configuration (GitHub Actions) for automated build + test
3. **Consider adding Hilt** for dependency injection as the project grows
4. **Add crash reporting** (Firebase Crashlytics or ACRA)
5. **Add analytics** to track feature usage across the three tools
6. **Screenshots for README** — add device screenshots for each feature
7. **Dark theme** support — all Compose screens should respect system dark mode
8. **Accessibility** audit — ensure all Compose components have content descriptions

---

## Module Dependency Summary

```
app → core, feature:media, feature:ebook, feature:lightlux
feature:media → core
feature:ebook → core
feature:lightlux → core
```

All feature modules depend only on `:core`, not on each other. This enforces module isolation and makes it easy to add or remove features.