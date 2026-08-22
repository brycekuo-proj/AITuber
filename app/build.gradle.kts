plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
}

android {
    namespace = "com.aituber.poc"
    compileSdk = 35

    defaultConfig {
        applicationId = "com.aituber.poc"
        minSdk = 29
        targetSdk = 35
        versionCode = 1
        versionName = "0.1.0"

    }
}

kotlin {
    jvmToolchain(17)
}

dependencies {
    testImplementation("junit:junit:4.13.2")
}
