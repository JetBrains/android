android {
  buildTypes {
    create("abc") {
      setJniDebuggable(true)
    }
    create("xyz") {
      isDebuggable = false
    }
  }
}
