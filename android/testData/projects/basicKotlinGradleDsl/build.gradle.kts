plugins {
  alias(libs.plugins.android.application) apply true
  alias(libs.plugins.kotlinAndroid) apply true
}

android {
  namespace = "com.example.kotlingradle"
  compileSdk { version = release(33) }

  defaultConfig {
    applicationId = "com.example.kotlingradle"
    minSdk { version = release(24) }
    targetSdk { version = release(33) }
    versionCode = 1
    versionName = "1.0"
  }

  compileOptions {
    sourceCompatibility = JavaVersion.VERSION_11
    targetCompatibility = JavaVersion.VERSION_11
  }
}

kotlin {
  compilerOptions {
    jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_11)
  }
}

dependencies {
  implementation("androidx.core:core-ktx:1.6.0")
  implementation("androidx.appcompat:appcompat:1.3.0")
}