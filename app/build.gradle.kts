import java.util.Base64
plugins {
    alias(libs.plugins.android.application)
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
    }

    buildFeatures {
        compose = true
        buildConfig = true
    }

    signingConfigs {
        create("release") {
            val storeFileVar = System.getenv("KEYSTORE_FILE") ?: System.getenv("KEYSTORE_PATH")
            val storePasswordVar = System.getenv("KEYSTORE_PASSWORD")
            val keyAliasVar = System.getenv("KEY_ALIAS")
            val keyPasswordVar = System.getenv("KEY_PASSWORD")
            val keystoreBase64 = System.getenv("KEYSTORE_BASE64")
            var keystoreFile: java.io.File? = null
            
            if (storeFileVar != null) {
                keystoreFile = rootProject.file(storeFileVar)
            } else if (keystoreBase64 != null && keystoreBase64.isNotEmpty()) {
                val decodedBytes = Base64.getDecoder().decode(keystoreBase64)
                keystoreFile = rootProject.file("upload_release.keystore")
                keystoreFile.writeBytes(decodedBytes)
            }
            
            if (keystoreFile == null || !keystoreFile.exists()) {
                throw GradleException("Release keystore file not found! Please set KEYSTORE_FILE or KEYSTORE_BASE64 in Secrets.")
            }
            if (storePasswordVar.isNullOrEmpty()) {
                throw GradleException("KEYSTORE_PASSWORD not set in Secrets!")
            }
            if (keyAliasVar.isNullOrEmpty()) {
                throw GradleException("KEY_ALIAS not set in Secrets!")
            }
            if (keyPasswordVar.isNullOrEmpty()) {
                throw GradleException("KEY_PASSWORD not set in Secrets!")
            }
            
            storeFile = keystoreFile
            storePassword = storePasswordVar
            keyAlias = keyAliasVar
            keyPassword = keyPasswordVar
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = true
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
            signingConfig = signingConfigs.getByName("release")
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
}
