# Add project specific ProGuard rules here.
# By default, the flags in this file are appended to flags specified
# in /Users/<username>/Library/Android/sdk/tools/proguard/proguard-android-optimize.txt
# You can edit the include path and order by changing the proguardFiles
# directive in build.gradle.kts.
#
# For more details, see http://developer.android.com/guide/developing/tools/proguard.html

# If your project uses WebView with JS, uncomment the following
# and specify the fully qualified class name to the JavaScript interface
# class:
#-keepclassmembers class fqcn.of.javascript.interface.for.webview {
#   public *;
#}

# Uncomment this to preserve the line number information for
# debugging stack traces.
-keepattributes SourceFile,LineNumberTable

# If you keep the line number information, uncomment this to
# hide the original source file name.
-renamesourcefileattribute SourceFile

# Firestore (Firestore uses reflection to deserialize POJOs)
-keep class com.wildtrail.app.data.remote.dto.** { *; }
-keepclassmembers class com.wildtrail.app.data.remote.dto.** { *; }

# Kotlinx Serialization
-keepattributes *Annotation*, InnerClasses
-dontnote kotlinx.serialization.SerializationKt
-keep,includedescriptorclasses class com.wildtrail.app.**$$serializer { *; }
-keepclassmembers class com.wildtrail.app.** {
    *** Companion;
}
-keepclasseswithmembers class com.wildtrail.app.** {
    kotlinx.serialization.KSerializer serializer(...);
}
