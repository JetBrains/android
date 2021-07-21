android {
  defaultConfig {
    testHandleProfiling = true
    testFunctionalTest = false
    targetSdk = 29
    resourceConfigurations += setOf("en", "fr")
    minSdk = 28
    maxSdk = 30
  }
  productFlavors {
    create("foo") {
      testHandleProfiling = false
      testFunctionalTest = true
      targetSdk = 28
      resourceConfigurations += setOf("uk")
      minSdk = 27
      maxSdk = 29
      dimension = "paid"
    }
  }
  flavorDimensions += listOf("paid", "country")
  compileSdk = 30
  buildToolsVersion = "1.2.3"
}