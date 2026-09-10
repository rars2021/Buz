plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
}

// Force this project to compile with JDK 17 no matter what JDK Gradle itself
// is running on. If a matching JDK is not installed, the foojay resolver
// (declared in settings.gradle.kts) downloads one automatically.
kotlin { jvmToolchain(17) }
java { toolchain { languageVersion.set(JavaLanguageVersion.of(17)) } }

android {
    namespace = "com.buz"
    compileSdk = 34

    defaultConfig {
        applicationId = "com.buz"
        minSdk = 24
        targetSdk = 34
        versionCode = 1
        versionName = "0.1.0"
    }

    buildFeatures { compose = true }
    composeOptions { kotlinCompilerExtensionVersion = "1.5.11" }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlinOptions { jvmTarget = "17" }

    packaging { resources.excludes += "/META-INF/{AL2.0,LGPL2.1}" }
}

dependencies {
    val composeBom = platform("androidx.compose:compose-bom:2024.05.00")
    implementation(composeBom)
    implementation("androidx.activity:activity-compose:1.9.0")
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.ui:ui-graphics")
    implementation("androidx.compose.foundation:foundation")
    implementation("androidx.compose.material3:material3")
    implementation("androidx.core:core-ktx:1.13.1")

    testImplementation("junit:junit:4.13.2")
}
