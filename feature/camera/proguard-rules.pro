# EGL/GLES native calls
-keep class com.robin.tools.feature.camera.opengl.** { *; }

# MediaCodec/MediaMuxer uses reflection internally
-keep class android.media.MediaCodec { *; }
-keep class android.media.MediaMuxer { *; }
-keep class android.media.MediaFormat { *; }
-keep class android.media.MediaExtractor { *; }
