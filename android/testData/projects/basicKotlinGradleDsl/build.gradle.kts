plugins {
  alias(libs.plugins.android.application) apply true
  alias(libs.plugins.kotlinAndroid) apply true
}

android {
  namespace = "com.example.kotlingradle"
  compileSdk = 33

  defaultConfig {
    applicationId = "com.example.kotlingradle"
    minSdk = 24
    targetSdk = 33
    versionCode = 1
    versionName = "1.0"
  }

  compileOptions {
    sourceCompatibility = JavaVersion.VERSION_1_8
    targetCompatibility = JavaVersion.VERSION_1_8
  }
  kotlinOptions {
    jvmTarget = "1.8"
  }
}

dependencies {
  implementation("androidx.core:core-ktx:1.6.0")
  implementation("androidx.appcompat:appcompat:1.3.0")
}