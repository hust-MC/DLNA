pluginManagement {
    repositories {
        google()
        mavenCentral()
        gradlePluginPortal()
        maven("http://fourthline.org/m2/") {
            isAllowInsecureProtocol = true
        }
    }
    plugins {
        id("com.android.application") version "7.3.1"
        id("org.jetbrains.kotlin.android") version "1.6.21"
    }
}
dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories {
        google()
        mavenCentral()
        maven { url = uri("https://jitpack.io") } // 添加其他仓库

        maven("http://fourthline.org/m2/", { isAllowInsecureProtocol = true })

    }
}

rootProject.name = "DLNA"
include(":app")
//include(":cling")
