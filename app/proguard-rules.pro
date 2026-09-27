# ProGuard / R8 rules for Skill-Kavach (SIH 2026 Release Build)

# ARCore Rules
-keep class com.google.ar.core.** { *; }
-keep interface com.google.ar.core.** { *; }
-dontwarn com.google.ar.core.**

# Application Data & Model Classes
-keep class com.example.skilkavach.data.** { *; }
-keepclassmembers class com.example.skilkavach.data.** { *; }
-keep class com.example.skilkavach.ar.** { *; }

# Room Database Rules
-keep class * extends androidx.room.RoomDatabase
-dontwarn androidx.room.paging.**

# OkHttp & Networking Rules
-keepattributes Signature, InnerClasses, AnnotationDefault, EnclosingMethod
-keepclassmembers class * {
    @com.squareup.okhttp3.** *;
}
-dontwarn okhttp3.**
-dontwarn okio.**

# ZXing QR Rules
-keep class com.google.zxing.** { *; }
-dontwarn com.google.zxing.**

# Firebase Messaging & Play Services Rules
-keep class com.google.firebase.** { *; }
-keep class com.google.android.gms.** { *; }
-dontwarn com.google.firebase.**
-dontwarn com.google.android.gms.**
