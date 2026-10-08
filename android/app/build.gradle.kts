plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
}

android {
    namespace = "space.neural.luna"
    compileSdk = 34

    defaultConfig {
        applicationId = "space.neural.luna"
        minSdk = 24
        targetSdk = 28
        versionCode = 45
        versionName = "4.5.0"
    }

    buildTypes {
        release {
            isMinifyEnabled = false
        }
    }
}

dependencies {
    implementation("androidx.core:core-ktx:1.13.1")
    implementation("androidx.activity:activity-ktx:1.9.2")
    implementation("androidx.webkit:webkit:1.12.1")
}
