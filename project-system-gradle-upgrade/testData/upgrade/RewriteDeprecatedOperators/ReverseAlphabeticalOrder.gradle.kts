android {
  defaultConfig {
    setTestHandleProfiling(true)
    setTestFunctionalTest(false)
    targetSdkVersion(29)
    minSdkVersion(28)
    maxSdkVersion(30)
  }
  productFlavors {
    create("foo") {
      setTestHandleProfiling(false)
      setTestFunctionalTest(true)
      targetSdkVersion(28)
      minSdkVersion(27)
      maxSdkVersion(29)
      setDimension("paid")
    }
  }
  flavorDimensions("paid", "country")
  compileSdkVersion(30)
  buildToolsVersion("1.2.3")
}