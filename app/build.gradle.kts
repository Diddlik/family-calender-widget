plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.kotlin.serialization)
}

android {
    namespace = "io.github.diddlik.calendarwidget"
    compileSdk = 37

    defaultConfig {
        applicationId = "io.github.diddlik.calendarwidget"
        minSdk = 26
        targetSdk = 37
        // Single source of the app version (Semantic Versioning); shown in the UI via BuildConfig.
        versionName = "0.1.0"
        // Google Play rejects reused version codes, so CI passes its increasing run number.
        versionCode = providers.gradleProperty("versionCode").map(String::toInt).getOrElse(1)
    }

    // The upload key is supplied by CI through environment variables and never stored in the repository.
    val signingStoreFile = providers.environmentVariable("SIGNING_STORE_FILE").orNull
    signingConfigs {
        if (signingStoreFile != null) {
            create("upload") {
                storeFile = file(signingStoreFile)
                storePassword = providers.environmentVariable("SIGNING_STORE_PASSWORD").get()
                keyAlias = providers.environmentVariable("SIGNING_KEY_ALIAS").get()
                keyPassword = providers.environmentVariable("SIGNING_KEY_PASSWORD").get()
            }
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            signingConfig = signingConfigs.findByName("upload")
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    // Lets Android 13+ offer a per-app language choice for the bundled translations.
    androidResources {
        generateLocaleConfig = true
    }
    buildFeatures {
        compose = true
        buildConfig = true
    }
    lint {
        warningsAsErrors = false
        abortOnError = true
    }
}

dependencies {
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.activity.compose)
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.compose.material3)
    implementation(libs.androidx.compose.material.icons.core)
    implementation(libs.androidx.glance.appwidget)
    implementation(libs.androidx.glance.material3)
    implementation(libs.androidx.work.runtime)
    implementation(libs.androidx.datastore)
    implementation(libs.kotlinx.serialization.json)

    testImplementation(libs.junit)
}
