plugins {
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.serialization)
    alias(libs.plugins.android.library)
    alias(libs.plugins.ksp)
}

android {
    namespace = "com.github.kr328.clash.service"
    compileSdk = 36

    defaultConfig {
        minSdk = 28
        consumerProguardFiles("consumer-rules.pro")

        // 通知栏配置（编译期环境变量注入）：
        // SLTE_NOTIFICATION_TITLE   通知标题（默认空 = 跟随应用名；显式设置则覆盖）
        // SLTE_NOTIFICATION_TRAFFIC 是否显示流量/流速（默认 true）
        val notificationTitle = System.getenv("SLTE_NOTIFICATION_TITLE") ?: ""
        buildConfigField("String", "NOTIFICATION_TITLE", "\"$notificationTitle\"")
        val notificationTraffic = System.getenv("SLTE_NOTIFICATION_TRAFFIC") ?: "true"
        buildConfigField("boolean", "NOTIFICATION_TRAFFIC", notificationTraffic)
    }

    buildFeatures {
        buildConfig = true
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    kotlinOptions {
        jvmTarget = "17"
    }
}

dependencies {
    // IClashManager 接口直接暴露 core 的模型类型，必须 api 透传给上层
    api(project(":kernel-core"))
    implementation(project(":kernel-common"))

    ksp(libs.kaidl)
    ksp(libs.androidx.room.compiler)

    implementation(libs.kotlinx.coroutines.android)
    implementation(libs.kotlinx.serialization.json)
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.room.runtime)
    implementation(libs.androidx.room.ktx)
    implementation(libs.kaidl.runtime)
    implementation(libs.rikkax.multiprocess)
}
