# Face Recognition Model

Bundled model file:

    mobile_face_net.tflite

## Model Spec (current bundle)

| Property | Value |
|----------|--------|
| Architecture | MobileFaceNet |
| Input | 112×112×3 RGB, float32 NHWC, normalized ≈ `[-1, 1]` via `(x - 127.5) / 128` |
| Output | 192-d L2-normalized embedding (`embeddings`) |
| Size | ~5 MB |
| Source | Community MobileFaceNet TFLite export (on-device, no network) |

`FaceEmbeddingExtractor` reads input/output tensor shapes from the interpreter at load time, so alternate MobileFaceNet exports (128-d or 192-d) keep working without code changes.

## Fallback

If the model file is missing or fails to load, the app falls back to ML Kit landmark geometry features (lower accuracy). The UI shows which engine is active.

## APK Packaging

`build.gradle.kts` sets `noCompress("tflite")` so the model is memory-mapped from the APK.
