android {
  buildTypes {
    getByName("release") {
      isDebuggable = false
    }
    getByName("debug") {
      isDebuggable = true
    }
  }
}
