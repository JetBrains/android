android {
  buildToolsVersion("1.2.3")
  compileSdkVersion(30)
  flavorDimensions("paid", "country")
  defaultConfig {
    setManifestPlaceholders(mapOf("a" to "b"))
    setMatchingFallbacks(listOf("demo", "trial"))
    maxSdkVersion(30)
    minSdkVersion(28)
    resConfigs("en", "fr")
    targetSdkVersion(29)
    setTestFunctionalTest(true)
    setTestHandleProfiling(false)
    setTestInstrumentationRunnerArguments(mapOf("one" to "two"))
  }
  buildTypes {
    create("mumble") {
      setManifestPlaceholders(mapOf("c" to "d"))
      setMatchingFallbacks(listOf("demo"))
    }
  }
  productFlavors {
    create("foo") {
      setDimension("paid")
      setManifestPlaceholders(mapOf("e" to "f", "g" to "h"))
      setMatchingFallbacks("trial")
      maxSdkVersion(29)
      minSdkVersion(27)
      resConfig("uk")
      targetSdkVersion(28)
      setTestFunctionalTest(false)
      setTestHandleProfiling(true)
      setTestInstrumentationRunnerArguments(mapOf("three" to "four", "five" to "six"))
    }
  }
}