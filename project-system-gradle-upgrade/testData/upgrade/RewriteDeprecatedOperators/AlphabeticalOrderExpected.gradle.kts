android {
  buildToolsVersion = "1.2.3"
  compileSdk = 30
  flavorDimensions += listOf("paid", "country")
  defaultConfig {
    matchingFallbacks += listOf("demo", "trial")
    maxSdk = 30
    minSdk = 28
    resourceConfigurations += setOf("en", "fr")
    targetSdk = 29
    testFunctionalTest = true
    testHandleProfiling = false
  }
  buildTypes {
    create("mumble") {
      matchingFallbacks += listOf("demo")
    }
  }
  productFlavors {
    create("foo") {
      dimension = "paid"
      matchingFallbacks += listOf("trial")
      maxSdk = 29
      minSdk = 27
      resourceConfigurations += setOf("uk")
      targetSdk = 28
      testFunctionalTest = false
      testHandleProfiling = true
    }
  }
}