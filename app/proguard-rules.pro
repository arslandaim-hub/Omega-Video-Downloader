# Add project specific ProGuard rules here.
# By default, the flags in this file are appended to flags specified
# in C:\Users\AK\AppData\Local\Android\sdk/tools/proguard/proguard-android.txt
# You can edit the include path and order by changing the proguardFiles
# directive in build.gradle.kts.
#
# For more details, see
#   http://developer.android.com/guide/developing/tools/proguard/index.html

# Keep app models and managers that use reflection/Gson/DataStore
-keep class com.arslandaim.omegavideodownloader.DownloadedVideo { *; }
-keep class com.arslandaim.omegavideodownloader.ActiveDownload { *; }
-keep class com.arslandaim.omegavideodownloader.VideoMetadata { *; }
-keep class com.arslandaim.omegavideodownloader.VideoQuality { *; }

# OkHttp rules
-keepattributes Signature
-keepattributes *Annotation*
-keep class okhttp3.** { *; }
-keep interface okhttp3.** { *; }
-dontwarn okhttp3.**

# Gson rules
-keep class com.google.gson.** { *; }
-keepattributes Signature
-keepattributes *Annotation*
-keepattributes EnclosingMethod
-keep class sun.misc.Unsafe { *; }
-keep class com.google.gson.stream.** { *; }

# Media3 / ExoPlayer rules
-keep class androidx.media3.** { *; }
-dontwarn androidx.media3.**

# youtubedl-android core rules
# The library uses JNI and reflection for yt-dlp/ffmpeg interaction
-keep class com.yausername.** { *; }
-dontwarn com.yausername.**

# Keep compression classes used to extract yt-dlp and python binaries
-keep class org.apache.commons.compress.** { *; }
-dontwarn org.apache.commons.compress.**

# General JNI protection
-keepclasseswithmembernames,includedescriptorclasses class * {
    native <methods>;
}
