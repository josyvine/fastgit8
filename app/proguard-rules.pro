# ===================================================================
# 1. Anti-Debugging & Metadata Stripping (Anti-Zip Decompilation)
# ===================================================================

# Strip all Android Logcat calls from release bytecode
-assumenosideeffects class android.util.Log {
    public static *** d(...);
    public static *** v(...);
    public static *** i(...);
    public static *** w(...);
    public static *** e(...);
    public static *** println(...);
}

# Remove debugging source file names, line number tables, and local variables
-renamesourcefileattribute ''
-keepattributes !SourceFile,!LineNumberTable,!LocalVariableTable,!LocalVariableTypeTable,!MethodParameters

# Preserve required annotations and generic signatures
-keepattributes *Annotation*,Signature,InnerClasses,EnclosingMethod

# ===================================================================
# 2. In-Memory Loader & Stub Application Protection
# ===================================================================

# Keep StubApplication intact so Android can launch the shell
-keep class com.vineyard.fastgit.app.StubApplication {
    public <init>();
    protected void attachBaseContext(android.content.Context);
    *;
}

# Preserve ClassLoader reflection fields utilized by InMemoryDexClassLoader
-keepclassmembers class java.lang.ClassLoader {
    private java.lang.ClassLoader parent;
    *;
}

# Preserve Android standard application entry components
-keep class * extends android.app.Application
-keep class * extends android.app.Activity
-keep class * extends androidx.core.content.FileProvider

# ===================================================================
# 3. Model Serialization & Networking (Moshi, Retrofit, & OkHttp)
# ===================================================================

# Moshi JSON adapters and codegen classes
-keepclassmembers class * {
    @com.squareup.moshi.Json *;
}
-keep class com.vineyard.fastgit.app.models.** { *; }
-keepclassmembers class com.vineyard.fastgit.app.models.** { *; }
-keep class * extends com.squareup.moshi.JsonAdapter
-keep class com.squareup.moshi.** { *; }

# Network API response data classes (WorkflowRunJobsResponse, WorkflowJob, WorkflowStep)
-keep class com.vineyard.fastgit.app.network.** { *; }
-keepclassmembers class com.vineyard.fastgit.app.network.** { *; }

# Retrofit service interfaces, proxy methods, and OkHttp streams
-keep class retrofit2.** { *; }
-keepclasseswithmembers class * {
    @retrofit2.http.* <methods>;
}
-keep class okhttp3.** { *; }

# Coil Image Loader
-keep class coil.** { *; }

# ===================================================================
# 4. Database & Cryptographic Libraries (Room & LazySodium / JNA)
# ===================================================================

# Room database abstractions, entities, and DAOs
-keep class androidx.room.** { *; }
-dontwarn androidx.room.**
-keep class * extends androidx.room.RoomDatabase
-keep @androidx.room.Entity class * { *; }
-keep @androidx.room.Dao interface * { *; }
-keep interface com.vineyard.fastgit.app.database.** { *; }
-keep class com.vineyard.fastgit.app.database.** { *; }

# LazySodium and Java Native Access (JNA) native bindings
-keep class com.goterl.lazysodium.** { *; }
-keep class com.sun.jna.** { *; }
-keepclassmembers class * extends com.sun.jna.** { *; }

# FIX FOR JNA DESKTOP AWT DEPENDENCY:
# Tell R8 to ignore missing desktop Java AWT classes that don't exist on Android
-dontwarn java.awt.**
-dontwarn com.sun.jna.**

# ===================================================================
# 5. Kotlin Coroutines & Flow (Cross-Dex Signature Synchronization)
# ===================================================================

# Preserves Coroutine Flow method descriptors across InMemoryDexClassLoader and APK
-keep class kotlinx.coroutines.** { *; }
-keep interface kotlinx.coroutines.** { *; }
-dontwarn kotlinx.coroutines.**

# ===================================================================
# 6. Kotlin Runtime & Standard Library (Intrinsics & Reflection)
# ===================================================================

# Prevents R8 from stripping or inlining Kotlin runtime internals required by external DEX
-keep class kotlin.jvm.internal.** { *; }
-keep class kotlin.** { *; }
-keep interface kotlin.** { *; }
-dontwarn kotlin.**