plugins {
    alias(libs.plugins.android.application) apply false
    alias(libs.plugins.android.library) apply false
    alias(libs.plugins.kotlin.android) apply false
    alias(libs.plugins.kotlin.compose) apply false
    alias(libs.plugins.kotlin.serialization) apply false
    alias(libs.plugins.ksp) apply false
    alias(libs.plugins.hilt) apply false
    alias(libs.plugins.ktlint) apply false
}

val jdkMajor = JavaVersion.current().majorVersion.toIntOrNull() ?: 0
if (jdkMajor in 1..16) {
    throw GradleException(
        "北辰需要 JDK 17+ 构建(当前为 ${JavaVersion.current()})。" +
            "请安装 JDK 17 或 21 并设置 JAVA_HOME,详见 README「构建」章节"
    )
}
