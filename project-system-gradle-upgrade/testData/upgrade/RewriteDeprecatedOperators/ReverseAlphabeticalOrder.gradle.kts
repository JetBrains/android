android {
  defaultConfig {
    setTestHandleProfiling(true)
    setTestFunctionalTest(false)
    targetSdkVersion(29)
    resConfigs("en", "fr")
    minSdkVersion(28)
    maxSdkVersion(30)
    setMatchingFallbacks(listOf("demo", "trial"))
  }
  productFlavors {
    create("foo") {
      setTestHandleProfiling(false)
      setTestFunctionalTest(true)
      targetSdkVersion(28)
      resConfig("uk")
      minSdkVersion(27)
      maxSdkVersion(29)
      setMatchingFallbacks("trial")
      setDimension("paid")
    }
  }
  buildTypes {
    create("mumble") {
      setMatchingFallbacks(listOf("demo"))
    }
  }
  flavorDimensions("paid", "country")
  compileSdkVersion(30)
  buildToolsVersion("1.2.3")
}