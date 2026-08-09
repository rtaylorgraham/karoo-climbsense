plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.jetbrains.kotlin.android)
}

android {
    namespace = "com.rtaylor.climbsense"
    compileSdk = 34
    // build-tools 34.0.0 on this machine is a Linux install (no .exe binaries); 36.0.0 is intact
    buildToolsVersion = "36.0.0"

    defaultConfig {
        applicationId = "com.rtaylor.climbsense"
        minSdk = 26
        targetSdk = 34
        // versionCode continues from pre-beta internal builds (in-place upgrades
        // on existing devices need it monotonic); versionName is the public story.
        versionCode = 7
        versionName = "0.9.0-beta"
    }

    buildFeatures {
        buildConfig = true
    }

    // Release signing: reads keystore config from gradle properties or env vars
    // (CLIMBSENSE_KEYSTORE, CLIMBSENSE_KEYSTORE_PASSWORD, CLIMBSENSE_KEY_ALIAS,
    // CLIMBSENSE_KEY_PASSWORD). Never committed; falls back to unsigned so CI
    // and fresh clones still build.
    val keystorePath = providers.gradleProperty("CLIMBSENSE_KEYSTORE")
        .orElse(providers.environmentVariable("CLIMBSENSE_KEYSTORE")).orNull
    if (keystorePath != null) {
        signingConfigs {
            create("release") {
                storeFile = file(keystorePath)
                storePassword = providers.gradleProperty("CLIMBSENSE_KEYSTORE_PASSWORD")
                    .orElse(providers.environmentVariable("CLIMBSENSE_KEYSTORE_PASSWORD")).orNull
                keyAlias = providers.gradleProperty("CLIMBSENSE_KEY_ALIAS")
                    .orElse(providers.environmentVariable("CLIMBSENSE_KEY_ALIAS")).getOrElse("climbsense")
                keyPassword = providers.gradleProperty("CLIMBSENSE_KEY_PASSWORD")
                    .orElse(providers.environmentVariable("CLIMBSENSE_KEY_PASSWORD")).orNull
            }
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            if (keystorePath != null) {
                signingConfig = signingConfigs.getByName("release")
            }
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

dependencies {
    implementation(libs.karoo.ext)
    implementation(libs.androidx.core.ktx)
    implementation(libs.kotlinx.coroutines.android)

    testImplementation(libs.junit)
    testImplementation(libs.kotlinx.coroutines.test)
}


