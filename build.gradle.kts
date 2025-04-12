// Top-level build file where you can add configuration options common to all sub-projects/modules.
buildscript {
    repositories {
        google()       // Google 的 Maven 仓库
        mavenCentral() // Maven 中央仓库
    }
    dependencies {
        classpath("com.android.tools.build:gradle:7.3.1") // AGP 版本
        classpath("org.jetbrains.kotlin:kotlin-gradle-plugin:1.6.21")
    }
}

allprojects {
    repositories {
        google()
        mavenCentral()
        maven { url = uri("https://jitpack.io") }
        jcenter()
        maven {
            url = uri("http://fourthline.org/m2/")
            isAllowInsecureProtocol = true
        }
    }
}

