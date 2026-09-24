# ===================================================================
# 1. Anti-Debugging & Metadata Stripping (Anti-Zip Decompilation)
# ===================================================================

# Strip all Android Logcat calls (d, v, i, w, e) from release bytecode
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

# Preserve required annotations and generic signatures for Retrofit & Kotlin Coroutines
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
# 3. Model Serialization & Networking (Moshi & Retrofit)
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
# 4. Database & Cryptographic Libraries (Room & LazySodium / JNA)
# ===================================================================

# Room database abstractions, entities, and DAOs
-keep class * extends androidx.room.RoomDatabase
-keep @androidx.room.Entity class * { *; }
-keep @androidx.room.Dao interface * { *; }

# LazySodium and Java Native Access (JNA) binaries and JNI wrappers
-keep class com.goterl.lazysodium.** { *; }
-keep class com.sun.jna.** { *; }
-keepclassmembers class * extends com.sun.jna.** { *; }