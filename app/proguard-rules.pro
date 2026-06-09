# Add project specific ProGuard rules here.
# You can control the set of applied configuration files using the
# proguardFiles setting in build.gradle.
#
# For more details, see
#   http://developer.android.com/guide/developing/tools/proguard.html

# Fix R8 error: Library class android.content.res.XmlResourceParser implements program class org.xmlpull.v1.XmlPullParser
# This happens when libraries bundle their own XML pull parser. Android framework provides these classes.
-dontwarn org.xmlpull.v1.**
-keep class org.xmlpull.** { *; }

# pdfbox-android optionally uses JP2 decoder
-dontwarn com.gemalto.jp2.JP2Decoder
-dontwarn com.gemalto.jp2.JP2Encoder

# epublib-core optional SLF4J logging
-dontwarn org.slf4j.Logger
-dontwarn org.slf4j.LoggerFactory

# If your project uses WebView with JS, uncomment the following
# and specify the fully qualified class name to the JavaScript interface
# class:
#-keepclassmembers class fqcn.of.javascript.interface.for.webview {
#   public *;
#}

# Uncomment this to preserve the line number information for
# debugging stack traces.
#-keepattributes SourceFile,LineNumberTable

# If you keep the line number information, uncomment this to
# hide the original source file name.
#-renamesourcefileattribute SourceFile