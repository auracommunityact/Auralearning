import java.util.Base64
plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.google.devtools.ksp)
    alias(libs.plugins.kotlin.serialization)
}

android {
    namespace = "com.example"
    compileSdk = 36
    buildToolsVersion = "36.0.0"

    defaultConfig {
        applicationId = "com.aistudio.auralearning.abcdef"
        minSdk = 24
        targetSdk = 36
        versionCode = 1
        versionName = "1.0"

        val supabaseUrl = System.getenv("SUPABASE_URL") ?: "https://qxoqflrqpwlythgqmjtq.supabase.co"
        val supabaseAnonKey = System.getenv("SUPABASE_ANON_KEY") ?: "eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9.eyJpc3MiOiJzdXBhYmFzZSIsInJlZiI6InF4b3FmbHJxcHdseXRocW1qdHEiLCJyb2xlIjoiYW5vbiIsImlhdCI6MTcwMDAwMDAwMCwiZXhwIjoyMDE1NTc2MDAwfQ.placeholder"
        val geminiApiKey = System.getenv("GEMINI_API_KEY") ?: ""

        buildConfigField("String", "SUPABASE_URL", "\"$supabaseUrl\"")
        buildConfigField("String", "SUPABASE_ANON_KEY", "\"$supabaseAnonKey\"")
        buildConfigField("String", "SUPABASE_KEY", "\"$supabaseAnonKey\"")
        buildConfigField("String", "GEMINI_API_KEY", "\"$geminiApiKey\"")
    }

    buildFeatures {
        compose = true
        buildConfig = true
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_21
        targetCompatibility = JavaVersion.VERSION_21
    }
    kotlinOptions {
        jvmTarget = "21"
    }

    signingConfigs {
        val storeFileVar = System.getenv("KEYSTORE_FILE") ?: System.getenv("KEYSTORE_PATH")
        val storePasswordVar = System.getenv("KEYSTORE_PASSWORD")
        val keyAliasVar = System.getenv("KEY_ALIAS")
        val keyPasswordVar = System.getenv("KEY_PASSWORD")
        val keystoreBase64 = System.getenv("KEYSTORE_BASE64")
        var keystoreFile: java.io.File? = null

        if (storeFileVar != null) {
            keystoreFile = rootProject.file(storeFileVar)
        } else if (!keystoreBase64.isNullOrEmpty()) {
            val decodedBytes = Base64.getDecoder().decode(keystoreBase64)
            keystoreFile = rootProject.file("upload_release.keystore")
            keystoreFile.writeBytes(decodedBytes)
        }

        if (keystoreFile != null && keystoreFile.exists() && !storePasswordVar.isNullOrEmpty() && !keyAliasVar.isNullOrEmpty() && !keyPasswordVar.isNullOrEmpty()) {
            create("release") {
                storeFile = keystoreFile
                storePassword = storePasswordVar
                keyAlias = keyAliasVar
                keyPassword = keyPasswordVar
            }
        }
    }

    buildTypes {
        debug {
            isMinifyEnabled = false
            signingConfig = signingConfigs.getByName("debug")
        }
        release {
            isMinifyEnabled = true
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
            val releaseSigning = signingConfigs.findByName("release")
            if (releaseSigning != null) {
                signingConfig = releaseSigning
            } else {
                signingConfig = signingConfigs.getByName("debug")
            }
        }
    }
}

dependencies {
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    implementation(libs.androidx.activity.compose)
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.ui.graphics)
    implementation(libs.androidx.compose.ui.tooling.preview)
    implementation(libs.androidx.compose.material3)
    implementation(libs.androidx.compose.material.icons.extended)
    implementation(libs.androidx.navigation.compose)
    implementation(libs.androidx.room.runtime)
    implementation(libs.androidx.room.ktx)
    ksp(libs.androidx.room.compiler)
    implementation(libs.androidx.datastore.preferences)
    implementation(libs.coil.compose)
    implementation(libs.retrofit)
    implementation(libs.converter.moshi)
    implementation(libs.moshi.kotlin)
    ksp(libs.moshi.kotlin.codegen)
    implementation(libs.logging.interceptor)
    implementation(platform(libs.supabase.bom))
    implementation(libs.supabase.postgrest)
    implementation(libs.supabase.auth)
    implementation(libs.supabase.storage)
    implementation(libs.supabase.realtime)
    implementation(libs.ktor.client.core)
    implementation(libs.ktor.client.android)
    implementation(libs.kotlinx.serialization.json)
    implementation(libs.androidx.core.splashscreen)
    implementation(libs.accompanist.permissions)
    implementation(libs.androidx.camera.camera2)
    implementation(libs.androidx.camera.lifecycle)
    implementation(libs.androidx.camera.view)
    implementation(libs.androidx.camera.core)
    implementation(libs.play.services.location)
    implementation(libs.play.services.auth)
    implementation(libs.play.services.ads)
    implementation(libs.user.messaging.platform)
    implementation(libs.zxing.core)
    implementation(libs.firebase.messaging)
    implementation(libs.mlkit.text.recognition)
    implementation(libs.mlkit.translate)
    implementation(libs.mlkit.language.id)
    implementation(libs.kotlinx.coroutines.play.services)
    implementation(libs.gson)
    implementation(libs.jsoup)
    implementation(libs.youtube.player.core)
}
