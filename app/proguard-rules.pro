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
# 3. Dynamic Payload Runtime Dependencies (CRITICAL FIX)
# Because payload.bin is loaded dynamically at runtime via InMemoryDexClassLoader,
# R8 cannot inspect its references and will strip Kotlin runtime and Coroutines.
# ===================================================================

-keep class kotlin.** { *; }
-keep interface kotlin.** { *; }
-dontwarn kotlin.**

-keep class kotlinx.** { *; }
-keep interface kotlinx.** { *; }
-dontwarn kotlinx.**

-keep class androidx.** { *; }
-keep interface androidx.** { *; }
-dontwarn androidx.**

# ===================================================================
# 4. Model Serialization & Networking (Moshi & Retrofit)
# ===================================================================

# Moshi JSON adapters and codegen classes
-keepclassmembers class * {
    @com.squareup.moshi.Json *;
}
-keep class com.vineyard.fastgit.app.models.** { *; }
-keep class * extends com.squareup.moshi.JsonAdapter

# Retrofit service interfaces and dynamic proxy methods
-keepclassmembers,allowobfuscation interface * {
    @retrofit2.http.* <methods>;
}

# ===================================================================
# 5. Database & Cryptographic Libraries (Room & LazySodium / JNA)
# ===================================================================

# Room database runtime and core classes
-keep class androidx.room.** { *; }
-dontwarn androidx.room.**

# Keep all Room database classes, entities, DAOs, and generated _Impl classes
-keep class * extends androidx.room.RoomDatabase { *; }
-keep class com.vineyard.fastgit.app.database.** { *; }
-keep interface com.vineyard.fastgit.app.database.** { *; }
-keepclassmembers class com.vineyard.fastgit.app.database.** { *; }
-keepclassmembers interface com.vineyard.fastgit.app.database.** { *; }
-keep class * extends com.vineyard.fastgit.app.database.** { *; }
-keep class * implements com.vineyard.fastgit.app.database.** { *; }

# Preserve Room annotations
-keep @androidx.room.Entity class * { *; }
-keep @androidx.room.Dao interface * { *; }
-keep @androidx.room.Database class * { *; }

# LazySodium and Java Native Access (JNA) native bindings
-keep class com.goterl.lazysodium.** { *; }
-keep class com.sun.jna.** { *; }
-keepclassmembers class * extends com.sun.jna.** { *; }

# FIX FOR JNA DESKTOP AWT DEPENDENCY:
# Tell R8 to ignore missing desktop Java AWT classes that don't exist on Android
-dontwarn java.awt.**
-dontwarn com.sun.jna.**