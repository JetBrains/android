android {
  buildTypes {
    getByName("debug") {
      isDebuggable = false
    }
    getByName("release") {
      isDebuggable = true
    }
  }
}
