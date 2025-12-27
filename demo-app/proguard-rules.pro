# Add project specific ProGuard rules here.
# Keep MediaSFU SDK classes
-keep class com.mediasfu.sdk.** { *; }

# Keep WebRTC classes
-keep class org.webrtc.** { *; }

# Keep Kotlin Serialization
-keepattributes *Annotation*, InnerClasses
-dontnote kotlinx.serialization.AnnotationsKt
-keepclassmembers class kotlinx.serialization.json.** {
    *** Companion;
}
-keepclasseswithmembers class kotlinx.serialization.json.** {
    kotlinx.serialization.KSerializer serializer(...);
}
