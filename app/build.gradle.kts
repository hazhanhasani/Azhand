plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("org.jetbrains.kotlin.plugin.compose")
}

android {
    namespace = "com.azhand.app"
    compileSdk = 35

    defaultConfig {
        applicationId = "com.azhand.app"
        minSdk = 26
        targetSdk = 35
        versionCode = 5
        versionName = "0.4.1"

        buildConfigField(
            "String",
            "API_BASE_URL",
            "\"${System.getenv("AZHAND_API_BASE_URL") ?: "https://example.workers.dev"}\""
        )
    }

    signingConfigs {
        val path = System.getenv("ANDROID_KEYSTORE_PATH")
        val storePass = System.getenv("ANDROID_KEYSTORE_PASSWORD")
        val alias = System.getenv("ANDROID_KEY_ALIAS")
        val keyPass = System.getenv("ANDROID_KEY_PASSWORD")

        if (!path.isNullOrBlank() &&
            !storePass.isNullOrBlank() &&
            !alias.isNullOrBlank() &&
            !keyPass.isNullOrBlank()
        ) {
            create("release") {
                storeFile = file(path)
                storePassword = storePass
                keyAlias = alias
                keyPassword = keyPass
            }
        }
    }

    buildTypes {
        debug {
            applicationIdSuffix = ".debug"
            versionNameSuffix = "-debug"
        }

        release {
            isMinifyEnabled = false
            signingConfig = signingConfigs.findByName("release")
        }
    }

    buildFeatures {
        compose = true
        buildConfig = true
    }

    packaging {
        resources {
            excludes += "/META-INF/{AL2.0,LGPL2.1}"
        }
    }
}

dependencies {
    implementation(platform("androidx.compose:compose-bom:2024.12.01"))
    implementation("androidx.activity:activity-compose:1.10.0")
    implementation("androidx.core:core-ktx:1.15.0")
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.ui:ui-tooling-preview")
    implementation("androidx.compose.material3:material3")
    debugImplementation("androidx.compose.ui:ui-tooling")
}
