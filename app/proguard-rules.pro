# Navigation 3, Compose and kotlinx.serialization ship their own consumer rules; these cover what they cannot see.

# Nav3 NavKeys are saved polymorphically by serializer lookup at runtime; keep the route classes and their companions' serializer().
-keep class net.venipa.stashling.nav.** { *; }

# kotlinx.serialization: generated serializers of the app's and :core's @Serializable types.
-keepclassmembers @kotlinx.serialization.Serializable class net.venipa.stashling.** {
    *** Companion;
    kotlinx.serialization.KSerializer serializer(...);
}
-keepclasseswithmembers class net.venipa.stashling.**$$serializer { *; }

# Stack traces in a bug report stay readable.
-keepattributes SourceFile,LineNumberTable
-renamesourcefileattribute SourceFile
