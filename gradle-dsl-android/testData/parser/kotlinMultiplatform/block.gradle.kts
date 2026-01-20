kotlin {
  android {
    namespace = "abc"
    compileSdk {
        version = release(36) {
          minorApiLevel = 1
        }
      }
    minSdk = 30
  }
}