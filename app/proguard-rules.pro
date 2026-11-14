# Proguard rules for Business Mileage Tracker
-keep class com.businesstracker.mileage.data.local.entity.** { *; }
-keep class com.businesstracker.mileage.domain.model.** { *; }
-keep class com.businesstracker.mileage.data.local.model.** { *; }

-dontwarn com.google.android.gms.**
-keep class com.google.android.gms.auth.api.signin.** { *; }
-keep class com.google.android.gms.common.api.** { *; }
