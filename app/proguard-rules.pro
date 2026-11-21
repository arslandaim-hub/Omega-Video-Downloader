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
-keep class com.arslandaim.omegavideodownloader.StreamCandidate { *; }
-keep class com.arslandaim.omegavideodownloader.StreamKind { *; }
-keep class com.arslandaim.omegavideodownloader.ResolveSource { *; }
-keep class com.arslandaim.omegavideodownloader.MovieSearchResult { *; }
-keep class com.arslandaim.omegavideodownloader.SearchSource { *; }
-keep class com.arslandaim.omegavideodownloader.SearchFilter { *; }
-keep class com.arslandaim.omegavideodownloader.WatchHistoryItem { *; }
-keep class com.arslandaim.omegavideodownloader.LikedVideo { *; }

-keepclassmembers class * {
    @com.google.gson.annotations.SerializedName <fields>;
}

# ==============================================================================
# 3. Native Binary Extraction & JNI (`youtubedl-android` / `yt-dlp` / `FFmpeg` / `aria2c`)
# ==============================================================================
-keep class com.yausername.** { *; }
-dontwarn com.yausername.**

-keep class org.apache.commons.compress.** { *; }
-dontwarn org.apache.commons.compress.**

-keepclasseswithmembernames,includedescriptorclasses class * {
    native <methods>;
}

# ==============================================================================
# 4. Services & JavaScript Bridge (WebView Sniffer)
# ==============================================================================
-keep class com.arslandaim.omegavideodownloader.PlaybackService { *; }
-keep class com.arslandaim.omegavideodownloader.DownloadService { *; }

-keepclassmembers class com.arslandaim.omegavideodownloader.resolver.** {
    @android.webkit.JavascriptInterface <methods>;
}

# Note: Media3, Coil, OkHttp, Gson, Biometric, and DataStore ship their own
# consumer ProGuard rules; blanket -keep rules have been removed for R8 shrinking.
-dontwarn kotlinx.coroutines.**
-dontwarn org.conscrypt.**
-dontwarn org.bouncycastle.**
-dontwarn org.openjsse.**
