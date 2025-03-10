// Top-level build file where you can add configuration options common to all sub-projects/modules.
buildscript {
    repositories {
        google()       // Google 的 Maven 仓库
        mavenCentral() // Maven 中央仓库
    }
    dependencies {
        classpath("com.android.tools.build:gradle:7.4.2") // AGP 版本
    }
}

