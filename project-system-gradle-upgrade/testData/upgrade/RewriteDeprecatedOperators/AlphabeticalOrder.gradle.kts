android {
  buildToolsVersion("1.2.3")
  compileSdkVersion(30)
  flavorDimensions("paid", "country")
  defaultConfig {
    setMatchingFallbacks(listOf("demo", "trial"))
    maxSdkVersion(30)
    minSdkVersion(28)
    resConfigs("en", "fr")
    targetSdkVersion(29)
    setTestFunctionalTest(true)
    setTestHandleProfiling(false)
  }
  buildTypes {
    create("mumble") {
      setMatchingFallbacks(listOf("demo"))
    }
  }
  productFlavors {
    create("foo") {
      setDimension("paid")
      setMatchingFallbacks("trial")
      maxSdkVersion(29)
      minSdkVersion(27)
      resConfig("uk")
      targetSdkVersion(28)
      setTestFunctionalTest(false)
      setTestHandleProfiling(true)
    }
  }
}