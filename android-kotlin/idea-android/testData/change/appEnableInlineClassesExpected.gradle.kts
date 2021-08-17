plugins {
  id("com.android.application")
}

android {
  compileSdkVersion(29)
  kotlinOptions {
    freeCompilerArgs = listOf("-Xinline-classes")
  }
}
