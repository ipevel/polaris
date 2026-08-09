plugins {
    alias(libs.plugins.android.library)
}

android {
    namespace = "com.github.kr328.clash.hideapi"
    compileSdk = 36

    defaultConfig {
        minSdk = 28
        consumerProguardFiles("consumer-rules.pro")
    }
}
