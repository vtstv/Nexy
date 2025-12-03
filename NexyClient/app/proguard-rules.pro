# Add project specific ProGuard rules here.
-keepattributes *Annotation*
-keepattributes Signature
-keepattributes InnerClasses
-keepattributes EnclosingMethod
-keepattributes RuntimeVisibleAnnotations
-keepattributes RuntimeVisibleParameterAnnotations
-keepattributes AnnotationDefault

# Gson specific
-keepclassmembers class * {
    @com.google.gson.annotations.SerializedName <fields>;
}
-keep class com.nexy.client.data.models.** { *; }

# Retrofit
-keepattributes Exceptions
-keep class retrofit2.** { *; }
-dontwarn retrofit2.**
-keep class okhttp3.** { *; }
-keep class okio.** { *; }

# Kotlin Coroutines
-keep class kotlin.coroutines.Continuation { *; }

# Nexy API
-keep interface com.nexy.client.data.api.** { *; }
-keep class com.nexy.client.data.api.** { *; }

# Generic type information for Gson
-keepattributes Signature
-keep class sun.misc.Unsafe { *; }
-keep class com.google.gson.** { *; }
-keep class * extends com.google.gson.TypeAdapter
-keep class * implements com.google.gson.TypeAdapterFactory
-keep class * implements com.google.gson.JsonSerializer
-keep class * implements com.google.gson.JsonDeserializer

# Prevent R8 from removing generic signature
-keep class kotlin.Metadata { *; }

# WebRTC
-keep class org.webrtc.** { *; }
-dontwarn org.webrtc.**
-keep class com.nexy.client.data.webrtc.** { *; }
