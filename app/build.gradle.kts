plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.kotlin.serialization)
}

import java.io.FileInputStream
import java.util.Properties

android {
    namespace = "com.robin.tools"
    compileSdk = 36

    defaultConfig {
        applicationId = "com.robin.tools"
        minSdk = 28
        targetSdk = 36
        versionCode = 1
        versionName = "1.0"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"

        // arm64-v8a only: FFmpeg is arm64-v8a exclusive, x86/x86_64/armeabi-v7a can't run anyway.
        // This removes ~12 MB of useless TFLite native libs from those ABIs.
        ndk {
            abiFilters += listOf("arm64-v8a")
        }
    }

    signingConfigs {
        create("release") {
            val localPropsFile = rootProject.file("local.properties")
            if (localPropsFile.exists()) {
                val localProps = FileInputStream(localPropsFile).use { Properties().apply { load(it) } }
                val keystorePath = localProps.getProperty("RELEASE_STORE_FILE")
                    ?: System.getenv("RELEASE_STORE_FILE")
                if (keystorePath != null) {
                    storeFile = file(keystorePath)
                    storePassword = localProps.getProperty("RELEASE_STORE_PASSWORD")
                        ?: System.getenv("RELEASE_STORE_PASSWORD") ?: ""
                    keyAlias = localProps.getProperty("RELEASE_KEY_ALIAS")
                        ?: System.getenv("RELEASE_KEY_ALIAS") ?: ""
                    keyPassword = localProps.getProperty("RELEASE_KEY_PASSWORD")
                        ?: System.getenv("RELEASE_KEY_PASSWORD") ?: ""
                }
            }
        }
    }

    buildTypes {
        debug {
            val localPropsFile = rootProject.file("local.properties")
            if (localPropsFile.exists()) {
                val localProps = FileInputStream(localPropsFile).use { Properties().apply { load(it) } }
                if (localProps.getProperty("RELEASE_STORE_FILE") != null) {
                    signingConfig = signingConfigs.getByName("release")
                }
            }
        }
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
            val localPropsFile = rootProject.file("local.properties")
            if (localPropsFile.exists()) {
                val localProps = FileInputStream(localPropsFile).use { Properties().apply { load(it) } }
                if (localProps.getProperty("RELEASE_STORE_FILE") != null) {
                    signingConfig = signingConfigs.getByName("release")
                }
            }
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    kotlinOptions {
        jvmTarget = "17"
    }

    buildFeatures {
        compose = true
        buildConfig = true
    }
}

dependencies {
    implementation(project(":core"))
    implementation(project(":feature:media"))
    implementation(project(":feature:ebook"))
    implementation(project(":feature:lightlux"))
    implementation(project(":feature:face"))
    implementation(project(":feature:camera"))

    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    implementation(libs.androidx.activity.compose)
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.ui.graphics)
    implementation(libs.androidx.compose.ui.tooling.preview)
    implementation(libs.androidx.compose.material3)
    implementation(libs.androidx.compose.material.icons.extended)
    implementation(libs.androidx.navigation.compose)
    implementation(libs.kotlinx.serialization.json)

    testImplementation(libs.junit)
    androidTestImplementation(libs.androidx.junit)
    androidTestImplementation(libs.androidx.espresso.core)
    androidTestImplementation(platform(libs.androidx.compose.bom))
    androidTestImplementation(libs.androidx.compose.ui.test.junit4)
    debugImplementation(libs.androidx.compose.ui.tooling)
    debugImplementation(libs.androidx.compose.ui.test.manifest)
}
