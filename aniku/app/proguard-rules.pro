# Add project specific ProGuard rules here.
# You can control the set of applied configuration files using the
# proguardFiles setting in build.gradle.
#
# For more details, see
#   http://developer.android.com/guide/developing/tools/proguard.html

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

# ============================================================
# FIX: NPE di Text/Compose gara-gara R8 obfuscate model class
# yang dipakai Moshi (field kebaca null padahal non-null).
# Cuma kejadian di build minify-on (perf/release), gak di debug.
# ============================================================

# --- Kotlin metadata & reflection (wajib buat moshi-kotlin reflection based) ---
-keep class kotlin.Metadata { *; }
-keepattributes RuntimeVisibleAnnotations, AnnotationDefault, Signature, InnerClasses, EnclosingMethod

# --- Moshi ---
-keep,allowobfuscation,allowshrinking interface com.squareup.moshi.JsonQualifier
-keepclassmembers class kotlin.Metadata { public <methods>; }
-keep class com.squareup.moshi.** { *; }
-keep interface com.squareup.moshi.** { *; }
-dontwarn com.squareup.moshi.**
-dontwarn okio.**

# Keep semua generated JsonAdapter (dari moshi-kotlin-codegen/ksp)
-keep class **JsonAdapter { *; }
-keepclassmembers class * extends com.squareup.moshi.JsonAdapter { *; }
-keepclasseswithmembers class * {
    @com.squareup.moshi.FromJson <methods>;
}
-keepclasseswithmembers class * {
    @com.squareup.moshi.ToJson <methods>;
}

# Keep semua data class model kita yang dipakai Moshi (@JsonClass) beserta
# constructor & field-nya SUPAYA NAMA/URUTAN FIELD GAK BERUBAH pas serialize/deserialize.
-keep @com.squareup.moshi.JsonClass class * { *; }
-keepclassmembers class com.example.network.** {
    *** Companion;
    <init>(...);
    <fields>;
}
-keep class com.example.network.** { *; }

# WatchHistoryItem & BookmarkedAnime (moshi tanpa codegen annotation, reflection based)
-keep class com.example.network.WatchHistoryItem { *; }
-keep class com.example.network.BookmarkedAnime { *; }

# --- Retrofit ---
-keepattributes Exceptions
-keep class retrofit2.** { *; }
-keepclasseswithmembers interface com.example.network.** {
    @retrofit2.http.* <methods>;
}
-dontwarn retrofit2.**
-if interface * { @retrofit2.http.* <methods>; }
-keep,allowobfuscation interface <1>

# --- OkHttp ---
-dontwarn okhttp3.**

# --- Firebase (Remote Config, FCM, Realtime Database) ---
-keepattributes Signature
-keep class com.google.firebase.** { *; }
-dontwarn com.google.firebase.**

# --- Data class umum: constructor + field, biar aman kalau ada model lain nanti ---
-keepclassmembers class com.example.** {
    public <init>(...);
}
