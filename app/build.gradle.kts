import java.io.InputStreamReader
import java.util.Properties

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.kotlin.serialization)
    alias(libs.plugins.ksp)
    alias(libs.plugins.hilt)
    alias(libs.plugins.ktlint)
}

ktlint {
    version.set("1.5.0")
    android.set(true)
    ignoreFailures.set(false)
}

val slteProps =
    Properties().apply {
        rootProject.file("app/gradle.properties").takeIf { it.isFile() }?.inputStream()?.use {
            load(InputStreamReader(it, Charsets.UTF_8))
        }
    }

fun slteValue(name: String): String? = System.getenv(name)?.takeIf { it.isNotBlank() }
    ?: slteProps.getProperty(name)?.trim()?.takeIf { it.isNotBlank() }

fun slteHttps(raw: String): String? = when {
    raw.startsWith("https://") -> raw
    raw.startsWith("http://") -> null
    else -> "https://$raw"
}

fun slteHost(url: String): String? = url
    .removePrefix("https://")
    .substringBefore('/')
    .substringBefore(':')
    .takeIf { it.isNotEmpty() }
    ?.lowercase()

val slteAppName = slteValue("POLARIS_APP_NAME") ?: "Polaris"
val slteApplicationId = slteValue("POLARIS_APPLICATION_ID") ?: "com.polaris.app"
val slteVersionCode = slteValue("POLARIS_VERSION_CODE")?.toIntOrNull() ?: 19
val slteVersionName = slteValue("POLARIS_VERSION_NAME") ?: "1.4.5"

val slteApiBaseUrl = slteValue("POLARIS_API_BASE_URL")?.let(::slteHttps) ?: "https://api.example.com"
val slteApiType = slteValue("POLARIS_API_TYPE") ?: "xiaov2b"
val slteSubscribePath = slteValue("POLARIS_SUBSCRIBE_PATH") ?: "/api/v1/client/subscribe"
val slteRemoteConfigUrls =
    slteValue("POLARIS_REMOTE_CONFIG_URLS")
        ?.split(',')
        ?.map { it.trim() }
        ?.filter { it.isNotEmpty() }
        ?.mapNotNull(::slteHttps)
        ?.joinToString(",") ?: ""

val slteAllowedDomains =
    buildList {
        slteValue("POLARIS_ALLOWED_DOMAINS")
            ?.split(',')
            ?.map { it.trim() }
            ?.filter { it.isNotEmpty() }
            ?.let(::addAll)
        slteValue("POLARIS_API_BASE_URL")?.let(::slteHttps)?.let(::add)
        slteRemoteConfigUrls.split(',').filter { it.isNotEmpty() }.forEach(::add)
    }.filter { it.isNotEmpty() }.mapNotNull(::slteHost).distinct().joinToString(",")

val slteTelegramGroupUrl = slteValue("POLARIS_TELEGRAM_GROUP_URL") ?: ""

val slteReleaseStoreFile = slteValue("POLARIS_RELEASE_STORE_FILE")

android {
    namespace = "com.slte.app"
    compileSdk = 36
    ndkVersion = "28.2.13676358"

    applicationVariants.all {
        outputs.all {
            (this as com.android.build.gradle.internal.api.BaseVariantOutputImpl).outputFileName =
                "Polaris-$versionName.apk"
        }
    }

    defaultConfig {
        applicationId = slteApplicationId
        minSdk = 28
        targetSdk = 36
        versionCode = slteVersionCode
        versionName = slteVersionName

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
        vectorDrawables { useSupportLibrary = true }

        ndk {
            abiFilters += listOf("arm64-v8a")
        }

        resValue("string", "app_name", slteAppName)

        buildConfigField("String", "API_BASE_URL", "\"$slteApiBaseUrl\"")
        buildConfigField("String", "API_TYPE", "\"$slteApiType\"")
        buildConfigField("String", "SUBSCRIBE_PATH", "\"$slteSubscribePath\"")

        buildConfigField("String", "REMOTE_CONFIG_URLS", "\"$slteRemoteConfigUrls\"")
        buildConfigField("String", "ALLOWED_DOMAINS", "\"$slteAllowedDomains\"")

        buildConfigField("String", "TELEGRAM_GROUP_URL", "\"$slteTelegramGroupUrl\"")
    }

    // 先提取密码供 buildTypes 做缺参前置校验，避免空密码静默进入签名配置
    val releaseStorePassword = slteValue("POLARIS_RELEASE_STORE_PASSWORD").orEmpty()
    val releaseKeyPassword = slteValue("POLARIS_RELEASE_KEY_PASSWORD").orEmpty()

    signingConfigs {
        create("release") {
            storeFile = rootProject.file(slteReleaseStoreFile ?: "release.keystore")
            storePassword = releaseStorePassword
            keyAlias = slteValue("POLARIS_RELEASE_KEY_ALIAS") ?: "slte"
            keyPassword = releaseKeyPassword
        }
    }

    sourceSets {
        getByName("test").java.srcDir("src/sharedTest/java")
        getByName("androidTest").java.srcDir("src/sharedTest/java")
    }

    testOptions {
        unitTests {
            all {
                // CI 上 662 个单测与 R8 混合同一 runner 内存，累积后 OOM：
                // 提高堆上限并每 200 个用例 fork 新 JVM，防内存累积
                it.maxHeapSize = "3g"
                it.setForkEvery(200)
            }
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
            val hasReleaseKey = slteReleaseStoreFile != null
            if (hasReleaseKey) {
                signingConfig = signingConfigs.getByName("release")
                gradle.taskGraph.whenReady {
                    val signingRelease = allTasks.any { it.name.contains("Release") }
                    if (signingRelease && (releaseStorePassword.isBlank() || releaseKeyPassword.isBlank())) {
                        throw GradleException(
                            "已提供 keystore 文件但缺少 POLARIS_RELEASE_STORE_PASSWORD/POLARIS_RELEASE_KEY_PASSWORD，" +
                                "禁止以空密码签名发布（本地调试请用 assembleDebug）",
                        )
                    }
                }
            } else {

                gradle.taskGraph.whenReady {
                    if (allTasks.any { it.name.contains("Release") }) {
                        throw GradleException(
                            "release 构建必须设置 POLARIS_RELEASE_STORE_FILE/PASSWORD/KEY_ALIAS/KEY_PASSWORD，" +
                                "禁止使用 debug 签名发布（本地调试请用 assembleDebug）",
                        )
                    }
                }
            }
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro",
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
            isIncludeAndroidResources = true
            isReturnDefaultValues = true
        }
    }

    lint {

        abortOnError = true
        checkReleaseBuilds = true
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

    bundle {
        language {

            enableSplit = false
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

    implementation(project(":kernel-service"))
    implementation(project(":kernel-common"))
    implementation(libs.kaidl.runtime)

    debugImplementation(libs.androidx.ui.tooling)
    debugImplementation(libs.androidx.ui.test.manifest)

    testImplementation(libs.junit)
    testImplementation(libs.kotlinx.coroutines.test)
    testImplementation(libs.mockk)
    testImplementation(libs.snakeyaml)
    testImplementation(libs.robolectric)
    testImplementation(platform(libs.androidx.compose.bom))
    testImplementation(libs.androidx.ui.test.junit4)

    testImplementation(libs.okhttp.mockwebserver)
    androidTestImplementation(libs.androidx.junit)
    androidTestImplementation(libs.androidx.espresso.core)
    androidTestImplementation(platform(libs.androidx.compose.bom))
    androidTestImplementation(libs.androidx.ui.test.junit4)
}

val verifyReleaseApiSurvivors =
    tasks.register("verifyReleaseApiSurvivors") {
        group = "verification"
        description = "校验 R8 未删除 data/remote 下的 @Serializable DTO 与 Retrofit 接口"

        val sourceDir = layout.projectDirectory.dir("src/main/java/com/slte/app/data/remote")
        val mappingFile = layout.buildDirectory.file("outputs/mapping/release/mapping.txt")

        dependsOn("minifyReleaseWithR8")
        inputs.dir(sourceDir)
        inputs.file(mappingFile)

        outputs.upToDateWhen { false }

        doLast {
            val declaration = Regex("""^\s*(?:@\w+[^)]*\)?\s*)*(?:public |internal |private )?(?:data |sealed |abstract |open )*(class|object|interface)\s+([A-Za-z0-9_]+)""")
            val serializableMarker = "@Serializable"
            val retrofitMarker = Regex("""\binterface\s+[A-Za-z0-9_]*Retrofit\b""")

            val expected = sortedSetOf<String>()
            sourceDir.asFile.walkTopDown().filter { it.isFile && it.extension == "kt" }.forEach { file ->
                var pendingSerializable = false
                file.readLines().forEach { line ->
                    val trimmed = line.trim()
                    when {
                        trimmed.isEmpty() -> pendingSerializable = false
                        trimmed.startsWith(serializableMarker) -> pendingSerializable = true
                        trimmed.startsWith("@") -> Unit
                        trimmed.startsWith("//") || trimmed.startsWith("*") || trimmed.startsWith("/*") -> Unit
                        else -> {
                            val name = declaration.find(line)?.groupValues?.get(2)
                            if (name != null && (pendingSerializable || retrofitMarker.containsMatchIn(line))) {
                                expected += name
                            }
                            pendingSerializable = false
                        }
                    }
                }
            }
            if (expected.isEmpty()) {
                throw GradleException("未在 ${sourceDir.asFile} 下找到任何 @Serializable DTO / Retrofit 接口，校验脚本可能已失效")
            }

            val mapping = mappingFile.get().asFile
            if (!mapping.isFile) throw GradleException("缺少 R8 mapping 文件：${mapping.absolutePath}")
            val mappingText = mapping.readText()

            val missing =
                expected.filterNot { name ->
                    val pattern =
                        Regex(
                            "^com\\.slte\\.app\\.data\\.remote\\.[A-Za-z0-9_.]*\\." + Regex.escape(name) + " -> ",
                            RegexOption.MULTILINE,
                        )
                    pattern.containsMatchIn(mappingText)
                }
            if (missing.isNotEmpty()) {
                throw GradleException(
                    "R8 删除了 data/remote 下的 ${missing.size} 个类：${missing.joinToString(", ")}\n" +
                        "这些类通过泛型实参 + 反射 serializer 使用，R8 看不到强引用。\n" +
                        "请检查 app/proguard-rules.pro：相关的 -keep 规则**不能**带 allowshrinking，\n" +
                        "否则 Retrofit 会在发请求前抛 Unable to create converter for class java.lang.Object。",
                )
            }
            logger.lifecycle("R8 存活校验通过（${expected.size} 个 @Serializable DTO / Retrofit 接口）")
        }
    }

val verifyKernelBinary =
    tasks.register("verifyKernelBinary") {
        group = "verification"
        description = "校验预编译内核产物 libclash.so 的 SHA-256 与 SHA256SUMS 记录一致"
        dependsOn(":kernel-core:verifyNativeLibraries")
    }
