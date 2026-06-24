# Face Recognition Model

Place the MobileFaceNet TFLite model file here:

    mobile_face_net.tflite

## Model Requirements

- Input: 112×112×3 RGB, normalized to [-1, 1]
- Output: 128-dimensional L2-normalized embedding
- Format: TensorFlow Lite FlatBuffer (.tflite)

## Recommended Source

Download a pre-trained MobileFaceNet TFLite model from:
https://github.com/sirius-ai/MobileFaceNet_TensorFlow

Or convert from InsightFace model zoo:
https://github.com/deepinsight/insightface

## APK Packaging

The build.gradle.kts includes `noCompress("tflite")` so the model
file is NOT compressed in the APK — TFLite reads it via memory-map.
