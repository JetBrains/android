android {
  buildToolsVersion("23.0.0")
  compileSdkVersion("android-J")
  defaultPublishConfig("debug")
  generatePureSplits(true)
  setPublishNonDefault(false)
  resourcePrefix("abcd")
  targetProjectPath(":tpp")
}
