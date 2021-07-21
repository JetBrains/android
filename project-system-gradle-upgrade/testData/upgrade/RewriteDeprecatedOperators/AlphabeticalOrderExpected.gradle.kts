android {
  buildToolsVersion = "1.2.3"
  compileSdk = 30
  flavorDimensions += listOf("paid", "country")
  defaultConfig {
    maxSdk = 30
    minSdk = 28
    resourceConfigurations += setOf("en", "fr")
    targetSdk = 29
    testFunctionalTest = true
    testHandleProfiling = false
  }
  productFlavors {
    create("foo") {
      dimension = "paid"
      maxSdk = 29
      minSdk = 27
      resourceConfigurations += setOf("uk")
      targetSdk = 28
      testFunctionalTest = false
      testHandleProfiling = true
    }
  }
}