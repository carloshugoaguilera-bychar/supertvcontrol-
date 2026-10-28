plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("org.jetbrains.kotlin.plugin.compose")
}

// En GitHub Actions, GITHUB_REPOSITORY = "usuario/repo": la app descarga las marcas de ese mismo repo.
val repo: String = System.getenv("GITHUB_REPOSITORY") ?: "TU_USUARIO/TU_REPO"

android {
    namespace = "com.claudia.controltv"
    compileSdk = 34

    defaultConfig {
        applicationId = "com.claudia.controltv"
        minSdk = 24
        targetSdk = 34
        // sube con cada compilacion de GitHub, asi Android siempre lo ve como actualizacion
        versionCode = System.getenv("GITHUB_RUN_NUMBER")?.toIntOrNull() ?: 1
        versionName = "1.0"
        buildConfigField(
            "String",
            "BRANDS_URL",
            "\"https://raw.githubusercontent.com/$repo/main/brands/\""
        )
    }

    // Firma fija: sin esto cada compilacion de GitHub firma distinto y Android no deja actualizar.
    signingConfigs {
        getByName("debug") {
            storeFile = file("debug.keystore")
            storePassword = "android"
            keyAlias = "androiddebugkey"
            keyPassword = "android"
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = false
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
}

dependencies {
    val composeBom = platform("androidx.compose:compose-bom:2024.09.00")
    implementation(composeBom)
    implementation("androidx.activity:activity-compose:1.9.2")
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.foundation:foundation")
    implementation("androidx.compose.material3:material3")
    implementation("androidx.compose.material:material-icons-core")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.8.1")
}
