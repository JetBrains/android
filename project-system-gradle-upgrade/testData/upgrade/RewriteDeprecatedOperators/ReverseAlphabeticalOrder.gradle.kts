android {
  defaultConfig {
    setTestHandleProfiling(true)
    setTestFunctionalTest(false)
    targetSdkVersion(29)
    resConfigs("en", "fr")
    minSdkVersion(28)
    maxSdkVersion(30)
    setMatchingFallbacks(listOf("demo", "trial"))
    setManifestPlaceholders(mapOf("a" to "b"))
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
      setManifestPlaceholders(mapOf("e" to "f", "g" to "h"))
      setDimension("paid")
    }
  }
  buildTypes {
    create("mumble") {
      setMatchingFallbacks(listOf("demo"))
      setManifestPlaceholders(mapOf("c" to "d"))
    }
  }
  flavorDimensions("paid", "country")
  compileSdkVersion(30)
  buildToolsVersion("1.2.3")
}