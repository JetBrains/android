plugins {
  id("com.android.application")
  id("org.jetbrains.kotlin.android")
}

android {
  compileSdk = 30
  packaging {
    jniLibs {
      useLegacyPackaging = true
    }
  }
}
