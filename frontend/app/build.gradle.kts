import java.util.Properties

plugins {
    alias(libs.plugins.android.application)
}

val apiLocal = Properties().apply {
    val config = rootProject.file("local.properties")
    if (config.exists()) config.inputStream().use { load(it) }
}
val apiBaseUrl = providers.gradleProperty("speakmate.api.baseUrl").orNull
    ?: apiLocal.getProperty("speakmate.api.baseUrl", "http://10.0.2.2:8081/")
val googleWebClientId = providers.gradleProperty("speakmate.google.webClientId").orNull
    ?: apiLocal.getProperty("speakmate.google.webClientId", "")
fun configString(value: String): String =
    "\"" + value.replace("\\", "\\\\").replace("\"", "\\\"") + "\""

android {
    namespace = "com.example.speakmate"
    compileSdk {
        version = release(37)
    }

    defaultConfig {
        applicationId = "com.example.speakmate"
        minSdk = 26
        targetSdk = 37
        versionCode = 1
        versionName = "1.0"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
        buildConfigField("String", "API_BASE_URL", configString(apiBaseUrl))
        buildConfigField("String", "GOOGLE_WEB_CLIENT_ID", configString(googleWebClientId))
    }

    buildTypes {
        release {
            optimization {
                enable = false
            }
        }
    }
    buildFeatures {
        buildConfig = true
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
    }
}

dependencies {
    implementation(libs.activity.ktx)
    implementation(libs.appcompat)
    implementation(libs.constraintlayout)
    implementation(libs.material)
    implementation("com.squareup.retrofit2:retrofit:3.0.0")
    implementation("androidx.credentials:credentials:1.6.0")
    implementation("androidx.credentials:credentials-play-services-auth:1.6.0")
    implementation("com.google.android.libraries.identity.googleid:googleid:1.2.1")
    testImplementation(libs.junit)
    androidTestImplementation(libs.espresso.core)
    androidTestImplementation(libs.ext.junit)
}
