plugins {
    alias(libs.plugins.android.application)
}

kotlin {
  compilerOptions {
    jvmTarget = org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_1_8
  }
}

android {
    namespace = "com.android.tools.screensharing.benchmark"
    compileSdk {
      version = release(36) {
        minorApiLevel = 1
      }
  }

    defaultConfig {
        applicationId = "com.android.tools.screensharing.benchmark"
        minSdk = 21
        targetSdk = 36
        versionCode = 1
        versionName = "1.0"
    }

    buildTypes {
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
            isDebuggable = false
            // We use the debug signing config here because we want to be able
            // to install this anywhere but we still want all of the shrinking,
            // etc. This app won't ever be distributed via the Play Store, so
            // we don't need a "release" signing config.
            signingConfig = signingConfigs.getByName("debug")
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_1_8
        targetCompatibility = JavaVersion.VERSION_1_8
    }
    buildFeatures {
        viewBinding = true
    }
}

dependencies {
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.appcompat)
    implementation(libs.material)
}
