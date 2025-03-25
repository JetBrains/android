androidApp {
  buildTypes {
    buildType("foo") {
      isMinifyEnabled = true
    }
    buildType("bar") {
      initWith(buildType("foo"))
    }
  }
}
