android {
  buildTypes {
    create("notRelease") {
      isDebuggable = true
    }
    create("notDebug") {
      isDebuggable = false
    }
  }
}
