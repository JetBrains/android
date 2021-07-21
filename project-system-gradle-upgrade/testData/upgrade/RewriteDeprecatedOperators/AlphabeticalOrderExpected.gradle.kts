android {
  buildToolsVersion = "1.2.3"
  compileSdk = 30
  flavorDimensions += listOf("paid", "country")
  defaultConfig {
    maxSdk = 30
    minSdk = 28
    targetSdk = 29
    testFunctionalTest = true
    testHandleProfiling = false
  }
  productFlavors {
    create("foo") {
      dimension = "paid"
      maxSdk = 29
      minSdk = 27
      targetSdk = 28
      testFunctionalTest = false
      testHandleProfiling = true
    }
  }
}