plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.ksp)
    alias(libs.plugins.hilt.android)
    // Google Services plugin for FCM
    alias(libs.plugins.google.services)
}

import java.util.Properties
import java.io.FileInputStream

android {
    namespace = "com.nexy.client"
    compileSdk = 35

    // Load keystore properties
    val keystorePropertiesFile = rootProject.file("keystore.properties")
    val keystoreProperties = Properties()
    if (keystorePropertiesFile.exists()) {
        keystoreProperties.load(FileInputStream(keystorePropertiesFile))
    }

    defaultConfig {
        applicationId = "com.nexy.client"
        minSdk = 26
        targetSdk = 34
        versionCode = 13
        versionName = "1.15.0"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
        vectorDrawables {
            useSupportLibrary = true
        }
        
        multiDexEnabled = true
        
        // FCM feature flag (can be disabled for builds without FCM)
        buildConfigField("boolean", "FCM_ENABLED", "false")
    }

    signingConfigs {
        create("release") {
            if (keystorePropertiesFile.exists()) {
                storeFile = rootProject.file(keystoreProperties["KEYSTORE_FILE"] as String)
                storePassword = keystoreProperties["KEYSTORE_PASSWORD"] as String
                keyAlias = keystoreProperties["KEY_ALIAS"] as String
                keyPassword = keystoreProperties["KEY_PASSWORD"] as String
                enableV1Signing = true
                enableV2Signing = true
            }
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            signingConfig = signingConfigs.getByName("release")
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
        }
        debug {
            // Debug builds don't need signing
        }
    }
    
    flavorDimensions += "environment"
    productFlavors {
        create("dev") {
            dimension = "environment"
            applicationIdSuffix = ".dev"
            versionNameSuffix = "-dev"
            buildConfigField("String", "SERVER_IP", "\"192.168.0.2\"")
            buildConfigField("String", "SERVER_PORT", "\"8080\"")
            buildConfigField("String", "WS_PROTOCOL", "\"ws\"")
            buildConfigField("String", "HTTP_PROTOCOL", "\"http\"")
            // FCM enabled for dev builds (set to "false" to disable)
            buildConfigField("boolean", "FCM_ENABLED", "true")
        }
        create("prod") {
            dimension = "environment"
            versionNameSuffix = "-prod"
            buildConfigField("String", "SERVER_IP", "\"your-server-ip\"")
            buildConfigField("String", "SERVER_PORT", "\"8080\"")
            buildConfigField("String", "WS_PROTOCOL", "\"ws\"")
            buildConfigField("String", "HTTP_PROTOCOL", "\"http\"")
            // FCM enabled for production builds (set to "false" to disable)
            buildConfigField("boolean", "FCM_ENABLED", "false")
        }
    }
    
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
        isCoreLibraryDesugaringEnabled = false
    }
    
    kotlinOptions {
        jvmTarget = "11"
    }
    
    buildFeatures {
        compose = true
        buildConfig = true
    }
    
    composeOptions {
        kotlinCompilerExtensionVersion = "1.5.14"
    }
    
    packaging {
        resources {
            excludes += "/META-INF/{AL2.0,LGPL2.1}"
            excludes += "META-INF/versions/9/OSGI-INF/MANIFEST.MF"
        }
    }
}

dependencies {
    // Core Android
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.appcompat)
    implementation(libs.material)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation("androidx.lifecycle:lifecycle-process:2.8.7")

    // Compose BOM
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.ui.graphics)
    implementation(libs.androidx.compose.ui.tooling.preview)
    implementation(libs.androidx.compose.material3)
    implementation(libs.androidx.compose.material.icons.extended)
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    implementation(libs.androidx.lifecycle.runtime.compose)
    implementation(libs.androidx.navigation.compose)
    debugImplementation(libs.androidx.compose.ui.tooling)

    // Hilt
    implementation(libs.hilt.android)
    ksp(libs.hilt.android.compiler)
    implementation(libs.hilt.navigation.compose)

    // Room
    implementation(libs.androidx.room.runtime)
    implementation(libs.androidx.room.ktx)
    ksp(libs.androidx.room.compiler)

    // Retrofit + OkHttp
    implementation(libs.retrofit)
    implementation(libs.retrofit.converter.gson)
    implementation(libs.okhttp)
    implementation(libs.okhttp.logging.interceptor)
    
    // Firebase (optional - only used when FCM_ENABLED = true)
    implementation(platform(libs.firebase.bom))
    implementation(libs.firebase.messaging)
    implementation(libs.firebase.analytics)

    // Coroutines
    implementation(libs.kotlinx.coroutines.android)
    implementation(libs.kotlinx.coroutines.core)

    // DataStore
    implementation(libs.androidx.datastore.preferences)

    // WebRTC
    implementation("io.getstream:stream-webrtc-android:1.2.2")

    // QR Codes
    implementation(libs.zxing.core)
    implementation("com.journeyapps:zxing-android-embedded:4.3.0")

    // Coil for image loading
    implementation(libs.coil.compose)

    // Accompanist for permissions
    implementation(libs.accompanist.permissions)

    // E2E Encryption (without libsignal - using Java Crypto API)
    // implementation(libs.libsignal.android)
    implementation(libs.androidx.security.crypto)
    implementation(libs.androidx.biometric)

    // Testing
    testImplementation(libs.junit)
    androidTestImplementation(libs.androidx.junit)
    androidTestImplementation(libs.androidx.espresso.core)
    androidTestImplementation(platform(libs.androidx.compose.bom))
    androidTestImplementation(libs.androidx.compose.ui.test.junit4)
    debugImplementation(libs.androidx.compose.ui.test.manifest)
}
