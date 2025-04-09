plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
}

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

    sourceSets {
        getByName("main") {
            manifest.srcFile("src/main/AndroidManifest.xml")
            java.srcDirs("src/main/java")
            res.srcDirs("src/main/res")
        }
    }

    lint {
        abortOnError = false
    }
}

dependencies {
    implementation("androidx.core:core-ktx:1.9.0")
    implementation("androidx.appcompat:appcompat:1.6.1")
    implementation("com.google.android.material:material:1.8.0")
    implementation("androidx.constraintlayout:constraintlayout:2.1.4")
    implementation("androidx.multidex:multidex:2.0.1")

    // Cling UPnP
    implementation("org.fourthline.cling:cling-core:2.1.2")
    implementation("org.fourthline.cling:cling-support:2.1.2")

    // Jetty
    implementation("org.eclipse.jetty:jetty-servlet:8.1.22.v20160922")
    implementation("org.eclipse.jetty:jetty-server:8.1.22.v20160922")
    implementation("org.eclipse.jetty:jetty-client:8.1.22.v20160922")

    // Servlet
    implementation("javax.servlet:javax.servlet-api:3.1.0")

    // Logging
    implementation("org.slf4j:slf4j-api:1.7.36")
    implementation("org.slf4j:slf4j-simple:1.7.36")

    // Coroutines
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.6.4")
    
    // OkHttp
    implementation("com.squareup.okhttp3:okhttp:4.9.3")

    // Testing
    testImplementation("junit:junit:4.13.2")
    androidTestImplementation("androidx.test.ext:junit:1.1.5")
    androidTestImplementation("androidx.test.espresso:espresso-core:3.5.1")
}