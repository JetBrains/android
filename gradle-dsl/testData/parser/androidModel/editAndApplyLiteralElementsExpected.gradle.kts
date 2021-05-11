android {
  buildToolsVersion("24.0.0")
  compileSdkVersion("24")
  defaultPublishConfig("release")
  generatePureSplits(false)
  namespace = "com.my.namespace2"
  setPublishNonDefault(true)
  resourcePrefix("efgh")
  targetProjectPath(":tpp2")
  testNamespace = "com.my.namespace2.test"
}
