android {
  buildToolsVersion = "1.2.3"
  compileSdkVersion(30)
  flavorDimensions += listOf("paid", "country")
  defaultConfig {
    manifestPlaceholders += mapOf("a" to "b")
    matchingFallbacks += listOf("demo", "trial")
    maxSdkVersion(30)
    minSdkVersion(28)
    resourceConfigurations += setOf("en", "fr")
    targetSdkVersion(29)
    testFunctionalTest = true
    testHandleProfiling = false
    testInstrumentationRunnerArguments += mapOf("one" to "two")
  }
  buildTypes {
    create("mumble") {
      manifestPlaceholders += mapOf("c" to "d")
      matchingFallbacks += listOf("demo")
    }
  }
  productFlavors {
    create("foo") {
      dimension = "paid"
      manifestPlaceholders += mapOf("e" to "f", "g" to "h")
      matchingFallbacks += listOf("trial")
      maxSdkVersion(29)
      minSdkVersion(27)
      resourceConfigurations += setOf("uk")
      targetSdkVersion(28)
      testFunctionalTest = false
      testHandleProfiling = true
      testInstrumentationRunnerArguments += mapOf("three" to "four", "five" to "six")
    }
  }
}