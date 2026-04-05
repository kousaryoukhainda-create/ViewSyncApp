# Add project specific ProGuard rules here.
# By default, the flags in this file are appended to the system
# default file located in $ANDROID_SDK_ROOT/tools/proguard/proguard-android.txt

# For more details, see
#   http://developer.android.com/guide/developing/tools/proguard.html

# Basic Android rules
-keepattributes *Annotation*
-keepattributes SourceFile,LineNumberTable
-keep public class * extends android.app.Activity
-keep public class * extends android.app.Application
-keep public class * extends android.app.Service
-keep public class * extends android.content.BroadcastReceiver
-keep public class * extends android.content.ContentProvider

# Keep Hilt generated classes
-keep class dagger.hilt.** { *; }
-keep class javax.inject.** { *; }
-keep class * extends dagger.hilt.android.internal.managers.ActivityComponentManager { *; }
-keep class * generatedAnnotation { *; }

# Keep Kotlin coroutines
-keepnames class kotlinx.coroutines.internal.MainDispatcherFactory {}
-keepnames class kotlinx.coroutines.CoroutineExceptionHandler {}

# Keep Retrofit
-keepattributes Signature
-keepattributes Exceptions
-keep class retrofit2.** { *; }

# Keep OkHttp
-dontwarn okhttp3.**
-dontwarn okio.**
-keepnames class okhttp3.internal.publicsuffix.PublicSuffixDatabase

# Keep YouTube Player
-keep class com.pierfrancescosoffritti.androidyoutubeplayer.** { *; }

# Keep data classes
-keep class com.youkhainda.viewsync.data.model.** { *; }

# Keep kotlinx serialization
-keepattributes *Annotation*, InnerClasses
-dontnote kotlinx.serialization.SerializationKt
-keep,includedescriptorclasses class com.youkhainda.viewsync.**$$serializer { *; }
-keepclassmembers class com.youkhainda.viewsync.** {
    *** Companion;
}
