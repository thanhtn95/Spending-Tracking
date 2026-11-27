# Keep Retrofit / Gson DTOs & serialized fields
-keep class com.spendingtracker.app.data.** { *; }
-keepattributes Signature, InnerClasses, EnclosingMethod, *Annotation*
-keepclassmembers class * {
    @com.google.gson.annotations.SerializedName <fields>;
}
-keep interface com.spendingtracker.app.data.SpendingApiService { *; }
-dontwarn okhttp3.**
-dontwarn okio.**
-dontwarn retrofit2.**
