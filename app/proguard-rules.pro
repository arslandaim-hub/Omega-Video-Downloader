# Omega Video Downloader ProGuard / R8 Optimization Rules

# ==============================================================================
# 1. Attributes & Debugging Information
# ==============================================================================
-keepattributes Signature
-keepattributes *Annotation*
-keepattributes EnclosingMethod
-keepattributes InnerClasses
-keepattributes SourceFile,LineNumberTable

# ==============================================================================
# 2. App Data Models & Serialized Fields (Gson / DataStore)
# ==============================================================================
-keep class com.arslandaim.omegavideodownloader.DownloadedVideo { *; }
-keep class com.arslandaim.omegavideodownloader.ActiveDownload { *; }
-keep class com.arslandaim.omegavideodownloader.VideoMetadata { *; }
-keep class com.arslandaim.omegavideodownloader.VideoQuality { *; }

-keepclassmembers class * {
    @com.google.gson.annotations.SerializedName <fields>;
}

# ==============================================================================
# 3. Gson Rules
# ==============================================================================
-keep class com.google.gson.** { *; }
-keep class sun.misc.Unsafe { *; }
-keep class com.google.gson.stream.** { *; }

# ==============================================================================
# 4. Native Binary Extraction & JNI (`youtubedl-android` / `yt-dlp` / `FFmpeg`)
# ==============================================================================
-keep class com.yausername.** { *; }
-dontwarn com.yausername.**

-keep class org.apache.commons.compress.** { *; }
-dontwarn org.apache.commons.compress.**

-keepclasseswithmembernames,includedescriptorclasses class * {
    native <methods>;
}

# ==============================================================================
# 5. Media3 & ExoPlayer Rules
# ==============================================================================
-keep class androidx.media3.** { *; }
-dontwarn androidx.media3.**

# Keep MediaSessionService & MediaController callbacks
-keep class com.arslandaim.omegavideodownloader.PlaybackService { *; }
-keep class com.arslandaim.omegavideodownloader.DownloadService { *; }

# ==============================================================================
# 6. Coil Image Loading & Video Decoding
# ==============================================================================
-keep class coil.** { *; }
-dontwarn coil.**
-keep class coil.decode.VideoFrameDecoder { *; }

# ==============================================================================
# 7. Networking (OkHttp) Rules
# ==============================================================================
-keep class okhttp3.** { *; }
-keep interface okhttp3.** { *; }
-dontwarn okhttp3.**

# ==============================================================================
# 8. Biometric & DataStore Rules
# ==============================================================================
-keep class androidx.biometric.** { *; }
-dontwarn androidx.biometric.**

-keep class androidx.datastore.** { *; }
-dontwarn androidx.datastore.**

# ==============================================================================
# 9. App Compat & Coroutines
# ==============================================================================
-dontwarn kotlinx.coroutines.**
