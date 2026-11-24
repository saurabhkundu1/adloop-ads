plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.compose)
}

android {
    namespace = "com.adloop.app"
    compileSdk = 37

    defaultConfig {
        applicationId = "com.adloop.app"
        minSdk = 33
        targetSdk = 37
        versionCode = 2
        versionName = "0.1.1"
    }

    buildFeatures {
        compose = true
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    lint {
        warningsAsErrors = true
        disable += listOf("AndroidGradlePluginVersion", "GradleDependency", "NewerVersionAvailable", "UseTomlInstead")
    }
}

dependencies {
    implementation(libs.androidx.core.ktx)
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.compose.foundation)
    implementation(libs.androidx.compose.material3)
    implementation(libs.androidx.compose.ui)
    testImplementation("junit:junit:4.13.2")
    testImplementation("org.json:json:20240303")
}
