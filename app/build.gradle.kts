plugins {
    alias(libs.plugins.android.application)
}

android {
    namespace = "com.c520.shengdewoxin"
    compileSdk = 36

    defaultConfig {
        applicationId = "com.c520.shengdewoxin"
        // Android 12 起：裝置端語音、RoleManager、FUSED 定位、App 自己的深淺色設定都有，程式不用寫舊版分支
        minSdk = 31
        targetSdk = 36
        versionCode = 2
        versionName = "0.2.0"
    }

    // 正式版簽章：金鑰路徑與密碼放在使用者的 ~/.gradle/gradle.properties（shengdewoxin.*），不進版控。
    // 沒有設定的電腦照樣能編 debug 版，只是編不出簽好章的正式版。
    val releaseStore = providers.gradleProperty("shengdewoxin.storeFile").orNull
    signingConfigs {
        if (releaseStore != null) {
            create("release") {
                storeFile = file(releaseStore)
                storePassword = providers.gradleProperty("shengdewoxin.storePassword").get()
                keyAlias = providers.gradleProperty("shengdewoxin.keyAlias").get()
                keyPassword = providers.gradleProperty("shengdewoxin.keyPassword").get()
            }
        }
    }

    buildTypes {
        release {
            signingConfig = signingConfigs.findByName("release")
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    lint {
        error += listOf(
            "ExportedReceiver", "ExportedService", "UnsafeImplicitIntentLaunch",
            "MutableImplicitPendingIntent", "UnspecifiedRegisterReceiverFlag",
        )
    }
}

kotlin {
    jvmToolchain(17)
}
