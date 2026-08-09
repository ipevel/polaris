pluginManagement {
    repositories {
        maven { url = uri("https://maven.aliyun.com/repository/google") }
        maven { url = uri("https://maven.aliyun.com/repository/central") }
        maven { url = uri("https://maven.aliyun.com/repository/gradle-plugin") }
        google()
        mavenCentral()
        gradlePluginPortal()
    }
}
dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories {
        maven { url = uri("https://maven.aliyun.com/repository/google") }
        maven { url = uri("https://maven.aliyun.com/repository/central") }
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
