# Proguard rules for Business Mileage Tracker
-dontobfuscate
-dontoptimize

-keep class com.businesstracker.mileage.** { *; }
-keep class com.businesstracker.mileage.data.local.entity.** { *; }
-keep class com.businesstracker.mileage.domain.model.** { *; }
-keep class com.businesstracker.mileage.data.local.model.** { *; }

-dontwarn com.google.android.gms.**
-keep class com.google.android.gms.auth.api.signin.** { *; }
-keep class com.google.android.gms.common.api.** { *; }

# Gson & Serialization
-keepattributes Signature
-keepattributes *Annotation*
-keepclassmembers class * {
    @com.google.gson.annotations.SerializedName <fields>;
}

# Osmdroid & Google Maps
-keep class org.osmdroid.** { *; }
-dontwarn org.osmdroid.**
-keep class com.google.maps.android.** { *; }
-dontwarn com.google.maps.android.**

# WorkManager & Hilt
-keep class androidx.work.** { *; }
-keep class androidx.hilt.work.** { *; }
