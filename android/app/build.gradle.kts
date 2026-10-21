import java.util.Properties

plugins {
    id("com.android.application")
}

val keyProps = Properties().apply {
    val f = rootProject.file("keystore/keystore.properties")
    if (f.exists()) f.inputStream().use { load(it) }
}

android {
    namespace = "kr.luckykoopa.shuttle"
    compileSdk = 34

    defaultConfig {
        applicationId = "kr.luckykoopa.shuttle"
        minSdk = 24
        targetSdk = 34
        versionCode = 1
        versionName = "1.0"
    }

    signingConfigs {
        create("release") {
            storeFile = rootProject.file(keyProps.getProperty("storeFile", "keystore/shuttle.jks"))
            storePassword = keyProps.getProperty("storePassword", "shuttle-app")
            keyAlias = keyProps.getProperty("keyAlias", "shuttle")
            keyPassword = keyProps.getProperty("keyPassword", "shuttle-app")
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            signingConfig = signingConfigs.getByName("release")
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
}

dependencies {
    implementation("androidx.swiperefreshlayout:swiperefreshlayout:1.1.0")
}
