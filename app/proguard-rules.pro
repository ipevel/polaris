# Add project specific ProGuard rules here.
-keepattributes *Annotation*, InnerClasses, Signature, Exceptions
-dontnote kotlinx.serialization.AnnotationsKt

# kotlinx.serialization: 保留 Companion 与 serializer 工厂
-keepclassmembers class kotlinx.serialization.json.** {
    *** Companion;
}
-keepclasseswithmembers class kotlinx.serialization.json.** {
    kotlinx.serialization.KSerializer serializer(...);
}

# 保留所有 @Serializable 标记的 DTO（含字段名，反序列化需要），
# 范围限定在 data/remote 下，避免全量 keep 导致 R8 无法混淆业务代码。
-keep,allowobfuscation,allowshrinking class com.slte.app.data.remote.** { *; }
-keepclassmembers class com.slte.app.data.remote.** {
    <fields>;
}

# Retrofit: 保留接口方法签名（注解驱动的反射）
-keep,allowobfuscation,allowshrinking interface retrofit2.http.** { *; }
-keep,allowobfuscation,allowshrinking @retrofit2.http.* interface * { *; }

# maxmind-db: Reader 通过 @MaxMindDbConstructor 注解反射构造解码类，
# 混淆类名或剥离注解会抛 "No constructor ... annotation was found"（GeoIP 解析失效）
-keep class com.maxmind.db.** { *; }
-keepclassmembers class com.maxmind.db.** {
    <init>(...);
}

# Hilt 生成代码由插件处理，无需手动 keep
