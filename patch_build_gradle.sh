sed -i '/versionName = "1.0"/a \
    }\n\
    buildFeatures {\n\
        compose = true\n\
        buildConfig = true\n\
    }\n\
    \n\
    signingConfigs {\n\
        create("release") {\n\
            val storeFileVar = System.getenv("KEYSTORE_FILE") ?: System.getenv("KEYSTORE_PATH")\n\
            val storePasswordVar = System.getenv("KEYSTORE_PASSWORD")\n\
            val keyAliasVar = System.getenv("KEY_ALIAS")\n\
            val keyPasswordVar = System.getenv("KEY_PASSWORD")\n\
            val keystoreBase64 = System.getenv("KEYSTORE_BASE64")\n\
            var keystoreFile: java.io.File? = null\n\
            \n\
            if (storeFileVar != null) {\n\
                keystoreFile = rootProject.file(storeFileVar)\n\
            } else if (keystoreBase64 != null && keystoreBase64.isNotEmpty()) {\n\
                val decodedBytes = java.util.Base64.getDecoder().decode(keystoreBase64)\n\
                keystoreFile = rootProject.file("upload_release.keystore")\n\
                keystoreFile.writeBytes(decodedBytes)\n\
            }\n\
            \n\
            if (keystoreFile == null || !keystoreFile.exists()) {\n\
                throw GradleException("Release keystore file not found! Please set KEYSTORE_FILE or KEYSTORE_BASE64 in Secrets.")\n\
            }\n\
            if (storePasswordVar.isNullOrEmpty()) {\n\
                throw GradleException("KEYSTORE_PASSWORD not set in Secrets!")\n\
            }\n\
            if (keyAliasVar.isNullOrEmpty()) {\n\
                throw GradleException("KEY_ALIAS not set in Secrets!")\n\
            }\n\
            if (keyPasswordVar.isNullOrEmpty()) {\n\
                throw GradleException("KEY_PASSWORD not set in Secrets!")\n\
            }\n\
            \n\
            storeFile = keystoreFile\n\
            storePassword = storePasswordVar\n\
            keyAlias = keyAliasVar\n\
            keyPassword = keyPasswordVar\n\
        }\n\
    }\n\
    \n\
    buildTypes {\n\
        release {\n\
            isMinifyEnabled = true\n\
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")\n\
            signingConfig = signingConfigs.getByName("release")\n\
        }\n\
    }' /app/applet/app/build.gradle.kts
