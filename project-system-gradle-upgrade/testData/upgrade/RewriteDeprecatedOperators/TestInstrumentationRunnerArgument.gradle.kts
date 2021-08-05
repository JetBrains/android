android {
  defaultConfig {
    testInstrumentationRunnerArgument("foo", "bar")
    testInstrumentationRunnerArgument("baz", "quux")
  }
  productFlavors {
    create("foo") {
      testInstrumentationRunnerArgument("a", "b")
    }
  }
}