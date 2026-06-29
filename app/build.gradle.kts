import java.util.Properties

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.ksp)
    alias(libs.plugins.google.services)
    alias(libs.plugins.kotlin.serialization)
}

// Read Maps API key from local.properties so we never commit it to git.
// The README explains how to add MAPS_API_KEY to local.properties.
val localProperties = Properties().apply {
    val file = rootProject.file("local.properties")
    if (file.exists()) load(file.inputStream())
}
val mapsApiKey: String = localProperties.getProperty("MAPS_API_KEY") ?: ""
val googleWebClientId: String = localProperties.getProperty("GOOGLE_WEB_CLIENT_ID") ?: ""
val weatherBackendBaseUrl: String =
    localProperties.getProperty("WEATHER_BACKEND_BASE_URL") ?: "https://example.com/"

android {
    namespace = "com.wildtrail.app"
    compileSdk = 35

    defaultConfig {
        applicationId = "com.wildtrail.app"
        minSdk = 26
        targetSdk = 35
        versionCode = 1
        versionName = "1.0"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
        vectorDrawables {
            useSupportLibrary = true
        }

        // Forwarded into AndroidManifest.xml via ${MAPS_API_KEY}
        manifestPlaceholders["MAPS_API_KEY"] = mapsApiKey

        // Exposed as BuildConfig.GOOGLE_WEB_CLIENT_ID. Empty by default —
        // the Google Sign-In button reads it and shows a setup-required
        // message until you fill in local.properties.
        buildConfigField("String", "GOOGLE_WEB_CLIENT_ID", "\"$googleWebClientId\"")
        buildConfigField("String", "WEATHER_BACKEND_BASE_URL", "\"$weatherBackendBaseUrl\"")
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
        }
        debug {
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

    // The BirdNET .tflite model is memory-mapped straight from the APK's
    // assets, so it must not be compressed (a compressed asset can't be mmap'd).
    androidResources {
        noCompress += "tflite"
    }

    packaging {
        resources {
            excludes += "/META-INF/{AL2.0,LGPL2.1}"
            excludes += "META-INF/LICENSE.md"
            excludes += "META-INF/LICENSE-notice.md"
        }
    }

    testOptions {
        unitTests {
            isIncludeAndroidResources = true
            isReturnDefaultValues = true
        }
    }

    // Allow Room to use schemas folder for migration verification
    ksp {
        arg("room.schemaLocation", "$projectDir/schemas")
    }
}

dependencies {
    // Core
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.splashscreen)
    // XML Material Components — required by themes.xml `Theme.Material3.*` parent
    implementation(libs.google.material)

    // Compose BOM ensures all Compose libraries are version-compatible
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.ui)
    implementation(libs.androidx.ui.graphics)
    implementation(libs.androidx.ui.tooling.preview)
    implementation(libs.androidx.material3)
    implementation(libs.androidx.material.icons.extended)

    // Navigation
    implementation(libs.androidx.navigation.compose)

    // Lifecycle / ViewModel + Compose integration
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    implementation(libs.androidx.lifecycle.runtime.compose)

    // Coroutines
    implementation(libs.kotlinx.coroutines.android)
    implementation(libs.kotlinx.coroutines.play.services)

    // Room (offline-first local DB)
    implementation(libs.androidx.room.runtime)
    implementation(libs.androidx.room.ktx)
    ksp(libs.androidx.room.compiler)

    // DataStore (preferences)
    implementation(libs.androidx.datastore.preferences)

    // Firebase BOM
    implementation(platform(libs.firebase.bom))
    implementation(libs.firebase.auth)
    implementation(libs.firebase.firestore)
    implementation(libs.firebase.storage)
    implementation(libs.firebase.analytics)

    // Coil image loading
    implementation(libs.coil.compose)

    // Google ML Kit — on-device image labeling used by the AI photo
    // description feature on the hike-detail screen. Runs entirely on the
    // phone, no API key, no network.
    implementation(libs.mlkit.image.labeling)

    // TensorFlow Lite — on-device BirdNET bird-sound classifier (model under
    // assets/birdnet/). If the interpreter ever reports a missing op, add
    // `org.tensorflow:tensorflow-lite-select-tf-ops` — but the standard BirdNET
    // model only uses built-in ops, so this single artifact should suffice.
    implementation(libs.tensorflow.lite)

    // Maps + Location
    implementation(libs.maps.compose)
    implementation(libs.play.services.maps)
    implementation(libs.play.services.location)

    // Networking (weather backend proxy)
    implementation(libs.retrofit2)
    implementation(libs.retrofit2.converter.gson)
    implementation(libs.okhttp.logging.interceptor)

    // Kotlinx Serialization (for serializing route coords)
    implementation(libs.kotlinx.serialization.json)

    // Permissions helper
    implementation(libs.accompanist.permissions)

    // Google Sign-In via Credential Manager
    implementation(libs.androidx.credentials)
    implementation(libs.androidx.credentials.playservices)
    implementation(libs.google.id)

    // MPAndroidChart for the elevation profile on the hike-detail screen.
    implementation(libs.mpandroidchart)

    // ---------- Testing ----------
    testImplementation(libs.junit)
    testImplementation(libs.mockito.kotlin)
    testImplementation(libs.mockito.core)
    testImplementation(libs.kotlinx.coroutines.test)
    testImplementation(libs.robolectric)
    testImplementation(libs.turbine)
    testImplementation(libs.androidx.arch.core.testing)
    testImplementation(libs.androidx.room.testing)

    // Instrumented tests
    androidTestImplementation(libs.androidx.test.ext.junit)
    androidTestImplementation(libs.androidx.test.runner)
    androidTestImplementation(libs.androidx.test.rules)
    androidTestImplementation(libs.androidx.espresso.core)
    androidTestImplementation(platform(libs.androidx.compose.bom))
    androidTestImplementation(libs.androidx.ui.test.junit4)
    androidTestImplementation(libs.kotlinx.coroutines.test)
    androidTestImplementation(libs.androidx.room.testing)

    debugImplementation(libs.androidx.ui.tooling)
    debugImplementation(libs.androidx.ui.test.manifest)
}
