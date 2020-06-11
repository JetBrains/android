android {
  buildTypes {
    create("newDebug") {
      isDebuggable = false
    }
    create("newRelease") {
      isDebuggable = true
    }
  }
}
