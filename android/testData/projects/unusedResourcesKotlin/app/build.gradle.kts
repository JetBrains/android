plugins {
  id("com.android.application")
  kotlin("android")
  kotlin("android.extensions")
}
apply(plugin = "com.android.application")

android {
  compileSdkVersion(19)
  buildToolsVersion("19.1.0")

  defaultConfig {
    targetSdkVersion(19)
    applicationId = "com.example.android.app"
    resValue("string", "APP_KEY", "abc")
  }
}

repositories {}
