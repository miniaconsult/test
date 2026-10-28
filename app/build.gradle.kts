plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
}

android {
    namespace = "fr.miniaconsult.alertesilencieux"
    compileSdk = 35

    defaultConfig {
        applicationId = "fr.miniaconsult.alertesilencieux"
        minSdk = 29
        targetSdk = 35
        versionCode = 3
        versionName = "3.0"
    }

    signingConfigs {
        // Clé de signature fixe pour pouvoir installer les mises à jour par-dessus
        // la version précédente (sans désinstaller ni perdre les réglages).
        create("stable") {
            storeFile = file("signing.keystore")
            storePassword = "alertesilencieux"
            keyAlias = "alertesilencieux"
            keyPassword = "alertesilencieux"
        }
    }

    buildTypes {
        debug {
            signingConfig = signingConfigs.getByName("stable")
        }
        release {
            isMinifyEnabled = false
            signingConfig = signingConfigs.getByName("stable")
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlinOptions {
        jvmTarget = "17"
    }
}
