android {
  defaultConfig {
    testHandleProfiling = true
    testFunctionalTest = false
    targetSdk = 29
    minSdk = 28
    maxSdk = 30
  }
  productFlavors {
    create("foo") {
      testHandleProfiling = false
      testFunctionalTest = true
      targetSdk = 28
      minSdk = 27
      maxSdk = 29
      dimension = "paid"
    }
  }
  flavorDimensions += listOf("paid", "country")
  compileSdk = 30
  buildToolsVersion = "1.2.3"
}