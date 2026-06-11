plugins {
    // AGP 9 has built-in Kotlin support: org.jetbrains.kotlin.android must
    // NOT be applied (the build fails if it is).
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.compose)
}

android {
    namespace = "app.kinsight.companion"
    compileSdk = 37

    defaultConfig {
        applicationId = "app.kinsight.companion"
        minSdk = 26
        targetSdk = 37
        versionCode = 1
        versionName = "0.1.0"
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
    implementation(project(":core:alerts"))
    implementation(project(":core:events"))
    implementation(project(":core:transport"))
    implementation(project(":core:pairing"))
    implementation(project(":core:design"))
    implementation(project(":watchdog"))

    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.activity.compose)
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.material3)

    testImplementation(libs.junit4)
}
