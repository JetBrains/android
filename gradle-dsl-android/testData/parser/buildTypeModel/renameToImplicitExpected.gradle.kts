android {
  buildTypes {
    getByName("release") {
      isDebuggable = true
    }
    getByName("debug") {
      isDebuggable = false
    }
  }
}
