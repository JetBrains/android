android {
  buildTypes {
    create("abc") {
      setUseProguard(false)
      setJniDebuggable(true)
    }
    create("xyz") {
      isDebuggable = false
      isUseProguard = true
    }
  }
}
