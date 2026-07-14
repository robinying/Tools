#!/usr/bin/env bash
# Bypass system multi-select UI: run instrumentation that feeds file paths / bitmaps
# directly into ConcatDelegate / CompressionService / SlideshowGenerator.
set -euo pipefail
ROOT="$(cd "$(dirname "$0")/.." && pwd)"
cd "$ROOT"

SEED_REMOTE="/sdcard/Download/tools_test_av.mp4"

echo "==> Device"
adb devices -l | head -5

if ! adb shell "test -f $SEED_REMOTE" 2>/dev/null; then
  # Prefer an existing short clip under Movies/VideoEditor or Download
  FOUND="$(adb shell "ls -1 /sdcard/Download/*.mp4 /sdcard/Movies/VideoEditor/*.mp4 2>/dev/null | head -1" | tr -d '\r')"
  if [[ -n "${FOUND:-}" ]]; then
    echo "==> Using seed: $FOUND"
    adb shell "cp '$FOUND' $SEED_REMOTE" || true
  else
    echo "ERROR: No seed MP4 at $SEED_REMOTE"
    echo "  adb push your_short.mp4 $SEED_REMOTE"
    exit 1
  fi
fi

adb shell "ls -la $SEED_REMOTE"

echo "==> Grant media perms (app + androidTest packages)"
for PKG in com.robin.tools \
           com.robin.tools.feature.media.test \
           com.robin.tools.feature.camera.test; do
  adb shell pm grant "$PKG" android.permission.READ_MEDIA_VIDEO 2>/dev/null || true
  adb shell pm grant "$PKG" android.permission.READ_MEDIA_IMAGES 2>/dev/null || true
  adb shell pm grant "$PKG" android.permission.POST_NOTIFICATIONS 2>/dev/null || true
done

echo "==> :feature:media ConcatBypassUiTest (delegate, no UI picker)"
./gradlew :feature:media:connectedDebugAndroidTest \
  -Pandroid.testInstrumentationRunnerArguments.class=com.robin.tools.feature.media.ConcatBypassUiTest

echo "==> :app ConcatServiceBypassUiTest (foreground service + paths)"
./gradlew :app:connectedDebugAndroidTest \
  -Pandroid.testInstrumentationRunnerArguments.class=com.robin.tools.ConcatServiceBypassUiTest

echo "==> :feature:camera SlideshowBypassUiTest (synthetic bitmaps)"
./gradlew :feature:camera:connectedDebugAndroidTest \
  -Pandroid.testInstrumentationRunnerArguments.class=com.robin.tools.feature.camera.SlideshowBypassUiTest

echo "==> Done (bypassed multi-select UI)"