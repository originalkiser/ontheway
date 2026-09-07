# Keep Gson-parsed DTOs (field names are matched to JSON keys via reflection).
-keep class com.reststop.countdown.data.remote.dto.** { *; }

# Retrofit / OkHttp
-dontwarn okhttp3.**
-dontwarn retrofit2.**
-keepattributes Signature
-keepattributes *Annotation*
