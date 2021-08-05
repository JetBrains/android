android {
  defaultConfig {
    testInstrumentationRunnerArguments(mapOf("foo" to "bar", "baz" to "quux"))
  }
  productFlavors {
    create("foo") {
      testInstrumentationRunnerArguments(mapOf("a" to "b"))
    }
  }
}