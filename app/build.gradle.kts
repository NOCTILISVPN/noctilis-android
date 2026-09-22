plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("org.jetbrains.kotlin.plugin.compose")
}

android {
    namespace = "net.noctilis.app"
    compileSdk = 35

    defaultConfig {
        applicationId = "net.noctilis.app"
        minSdk = 26
        targetSdk = 35
        // versionCode растёт на каждую сборку в CI (см. .github/workflows/android.yml)
        versionCode = (System.getenv("APP_VERSION_CODE") ?: "1").toInt()
        versionName = System.getenv("APP_VERSION_NAME") ?: "0.0.1-dev"
        ndk {
            // Только arm64: все современные телефоны. Три архитектуры давали APK 102 МБ,
            // Chrome на телефоне вис на проверке такого файла (22.09).
            abiFilters += listOf("arm64-v8a")
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            // пока нет боевого ключа подписи — подписываем отладочным, но сборка не debuggable
            signingConfig = signingConfigs.getByName("debug")
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
    packaging {
        jniLibs.useLegacyPackaging = false   // сжатые .so в APK
    }
}

dependencies {
    // Ядро туннеля: sing-box 1.11.15, собирается в CI из исходников (job libbox)
    implementation(files("libs/libbox.aar"))

    val composeBom = platform("androidx.compose:compose-bom:2024.12.01")
    implementation(composeBom)
    implementation("androidx.activity:activity-compose:1.9.3")
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.material3:material3")
    implementation("androidx.compose.ui:ui-tooling-preview")
    debugImplementation("androidx.compose.ui:ui-tooling")
    implementation("androidx.core:core-ktx:1.13.1")
    implementation("androidx.browser:browser:1.8.0")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.9.0")
}
