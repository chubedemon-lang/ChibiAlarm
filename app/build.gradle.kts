plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
}
android {
    namespace = "com.chibialarm.app"
    compileSdk = 35
    defaultConfig {
        applicationId = "com.chibialarm.app"
        minSdk = 26
        targetSdk = 35
        versionCode = 13
        versionName = "1.3"
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlinOptions { jvmTarget = "17" }
}
