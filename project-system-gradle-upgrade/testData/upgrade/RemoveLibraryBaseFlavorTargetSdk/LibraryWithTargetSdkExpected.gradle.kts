plugins {
  id("com.android.library")
  kotlin("android")
}
android {
  compileSdk = 30
  defaultConfig {
    minSdk = 21
  }
  lint {
    targetSdk = 27
  }
  testOptions {
    targetSdk = 27
  }
}