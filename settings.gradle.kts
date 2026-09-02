// 仓库镜像开关：本地中国网络默认走 aliyun 镜像加速；
// 海外环境（GitHub Actions runner）访问镜像不稳定会导致插件/依赖解析失败，
// 设环境变量 SLTE_USE_MIRROR=false 直连官方仓库（google / mavenCentral / portal）
fun useAliyunMirror(): Boolean = System.getenv("SLTE_USE_MIRROR")?.toBoolean() ?: true

pluginManagement {
    repositories {
        if (System.getenv("SLTE_USE_MIRROR")?.toBoolean() != false) {
            maven { url = uri("https://maven.aliyun.com/repository/google") }
            maven { url = uri("https://maven.aliyun.com/repository/central") }
            maven { url = uri("https://maven.aliyun.com/repository/gradle-plugin") }
        }
        google()
        mavenCentral()
        gradlePluginPortal()
    }
}
dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories {
        if (useAliyunMirror()) {
            maven { url = uri("https://maven.aliyun.com/repository/google") }
            maven { url = uri("https://maven.aliyun.com/repository/central") }
        }
        maven { url = uri("https://raw.githubusercontent.com/MetaCubeX/maven-backup/main/releases") }
        google()
        mavenCentral()
    }
}

rootProject.name = "SLTE"
include(":app")
include(":kernel-common")
include(":kernel-core")
include(":kernel-service")
include(":kernel-hideapi")
