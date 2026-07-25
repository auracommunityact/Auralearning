import java.util.Base64
import java.util.Properties
import java.io.FileInputStream

plugins {
  alias(libs.plugins.android.application)
  alias(libs.plugins.kotlin.compose)
  alias(libs.plugins.google.devtools.ksp)
  alias(libs.plugins.roborazzi)
  alias(libs.plugins.secrets)
  alias(libs.plugins.kotlin.serialization)
}

android {
  namespace = "com.example"
  compileSdk = 36

  defaultConfig {
    applicationId = "com.auracommunityact.auralearning"
    minSdk = 24
    targetSdk = 34
    versionCode = 3
    versionName = "1.0.3"

    testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
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
        } else {
            val defaultJks = rootProject.file("aura-learning.jks")
            if (defaultJks.exists()) {
                keystoreFile = defaultJks
            } else {
                val defaultRelease = rootProject.file("upload_release.keystore")
                if (defaultRelease.exists()) {
                    keystoreFile = defaultRelease
                }
            }
        }

        if (keystoreFile != null && keystoreFile.exists()) {
            storeFile = keystoreFile
            storePassword = storePasswordVar ?: "aura1234"
            keyAlias = keyAliasVar ?: "aura"
            keyPassword = keyPasswordVar ?: "aura1234"
            enableV1Signing = true
            enableV2Signing = true
            println("Using release signing key: ${keystoreFile.name} with alias: $keyAlias")
        } else {
            println("WARNING: Release keystore file not found.")
        }
    }
  }


  buildTypes {
    release {
      isCrunchPngs = true
      isMinifyEnabled = true
      isShrinkResources = true
      proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
      signingConfig = signingConfigs.getByName("release")
    }
    debug {
      
    }
  }
  lint {
    abortOnError = false
    checkReleaseBuilds = false
  }

  compileOptions {
    sourceCompatibility = JavaVersion.VERSION_11
    targetCompatibility = JavaVersion.VERSION_11
  }
  buildFeatures {
    compose = true
    buildConfig = true
  }
  testOptions { unitTests { isIncludeAndroidResources = true } }
}

// Configure the Secrets Gradle Plugin to use .env and .env.example files
// to match the convention used in Web projects.
secrets {
  propertiesFileName = ".env"
  defaultPropertiesFileName = ".env.example"
}

// Some unused dependencies are commented out below instead of being removed.
// This makes it easy to add them back in the future if needed.
dependencies {
  implementation(platform(libs.androidx.compose.bom))
  implementation(platform(libs.supabase.bom))
  implementation(libs.accompanist.permissions)
  implementation(libs.androidx.activity.compose)
  // implementation(libs.androidx.camera.camera2)
  // implementation(libs.androidx.camera.core)
  // implementation(libs.androidx.camera.lifecycle)
  // implementation(libs.androidx.camera.view)
  implementation(libs.androidx.compose.material.icons.core)
  implementation(libs.androidx.compose.material.icons.extended)
  implementation(libs.androidx.compose.material3)
  implementation(libs.androidx.compose.ui)
  implementation(libs.androidx.compose.ui.graphics)
  implementation(libs.androidx.compose.ui.tooling.preview)
  implementation(libs.androidx.core.ktx)
  implementation(libs.androidx.core.splashscreen)
  implementation("androidx.browser:browser:1.8.0")
  implementation(libs.androidx.datastore.preferences)
  implementation(libs.androidx.lifecycle.runtime.compose)
  implementation(libs.androidx.lifecycle.runtime.ktx)
  implementation(libs.androidx.lifecycle.viewmodel.compose)
  implementation(libs.androidx.navigation.compose)
  implementation("androidx.webkit:webkit:1.11.0")
  implementation(libs.androidx.room.ktx)
  implementation(libs.androidx.room.runtime)
  implementation("com.google.code.gson:gson:2.10.1")
  implementation(libs.coil.compose)
  // implementation(libs.converter.moshi)
  implementation(libs.supabase.postgrest)
  implementation(libs.supabase.auth)
  implementation(libs.supabase.storage)
  implementation(libs.supabase.realtime)
  implementation(libs.ktor.client.core)
  implementation(libs.ktor.client.android)
  implementation(libs.kotlinx.serialization.json)
  implementation(libs.zxing.core)
  implementation(libs.kotlinx.coroutines.android)
  implementation(libs.kotlinx.coroutines.core)
  implementation(libs.logging.interceptor)
  // implementation(libs.moshi.kotlin)
  implementation(libs.okhttp)
  // implementation(libs.play.services.location)
  implementation(libs.play.services.auth)
  implementation(libs.play.services.ads)
  implementation(libs.user.messaging.platform)
  implementation(libs.retrofit)
  implementation(libs.retrofit.converter.kotlinx.serialization)
  implementation("com.google.mlkit:translate:17.0.2")
  implementation("com.google.mlkit:language-id:17.0.5")
  implementation("org.jsoup:jsoup:1.17.2")
  implementation("org.jetbrains.kotlinx:kotlinx-coroutines-play-services:1.7.3")
  implementation(libs.mlkit.text.recognition)
  implementation(libs.firebase.messaging)
  testImplementation(libs.androidx.compose.ui.test.junit4)
  testImplementation(libs.androidx.core)
  testImplementation(libs.androidx.junit)
  testImplementation(libs.junit)
  testImplementation(libs.kotlinx.coroutines.test)
  testImplementation(libs.robolectric)
  testImplementation(libs.roborazzi)
  testImplementation(libs.roborazzi.compose)
  testImplementation(libs.roborazzi.junit.rule)
  androidTestImplementation(platform(libs.androidx.compose.bom))
  androidTestImplementation(libs.androidx.compose.ui.test.junit4)
  androidTestImplementation(libs.androidx.espresso.core)
  androidTestImplementation(libs.androidx.junit)
  androidTestImplementation(libs.androidx.runner)
  debugImplementation(libs.androidx.compose.ui.test.manifest)
  debugImplementation(libs.androidx.compose.ui.tooling)
  ksp(libs.androidx.room.compiler)
  // "ksp"(libs.moshi.kotlin.codegen)
}

// Helper function to check CI environment
fun isCi(): Boolean = System.getenv("CI") == "true" || System.getenv("GITHUB_ACTIONS") == "true"

tasks.register("validateReleaseSigning") {
    group = "verification"
    description = "Validates the presence of the release keystore and signing credentials before starting the release build."

    doLast {
        val storeFileVar = System.getenv("KEYSTORE_FILE") ?: System.getenv("KEYSTORE_PATH")
        val storePasswordVar = System.getenv("KEYSTORE_PASSWORD")
        val keyAliasVar = System.getenv("KEY_ALIAS")
        val keyPasswordVar = System.getenv("KEY_PASSWORD")
        val keystoreBase64 = System.getenv("KEYSTORE_BASE64")

        println("==================================================")
        println("   AURA RELEASE SIGNING CONFIGURATION VALIDATION   ")
        println("==================================================")
        
        // CI Strict Check
        if (isCi()) {
            val missingVars = mutableListOf<String>()
            if (storePasswordVar.isNullOrEmpty()) missingVars.add("KEYSTORE_PASSWORD")
            if (keyAliasVar.isNullOrEmpty()) missingVars.add("KEY_ALIAS")
            if (keyPasswordVar.isNullOrEmpty()) missingVars.add("KEY_PASSWORD")
            if (storeFileVar.isNullOrEmpty() && keystoreBase64.isNullOrEmpty()) missingVars.add("KEYSTORE_FILE/PATH or KEYSTORE_BASE64")

            if (missingVars.isNotEmpty()) {
                throw GradleException("CI Build Failed: Missing mandatory environment variables: ${missingVars.joinToString(", ")}")
            }
            println("CI Check: All mandatory variables present.")
        }

        println("KEYSTORE_FILE: ${storeFileVar ?: "Not Set (Using defaults)"}")
        println("KEYSTORE_PASSWORD: ${if (storePasswordVar.isNullOrEmpty()) "Not Set (Using fallback)" else "Set (length: ${storePasswordVar.length})"}")
        println("KEY_ALIAS: ${keyAliasVar ?: "Not Set (Using fallback)"}")
        println("KEY_PASSWORD: ${if (keyPasswordVar.isNullOrEmpty()) "Not Set (Using fallback)" else "Set (length: ${keyPasswordVar.length})"}")
        println("KEYSTORE_BASE64: ${if (keystoreBase64.isNullOrEmpty()) "Not Set" else "Set (length: ${keystoreBase64.length})"}")

        var hasKeystore = false
        var keystoreSource = ""

        if (storeFileVar != null) {
            val file = rootProject.file(storeFileVar)
            if (file.exists()) {
                hasKeystore = true
                keystoreSource = "File: ${file.absolutePath} (defined via KEYSTORE_FILE/KEYSTORE_PATH)"
            } else {
                println("ERROR: KEYSTORE_FILE / KEYSTORE_PATH is configured to '$storeFileVar' but the file does not exist.")
            }
        } else if (!keystoreBase64.isNullOrEmpty()) {
            try {
                val decodedBytes = Base64.getDecoder().decode(keystoreBase64)
                if (decodedBytes.isNotEmpty()) {
                    hasKeystore = true
                    keystoreSource = "Base64 (decoded via KEYSTORE_BASE64)"
                }
            } catch (e: Exception) {
                println("ERROR: KEYSTORE_BASE64 env variable contains invalid Base64.")
            }
        } else {
            // Check standard fallback keystores
            val auraJks = rootProject.file("aura-learning.jks")
            val uploadKeystore = rootProject.file("upload_release.keystore")
            val releaseKeystore = rootProject.file("release.keystore")

            if (auraJks.exists()) {
                hasKeystore = true
                keystoreSource = "Default File: ${auraJks.name}"
            } else if (uploadKeystore.exists()) {
                hasKeystore = true
                keystoreSource = "Default File: ${uploadKeystore.name}"
            } else if (releaseKeystore.exists()) {
                hasKeystore = true
                keystoreSource = "Default File: ${releaseKeystore.name}"
            }
        }

        if (!hasKeystore) {
            throw GradleException("Release build cannot start because the keystore file could not be resolved. Please configure KEYSTORE_FILE, KEYSTORE_BASE64, or place upload_release.keystore / aura-learning.jks in the project root directory.")
        }

        println("Keystore resolved successfully: $keystoreSource")

        // Map default settings if they are missing but we are falling back
        if (!isCi()) {
            if (storePasswordVar.isNullOrEmpty()) {
                println("WARNING: KEYSTORE_PASSWORD is not set. A default password will be mapped at build time.")
            }
            if (keyAliasVar.isNullOrEmpty()) {
                println("WARNING: KEY_ALIAS is not set. A default key alias will be mapped at build time.")
            }
            if (keyPasswordVar.isNullOrEmpty()) {
                println("WARNING: KEY_PASSWORD is not set. A default key password will be mapped at build time.")
            }
        }
        
        println("==================================================")
        println("   VALIDATION COMPLETED SUCCESSFULLY              ")
        println("==================================================")
    }
}

// Ensure validation runs before task execution for preReleaseBuild or assembleRelease
tasks.whenTaskAdded {
    if (name == "preReleaseBuild") {
        dependsOn("validateReleaseSigning")
    }
}
