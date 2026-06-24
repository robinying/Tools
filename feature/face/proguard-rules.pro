# Keep TFLite native libs
-keep class org.tensorflow.lite.** { *; }
-dontwarn org.tensorflow.lite.**

# ML Kit (model is bundled — no special rules needed for detection)
-dontwarn com.google.mlkit.**
