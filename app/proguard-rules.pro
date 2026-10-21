# Sokhan ProGuard/R8 rules.

# --- Kotlin serialization ---
# kotlinx.serialization generated serializer classes & companions.
-keepattributes *Annotation*, InnerClasses
-dontnote kotlinx.serialization.AnnotationsKt

-keepclassmembers class kotlinx.serialization.json.** {
    *** Companion;
}
-keepclasseswithmembers class kotlinx.serialization.json.** {
    kotlinx.serialization.KSerializer serializer(...);
}

-keep,includedescriptorclasses class com.sokhan.chat.**$$serializer { *; }
-keepclassmembers class com.sokhan.chat.** {
    *** Companion;
}
-keepclasseswithmembers class com.sokhan.chat.** {
    kotlinx.serialization.KSerializer serializer(...);
}

# Keep domain data classes used as @Serializable payload types.
-keep class com.sokhan.chat.** { *; }

# --- OkHttp / Okio ---
-dontwarn okhttp3.**
-dontwarn okio.**
-dontwarn javax.annotation.**
-dontwarn org.jetbrains.annotations.**
-keepnames class okhttp3.internal.publicsuffix.PublicSuffixDatabase

# --- CommonMark (reflection-light parser) ---
-dontwarn org.commonmark.**

# --- Coroutines ---
-dontwarn kotlinx.coroutines.**

# --- Compose / AndroidX (shipped rules normally cover these; safe extras) ---
-dontwarn androidx.**
