# GestoShare Release ProGuard / R8 Rules

# Keep line numbers & source attributes for crash diagnostics without leaking local paths
-keepattributes SourceFile,LineNumberTable
-renamesourcefileattribute SourceFile
-keepattributes Signature,InnerClasses,EnclosingMethod,*Annotation*

# Strip verbose/debug android.util.Log calls in Release builds
-assumenosideeffects class android.util.Log {
    public static boolean isLoggable(java.lang.String, int);
    public static int v(...);
    public static int d(...);
}

# ONNX Runtime Android (JNI + native session & tensor bindings)
-keep class ai.onnxruntime.** { *; }
-dontwarn ai.onnxruntime.**

# MediaPipe Tasks Vision & Protobuf Lite (JNI callbacks + Graph/Packet types)
-keep class com.google.mediapipe.** { *; }
-keep class com.google.protobuf.** { *; }
-dontwarn com.google.mediapipe.**
-dontwarn com.google.protobuf.**

# CameraX & Lifecycle Camera2 bindings
-keep class androidx.camera.** { *; }
-keepclassmembers class * extends androidx.camera.core.impl.ExtendableUseCaseConfigFactory { *; }

# Google Play Services Nearby Connections & Common
-keep class com.google.android.gms.nearby.** { *; }
-keep class com.google.android.gms.common.** { *; }
-dontwarn com.google.android.gms.**

# Room SQLite Entities & DAOs
-keep class * extends androidx.room.RoomDatabase { *; }
-keep @androidx.room.Entity class * { *; }
-keep @androidx.room.Dao interface * { *; }
-dontwarn androidx.room.paging.**

# Application Model Classes & ViewModel reflection safety
-keep class com.example.model.** { *; }
-keep class * extends androidx.lifecycle.ViewModel {
    <init>(...);
}
-keep class * extends androidx.lifecycle.AndroidViewModel {
    <init>(...);
}
