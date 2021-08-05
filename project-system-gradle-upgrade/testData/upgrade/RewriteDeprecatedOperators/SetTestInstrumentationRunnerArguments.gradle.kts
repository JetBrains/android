android {
  defaultConfig {
    setTestInstrumentationRunnerArguments(mapOf("foo" to "bar", "baz" to "quux"))
  }
  productFlavors {
    create("foo") {
      setTestInstrumentationRunnerArguments(mapOf("a" to "b"))
    }
  }
}