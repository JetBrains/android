android {
  buildTypes {
    create("foo") {
      isDebuggable = true
      isMinifyEnabled = true
      buildConfigField("abcd", "efgh", "ijkl")
      applicationIdSuffix = ".foo"
    }
    create("bar") {
      initWith(buildTypes.getByName("foo"))
      isDebuggable = false
      applicationIdSuffix = ".bar"
    }
  }
}