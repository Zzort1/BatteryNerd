plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.plugin.compose")
}

android {
    namespace = "au.com.evagames.batterynerd"
    compileSdk = 36

    defaultConfig {
        applicationId = "au.com.evagames.batterynerd"
        minSdk = 26
        targetSdk = 36
        versionCode = 1
        versionName = "0.1.1"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
        vectorDrawables {
            useSupportLibrary = true
        }
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
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    buildFeatures {
        compose = true
    }
}

dependencies {
    implementation(libs.androidx.core.ktx.v1180)
    implementation(libs.androidx.lifecycle.runtime.ktx.v2100)
    implementation(libs.androidx.activity.compose.v1130)
    implementation(libs.androidx.lifecycle.viewmodel.compose.v2100)
    implementation(libs.material.v1120)
    implementation(platform(libs.androidx.compose.bom.v20260301))
    implementation(libs.ui)
    implementation(libs.ui.tooling.preview)
    implementation(libs.material3)
    implementation(libs.androidx.material.icons.extended)
    implementation(libs.androidx.foundation)

    debugImplementation(libs.ui.tooling)
    debugImplementation(libs.ui.test.manifest)
}
