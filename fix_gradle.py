import re

with open("app/build.gradle.kts", "r") as f:
    content = f.read()

# Replace the whole android block
android_block = """android {
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
                val decodedBytes = java.util.Base64.getDecoder().decode(keystoreBase64)
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
}"""

content = re.sub(r"android\s*\{.*?(?=\ndependencies\s*\{)", android_block + "\n", content, flags=re.DOTALL)

with open("app/build.gradle.kts", "w") as f:
    f.write(content)

