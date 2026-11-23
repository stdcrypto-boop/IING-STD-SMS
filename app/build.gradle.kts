plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
}
android {
    namespace = "com.iingstd.sms"
    compileSdk = 35
    defaultConfig {
        applicationId = "com.iingstd.sms"
        minSdk = 26
        targetSdk = 35
        versionCode = 1000 + (System.getenv("GITHUB_RUN_NUMBER")?.toIntOrNull() ?: 1)
        versionName = "0.1.0"
    }
    signingConfigs {
        create("smsRelease") {
            val storePath = System.getenv("SMS_STORE_FILE")
            if (!storePath.isNullOrBlank()) {
                storeFile = file(storePath)
                storePassword = System.getenv("SMS_STORE_PASSWORD")
                keyAlias = System.getenv("SMS_KEY_ALIAS")
                keyPassword = System.getenv("SMS_KEY_PASSWORD")
            }
        }
    }
    buildTypes {
        getByName("release") {
            signingConfig = signingConfigs.getByName("smsRelease")
            isMinifyEnabled = false
        }
    }
    compileOptions { sourceCompatibility = JavaVersion.VERSION_17; targetCompatibility = JavaVersion.VERSION_17 }
    kotlinOptions { jvmTarget = "17" }
}
