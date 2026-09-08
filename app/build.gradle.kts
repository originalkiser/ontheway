import java.util.Properties

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.compose)
}

// Local, un-committed secrets (API keys) are read from local.properties.
// Copy local.properties.template to local.properties and fill in real values.
val localProperties = Properties().apply {
    val localPropertiesFile = rootProject.file("local.properties")
    if (localPropertiesFile.exists()) {
        localPropertiesFile.inputStream().use { load(it) }
    }
}
val mapsApiKey: String = localProperties.getProperty("MAPS_API_KEY") ?: "YOUR_API_KEY_HERE"
// A second, separately-restricted key for the Directions/Places REST calls made directly over
// HTTPS (via Retrofit). An "Android apps" restriction only authenticates calls made through
// Google's own client libraries (e.g. the Maps SDK's tile requests) - it can't authenticate a
// raw HTTP call, which always looks like an anonymous request with no Referer header. So this
// key must be restricted by API only (Directions API, Places API (New)), not by Android app.
// Falls back to MAPS_API_KEY if unset, so existing single-key setups keep working.
val placesApiKey: String = localProperties.getProperty("PLACES_API_KEY") ?: mapsApiKey

android {
    namespace = "com.reststop.countdown"
    compileSdk = 35

    defaultConfig {
        applicationId = "com.reststop.countdown"
        minSdk = 26
        targetSdk = 35
        versionCode = 1
        versionName = "1.0"

        // Exposed to AndroidManifest.xml via ${MAPS_API_KEY}
        manifestPlaceholders["MAPS_API_KEY"] = mapsApiKey
        // Exposed to Kotlin code via BuildConfig for the Retrofit clients.
        buildConfigField("String", "MAPS_API_KEY", "\"$mapsApiKey\"")
        buildConfigField("String", "PLACES_API_KEY", "\"$placesApiKey\"")
    }

    // A committed (non-secret) debug keystore so every debug build - local or CI - is signed
    // with the same key. Without this, each machine/CI runner gets its own auto-generated
    // debug key with a different SHA-1, which breaks an Android-restricted Maps API key.
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
            isMinifyEnabled = true
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
        }
        debug {
            isMinifyEnabled = false
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
        resources {
            excludes += "/META-INF/{AL2.0,LGPL2.1}"
        }
    }
}

dependencies {
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    implementation(libs.androidx.lifecycle.runtime.compose)
    implementation(libs.androidx.activity.compose)

    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.ui)
    implementation(libs.androidx.ui.graphics)
    implementation(libs.androidx.ui.tooling.preview)
    implementation(libs.androidx.material3)
    implementation(libs.androidx.material.icons.extended)
    debugImplementation(libs.androidx.ui.tooling)

    implementation(libs.play.services.maps)
    implementation(libs.play.services.location)
    implementation(libs.maps.compose)
    // Paid, but the first 1,000 destinations/month are free - well within personal/family use.
    // Gives real turn-by-turn: road-snapped route, native instruction banner, voice guidance,
    // lane info, and automatic rerouting, in place of the hand-rolled driving view.
    implementation(libs.navigation.sdk)

    implementation(libs.retrofit.core)
    implementation(libs.retrofit.gson)
    implementation(libs.okhttp.core)
    implementation(libs.okhttp.logging)

    implementation(libs.kotlinx.coroutines.android)
    implementation(libs.kotlinx.coroutines.play.services)

    testImplementation(libs.junit)
}
