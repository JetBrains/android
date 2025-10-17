val sdkVersion by extra(30)
android {
  compileSdk {
    version = release(sdkVersion)
  }
}