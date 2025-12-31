# JNI
-keepclasseswithmembernames class * {
    native <methods>;
}

# OcrEngine JNI bridge
-keep class me.fleey.ppocrv5.ocr.OcrEngine { *; }
-keep class me.fleey.ppocrv5.ocr.OcrResult { *; }

# Kotlin Serialization
-keepattributes *Annotation*, InnerClasses
-dontnote kotlinx.serialization.AnnotationsKt

-keepclassmembers @kotlinx.serialization.Serializable class ** {
    *** Companion;
}
-keepclasseswithmembers class ** {
    kotlinx.serialization.KSerializer serializer(...);
}

# Navigation Compose
-keep class * extends androidx.navigation.NavArgs
-keepnames class * extends android.os.Parcelable
-keepnames class * extends java.io.Serializable

# Coil
-dontwarn coil3.**

# Remove logging in release
-assumenosideeffects class android.util.Log {
    public static int v(...);
    public static int d(...);
    public static int i(...);
}
