android {
  buildTypes {
    getByName("release") {
      firebaseCrashlytics {
        nativeSymbolUploadEnabled = false
      }
    }
  }
}
