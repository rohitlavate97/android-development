# ==============================================================================
# Enterprise Finance Tracker — Production ProGuard & R8 Configuration
# ==============================================================================

# 1. Line Number Preservation for Crashlytics Deobfuscation
-keepattributes LineNumberTable,SourceFile,Signature,InnerClasses,EnclosingMethod
-renamesourcefileattribute SourceFile

# 2. Strip Logging in Production Release Builds
-assumenosideeffects class android.util.Log {
    public static boolean isLoggable(java.lang.String, int);
    public static int v(...);
    public static int d(...);
    public static int i(...);
    public static int w(...);
}

# 3. Kotlinx Serialization Keep Rules
-keepattributes *Annotation*,InnerClasses
-dontnote kotlinx.serialization.SerializationKt
-keepclassmembers class * {
    *** Companion;
}
-keepclasseswithmembers class * {
    kotlinx.serialization.KSerializer serializer(...);
}
-keep,allowobfuscation,allowshrinking class * {
    <fields>;
}

# 4. Room SQLite Persistence Keep Rules
-keep class * extends androidx.room.RoomDatabase
-dontwarn androidx.room.paging.**
-keepclassmembers class * extends androidx.room.RoomDatabase {
    <init>();
}
-keep class **_Impl { *; }
-keep @androidx.room.Entity class * { *; }
-keep @androidx.room.Dao class * { *; }

# 5. Retrofit & OkHttp Keep Rules
-dontwarn retrofit2.**
-keep class retrofit2.** { *; }
-keepattributes Exceptions
-keepclasseswithmembers interface * {
    @retrofit2.http.* <methods>;
}
-dontwarn okhttp3.**
-dontwarn okio.**

# 6. Koin Dependency Injection Keep Rules
-keep class io.insert.koin.** { *; }
-dontwarn io.insert.koin.**

# 7. Kotlin Coroutines & Datetime
-keep class kotlinx.coroutines.** { *; }
-keep class kotlinx.datetime.** { *; }
