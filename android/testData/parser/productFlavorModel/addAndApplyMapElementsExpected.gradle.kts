android {
  defaultConfig {
    manifestPlaceholders = mubableMapOf("activityLabel1" to "newName1", "activityLabel2" to "newName2")
    testInstrumentationRunnerArguments = mutableMapOf("size" to "small", "key" to "value")
  }
}
