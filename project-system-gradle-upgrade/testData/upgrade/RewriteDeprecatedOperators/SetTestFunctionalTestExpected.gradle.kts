android {
  defaultConfig {
    testFunctionalTest = true
  }
  productFlavors {
    create("foo") {
      testFunctionalTest = false
    }
  }
}
