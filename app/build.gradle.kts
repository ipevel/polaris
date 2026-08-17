plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.kotlin.serialization)
    alias(libs.plugins.ksp)
    alias(libs.plugins.hilt)
}

android {
    namespace = "com.slte.app"
    compileSdk = 36
    ndkVersion = "28.2.13676358"

    // 安装包输出名带版本号：SLTE-1.0.0.apk / SLTE-1.0.0-debug.apk
    applicationVariants.all {
        outputs.all {
            (this as com.android.build.gradle.internal.api.BaseVariantOutputImpl).outputFileName =
                "SLTE-${versionName}.apk"
        }
    }

    defaultConfig {
        applicationId = "com.slte.app"
        minSdk = 28
        targetSdk = 36
        versionCode = 1
        versionName = "1.0.0"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
        vectorDrawables { useSupportLibrary = true }

        // 仅发布 arm64-v8a：内核 so（libclash/libbridge）只编译了 arm64，其他 ABI
        // 打包会导致 x86 设备安装成功但运行崩溃；统一 ABI 让不支持的设备在安装时即被拒绝
        ndk {
            abiFilters += listOf("arm64-v8a")
        }

        // 后端 API 配置，集中管理便于切换环境（BuildConfig 注入）
        // 默认生产地址；本地/CI 可用环境变量 SLTE_API_BASE_URL / SLTE_API_TYPE 覆盖，无需改代码
        val apiBaseUrl = System.getenv("SLTE_API_BASE_URL") ?: "https://api.example.com"
        // 后端类型：xiaov2b（V2Board 系）/ xboard（Xboard，API 兼容复用同一适配器）；
        // 本地/CI 可用环境变量 SLTE_API_TYPE 切换，无需改代码
        val apiType = System.getenv("SLTE_API_TYPE") ?: "xiaov2b"
        buildConfigField("String", "API_BASE_URL", "\"$apiBaseUrl\"")
        buildConfigField("String", "API_TYPE", "\"$apiType\"")
        // 订阅接口路径（与后端契约；换后端/改路径时同步此常量与 API_BASE_URL）
        buildConfigField("String", "SUBSCRIBE_PATH", "\"/api/v1/client/subscribe\"")
        // 远程配置 URL 列表（下发 JSON，逗号分隔多个）：随机轮询拉取，单个被墙/失效不影响；
        // 配置源为直链（CF Workers/静态托管），配置内 API 地址可任意切换
        // 远程配置源（逗号分隔，https）：部署者填入自有 OSS/Worker 地址；为空则跳过远程配置，使用下方 API 地址
        val remoteConfigUrls = System.getenv("SLTE_REMOTE_CONFIG_URLS") ?: ""
        buildConfigField("String", "REMOTE_CONFIG_URLS", "\"$remoteConfigUrls\"")
        // Crisp 客服配置：从代码迁移到 BuildConfig，便于环境切换
        buildConfigField("String", "CRISP_WEBSITE_ID", "\"\"")
        buildConfigField("boolean", "CRISP_ENABLED", "false")
        // 白名单追加域名（逗号分隔，默认空）：部署者自持域可在此注入，无需改代码；
        // 追加即放宽凭据发送范围，仅当域名不受第三方控制时使用
        val allowedDomains = System.getenv("SLTE_ALLOWED_DOMAINS") ?: ""
        buildConfigField("String", "ALLOWED_DOMAINS", "\"$allowedDomains\"")
    }

    signingConfigs {
        // 发布签名：从环境变量/CI secret 注入，禁止把 keystore 提交仓库
        create("release") {
            storeFile = file(System.getenv("SLTE_RELEASE_STORE_FILE") ?: "release.keystore")
            storePassword = System.getenv("SLTE_RELEASE_STORE_PASSWORD") ?: ""
            keyAlias = System.getenv("SLTE_RELEASE_KEY_ALIAS") ?: "slte"
            keyPassword = System.getenv("SLTE_RELEASE_KEY_PASSWORD") ?: ""
        }
    }

    buildTypes {
        debug {
            isMinifyEnabled = false
            versionNameSuffix = "-debug"
        }
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            val hasReleaseKey = !System.getenv("SLTE_RELEASE_STORE_FILE").isNullOrBlank()
            if (hasReleaseKey) {
                signingConfig = signingConfigs.getByName("release")
            } else {
                // 禁止 release 静默退回 debug 签名
                // debug 密钥是公开默认值，同签名恶意包可覆盖安装并窃取会话凭据。
                // 延迟到任务图判断：只在实际构建 release 变体时失败，不影响 assembleDebug。
                gradle.taskGraph.whenReady {
                    if (allTasks.any { it.name.contains("Release") }) {
                        throw GradleException(
                            "release 构建必须设置 SLTE_RELEASE_STORE_FILE/PASSWORD/KEY_ALIAS/KEY_PASSWORD，" +
                                "禁止使用 debug 签名发布（本地调试请用 assembleDebug）"
                        )
                    }
                }
            }
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    kotlinOptions {
        jvmTarget = "17"
    }

    testOptions {
        unitTests {
            // JVM 单测中未 mock 的 android.* 调用返回默认值而非抛异常
            // （拦截器等网络层在测试里会触发 android.util.Log）
            isReturnDefaultValues = true
        }
    }

    buildFeatures {
        compose = true
        buildConfig = true
    }

    packaging {
        resources {
            excludes += "/META-INF/{AL2.0,LGPL2.1}"
        }
        jniLibs {
            useLegacyPackaging = true
        }
    }
}

dependencies {
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    implementation(libs.androidx.activity.compose)

    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.ui)
    implementation(libs.androidx.ui.graphics)
    implementation(libs.androidx.ui.tooling.preview)
    implementation(libs.androidx.material3)
    implementation(libs.androidx.material.icons.extended)
    implementation(libs.androidx.navigation.compose)
    implementation(libs.androidx.hilt.navigation.compose)
    implementation(libs.hilt.android)
    ksp(libs.hilt.compiler)
    implementation(libs.kotlinx.serialization.json)
    implementation(libs.coil.compose)
    implementation(libs.coil.svg)
    implementation(libs.coil.network.okhttp)
    implementation(libs.multiplatform.markdown.renderer.m3)
    implementation(libs.lottie.compose)
    implementation(libs.maxminddb)
    implementation(libs.retrofit.core)
    implementation(libs.retrofit.kotlinx.serialization)
    implementation(libs.androidx.security.crypto)
    implementation(libs.androidx.webkit)

    // VPN 内核（mihomo，通过 kaild Binder 与 :background 进程通信）
    implementation(project(":kernel-service"))
    implementation(project(":kernel-common"))
    implementation(libs.kaidl.runtime)

    // Crisp 客服 SDK
    implementation(libs.crisp.sdk)

    debugImplementation(libs.androidx.ui.tooling)
    debugImplementation(libs.androidx.ui.test.manifest)

    testImplementation(libs.junit)
    testImplementation(libs.snakeyaml)
    // 拦截器/配置竞速集成测试的本地假服务器
    testImplementation(libs.okhttp.mockwebserver)
    androidTestImplementation(libs.androidx.junit)
    androidTestImplementation(libs.androidx.espresso.core)
    androidTestImplementation(platform(libs.androidx.compose.bom))
    androidTestImplementation(libs.androidx.ui.test.junit4)
}
