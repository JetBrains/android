android {
  defaultConfig {
    testInstrumentationRunnerArguments += mapOf("three" to "four", "five" to "six")
    testHandleProfiling = true
    testFunctionalTest = false
    targetSdk = 29
    resourceConfigurations += setOf("en", "fr")
    minSdk = 28
    maxSdk = 30
    matchingFallbacks += listOf("demo", "trial")
    manifestPlaceholders += mapOf("a" to "b")
  }
  productFlavors {
    create("foo") {
      testInstrumentationRunnerArguments += mapOf("one" to "two")
      testHandleProfiling = false
      testFunctionalTest = true
      targetSdk = 28
      resourceConfigurations += setOf("uk")
      minSdk = 27
      maxSdk = 29
      matchingFallbacks += listOf("trial")
      manifestPlaceholders += mapOf("e" to "f", "g" to "h")
      dimension = "paid"
    }
  }
  buildTypes {
    create("mumble") {
      matchingFallbacks += listOf("demo")
      manifestPlaceholders += mapOf("c" to "d")
    }
  }
  flavorDimensions += listOf("paid", "country")
  compileSdk = 30
  buildToolsVersion = "1.2.3"
}