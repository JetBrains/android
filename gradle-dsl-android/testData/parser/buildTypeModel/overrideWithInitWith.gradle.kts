android {
  buildTypes {
    create("foo") {
      isDebuggable = true
      isMinifyEnabled = true
      buildConfigField("abcd", "efgh", "ijkl")
      applicationIdSuffix = ".foo"
    }
    create("bar") {
      initWith(getByName("foo"))
      isDebuggable = false
      applicationIdSuffix = ".bar"
    }
  }
}