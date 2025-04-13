plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
}

android {
    namespace = "com.example.dlna"
    compileSdk = 33

    defaultConfig {
        applicationId = "com.example.dlna"
        minSdk = 24
        targetSdk = 33
        versionCode = 1
        versionName = "1.0"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_1_8
        targetCompatibility = JavaVersion.VERSION_1_8
    }
    kotlinOptions {
        jvmTarget = "1.8"
    }

    packagingOptions {
        resources {
            excludes += "/META-INF/{AL2.0,LGPL2.1}"
            merges += "META-INF/LICENSE.md"
            merges += "META-INF/LICENSE-notice.md"
            merges += "META-INF/beans.xml"
        }
    }
}

dependencies {

    implementation("androidx.core:core-ktx:1.9.0")
    implementation("androidx.appcompat:appcompat:1.6.1")
    implementation("com.google.android.material:material:1.8.0")
    implementation("androidx.constraintlayout:constraintlayout:2.1.4")

    implementation("org.fourthline.cling:cling-core:2.1.2")
    implementation("org.fourthline.cling:cling-support:2.1.2")

    // https://mvnrepository.com/artifact/org.eclipse.jetty/jetty-servlet
    implementation("org.eclipse.jetty:jetty-servlet:8.1.22.v20160922")
// https://mvnrepository.com/artifact/org.eclipse.jetty/jetty-server
    implementation("org.eclipse.jetty:jetty-server:8.1.22.v20160922")
// https://mvnrepository.com/artifact/org.eclipse.jetty/jetty-client
    implementation("org.eclipse.jetty:jetty-client:8.1.22.v20160922")
// https://mvnrepository.com/artifact/org.slf4j/slf4j-jdk14
    testImplementation("org.slf4j:slf4j-jdk14:1.7.25")
// https://mvnrepository.com/artifact/javax.servlet/javax.servlet-api
    implementation("javax.servlet:javax.servlet-api:3.1.0")

    implementation(project(":videoplayer"))
}