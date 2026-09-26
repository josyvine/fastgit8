# ===================================================================
# 1. Anti-Debugging & Metadata Stripping (Anti-Zip Decompilation)
# ===================================================================

# Strip all standard Android system Logcat calls from release bytecode
-assumenosideeffects class android.util.Log {
    public static *** d(...);
    public static *** v(...);
    public static *** i(...);
    public static *** w(...);
    public static *** e(...);
    public static *** println(...);
}

# Obfuscate source file names
-renamesourcefileattribute 'SourceFile'

# Strip local variables and parameter names
-keepattributes SourceFile,LineNumberTable,!LocalVariableTable,!LocalVariableTypeTable,!MethodParameters

# Preserve required annotations and generic signatures
-keepattributes *Annotation*,Signature,InnerClasses,EnclosingMethod

# ===================================================================
# 2. In-Memory Loader & Stub Application Protection
# ===================================================================

-keep class com.vineyard.fastgit.app.StubApplication {
    public <init>();
    protected void attachBaseContext(android.content.Context);
    *;
}

-keepclassmembers class java.lang.ClassLoader {
    private java.lang.ClassLoader parent;
    *;
}

-keep class * extends android.app.Application
-keep class * extends android.app.Activity
-keep class * extends androidx.core.content.FileProvider

# ===================================================================
# 3. Model Serialization & Networking (Moshi, Retrofit, & OkHttp)
# ===================================================================

-keepclassmembers class * {
    @com.squareup.moshi.Json *;
}
-keep class com.vineyard.fastgit.app.models.** { *; }
-keepclassmembers class com.vineyard.fastgit.app.models.** { *; }
-keep class * extends com.squareup.moshi.JsonAdapter
-keep class com.squareup.moshi.** { *; }

-keep class com.vineyard.fastgit.app.network.** { *; }
-keepclassmembers class com.vineyard.fastgit.app.network.** { *; }

-keep class retrofit2.** { *; }
-keepclasseswithmembers class * {
    @retrofit2.http.* <methods>;
}
-keep class okhttp3.** { *; }

-keep class coil.** { *; }

# ===================================================================
# 4. Database & Cryptographic Libraries (Room & LazySodium / JNA)
# ===================================================================

-keep class androidx.room.** { *; }
-dontwarn androidx.room.**
-keep class * extends androidx.room.RoomDatabase
-keep @androidx.room.Entity class * { *; }
-keep @androidx.room.Dao interface * { *; }
-keep interface com.vineyard.fastgit.app.database.** { *; }
-keep class com.vineyard.fastgit.app.database.** { *; }

-keep class com.goterl.lazysodium.** { *; }
-keep class com.sun.jna.** { *; }
-keepclassmembers class * extends com.sun.jna.** { *; }

-dontwarn java.awt.**
-dontwarn com.sun.jna.**

# ===================================================================
# 5. Kotlin Coroutines & Flow
# ===================================================================

-keep class kotlinx.coroutines.** { *; }
-keep interface kotlinx.coroutines.** { *; }
-dontwarn kotlinx.coroutines.**

# ===================================================================
# 6. Kotlin Runtime & Standard Library
# ===================================================================

-keep class kotlin.jvm.internal.** { *; }
-keep class kotlin.** { *; }
-keep interface kotlin.** { *; }
-dontwarn kotlin.**

# ===================================================================
# 7. App Logger & ViewModels
# ===================================================================

-keep class com.vineyard.fastgit.app.utils.** { *; }
-keepclassmembers class com.vineyard.fastgit.app.utils.** { *; }

-keep class com.vineyard.fastgit.app.viewmodel.** { *; }
-keepclassmembers class com.vineyard.fastgit.app.viewmodel.** { *; }

# ===================================================================
# 8. AndroidX Core, Activity & Lifecycle Protection
# ===================================================================

-keep class androidx.core.content.ContextCompat { *; }
-keep class androidx.core.app.ActivityCompat { *; }
-keep class androidx.core.view.** { *; }
-dontwarn androidx.core.**

-keep class androidx.activity.** { *; }
-dontwarn androidx.activity.**

-keep class androidx.lifecycle.** { *; }
-dontwarn androidx.lifecycle.**

# ===================================================================
# 9. Jetpack Compose Core Engine (WITHOUT bloated icon packs)
# ===================================================================

# Essential runtime (fixes ComposableLambdaKt crash)
-keep class androidx.compose.runtime.** { *; }

# Essential UI foundations
-keep class androidx.compose.ui.** { *; }
-keep class androidx.compose.foundation.** { *; }
-keep class androidx.compose.animation.** { *; }
-keep class androidx.compose.material3.** { *; }

# Don't warn on missing optional compose desktop/tooling classes
-dontwarn androidx.compose.**