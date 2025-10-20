android {
  defaultConfig {
    manifestPlaceholders = mutableMapOf("activityLabel1" to "defaultName1", "activityLabel2" to "defaultName2")
    testInstrumentationRunnerArgument("size", "medium")
    testInstrumentationRunnerArgument("foo", "bar")
  }
}
