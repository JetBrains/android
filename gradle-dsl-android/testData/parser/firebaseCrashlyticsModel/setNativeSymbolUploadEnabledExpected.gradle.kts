android {
  buildTypes {
    getByName("release") {
      firebaseCrashlytics {
        nativeSymbolUploadEnabled = true
      }
    }
  }
}
