-keepattributes *Annotation*, InnerClasses, Signature, Exceptions, AnnotationDefault
-dontnote kotlinx.serialization.AnnotationsKt

-keepclassmembers class kotlinx.serialization.json.** {
    *** Companion;
}
-keepclasseswithmembers class kotlinx.serialization.json.** {
    kotlinx.serialization.KSerializer serializer(...);
}

-keep class kotlinx.serialization.json.JsonElement { *; }
-keep class kotlinx.serialization.json.JsonPrimitive { *; }
-keep class kotlinx.serialization.json.JsonNull { *; }
-keep class kotlinx.serialization.json.JsonArray { *; }
-keep class kotlinx.serialization.json.JsonObject { *; }
-keep class kotlinx.serialization.json.internal.** { *; }

-keep,allowobfuscation class com.slte.app.data.remote.** { *; }
-keepclassmembers class com.slte.app.data.remote.** {
    <fields>;
}

-keepattributes RuntimeVisibleAnnotations, RuntimeVisibleParameterAnnotations
-keep,allowobfuscation interface retrofit2.http.** { *; }
-keepclassmembers,allowobfuscation interface * {
    @retrofit2.http.* <methods>;
}

-keep class com.maxmind.db.** { *; }
-keepclassmembers class com.maxmind.db.** {
    <init>(...);
}
