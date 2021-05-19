android {
  buildToolsVersion = "24.0.0"
  compileSdkVersion("24")
  defaultPublishConfig = "release"
  generatePureSplits = false
  namespace = "com.my.namespace"
  setPublishNonDefault(true)
  resourcePrefix("efgh")
  targetProjectPath = ":tpp"
  testNamespace = "com.my.namespace.test"
}
