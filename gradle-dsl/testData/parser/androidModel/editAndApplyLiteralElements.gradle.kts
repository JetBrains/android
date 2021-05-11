android {
  buildToolsVersion("23.0.0")
  compileSdkVersion("23")
  defaultPublishConfig("debug")
  generatePureSplits(true)
  namespace = "com.my.namespace"
  setPublishNonDefault(false)
  resourcePrefix("abcd")
  targetProjectPath(":tpp")
  testNamespace = "com.my.namespace.test"
}
