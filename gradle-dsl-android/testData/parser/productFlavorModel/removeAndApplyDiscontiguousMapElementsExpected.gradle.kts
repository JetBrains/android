android {
  defaultConfig {
    manifestPlaceholders = mutableMapOf("activityLabel2" to "defaultName2")
    testInstrumentationRunnerArgument("foo", "bar")
  }
}
